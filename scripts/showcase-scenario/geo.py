# -*- coding: utf-8 -*-
"""장소 -> 지역 분류와 지역 간 이동 시간 모델.

분류 종류
  FOLLOW : 행사장/공원 안. 앞뒤 장면의 지역을 물려받는다.
  TRANSIT: 이동 수단 안. 이동 시간 검사에서 제외한다.
  SCHOOL : 이름이 학교마다 겹치는 교내 장소. 그 유저의 소속 캠퍼스로 해석한다.
  LOCAL  : 집/동네. 그 유저의 홈 지역으로 해석한다.
  그 외   : 고정 지역명.
"""

SCHOOL_OF = {**{i: "SKKU" for i in range(1, 8)},
             **{i: "KOREA" for i in range(8, 15)},
             **{i: "SUNGSHIN" for i in range(15, 21)}}
HOME_REGION = dict(SCHOOL_OF)

# 동네 어디에나 있을 법한 장소. 그 유저의 홈 지역으로 해석한다.
FOLLOW_KEYS = ["그늘막 쉼터", "공원 벤치", "숲길 그늘", "한적한 골목길"]

TRANSIT_KEYS = ["지하철", "버스 창가", "환승역 계단", "환승통로", "환승",
                "고속버스터미널", "KTX 승강장", "청량리역 승강장", "서울역 3번 출구",
                "셔틀버스", "마을버스 정류장"]

# 학교 표식이 붙어 지역이 고정되는 교내/주변 장소. SCHOOL_KEYS 보다 먼저 본다.
ANCHORED_KEYS = {
    "SKKU": ["금잔디광장", "퇴계인문관", "수선관", "다산경제관", "경영관", "중앙학술정보관",
             "성대 후문", "명륜동", "혜화역", "대학로", "성균관 돌담길"],
    "KOREA": ["중앙광장", "하나스퀘어", "참살이길", "개운사", "안암역", "안암동",
              "정경관", "정경대", "문과대 서관", "우당교양관", "백주년기념관", "애기능생활관",
              "고대 앞", "고대 정문", "고대 체육관"],
    "SUNGSHIN": ["돈암수정캠퍼스", "미아리고개", "성신여대입구역", "성신여대 앞", "돈암동",
                 "난향관", "수정관", "학생누리관"],
}

# 이름만으로는 어느 학교인지 알 수 없는 교내 장소.
SCHOOL_KEYS = ["중앙도서관", "학술정보관", "미디어관", "인문관", "정문 앞", "학생회관",
               "교내", "학교 앞", "등굣길", "기숙사"]

REGION_KEYS = {
    "HONGDAE": ["홍대", "합정", "연남동", "경의선숲길"],
    "SEONGSU": ["성수동", "성수역", "서울숲", "건대입구"],
    "JONGNO": ["동대문역사문화공원", "을지로", "청계천", "익선동", "북촌", "광화문",
               "교보문고", "DDP", "전시관", "남산"],
    "JAMSIL": ["잠실", "석촌호수", "올림픽공원", "종합운동장", "롯데월드", "자이로드롭",
               "워터존", "실내 회전목마", "외야 잔디석", "관중석 통로", "푸드존", "푸드트럭",
               "메인 스테이지", "서브 스테이지", "굿즈 부스", "야구장", "매표소", "스탠딩존",
               "퍼레이드 관람", "페스티벌 입장 게이트", "송리단길", "방이동", "몽촌토성역",
               "잔디밭 돗자리 자리", "물놀이장 옆 그늘"],
    "HANGANG": ["한강", "뚝섬", "강변 계단"],
    "GANGNEUNG": ["강릉", "경포", "안목해변", "방파제", "해변 파라솔", "중앙시장 닭강정"],
}

LOCAL_KEYS = ["자취방", "원룸", "본가 내 방", "집 앞", "동네", "작은 동네 카페",
              "루프탑 카페", "디저트 전문 카페", "노포 국밥집", "골목 이자카야"]

REGIONS = ["SKKU", "KOREA", "SUNGSHIN", "HONGDAE", "SEONGSU",
           "JONGNO", "JAMSIL", "HANGANG", "GANGNEUNG"]

_T = {
    ("SKKU", "KOREA"): 25, ("SKKU", "SUNGSHIN"): 25, ("SKKU", "HONGDAE"): 40,
    ("SKKU", "SEONGSU"): 35, ("SKKU", "JONGNO"): 15, ("SKKU", "JAMSIL"): 50,
    ("SKKU", "HANGANG"): 35, ("SKKU", "GANGNEUNG"): 200,
    ("KOREA", "SUNGSHIN"): 20, ("KOREA", "HONGDAE"): 45, ("KOREA", "SEONGSU"): 30,
    ("KOREA", "JONGNO"): 25, ("KOREA", "JAMSIL"): 50, ("KOREA", "HANGANG"): 35, ("KOREA", "GANGNEUNG"): 200,
    ("SUNGSHIN", "HONGDAE"): 50, ("SUNGSHIN", "SEONGSU"): 40, ("SUNGSHIN", "JONGNO"): 25,
    ("SUNGSHIN", "JAMSIL"): 55, ("SUNGSHIN", "HANGANG"): 45, ("SUNGSHIN", "NAMSAN"): 35,
    ("SUNGSHIN", "GANGNEUNG"): 210,
    ("HONGDAE", "SEONGSU"): 35, ("HONGDAE", "JONGNO"): 25, ("HONGDAE", "JAMSIL"): 45,
    ("HONGDAE", "HANGANG"): 20, ("HONGDAE", "GANGNEUNG"): 220,
    ("SEONGSU", "JONGNO"): 25, ("SEONGSU", "JAMSIL"): 25, ("SEONGSU", "HANGANG"): 15, ("SEONGSU", "GANGNEUNG"): 195,
    ("JONGNO", "JAMSIL"): 40, ("JONGNO", "HANGANG"): 30, ("JONGNO", "NAMSAN"): 15,
    ("JONGNO", "GANGNEUNG"): 205,
    ("JAMSIL", "HANGANG"): 25, ("JAMSIL", "GANGNEUNG"): 190, ("HANGANG", "GANGNEUNG"): 200,
}


def travel_minutes(a, b):
    if a == b:
        return 10
    return _T.get((a, b)) or _T.get((b, a))


def classify(place):
    for key in FOLLOW_KEYS:
        if key in place:
            return "FOLLOW"
    for key in TRANSIT_KEYS:
        if key in place:
            return "TRANSIT"
    for region, keys in ANCHORED_KEYS.items():
        for key in keys:
            if key in place:
                return region
    for key in SCHOOL_KEYS:
        if key in place:
            return "SCHOOL"
    for region, keys in REGION_KEYS.items():
        for key in keys:
            if key in place:
                return region
    for key in LOCAL_KEYS:
        if key in place:
            return "LOCAL"
    return None


def region_of(place, user_id):
    kind = classify(place)
    if kind == "SCHOOL":
        return SCHOOL_OF[user_id]
    if kind in (None, "LOCAL"):
        return HOME_REGION[user_id]
    return kind
