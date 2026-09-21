# -*- coding: utf-8 -*-
"""쇼케이스 시나리오 전수 생성기.

보장하는 것
  1) 시드 유저 20명 x 39일 = 780일이 빠짐없이 존재한다.
  2) 하루마다 대화 3회 이상, 고유 장소 5곳 이상.
  3) 만남은 양쪽 하루에 같은 시각/장소로 똑같이 들어간다.
  4) 지역 간 이동 시간을 지키고, 수업은 본인 학교에서만 듣는다.
  5) 기존 호감도 곡선(182쌍 566건)의 날짜와 값을 그대로 유지한다.
"""
import collections, datetime, json, os, random, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import geo, profiles, dialogue, extra_content, weather

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
OUT = os.path.join(REPO, "backend/src/main/resources/seed/showcase-scenarios.json")
ASSETS = profiles.ASSETS
PLACES = ASSETS["places"]
USERS = profiles.USERS
CURVES = ASSETS["curves"]
ANCHOR = ASSETS["anchorDate"]

START = datetime.date(2026, 8, 20)
DAYS = 39
DATES = [START + datetime.timedelta(days=i) for i in range(DAYS)]

MIN_DIALOGUES = 3
MIN_PLACES = 5

# (시작, 끝) 분 단위. 지역이 바뀌려면 앞 슬롯 끝과 다음 슬롯 시작 사이가 이동 시간보다 커야 한다.
WEEKDAY_SLOTS = [("08:10", "08:45"), ("12:05", "13:00"), ("14:30", "15:40"),
                 ("16:10", "17:20"), ("18:20", "19:40"), ("20:10", "21:30"),
                 ("21:50", "22:50")]
WEEKEND_SLOTS = [("09:40", "10:40"), ("11:10", "12:20"), ("12:50", "14:00"),
                 ("14:30", "15:50"), ("16:20", "17:40"), ("18:10", "19:30"),
                 ("20:00", "21:20")]
# 평일에 지역을 바꿔도 되는 슬롯 경계 (앞 슬롯 끝 ~ 다음 슬롯 시작 여유가 큰 지점)
WEEKDAY_AWAY_START = 2          # 오후부터 원정 가능
WEEKEND_AWAY_START = 0          # 주말은 아침부터 원정 가능

SLOT_ROLE_WEEKDAY = ["commute", "meal", "study", "cafe", "meal", "night", "outdoor"]
SLOT_ROLE_WEEKEND = ["cafe", "play", "meal", "play", "outdoor", "meal", "night"]
MEETING_ROLES = ["meal", "cafe", "night", "play", "study", "outdoor", "commute"]

rng = random.Random(20260920)


def hm(date, s):
    h, m = s.split(":")
    return datetime.datetime.combine(date, datetime.time(int(h), int(m)))


# ---------------------------------------------------------------- 문장 풀
def _role_pool(field):
    pool = collections.defaultdict(list)
    for place, meta in PLACES.items():
        pool[profiles.role_of(place)].extend(meta[field])
    return {k: sorted(set(v)) for k, v in pool.items()}


NARR_BY_ROLE = _role_pool("narration")
MIND_BY_ROLE = _role_pool("mind")
DNARR_BY_ROLE = _role_pool("dialogNarr")


class Pools:
    """같은 유저가 같은 문장을 반복해서 보지 않도록 유저별로 소진식 추첨을 한다."""

    def __init__(self):
        self.bag = {}

    def take(self, key, options, rnd):
        if not options:
            return None
        left = self.bag.get(key)
        if not left:
            left = list(options)
            rnd.shuffle(left)
            self.bag[key] = left
        return left.pop()


POOLS = Pools()


def bucket_of(hour):
    return "morning" if hour < 11 else ("day" if hour < 18 else "night")


def _pool(place, role, hour, field, by_time, generic_index, home_pair):
    meta = PLACES[place]
    if place in profiles.HOME_PLACES:
        base = meta[by_time].get(bucket_of(hour), [])
        return sorted(set(base) | set(home_pair)), place + bucket_of(hour)
    base = meta[by_time].get(bucket_of(hour))
    if not base and len(meta[by_time]) <= 1:
        base = meta[field]
    if base:
        return sorted(set(base)), place + bucket_of(hour)
    pair = extra_content.GENERIC.get(role) or extra_content.GENERIC["outdoor"]
    return pair[generic_index], "generic:" + role


# 지금 만들고 있는 날짜의 날씨. generate() 가 날짜마다 바꾼다.
SKY = weather.CLEAR


def _sky_fit(opts, role, generic_index, key):
    """그날 날씨와 안 맞는 문장을 뺀다. 다 빠지면 장소 중립 문장으로 대신한다."""
    fit = [o for o in opts if weather.text_ok(o, SKY)]
    if fit:
        return fit, key + ":" + SKY
    pair = extra_content.GENERIC.get(role) or extra_content.GENERIC["outdoor"]
    return [o for o in pair[generic_index] if weather.text_ok(o, SKY)], "generic:" + role + ":" + SKY


def narration_for(user, place, hour):
    role = profiles.role_of(place)
    home = extra_content.HOME_MORNING if bucket_of(hour) == "morning" else extra_content.HOME_NIGHT
    opts, key = _pool(place, role, hour, "narration", "narrationByTime", 0, home)
    opts, key = _sky_fit(opts, role, 0, key)
    return POOLS.take(("n", user, key), opts, rng)


def mind_for(user, place, hour):
    role = profiles.role_of(place)
    opts, key = _pool(place, role, hour, "mind", "mindByTime", 1, extra_content.HOME_MIND)
    opts, key = _sky_fit(opts, role, 1, key)
    return POOLS.take(("m", user, key), opts, rng)


# ---------------------------------------------------------------- 장소 선택
CATALOG = profiles.build_catalogs()
BY_USER_REGION_ROLE = {}
for uid in USERS:
    table = collections.defaultdict(list)
    for role, places in CATALOG[uid].items():
        for place in places:
            kind = geo.classify(place)
            if kind == "TRANSIT":
                continue
            if kind == "FOLLOW":
                table[(geo.HOME_REGION[uid], role)].append(place)
                continue
            table[(geo.region_of(place, uid), role)].append(place)
    BY_USER_REGION_ROLE[uid] = table


OPEN_AIR_KINDS = {"baseball", "stage", "street", "alley"}


def open_air(place):
    return profiles.role_of(place) == "outdoor" or dialogue.kind_of(place) in OPEN_AIR_KINDS


def place_ok(place, hour, allow_open_air):
    if not profiles.time_ok(place, hour):
        return False
    return allow_open_air or SKY != weather.RAIN or not open_air(place)


def pick_place(uid, region, role, used, rnd, hour=12):
    table = BY_USER_REGION_ROLE[uid]
    order = [role] + [r for r in ("cafe", "meal", "outdoor", "play", "study", "night",
                                  "commute", "store") if r != role]
    for allow_open_air in (False, True):
        for allow_used in (False, True):
            for r in order:
                cands = [p for p in table.get((region, r), [])
                         if place_ok(p, hour, allow_open_air) and (allow_used or p not in used)]
                if cands:
                    return rnd.choice(cands)
    return None


def shared_place(a, b, region, role, taken, hour, rnd):
    ta, tb = BY_USER_REGION_ROLE[a], BY_USER_REGION_ROLE[b]
    order = [role] + [r for r in MEETING_ROLES if r != role]
    for allow_open_air in (False, True):
        for allow_taken in (False, True):
            pool = []
            for r in order:
                cands = sorted(set(ta.get((region, r), [])) & set(tb.get((region, r), [])))
                cands = [p for p in cands if place_ok(p, hour, allow_open_air)
                         and (allow_taken or p not in taken)]
                if cands:
                    pool = cands
                    break
            if pool:
                return rnd.choice(pool)
    return None


# ---------------------------------------------------------------- 만남 일정
def forced_pairs(date_str):
    out = []
    for key, by_date in CURVES.items():
        if date_str in by_date:
            a, b = (int(x) for x in key.split("-"))
            out.append((a, b))
    return out


def components(pairs):
    parent = {}

    def find(x):
        parent.setdefault(x, x)
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    for a, b in pairs:
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[ra] = rb
    groups = collections.defaultdict(list)
    for a, b in pairs:
        groups[find(a)].append((a, b))
    return list(groups.values())


def plan_regions(date, pairs):
    """유저별 원정 계획을 정한다. plan[uid] = (원정지역 또는 None, 시작 슬롯)."""
    weekend = date.weekday() >= 5
    away_start = WEEKEND_AWAY_START if weekend else WEEKDAY_AWAY_START
    plan = {uid: (None, 99) for uid in USERS}

    for group in components(pairs):
        members = sorted({u for pair in group for u in pair})
        schools = {USERS[u]["school"] for u in members}
        if len(schools) == 1:
            continue  # 같은 학교끼리는 자기 캠퍼스에서 만난다
        # 여러 학교가 섞이면 공통 지역이 필요하다
        if weekend:
            region = rng.choice(sorted(profiles.OUTING_REGIONS))
        else:
            region = rng.choice(sorted(profiles.OUTING_REGIONS) +
                                [geo.SCHOOL_OF[u] for u in members])
        for u in members:
            if geo.HOME_REGION[u] != region:
                plan[u] = (region, away_start)

    # 약속이 없어 혼자 남는 사람은 사람이 모인 곳으로 합류시킨다.
    tied = {u for pair in pairs for u in pair}
    for _ in range(2):
        pop = collections.Counter(region_at(plan, u, away_start) for u in USERS)
        alone = [u for u in USERS
                 if u not in tied and pop[region_at(plan, u, away_start)] <= 1]
        if not alone:
            break
        for u in alone:
            target = max((r for r in pop if r != region_at(plan, u, away_start)),
                         key=lambda r: pop[r], default=None)
            if target is None:
                continue
            plan[u] = (None, 99) if target == geo.HOME_REGION[u] else (target, away_start)
            pop[target] += 1
    return plan


def region_at(plan, uid, slot):
    away, since = plan[uid]
    return away if (away and slot >= since) else geo.HOME_REGION[uid]


def assign_slots(date, pairs, plan):
    """간선 색칠: 같은 유저가 한 슬롯에 두 만남을 갖지 않도록 슬롯을 배정한다."""
    weekend = date.weekday() >= 5
    away_start = WEEKEND_AWAY_START if weekend else WEEKDAY_AWAY_START
    slots = WEEKEND_SLOTS if weekend else WEEKDAY_SLOTS
    busy = collections.defaultdict(set)
    meetings = []

    unplaced = []
    degree = collections.Counter()
    for a, b in pairs:
        degree[a] += 1
        degree[b] += 1
    for a, b in sorted(pairs, key=lambda p: -(degree[p[0]] + degree[p[1]])):
        same_region = [i for i in range(len(slots))
                       if region_at(plan, a, i) == region_at(plan, b, i)]
        cands = [i for i in same_region if i not in busy[a] and i not in busy[b]]
        if USERS[a]["school"] != USERS[b]["school"] and not weekend:
            cands = [i for i in cands if i >= away_start] or cands
        if not cands:
            unplaced.append((a, b))
            continue
        slot = rng.choice(cands)
        busy[a].add(slot)
        busy[b].add(slot)
        meetings.append({"slot": slot, "members": [a, b], "region": region_at(plan, a, slot),
                         "rapportPairs": [(a, b)]})

    # 지역이 겹치는 슬롯을 못 찾은 고정 쌍은 원정 시작을 앞당겨 자리를 만든다.
    for a, b in unplaced:
        done = False
        for earlier in range(0, len(slots)):
            for u in (a, b):
                away, since = plan[u]
                if away and since > earlier:
                    plan[u] = (away, earlier)
            cands = [i for i in range(len(slots))
                     if region_at(plan, a, i) == region_at(plan, b, i)
                     and i not in busy[a] and i not in busy[b]]
            if cands:
                slot = rng.choice(cands)
                busy[a].add(slot)
                busy[b].add(slot)
                meetings.append({"slot": slot, "members": [a, b],
                                 "region": region_at(plan, a, slot), "rapportPairs": [(a, b)]})
                done = True
                break
        if not done:
            meetings.append({"slot": None, "members": [a, b], "region": None,
                             "rapportPairs": [(a, b)], "unplaced": True})
    return meetings, busy


def add_filler(date, plan, meetings, busy):
    """대화 3회를 채운다. 짝이 없으면 같은 자리의 만남에 한 명 더 끼워 3자 대화로 만든다."""
    weekend = date.weekday() >= 5
    slots = WEEKEND_SLOTS if weekend else WEEKDAY_SLOTS
    count = collections.Counter()
    for m in meetings:
        for u in m["members"]:
            count[u] += 1
    met = {frozenset(m["members"]) for m in meetings}

    def need(u):
        return MIN_DIALOGUES - count[u]

    for slot in rng.sample(range(len(slots)), len(slots)):
        by_region = collections.defaultdict(list)
        for uid in USERS:
            if slot in busy[uid] or need(uid) <= 0:
                continue
            by_region[region_at(plan, uid, slot)].append(uid)
        for region, members in by_region.items():
            rng.shuffle(members)
            members.sort(key=lambda u: -need(u))
            while len(members) >= 2:
                a = members.pop(0)
                idx = next((i for i, b in enumerate(members)
                            if frozenset((a, b)) not in met), 0)
                b = members.pop(idx)
                meetings.append({"slot": slot, "members": sorted((a, b)), "region": region,
                                 "rapportPairs": []})
                met.add(frozenset((a, b)))
                busy[a].add(slot)
                busy[b].add(slot)
                count[a] += 1
                count[b] += 1

    for _ in range(4):
        short = [u for u in USERS if need(u) > 0]
        if not short:
            break
        progressed = False
        for uid in sorted(short, key=lambda u: -need(u)):
            for slot in rng.sample(range(len(slots)), len(slots)):
                if slot in busy[uid]:
                    continue
                region = region_at(plan, uid, slot)
                free = [v for v in USERS if v != uid and slot not in busy[v]
                        and region_at(plan, v, slot) == region]
                if free:
                    free.sort(key=lambda v: (frozenset((uid, v)) in met, -need(v)))
                    other = free[0]
                    meetings.append({"slot": slot, "members": sorted((uid, other)),
                                     "region": region, "rapportPairs": []})
                    met.add(frozenset((uid, other)))
                    busy[uid].add(slot)
                    busy[other].add(slot)
                    count[uid] += 1
                    count[other] += 1
                    progressed = True
                    break
                joinable = [m for m in meetings
                            if m["slot"] == slot and m["region"] == region
                            and len(m["members"]) == 2]
                if joinable:
                    m = rng.choice(joinable)
                    m["members"] = sorted(m["members"] + [uid])
                    busy[uid].add(slot)
                    count[uid] += 1
                    progressed = True
                    break
            if need(uid) <= 0:
                continue
        if not progressed:
            break
    return meetings


def schedule(date):
    date_str = date.isoformat()
    pairs = forced_pairs(date_str)
    plan = plan_regions(date, pairs)
    meetings, busy = assign_slots(date, pairs, plan)
    meetings = add_filler(date, plan, meetings, busy)
    meetings.sort(key=lambda m: m["slot"])
    return plan, meetings


# ---------------------------------------------------------------- 하루 만들기
TRANSIT_PLACES = ["지하철 2호선 안", "버스 창가 자리", "환승역 계단"]
MORNING_WEEKDAY = [("07:00", "07:35"), ("07:45", "08:02")]
MORNING_WEEKEND = [("08:15", "08:55"), ("09:05", "09:25")]
MORNING_WEEKEND_EARLY = [("06:50", "07:25"), ("07:35", "07:55")]
MORNING_WEEKDAY_EARLY = [("06:40", "07:10"), ("07:20", "07:40")]
NIGHT_WINDOWS = [("23:05", "23:30"), ("23:36", "23:56")]


DEPT_PATTERN = re.compile(r"\S+학과 전공 수업")


def action_scene(uid, place, start, end, seen=None):
    hour = start.hour
    narration = narration_for(uid, place, hour)
    for _ in range(6):
        if seen is None or narration not in seen:
            break
        narration = narration_for(uid, place, hour)
    narration = DEPT_PATTERN.sub(USERS[uid]["dept"] + " 전공 수업", narration)
    if seen is not None:
        seen.add(narration)
    return {
        "start": start.isoformat(timespec="seconds"),
        "end": end.isoformat(timespec="seconds"),
        "type": "action",
        "place": place,
        "with": [],
        "narration": narration,
        "mind": mind_for(uid, place, hour) if rng.random() < 0.92 else None,
    }


def dialogue_scene(place, start, end, script, cast):
    lines = dialogue.build_lines(rng, script, cast, start, end)
    return {
        "start": start.isoformat(timespec="seconds"),
        "end": end.isoformat(timespec="seconds"),
        "lines": lines,
    }


def stage_between(a, b, date_str):
    key = "-".join(sorted((str(a), str(b)), key=int))
    curve = CURVES.get(key)
    if not curve:
        return 1
    past = [v["rapport"] for d, v in curve.items() if d <= date_str]
    return dialogue.STAGE_OF(max(past) if past else min(v["rapport"] for v in curve.values()))


def build_day(uid, date, plan, meetings):
    date_str = date.isoformat()
    weekend = date.weekday() >= 5
    slots = WEEKEND_SLOTS if weekend else WEEKDAY_SLOTS
    roles = SLOT_ROLE_WEEKEND if weekend else SLOT_ROLE_WEEKDAY
    mornings = MORNING_WEEKEND if weekend else MORNING_WEEKDAY
    away_region, away_from = plan[uid]
    if away_region and away_from < len(slots):
        need_first = geo.travel_minutes(geo.HOME_REGION[uid], away_region)
        latest = hm(date, slots[away_from][0]) - datetime.timedelta(minutes=need_first + 6)
        if hm(date, mornings[1][1]) > latest:
            floor = hm(date, "05:40")
            m2s, m2e = latest - datetime.timedelta(minutes=20), latest
            m1s, m1e = latest - datetime.timedelta(minutes=68), latest - datetime.timedelta(minutes=33)
            if m1s < floor:
                m1s, m1e = floor, floor + datetime.timedelta(minutes=30)
                m2s = max(m1e + datetime.timedelta(minutes=8), m2s)
            if m2s <= m1e:
                m2s, m2e = None, None
            mornings = [(m1s.strftime("%H:%M"), m1e.strftime("%H:%M"))]
            if m2s:
                mornings.append((m2s.strftime("%H:%M"), m2e.strftime("%H:%M")))
    home_place = USERS[uid]["home"]
    home_region = geo.HOME_REGION[uid]

    mine = {m["slot"]: m for m in meetings if uid in m["members"]}
    scenes = []
    used = set()
    seen = set()

    scenes.append(action_scene(uid, home_place, hm(date, mornings[0][0]), hm(date, mornings[0][1]), seen))
    used.add(home_place)
    if len(mornings) > 1 and rng.random() < 0.75:
        p = pick_place(uid, home_region, "store" if rng.random() < 0.4 else "commute",
                       used, rng, int(mornings[1][0][:2])) or home_place
        scenes.append(action_scene(uid, p, hm(date, mornings[1][0]), hm(date, mornings[1][1]), seen))
        used.add(p)

    cur_region = home_region
    last_end = datetime.datetime.fromisoformat(scenes[-1]["end"])

    def insert_lectures():
        nonlocal last_end
        if weekend or away_from < 2:
            return
        for window, chance in ((("09:10", "10:25"), 0.82), (("10:45", "11:55"), 0.6)):
            if rng.random() > chance:
                continue
            if hm(date, window[0]) <= last_end:
                continue
            room = pick_place(uid, home_region, "lecture", used, rng, int(window[0][:2]))
            if not room or profiles.role_of(room) != "lecture":
                continue
            scenes.append(action_scene(uid, room, hm(date, window[0]), hm(date, window[1]), seen))
            used.add(room)
            last_end = hm(date, window[1])

    for i, (s, e) in enumerate(slots):
        if i == 1:
            insert_lectures()
        region = region_at(plan, uid, i)
        start, end = hm(date, s), hm(date, e)
        if region != cur_region:
            need = geo.travel_minutes(cur_region, region)
            if (start - last_end).total_seconds() / 60 < need:
                if i not in mine:
                    continue
                while scenes and (start - datetime.datetime.fromisoformat(
                        scenes[-1]["end"])).total_seconds() / 60 < need:
                    dropped = scenes.pop()
                    used.discard(dropped["place"])
                last_end = (datetime.datetime.fromisoformat(scenes[-1]["end"])
                            if scenes else start - datetime.timedelta(minutes=need + 5))
            tp = rng.choice(TRANSIT_PLACES)
            mid = last_end + datetime.timedelta(minutes=max(4, need // 4))
            span = max(6, min(16, need - 10))
            scenes.append(action_scene(uid, tp, mid, mid + datetime.timedelta(minutes=span), seen))
            cur_region = region

        if i in mine:
            m = mine[i]
            others = [x for x in m["members"] if x != uid]
            place = m["place"]
            if place is None:
                place = fallback_place(m["members"], region, rng, int(s[:2]))
            m["place"] = place
            if "lines" not in m:
                built = dialogue_scene(place, start, end, m["script"], m["cast"])
                m["lines"] = built["lines"]
                m["start"], m["end"] = built["start"], built["end"]
            sc = {"start": m["start"], "end": m["end"], "type": "dialogue",
                  "place": place, "with": [str(o) for o in others],
                  "lines": json.loads(json.dumps(m["lines"]))}
            scenes.append(sc)
            used.add(place)
            last_end = end
            continue

        if rng.random() < 0.93:
            place = pick_place(uid, region, roles[i], used, rng, int(s[:2]))
            if place:
                pad = rng.randint(0, 8)
                scenes.append(action_scene(uid, place,
                                           start + datetime.timedelta(minutes=pad),
                                           end - datetime.timedelta(minutes=rng.randint(0, 6)),
                                           seen))
                used.add(place)
                last_end = end

    # 귀가: 마지막으로 머문 곳에서 집까지 이동 시간을 확보한 뒤 밤 장면을 놓는다.
    last_stay_end = last_end
    for sc in reversed(scenes):
        if geo.classify(sc["place"]) != "TRANSIT":
            last_stay_end = datetime.datetime.fromisoformat(sc["end"])
            break
    ride = geo.travel_minutes(cur_region, home_region) if cur_region != home_region else 0
    if ride:
        tp = rng.choice(TRANSIT_PLACES)
        t0 = last_stay_end + datetime.timedelta(minutes=4)
        span = max(6, min(16, ride - 8))
        scenes.append(action_scene(uid, tp, t0, t0 + datetime.timedelta(minutes=span), seen))
    arrival = last_stay_end + datetime.timedelta(minutes=max(ride, 10))
    day_end = hm(date, "23:58")

    walk = pick_place(uid, home_region, "commute", used, rng, 22)
    if walk and arrival + datetime.timedelta(minutes=21) <= day_end - datetime.timedelta(minutes=30):
        w0 = max(arrival, hm(date, "22:20"))
        scenes.append(action_scene(uid, walk, w0, w0 + datetime.timedelta(minutes=15), seen))
        used.add(walk)
        arrival = w0 + datetime.timedelta(minutes=15)

    n0 = max(arrival + datetime.timedelta(minutes=6), hm(date, "23:05"))
    if n0 + datetime.timedelta(minutes=10) > day_end:
        n0 = min(n0, day_end - datetime.timedelta(minutes=12))
    if n0 + datetime.timedelta(minutes=10) <= day_end and n0 >= arrival:
        n1 = min(n0 + datetime.timedelta(minutes=25), day_end)
        scenes.append(action_scene(uid, home_place, n0, n1, seen))
        n2 = n1 + datetime.timedelta(minutes=6)
        if rng.random() < 0.8 and n2 + datetime.timedelta(minutes=15) <= day_end:
            scenes.append(action_scene(uid, home_place, n2,
                                       min(n2 + datetime.timedelta(minutes=20), day_end), seen))

    scenes.sort(key=lambda sc: sc["start"])
    scenes = pad_scenes(uid, date, scenes, plan, used, rng, seen)
    scenes = ensure_variety(uid, scenes, used, rng, seen)

    relationships = []
    for m in meetings:
        if uid not in m["members"]:
            continue
        for other in m["members"]:
            if other == uid:
                continue
            key = "-".join(sorted((str(uid), str(other)), key=int))
            entry = CURVES.get(key, {}).get(date_str)
            if not entry:
                continue
            relationships.append({
                "partnerId": str(other),
                "updateTime": hm(date, slots[m["slot"]][1]).isoformat(timespec="seconds"),
                "rapport": entry["rapport"],
                "partnerModel": entry["model"],
            })
    relationships.sort(key=lambda r: int(r["partnerId"]))
    return {"userId": str(uid), "date": date_str, "scenes": scenes,
            "questions": [], "relationships": relationships}


def prepare_meetings(date, plan, meetings, usage, facts, met):
    """만남마다 장소와 대본을 미리 정한다. 한 사람이 하루에 같은 곳을, 39일 동안 같은 대본을 겪지 않게 한다."""
    weekend = date.weekday() >= 5
    roles = SLOT_ROLE_WEEKEND if weekend else SLOT_ROLE_WEEKDAY
    date_str = date.isoformat()
    places_by_user = collections.defaultdict(set)
    met_today = set()

    for m in sorted(meetings, key=lambda x: (x["slot"] is None, x["slot"] or 0)):
        if m.get("unplaced"):
            continue
        members = m["members"]
        role = roles[m["slot"]]
        taken = set().union(*(places_by_user[u] for u in members)) if members else set()
        hour = int((WEEKEND_SLOTS if weekend else WEEKDAY_SLOTS)[m["slot"]][0][:2])
        place = shared_place(members[0], members[1], m["region"], role, taken, hour, rng) \
            or fallback_place(members, m["region"], rng, hour)
        m["place"] = place
        for u in members:
            places_by_user[u].add(place)

        stage = max(stage_between(x, y, date_str)
                    for x in members for y in members if x != y)
        pairs = [frozenset((x, y)) for x in members for y in members if x < y]
        known = all(p in met or "-".join(map(str, sorted(p))) in CURVES for p in pairs)
        again = any(p in met_today for p in pairs)
        m["script"], m["cast"] = dialogue.choose(rng, place, members, stage, hour, weekend,
                                                 usage, facts, known, again, SKY)
        met.update(pairs)
        met_today.update(pairs)
    return meetings


def generate():
    days = []
    usage = collections.defaultdict(collections.Counter)
    facts = collections.defaultdict(dict)
    met = set()
    global SKY
    for date in DATES:
        SKY = weather.of(date)
        plan, meetings = schedule(date)
        for m in meetings:
            m.pop("place", None)
            m.pop("lines", None)
        prepare_meetings(date, plan, meetings, usage, facts, met)
        for uid in sorted(USERS):
            days.append(build_day(uid, date, plan, meetings))
    return {"anchorDate": ANCHOR, "days": days}




def fallback_place(members, region, rnd, hour=12):
    pool = set()
    for uid in members:
        for (r, role), places in BY_USER_REGION_ROLE[uid].items():
            if r == region and role in MEETING_ROLES:
                pool.update(p for p in places if profiles.time_ok(p, hour))
    dry = sorted(p for p in pool if place_ok(p, hour, False))
    return rnd.choice(dry or sorted(pool)) if pool else None


def s0_hour(moment):
    return (moment + datetime.timedelta(minutes=12)).hour


PAD_MAX_MINUTES = 75


def ensure_variety(uid, scenes, used, rnd, seen):
    """고유 장소가 MIN_PLACES 보다 적으면, 겹친 장소의 행동 장면을 같은 지역의 다른 장소로 바꾼다."""
    for _ in range(10):
        places = collections.Counter(sc["place"] for sc in scenes)
        if len(places) >= MIN_PLACES:
            return scenes
        swapped = False
        for i, sc in enumerate(scenes):
            kind = geo.classify(sc["place"])
            if sc["type"] != "action" or places[sc["place"]] < 2 or kind == "TRANSIT" \
                    or sc["place"] in profiles.HOME_PLACES:
                continue
            region = geo.region_of(sc["place"], uid)
            start = datetime.datetime.fromisoformat(sc["start"])
            end = datetime.datetime.fromisoformat(sc["end"])
            taken = set(places)
            for role in ("cafe", "outdoor", "store", "meal", "commute", "play"):
                candidate = pick_place(uid, region, role, taken, rnd, start.hour)
                if candidate and candidate not in taken:
                    scenes[i] = action_scene(uid, candidate, start, end, seen)
                    used.add(candidate)
                    swapped = True
                    break
            if swapped:
                break
        if not swapped:
            return scenes
    return scenes


def pad_scenes(uid, date, scenes, plan, used, rnd, seen=None, target=12):
    """빈 시간대에 장면을 끼워 하루를 target 개 이상으로 채운다."""
    guard = 0
    while len(scenes) < target and guard < 30:
        guard += 1
        gaps = []
        for a, b in zip(scenes, scenes[1:]):
            ae = datetime.datetime.fromisoformat(a["end"])
            bs = datetime.datetime.fromisoformat(b["start"])
            free = (bs - ae).total_seconds() / 60
            ra = geo.region_of(a["place"], uid)
            rb = geo.region_of(b["place"], uid)
            if ra == rb and free >= 40:
                gaps.append((free, ae, bs, ra))
        if not gaps:
            break
        gaps.sort(reverse=True, key=lambda g: g[0])
        free, ae, bs, region = gaps[0]
        place = pick_place(uid, region, rnd.choice(["cafe", "outdoor", "store", "study"]),
                           used, rnd, s0_hour(ae))
        if not place:
            break
        s0 = ae + datetime.timedelta(minutes=12)
        s1 = min(s0 + datetime.timedelta(minutes=min(int(free) - 24, PAD_MAX_MINUTES)),
                 bs - datetime.timedelta(minutes=12))
        if (s1 - s0).total_seconds() / 60 < 12:
            break
        scenes.append(action_scene(uid, place, s0, s1, seen))
        used.add(place)
        scenes.sort(key=lambda sc: sc["start"])
    return scenes


if __name__ == "__main__":
    doc = generate()
    json.dump(doc, open(OUT, "w", encoding="utf-8"), ensure_ascii=False)
    print("생성 완료:", len(doc["days"]), "일")
