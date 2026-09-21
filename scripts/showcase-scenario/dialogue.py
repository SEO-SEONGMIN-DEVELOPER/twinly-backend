# -*- coding: utf-8 -*-
"""대본형 대화 선택기.

만남마다 장소 종류·관계 단계·시간대·요일에 맞는 대본을 하나 고른다.
대본은 처음부터 끝까지 한 흐름으로 쓰여 있어 질문과 대답이 어긋나지 않는다.
한 사람이 39일 동안 같은 대본을 다시 보지 않도록, 참가자 중 아무도 안 쓴 대본을 먼저 고른다.
"""
import datetime
import itertools

import profiles
import weather as weather_rules
from scripts_place import BY_KIND, GROUP, OUTDOOR
from scripts_talk import TALK

# 앞에서부터 먼저 맞는 규칙을 쓴다.
KIND_RULES = [
    ("amuse", ["롯데월드", "자이로드롭", "회전목마", "워터존", "퍼레이드", "물놀이장"]),
    ("baseball", ["야구장", "응원석", "관중석", "외야", "3루"]),
    ("stage", ["스테이지", "스탠딩존", "페스티벌", "주경기장"]),
    ("game", ["방탈출", "보드게임"]),
    ("karaoke", ["노래방"]),
    ("movie", ["영화관"]),
    ("theater", ["소극장"]),
    ("books", ["교보문고", "중고서점"]),
    ("exhibit", ["전시관", "DDP"]),
    ("shop", ["팝업", "편집숍", "굿즈", "레코드숍"]),
    ("street", ["거리공연"]),
    ("alley", ["한옥길", "인쇄골목"]),
    ("gym", ["체육관", "농구장"]),
    ("drink", ["포차", "호프", "이자카야", "맥주", "술집", "칵테일", "곱창"]),
]

# 장소 종류마다 쓸 수 있는 대본 묶음. 앞 묶음이 그 장소다운 대본이고, TALK 는 어디서나 되는 대화다.
POOLS = {
    "street": [BY_KIND["street"], OUTDOOR, TALK],
    "alley": [BY_KIND["alley"], OUTDOOR, TALK],
}

# 어느 장소든 대화가 나오기 전에 이 확률로 장소 대본을 먼저 고른다.
PLACE_FIRST = 0.55

# 그날 이미 만난 사이면 이 확률로 재회 대본을 먼저 고른다.
REUNION_FIRST = 0.7

ACTION_FALLBACK = "웃으며"


HOME_TYPE = {"자취방": "자취", "명륜동 원룸": "자취", "안암동 원룸": "자취",
             "기숙사 방": "기숙사", "본가 내 방": "본가"}


def home_type(user_id):
    return HOME_TYPE[profiles.USERS[user_id]["home"]]


def casts(script, members, facts=None):
    """대본의 A/B/C 역할을 거주 조건과 이미 밝힌 개인 설정에 맞게 참가자에게 배정하는 모든 경우."""
    roles = ["A", "B", "C"][:len(members)]
    out = []
    for order in itertools.permutations(members):
        cast = dict(zip(roles, order))
        if not all(home_type(cast[r]) in allowed for r, allowed in script["who"].items() if r in cast):
            continue
        if facts is not None and not all(
                facts[cast[r]].get(key, value) == value
                for r, said in script["facts"].items() if r in cast
                for key, value in said.items()):
            continue
        out.append(cast)
    return out


def kind_of(place):
    for kind, keys in KIND_RULES:
        if any(key in place for key in keys):
            return kind
    role = profiles.role_of(place)
    return role if role in BY_KIND else "talk"


def pools_for(kind):
    if kind in POOLS:
        return POOLS[kind]
    if kind in BY_KIND:
        return [BY_KIND[kind], TALK]
    return [TALK]


def STAGE_OF(rapport):
    return 0 if rapport < 40 else (1 if rapport < 70 else 2)


def _fits(script, stage, hour, weekend, cross_school, members, place, facts, known, met_today, sky):
    if not weather_rules.tag_ok(script["weather"], sky):
        return False
    if script["history"] and not known:
        return False
    if script["opener"] and met_today:
        return False
    if script["later"] and not met_today:
        return False
    if script["at"] and not any(key in place for key in script["at"]):
        return False
    if any(key in place for key in script["not_at"]):
        return False
    if (script["who"] or script["facts"]) and not casts(script, members, facts):
        return False
    if cross_school and script["school"] == "same":
        return False
    if stage not in script["stages"]:
        return False
    if script["time"] == "am" and hour >= 11:
        return False
    if script["time"] == "ev" and hour < 17:
        return False
    if script["days"] == "weekday" and weekend:
        return False
    return True


def choose(rng, place, members, stage, hour, weekend, usage, facts, known, met_today=False,
           sky=weather_rules.CLEAR):
    """대본과 배역을 함께 고른다. 참가자 누구도 안 본 대본을 우선하고, 모두 봤다면 가장 덜 본 대본을 고른다.
    facts 는 유저별로 이미 밝힌 개인 설정이고, known 은 참가자들이 전에 만난 적 있는 사이인지다.
    met_today 면 그날 이미 만난 사이라 인사형 대본을 빼고, 재회 대본을 먼저 고른다.
    """
    if len(members) > 2:
        pools = [GROUP]
    else:
        pools = pools_for(kind_of(place))

    def seen_count(script):
        return sum(usage[u][id(script)] for u in members)

    cross_school = len({profiles.USERS[u]["school"] for u in members}) > 1
    fitting = [[s for s in pool if _fits(s, stage, hour, weekend, cross_school, members, place, facts, known, met_today, sky)] for pool in pools]
    fresh = [[s for s in pool if seen_count(s) == 0] for pool in fitting]

    again = [s for pool in fresh for s in pool if s["later"]]
    primary, rest = fresh[0], [s for pool in fresh[1:] for s in pool]
    if again and rng.random() < REUNION_FIRST:
        script = rng.choice(again)
    elif primary and (not rest or rng.random() < PLACE_FIRST):
        script = rng.choice(primary)
    elif rest:
        script = rng.choice(rest)
    else:
        every = [s for pool in fitting for s in pool] or [s for pool in pools for s in pool]
        low = min(seen_count(s) for s in every)
        script = rng.choice([s for s in every if seen_count(s) == low])

    cast = rng.choice(casts(script, members, facts) or casts(script, members))
    for u in members:
        usage[u][id(script)] += 1
    for role, said in script["facts"].items():
        if role in cast:
            for key, value in said.items():
                facts[cast[role]].setdefault(key, value)
    return script, cast


def build_lines(rng, script, speaker, start, end):
    """정해진 배역대로 대사를 붙이고, 대사 시각을 장면 구간 안에 고르게 흩는다."""

    lines = script["lines"]
    span = int((end - start).total_seconds() // 60)
    step = max(1, (span - 4) // (len(lines) + 1))
    at = start + datetime.timedelta(minutes=2)

    out = [{"t": "narr", "text": rng.choice(script["narr"]),
            "occursAt": at.isoformat(timespec="seconds")}]
    for who, action, text in lines:
        at = min(at + datetime.timedelta(minutes=rng.randint(max(1, step - 2), step + 2)),
                 end - datetime.timedelta(minutes=1))
        out.append({
            "t": "bubble",
            "userId": str(speaker[who]),
            "action": action or ACTION_FALLBACK,
            "text": text,
            "occursAt": at.isoformat(timespec="seconds"),
        })
    return out


def inventory():
    counts = {kind: len(scripts) for kind, scripts in BY_KIND.items()}
    counts["talk"] = len(TALK)
    counts["group"] = len(GROUP)
    return counts
