# -*- coding: utf-8 -*-
"""시드 유저 20명의 고정 정보와, 유저별로 갈 수 있는 장소 카탈로그."""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geo

ASSETS = json.load(open(os.path.dirname(os.path.abspath(__file__)) + "/assets.json",
                        encoding="utf-8"))
PLACES = ASSETS["places"]

# UserSeeder.SHOWCASE_USERS 와 같은 순서. home 은 기존 데이터의 '하루 첫 장면' 최빈값에서 뽑았다.
USERS = {
    1:  dict(name="김도윤", gender="M", school="SKKU",     dept="미디어커뮤니케이션학과", home="자취방"),
    2:  dict(name="이시우", gender="M", school="SKKU",     dept="경영학과",               home="자취방"),
    3:  dict(name="박하준", gender="M", school="SKKU",     dept="심리학과",               home="자취방"),
    4:  dict(name="최은우", gender="M", school="SKKU",     dept="통계학과",               home="자취방"),
    5:  dict(name="정지호", gender="M", school="SKKU",     dept="사회학과",               home="명륜동 원룸"),
    6:  dict(name="강서윤", gender="F", school="SKKU",     dept="영어영문학과",           home="명륜동 원룸"),
    7:  dict(name="조하윤", gender="F", school="SKKU",     dept="경제학과",               home="기숙사 방"),
    8:  dict(name="윤준서", gender="M", school="KOREA",    dept="사회학과",               home="자취방"),
    9:  dict(name="장유찬", gender="M", school="KOREA",    dept="철학과",                 home="기숙사 방"),
    10: dict(name="임서준", gender="M", school="KOREA",    dept="영어영문학과",           home="안암동 원룸"),
    11: dict(name="한이든", gender="M", school="KOREA",    dept="국어국문학과",           home="기숙사 방"),
    12: dict(name="오재이", gender="M", school="KOREA",    dept="한국사학과",             home="기숙사 방"),
    13: dict(name="서지우", gender="F", school="KOREA",    dept="사학과",                 home="기숙사 방"),
    14: dict(name="신서연", gender="F", school="KOREA",    dept="독어독문학과",           home="자취방"),
    15: dict(name="권다인", gender="F", school="SUNGSHIN", dept="국어국문학과",           home="자취방"),
    16: dict(name="황예린", gender="F", school="SUNGSHIN", dept="영어영문학과",           home="본가 내 방"),
    17: dict(name="안시아", gender="F", school="SUNGSHIN", dept="일본어문·문화학과",      home="기숙사 방"),
    18: dict(name="송유주", gender="F", school="SUNGSHIN", dept="문화예술경영학과",       home="자취방"),
    19: dict(name="류채원", gender="F", school="SUNGSHIN", dept="중국어문·문화학과",      home="기숙사 방"),
    20: dict(name="전지아", gender="F", school="SUNGSHIN", dept="프랑스어문·문화학과",    home="기숙사 방"),
}

HOME_PLACES = {"자취방", "기숙사 방", "명륜동 원룸", "안암동 원룸", "본가 내 방"}
OUTING_REGIONS = {"HONGDAE", "SEONGSU", "JONGNO", "JAMSIL", "HANGANG"}
FAR_REGIONS = {"GANGNEUNG"}

# 장면 역할. 하루 골격을 짤 때 이 역할별로 장소를 고른다. 위에서부터 먼저 맞는 것을 쓴다.
ROLE_ORDER = ["home", "transit", "lecture", "study", "night", "meal", "cafe",
              "store", "play", "commute", "outdoor"]
ROLE_KEYS = {
    "home":     ["자취방", "기숙사 방", "원룸", "본가 내 방"],
    "transit":  ["지하철", "버스 창가", "환승역 계단", "환승통로", "고속버스터미널",
                 "KTX 승강장", "청량리역 승강장", "셔틀버스", "가는 버스"],
    "lecture":  ["강의실", "세미나실", "실습실"],
    "study":    ["열람실", "스터디룸", "스터디카페", "그룹실", "그룹스터디", "라운지",
                 "휴게실", "휴게공간", "복도", "중고서점", "교보문고"],
    "night":    ["포차", "호프집", "이자카야", "맥주집", "술집", "칵테일바", "영화관",
                 "노래방", "곱창골목", "곱창집"],
    "meal":     ["식당", "국숫집", "덮밥집", "김치찌개집", "백반집", "분식집", "떡볶이집",
                 "고깃집", "중국집", "국밥집", "파스타집", "마라탕집", "수제버거집",
                 "한식당", "푸드코트", "먹자골목", "닭강정", "베이글집", "브런치집",
                 "새마을시장", "라멘집", "음식점", "치킨집", "푸드존", "푸드트럭"],
    "cafe":     ["카페", "커피집", "커피거리", "디저트"],
    "store":    ["편의점"],
    "play":     ["방탈출", "보드게임", "팝업", "편집숍", "레코드숍", "전시관", "굿즈",
                 "야구장", "응원석", "관중석", "스테이지", "매표소", "자이로드롭",
                 "회전목마", "워터존", "퍼레이드", "체육관", "농구장", "거리공연",
                 "소극장", "인쇄골목", "한옥길", "중앙무대", "스탠딩존", "물놀이장"],
    "commute":  ["귀갓길", "버스정류장", "막차 승강장", "출구", "등굣길", "횡단보도",
                 "언덕", "초입", "계단", "게시판", "정류장", "만남의 광장", "만남의 장소",
                 "정문", "입구", "가로질러", "게이트", "대합실"],
    "outdoor":  [],
}

# 아침에만/밤에만 어울리는 장소를 가른다.
MORNING_ONLY = ["등굣길"]
EVENING_ONLY = ["귀갓길", "막차 승강장", "밤의", "포차", "호프", "이자카야", "맥주집", "술집",
                "칵테일바", "노래방", "곱창"]
LATE_MORNING = ["영화관"]
BREAKFAST_OK = ["베이글", "브런치", "국밥", "백반", "학생식당", "생활관 식당", "학생회관",
                "학생누리관", "푸드코트", "분식"]


def role_of(place):
    for role in ROLE_ORDER:
        for key in ROLE_KEYS[role]:
            if key in place:
                return role
    return "outdoor"


def time_ok(place, hour):
    if any(k in place for k in MORNING_ONLY):
        return hour < 11
    if any(k in place for k in EVENING_ONLY):
        return hour >= 17
    if any(k in place for k in LATE_MORNING):
        return hour >= 11
    role = role_of(place)
    if role == "meal" and not any(k in place for k in BREAKFAST_OK):
        return hour >= 11
    if role == "play":
        return hour >= 10
    return True


# 다른 학교 동네에 가도 들를 수 있는 장소 역할. 교내 시설은 따로 막는다.
PUBLIC_ROLES = {"meal", "cafe", "night", "outdoor", "commute", "store", "play"}
CAMPUS_INTERIOR = ["강의실", "세미나실", "실습실", "라운지", "휴게실", "열람실", "스터디룸", "그룹실",
                   "복도", "학생식당", "생활관 식당", "학생누리관", "학생회관", "체육관", "게시판",
                   "가로질러"]


def _same_school_users(school):
    return {u for u, v in USERS.items() if v["school"] == school}


def catalog_for(user_id):
    """유저가 갈 수 있는 장소 목록. 기존 데이터의 이용 실적을 존중하되 같은 학교끼리는 공유한다."""
    school = USERS[user_id]["school"]
    mates = _same_school_users(school)
    out = []
    for place, meta in PLACES.items():
        if place in HOME_PLACES and place != USERS[user_id]["home"]:
            continue
        kind = geo.classify(place)
        users = set(meta["users"])
        if kind in ("SCHOOL", "LOCAL", "FOLLOW", "TRANSIT"):
            ok = bool(users & mates) or user_id in users
        elif kind in ("SKKU", "KOREA", "SUNGSHIN"):
            ok = (kind == school or user_id in users or bool(users & mates)
                  or (role_of(place) in PUBLIC_ROLES
                      and not any(key in place for key in CAMPUS_INTERIOR)))
        else:
            ok = True
        if ok:
            out.append(place)
    return out


def build_catalogs():
    cat = {}
    for uid in USERS:
        by_role = {}
        for place in catalog_for(uid):
            by_role.setdefault(role_of(place), []).append(place)
        cat[uid] = by_role
    return cat


if __name__ == "__main__":
    import collections
    cats = build_catalogs()
    for uid in sorted(cats):
        tot = sum(len(v) for v in cats[uid].values())
        print(f"userId {uid:>2} {USERS[uid]['name']} ({USERS[uid]['school']}) 장소 {tot}곳 "
              + " ".join(f"{r}:{len(v)}" for r, v in sorted(cats[uid].items())))
    print()
    regions = collections.Counter()
    for uid in cats:
        for r, ps in cats[uid].items():
            for p in ps:
                regions[geo.region_of(p, uid)] += 1
    print("유저×장소 조합의 지역 분포:", dict(regions))
