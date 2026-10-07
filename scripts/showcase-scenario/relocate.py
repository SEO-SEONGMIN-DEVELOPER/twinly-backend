# -*- coding: utf-8 -*-
"""옛 세계(실제 서울 캠퍼스·동네)로 만든 시나리오를 places.csv 세계로 옮긴다.

build.py → apply_texts.py → validate.py 다음에 돌린다. validate.py 는 옛 장소 이름으로
지역을 판정하므로 옮긴 뒤에는 돌리지 않는다.

하는 일
  1) 장소를 place_map.json 의 장소 코드로 바꾸고, 엔진과 같은 "세계 구역 장소" 표시 문장
     (내 집은 "집")과 placeCode 를 싣는다.
  2) 탈것 안 장면(지하철·버스·환승 계단)을 앞뒤 장소를 잇는 이동(move)으로 바꾼다. 이동 수단은 거리로 정한다.
  3) 장소가 바뀌는 사이마다 이동(move) 장면을 넣는다. 다음 장면 시작에 맞춰 도착하고,
     길이는 구역 사이 이동 시간과 빈틈 중 짧은 쪽이다.
     앞뒤 장면을 함께한 사람은, 그 사람 쪽에도 같은 이동이 생길 때만 동행으로 넣는다.
  4) rewrites.json 으로 옛 지명·여름 표현이 든 문장을 바꾼다.
  5) place_fixes.json 으로 동선이 어색한 장면의 장소를 장면 단위로 바꾼다(키는 아래 scene_fixes 와 같다).
  6) scene_fixes.json 으로 문맥 검토에서 고친 문장을 장면 단위로 덮어쓴다.
     키는 행동 A|유저|날짜|시각, 대화 D|날짜|시각|참가자, 이동 M|유저|날짜|시각 이고,
     필드는 narration·mind·lines.<n>.text·lines.<n>.action 이다. 맞는 장면이 없는 키가 있으면 멈춘다.

이미 옮긴 파일(placeCode 가 있는 파일)에는 다시 돌리지 않는다.
"""
import csv, datetime, hashlib, json, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import weather

REPO = os.path.abspath(os.path.join(HERE, "..", ".."))
SRC = os.path.join(REPO, "backend/src/main/resources/seed/showcase-scenarios.json")

PLACES = {row["code"]: row for row in csv.DictReader(open(os.path.join(HERE, "places.csv"), encoding="utf-8"))}
PLACE_MAP = json.load(open(os.path.join(HERE, "place_map.json"), encoding="utf-8"))
REWRITES = json.load(open(os.path.join(HERE, "rewrites.json"), encoding="utf-8"))
SCENE_FIXES = json.load(open(os.path.join(HERE, "scene_fixes.json"), encoding="utf-8"))
PLACE_FIXES = json.load(open(os.path.join(HERE, "place_fixes.json"), encoding="utf-8"))
USED_PLACE_FIXES = set()

HOME = "P0258"
RIDES = {"지하철 2호선 안", "환승역 계단", "버스 창가 자리"}
NEAR_CAMPUS = {("성북구", sector) for sector in ("후문 상권", "학생 거리", "주택가", "대학앞역")}
T = datetime.datetime.fromisoformat


def display(code):
    row = PLACES[code]
    return "집" if code == HOME else f"{row['world']} {row['sector']} {row['arena']}"


def label(code):
    row = PLACES[code]
    return "집" if code == HOME else f"{row['sector']} {row['arena']}"


def zone(code):
    row = PLACES[code]
    if row["world"] == "트윈리" or (row["world"], row["sector"]) in NEAR_CAMPUS:
        return "캠퍼스"
    return f"{row['world']} {row['sector']}"


def travel(a, b):
    """(분, 이동 수단)."""
    ra, rb = PLACES[a], PLACES[b]
    if (ra["world"], ra["sector"]) == (rb["world"], rb["sector"]):
        return 5, "walk"
    if zone(a) == zone(b):
        return 10, "walk"
    if "근교" in (ra["world"], rb["world"]):
        return 70, "transit"
    if {ra["world"], rb["world"]} & {"한강", "도심"}:
        return 35, "transit"
    return 20, "transit"


def ro(word):
    ch = word[-1]
    if "가" <= ch <= "힣":
        return "로" if (ord(ch) - 0xAC00) % 28 in (0, 8) else "으로"
    return "로"


WALK = ["{d}{ro} 걸어갔다.", "{d}{ro} 향했다.", "{d}까지 걸었다.", "{d}{ro} 발걸음을 옮겼다.",
        "{d}{ro} 자리를 옮겼다.", "천천히 걸어 {d}에 닿았다.", "휴대폰을 넣고 {d}{ro} 향했다."]
WALK_RAIN = ["우산을 쓰고 {d}{ro} 걸어갔다.", "빗길을 걸어 {d}{ro} 향했다.", "물웅덩이를 피해 {d}까지 걸었다."]
WALK_MORNING = ["아침 공기를 맞으며 {d}{ro} 걸었다.", "이른 시간에 {d}{ro} 향했다.", "하품을 참으며 {d}{ro} 걸어갔다."]
WALK_NIGHT = ["어두워진 길을 걸어 {d}{ro} 갔다.", "밤길을 따라 {d}{ro} 향했다.", "가로등 아래를 지나 {d}까지 걸었다."]
LECTURE = ["수업에 늦지 않게 {d}{ro} 서둘러 갔다.", "수업을 들으러 {d}{ro} 향했다.", "가방을 고쳐 메고 {d}{ro} 걸어갔다."]
TRANSIT = ["버스를 타고 {d}{ro} 갔다.", "지하철을 타고 {d}까지 갔다.", "대중교통으로 {d}까지 이동했다.",
           "버스에서 창밖을 보다 {d}에 내렸다.", "지하철 몇 정거장을 지나 {d}{ro} 갔다."]
TRANSIT_FAR = ["한참을 이동해 {d}에 도착했다.", "오래 차를 타고 {d}까지 갔다."]
TOGETHER = ["함께 {d}{ro} 걸어갔다.", "같이 {d}까지 걸었다.", "나란히 {d}{ro} 향했다."]
TOGETHER_TRANSIT = ["함께 버스를 타고 {d}{ro} 갔다.", "같이 지하철을 타고 {d}까지 갔다."]
HOME_WALK = ["집으로 돌아갔다.", "집으로 향했다.", "집까지 걸어서 돌아왔다.", "잠깐 쉬러 집에 들렀다."]
HOME_NIGHT = ["늦은 밤 집으로 돌아왔다.", "하루를 마치고 집으로 돌아왔다.", "불 꺼진 골목을 지나 집에 도착했다."]
HOME_TRANSIT = ["버스를 타고 집으로 돌아갔다.", "지하철을 타고 집으로 향했다.", "막히는 길을 지나 집에 도착했다."]


def narration(user_id, date, start, to, mode, together, used):
    hour = T(start).hour
    far = "근교" in (PLACES[to]["world"],)
    if to == HOME:
        pool = HOME_TRANSIT if mode == "transit" else HOME_NIGHT if hour >= 21 else HOME_WALK
    elif together:
        pool = TOGETHER_TRANSIT if mode == "transit" else TOGETHER
    elif mode == "transit":
        pool = TRANSIT_FAR if far else TRANSIT
    elif weather.of(date) == weather.RAIN:
        pool = WALK_RAIN
    elif hour < 9:
        pool = WALK_MORNING
    elif hour >= 21:
        pool = WALK_NIGHT
    elif PLACES[to]["category"] == "강의/실습":
        pool = LECTURE
    else:
        pool = WALK
    seed = int(hashlib.sha256(f"{user_id}|{date}|{start}".encode()).hexdigest(), 16)
    d = label(to)
    texts = [pool[(seed + k) % len(pool)].format(d=d, ro=ro(d)) for k in range(len(pool))]
    text = next((t for t in texts if t not in used), texts[0])
    used.add(text)
    return text


def rewrite(text):
    return REWRITES.get(text, text) if text else text


def code_of(user_id, date, scene):
    key = scene_key(user_id, date, scene)
    if key in PLACE_FIXES:
        USED_PLACE_FIXES.add(key)
        return PLACE_FIXES[key]
    return PLACE_MAP[scene["place"]]


def relocated(user_id, date, scene):
    code = code_of(user_id, date, scene)
    out = {}
    for key, value in scene.items():
        out[key] = value
        if key == "place":
            out["place"] = display(code)
            out["placeCode"] = code
    if scene["type"] == "action":
        out["narration"] = rewrite(scene["narration"])
        out["mind"] = rewrite(scene["mind"])
    else:
        out["lines"] = [{**line, "text": rewrite(line["text"]),
                         **({"action": rewrite(line["action"])} if line.get("action") else {})}
                        for line in scene["lines"]]
    return out


def move(start, end, src, dst, mode, mapped_with, text, mind):
    return {"start": start, "end": end, "type": "move",
            "from": display(src), "fromPlaceCode": src,
            "place": display(dst), "placeCode": dst,
            "with": mapped_with, "travelMode": mode, "mapVersion": None,
            "narration": text, "mind": mind}


def is_ride(scene):
    return scene["type"] == "action" and scene["place"] in RIDES


def plan_day(day):
    """(머무는 장면들, 이동 계획들). 이동 계획은 아직 동행 확인 전이다."""
    scenes = sorted(day["scenes"], key=lambda s: s["start"])
    stays, plans = [], []
    i = 0
    while i < len(scenes):
        scene = scenes[i]
        if is_ride(scene) and stays and i + 1 < len(scenes):
            src = stays[-1]["placeCode"]
            dst = code_of(day["userId"], day["date"], scenes[i + 1])
            if src != dst:
                plans.append(dict(start=scene["start"], end=scene["end"], src=src, dst=dst, mode=travel(src, dst)[1],
                                  candidates=[], text=rewrite(scene["narration"]), mind=rewrite(scene["mind"])))
                i += 1
                continue
        stays.append(relocated(day["userId"], day["date"], scene))
        i += 1

    for a, b in zip(stays, stays[1:]):
        if a["placeCode"] == b["placeCode"]:
            continue
        if any(p["src"] == a["placeCode"] and p["dst"] == b["placeCode"]
               and a["end"] <= p["start"] and p["end"] <= b["start"] for p in plans):
            continue
        gap = int((T(b["start"]) - T(a["end"])).total_seconds() // 60)
        minutes, mode = travel(a["placeCode"], b["placeCode"])
        minutes = min(minutes, gap)
        if minutes <= 0:
            raise SystemExit(f"이동 시간이 없다: {day['userId']} {day['date']} {a['start']} -> {b['start']}")
        start = (T(b["start"]) - datetime.timedelta(minutes=minutes)).isoformat()
        plans.append(dict(start=start, end=b["start"], src=a["placeCode"], dst=b["placeCode"], mode=mode,
                          candidates=sorted(set(a["with"]) & set(b["with"])), text=None, mind=None))
    return stays, plans


def scene_key(user_id, date, scene):
    at = scene["start"][11:16]
    if scene["type"] == "dialogue":
        members = sorted([user_id] + scene["with"], key=int)
        return f"D|{date}|{at}|{'-'.join(members)}"
    return f"{'A' if scene['type'] == 'action' else 'M'}|{user_id}|{date}|{at}"


def apply_fixes(doc):
    used = set()
    for day in doc["days"]:
        for scene in day["scenes"]:
            key = scene_key(day["userId"], day["date"], scene)
            for field, text in SCENE_FIXES.get(key, {}).items():
                parts = field.split(".")
                if parts[0] == "lines":
                    scene["lines"][int(parts[1])][parts[2]] = text
                else:
                    scene[field] = text
                used.add((key, field))
    unused = [(key, field) for key, fields in SCENE_FIXES.items() for field in fields if (key, field) not in used]
    if unused:
        raise SystemExit(f"맞는 장면이 없는 수정: {unused[:5]}")


def relocate(doc):
    if any("placeCode" in s for day in doc["days"] for s in day["scenes"]):
        raise SystemExit("이미 옮긴 파일이다. build.py 와 apply_texts.py 로 다시 만든 뒤 돌린다.")

    planned = {(day["userId"], day["date"]): plan_day(day) for day in doc["days"]}

    def signature(plan):
        return plan["start"], plan["end"], plan["src"], plan["dst"]

    index = {(user, date, signature(p)): p for (user, date), (_, plans) in planned.items() for p in plans}

    for day in doc["days"]:
        user, date = day["userId"], day["date"]
        stays, plans = planned[(user, date)]
        moves, used = [], set()
        for plan in plans:
            together = [other for other in plan["candidates"]
                        if user in index.get((other, date, signature(plan)), {}).get("candidates", [])]
            text = plan["text"] or narration(user, date, plan["start"], plan["dst"], plan["mode"], together, used)
            moves.append(move(plan["start"], plan["end"], plan["src"], plan["dst"], plan["mode"],
                              together, text, plan["mind"]))
        day["scenes"] = sorted(stays + moves, key=lambda s: s["start"])
    unused = set(PLACE_FIXES) - USED_PLACE_FIXES
    if unused:
        raise SystemExit(f"맞는 장면이 없는 장소 수정: {sorted(unused)[:5]}")
    apply_fixes(doc)
    return doc


if __name__ == "__main__":
    doc = relocate(json.load(open(SRC, encoding="utf-8")))
    json.dump(doc, open(SRC, "w", encoding="utf-8"), ensure_ascii=False)
    scenes = [s for day in doc["days"] for s in day["scenes"]]
    print("옮김 완료:", len(doc["days"]), "일,", len(scenes), "장면, 이동", sum(s["type"] == "move" for s in scenes))
