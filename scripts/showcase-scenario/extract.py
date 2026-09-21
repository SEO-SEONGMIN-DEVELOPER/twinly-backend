# -*- coding: utf-8 -*-
"""리소스에서 재사용 자산(장소 카탈로그, 서술/속마음 풀, 고정 호감도 곡선)을 뽑아 assets.json 으로 만든다.

assets.json 은 이미 저장돼 있다. build.py 는 그 파일만 읽으므로 평소에는 이 스크립트를 돌릴 필요가 없다.
돌리면 현재 리소스 기준으로 assets.json 을 덮어쓴다.
"""
import json, collections, datetime, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geo

REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SRC = os.path.join(REPO, "backend/src/main/resources/seed/showcase-scenarios.json")
OUT = os.path.dirname(os.path.abspath(__file__)) + "/assets.json"

raw = json.load(open(SRC, encoding="utf-8"))

place_users = collections.defaultdict(set)
place_hours = collections.defaultdict(collections.Counter)
place_dur = collections.defaultdict(list)
narration = collections.defaultdict(set)
mind = collections.defaultdict(set)
narration_bucket = collections.defaultdict(lambda: collections.defaultdict(set))
mind_bucket = collections.defaultdict(lambda: collections.defaultdict(set))


def bucket_of(hour):
    return "morning" if hour < 11 else ("day" if hour < 18 else "night")


def clean(text):
    return text if text and "{" not in text else None
dialog_actions = collections.defaultdict(set)
dialog_narr = collections.defaultdict(set)
weekday_places = collections.defaultdict(collections.Counter)

for day in raw["days"]:
    uid = int(day["userId"])
    wd = datetime.date.fromisoformat(day["date"]).weekday()
    for sc in day["scenes"]:
        p = sc["place"]
        place_users[p].add(uid)
        h = int(sc["start"][11:13])
        place_hours[p][h] += 1
        dur = (datetime.datetime.fromisoformat(sc["end"])
               - datetime.datetime.fromisoformat(sc["start"])).total_seconds() / 60
        place_dur[p].append(int(dur))
        weekday_places[p][wd] += 1
        if sc["type"] == "action":
            n, m = clean(sc["narration"]), clean(sc.get("mind"))
            if n:
                narration[p].add(n)
                narration_bucket[p][bucket_of(h)].add(n)
            if m:
                mind[p].add(m)
                mind_bucket[p][bucket_of(h)].add(m)
        else:
            for line in sc["lines"]:
                if line["t"] == "bubble" and clean(line.get("action")):
                    dialog_actions[p].add(line["action"])
                elif line["t"] == "narr" and clean(line.get("text")):
                    dialog_narr[p].add(line["text"])

# 고정 호감도 곡선: (쌍, 날짜) -> (rapport, partnerModel)
curves = collections.defaultdict(dict)
for day in raw["days"]:
    for rel in day["relationships"]:
        key = "-".join(sorted((day["userId"], rel["partnerId"]), key=int))
        curves[key][day["date"]] = {"rapport": rel["rapport"], "model": rel["partnerModel"]}

places = {}
for p in place_users:
    durs = sorted(place_dur[p])
    places[p] = {
        "kind": geo.classify(p),
        "users": sorted(place_users[p]),
        "hours": dict(place_hours[p]),
        "durMin": durs[len(durs) // 10],
        "durMax": durs[-max(1, len(durs) // 10)],
        "weekdays": dict(weekday_places[p]),
        "narration": sorted(narration[p]),
        "mind": sorted(mind[p]),
        "narrationByTime": {k: sorted(v) for k, v in narration_bucket[p].items()},
        "mindByTime": {k: sorted(v) for k, v in mind_bucket[p].items()},
        "dialogActions": sorted(dialog_actions[p]),
        "dialogNarr": sorted(dialog_narr[p]),
    }

json.dump({"anchorDate": raw["anchorDate"], "places": places, "curves": curves},
          open(OUT, "w", encoding="utf-8"), ensure_ascii=False)
print("assets.json 저장:", OUT)
print("  장소", len(places), "곳 / 곡선", len(curves), "쌍")
print("  narration 총", sum(len(v["narration"]) for v in places.values()))
print("  mind 총", sum(len(v["mind"]) for v in places.values()))
print("  dialogAction 총", sum(len(v["dialogActions"]) for v in places.values()))
