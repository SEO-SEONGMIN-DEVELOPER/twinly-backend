# -*- coding: utf-8 -*-
"""생성 결과 정합성 검사."""
import collections, datetime, json, os, re, sys
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
import geo, profiles

T = datetime.datetime.fromisoformat
REPO = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
SRC = os.path.join(REPO, "backend/src/main/resources/seed/showcase-scenarios.json")


def regions(day):
    uid = int(day["userId"])
    sc = sorted(day["scenes"], key=lambda s: s["start"])
    out = []
    for s in sc:
        kind = geo.classify(s["place"])
        out.append(None if kind in ("FOLLOW", "TRANSIT") else geo.region_of(s["place"], uid))
    for i, r in enumerate(out):
        if r is None:
            prev = next((out[j] for j in range(i - 1, -1, -1) if out[j]), None)
            nxt = next((out[j] for j in range(i + 1, len(out)) if out[j]), None)
            out[i] = prev or nxt or geo.HOME_REGION[uid]
    return sc, out, [geo.classify(s["place"]) for s in sc]


def run(doc, expected_curves):
    fail = collections.Counter()
    detail = collections.defaultdict(list)

    def bad(tag, info):
        fail[tag] += 1
        if len(detail[tag]) < 4:
            detail[tag].append(info)

    users = sorted({int(x["userId"]) for x in doc["days"]})
    dates = sorted({x["date"] for x in doc["days"]})
    have = {(x["userId"], x["date"]) for x in doc["days"]}
    for u in users:
        for d in dates:
            if (str(u), d) not in have:
                bad("1.전수누락", (u, d))

    idx = {(x["userId"], x["date"]): x for x in doc["days"]}
    for day in doc["days"]:
        uid = int(day["userId"])
        sc, regs, kinds = regions(day)

        if sum(1 for s in sc if s["type"] == "dialogue") < 3:
            bad("2.대화3회미만", (uid, day["date"]))
        if len({s["place"] for s in sc}) < 5:
            bad("2.장소5곳미만", (uid, day["date"]))

        for a, b in zip(sc, sc[1:]):
            if T(b["start"]) < T(a["end"]):
                bad("3.시간겹침", (uid, day["date"], a["place"], b["place"]))
        for s in sc:
            if T(s["start"]).date().isoformat() != day["date"] or \
               T(s["end"]).date().isoformat() != day["date"]:
                bad("3.날짜이탈", (uid, day["date"], s["place"]))
            if T(s["end"]) <= T(s["start"]):
                bad("3.역전구간", (uid, day["date"], s["place"]))
            for line in s.get("lines", []):
                if not (T(s["start"]) <= T(line["occursAt"]) <= T(s["end"])):
                    bad("3.대사시각이탈", (uid, day["date"], s["place"]))

        last = None
        for s, r, k in zip(sc, regs, kinds):
            if k == "TRANSIT":
                continue
            if last is not None:
                lr, le = last
                if lr != r:
                    need = geo.travel_minutes(lr, r)
                    got = (T(s["start"]) - le).total_seconds() / 60
                    if got < need:
                        bad("4.이동시간부족", (uid, day["date"], lr, r, int(got), need))
            last = (r, T(s["end"]))

        school = profiles.USERS[uid]["school"]
        weekend = datetime.date.fromisoformat(day["date"]).weekday() >= 5
        for s, r in zip(sc, regs):
            if profiles.role_of(s["place"]) == "lecture":
                if r != school:
                    bad("4.타교수업", (uid, day["date"], s["place"], r))
                if weekend:
                    bad("4.주말수업", (uid, day["date"], s["place"]))

        for s in sc:
            for w in s.get("with", []):
                other = idx.get((w, day["date"]))
                hit = [t for t in (other["scenes"] if other else [])
                       if t["start"] == s["start"] and day["userId"] in t.get("with", [])]
                if not hit:
                    bad("5.미러없음", (uid, w, day["date"], s["start"]))
                elif hit[0]["place"] != s["place"] or hit[0]["lines"] != s["lines"]:
                    bad("5.미러내용불일치", (uid, w, day["date"]))
            if s["type"] == "dialogue":
                spk = {l["userId"] for l in s["lines"] if l["t"] == "bubble"}
                if spk != {day["userId"]} | set(s["with"]):
                    bad("5.화자불일치", (uid, day["date"], s["place"]))

        partners = [rel["partnerId"] for rel in day["relationships"]]
        if len(partners) != len(set(partners)):
            bad("6.호감도상대중복", (uid, day["date"]))
        for rel in day["relationships"]:
            if T(rel["updateTime"]).date().isoformat() != day["date"]:
                bad("6.호감도시각이탈", (uid, day["date"]))
            met = any(rel["partnerId"] in s.get("with", []) for s in sc)
            if not met:
                bad("6.안만났는데호감도", (uid, day["date"], rel["partnerId"]))

    # 원본 호감도 곡선 보존
    def curve(document):
        out = collections.defaultdict(dict)
        for x in document["days"]:
            for r in x["relationships"]:
                key = "-".join(sorted((x["userId"], r["partnerId"]), key=int))
                out[key][x["date"]] = (r["rapport"], r["partnerModel"])
        return out

    before, after = expected_curves, curve(doc)
    for key, by_date in before.items():
        for d, v in by_date.items():
            if after.get(key, {}).get(d) != v:
                bad("7.호감도곡선변경", (key, d, v, after.get(key, {}).get(d)))
    for key, by_date in after.items():
        for d, v in by_date.items():
            if before.get(key, {}).get(d) != v:
                bad("7.호감도추가", (key, d, v))

    # 쌍별 단조 증가
    for key, by_date in after.items():
        seq = [v[0] for _, v in sorted(by_date.items())]
        if any(y < x for x, y in zip(seq, seq[1:])):
            bad("7.호감도역행", (key, seq))

    text_rules(doc, bad)
    return fail, detail


RAIN_WORDS = re.compile(r"비가|비를|빗|우산|장마|소나기|폭우")
SUN_WORDS = re.compile(r"햇살|햇볕|땡볕|쨍|노을")
NAMES = {n for p in profiles.USERS.values() for n in (p["name"], p["name"][1:])}


def text_rules(doc, bad):
    """장면 문장이 재사용되지 않고, 날씨·학과·이름 규칙을 지키는지 검사한다. 대화는 양쪽 미러를 한 번만 센다."""
    import weather

    used = collections.defaultdict(set)
    done = set()
    for day in doc["days"]:
        sky = weather.of(day["date"])
        dept = profiles.USERS[int(day["userId"])]["dept"]
        for s in day["scenes"]:
            if s["type"] == "action":
                owner = (day["userId"], day["date"], s["start"])
                texts = [("narration", s["narration"]), ("mind", s.get("mind"))]
                for text in (s["narration"], s.get("mind")):
                    if text and "학과" in text and dept not in text:
                        bad("8.남의학과", (day["userId"], day["date"], text))
            else:
                owner = (day["date"], s["start"], frozenset([day["userId"]] + s["with"]))
                if owner in done:
                    continue
                done.add(owner)
                if not s["lines"] or s["lines"][0]["t"] != "narr":
                    bad("8.첫줄나레이션아님", (day["date"], s["place"]))
                if not 5 <= len(s["lines"]) <= 7:
                    bad("8.대사줄수", (day["date"], s["place"], len(s["lines"])))
                texts = [(l["t"], l["text"]) for l in s["lines"]]
            for kind, text in texts:
                if not text:
                    continue
                used[(kind, text)].add(owner)
                if sky != weather.RAIN and RAIN_WORDS.search(text):
                    bad("10.비아닌날비", (day["date"], text))
                if sky != weather.CLEAR and SUN_WORDS.search(text):
                    bad("10.안맑은날햇살", (day["date"], text))
                if any(name in text for name in NAMES):
                    bad("10.이름노출", (day["date"], text))
    for (kind, text), owners in used.items():
        if len(owners) > 1:
            bad("11.문장재사용", (kind, text, len(owners)))


def curves_from_assets():
    """assets.json 에 저장된 원본 호감도 곡선을 curve() 와 같은 모양으로 돌려준다."""
    assets = json.load(open(os.path.join(HERE, "assets.json"), encoding="utf-8"))
    return {key: {d: (v["rapport"], v["model"]) for d, v in by_date.items()}
            for key, by_date in assets["curves"].items()}


if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else SRC
    doc = json.load(open(target, encoding="utf-8"))
    fail, detail = run(doc, curves_from_assets())
    if not fail:
        print("정합성 검사 전부 통과")
    for tag in sorted(fail):
        print(f"{tag}: {fail[tag]}건")
        for d in detail[tag]:
            print("   ", d)
