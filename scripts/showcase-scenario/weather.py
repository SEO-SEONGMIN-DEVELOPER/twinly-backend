# -*- coding: utf-8 -*-
"""날짜별 날씨와, 날씨가 들어간 문장의 조건.

같은 날 한쪽에서는 비를 맞고 다른 쪽에서는 햇살에 눈을 찡그리지 않도록
날짜마다 날씨를 하나로 정하고, 날씨가 드러나는 문장은 그 날씨에만 쓴다.
"""
import datetime

CLEAR, CLOUDY, RAIN = "clear", "cloudy", "rain"

# 원본 날짜 기준. 비 오는 날은 나들이가 몰리는 주말을 피해 평일로 잡았다.
RAIN_DAYS = {"2026-08-24", "2026-09-02", "2026-09-10", "2026-09-23"}
CLOUDY_DAYS = {"2026-08-21", "2026-08-27", "2026-08-31", "2026-09-04",
               "2026-09-08", "2026-09-15", "2026-09-18", "2026-09-25"}


def of(date):
    key = date.isoformat() if isinstance(date, datetime.date) else date
    if key in RAIN_DAYS:
        return RAIN
    if key in CLOUDY_DAYS:
        return CLOUDY
    return CLEAR


# 조건 → 허용되는 날씨
ALLOWED = {
    "rain": {RAIN},
    "wet": {CLOUDY, RAIN},
    "cloudy": {CLOUDY},
    "sunny": {CLEAR},
    "dry": {CLEAR, CLOUDY},
}

TEXT_TAGS = {
    "우산을 안 챙겨 나와서 결국 비를 맞으며 걸었다.": "rain",
    "일기예보 좀 볼걸. 신발이 다 젖었다.": "rain",
    "비 오는 날은 진짜 나가기 싫다.": "rain",
    "우산을 챙길지 말지 창밖을 보며 고민했다.": "wet",
    "우산을 살까 하다가 그냥 나왔다.": "wet",
    "아침부터 날씨 앱을 다섯 번은 확인했다.": "wet",
    "커튼을 걷었더니 날이 흐려서 다시 눕고 싶어졌다.": "wet",
    "구름 낀 하늘 아래 이어폰을 끼고 천천히 걸었다.": "cloudy",
    "아침 햇살이 세서 눈을 찡그리며 걸었다.": "sunny",
    "햇살이 좋아 눈을 찡그렸다.": "sunny",
    "하늘이 예뻐서 사진을 찍었다.": "sunny",
    "햇볕이 들어와 자리를 옮겼다.": "sunny",
    "커튼을 걷으니 생각보다 해가 높았다.": "sunny",
    "해가 지면서 강물이 주황색으로 물들었다.": "sunny",
    "루프탑에 앉아 해가 지는 걸 봤다.": "sunny",
    "날이 좋아서 다행이다.": "sunny",
    "이 시간대 하늘이 제일 예쁜 것 같다.": "sunny",
    "잔디에 누워 지나가는 구름을 한참 봤다.": "dry",
    "해가 지면서 조명이 켜지는 걸 잔디에 앉아 봤다.": "dry",
    "날이 선선해서 걷기 딱 좋다.": "dry",
    "저녁 바람을 맞으며 동네를 한 바퀴 걸었다.": "dry",
}


def text_ok(text, weather):
    tag = TEXT_TAGS.get(text)
    return tag is None or weather in ALLOWED[tag]


def tag_ok(tag, weather):
    return tag is None or weather in ALLOWED[tag]
