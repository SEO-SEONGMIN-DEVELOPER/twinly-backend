# -*- coding: utf-8 -*-
"""생성 결과 정합성 검사."""
import collections, datetime, json, os, sys
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

    dialogue_rules(doc, bad)
    return fail, detail


def dialogue_rules(doc, bad):
    """대본에 붙은 조건(관계 단계·시간대·요일·학교·거주·장소 종류)과 유저별 대본 중복을 검사한다."""
    import dialogue
    import scripts_place
    import scripts_talk
    import build

    by_lines = {}
    for kind, scripts in scripts_place.BY_KIND.items():
        for sc in scripts:
            by_lines[tuple(t for _, _, t in sc["lines"])] = (kind, sc)
    for sc in scripts_talk.TALK:
        by_lines[tuple(t for _, _, t in sc["lines"])] = ("talk", sc)
    for sc in scripts_place.GROUP:
        by_lines[tuple(t for _, _, t in sc["lines"])] = ("group", sc)

    seen = collections.defaultdict(collections.Counter)
    for day in doc["days"]:
        weekend = datetime.date.fromisoformat(day["date"]).weekday() >= 5
        for s in day["scenes"]:
            if s["type"] != "dialogue":
                continue
            bubbles = [l for l in s["lines"] if l["t"] == "bubble"]
            key = tuple(l["text"] for l in bubbles)
            if key not in by_lines:
                bad("8.대본아님", (day["userId"], day["date"], s["place"]))
                continue
            kind, sc = by_lines[key]
            seen[day["userId"]][id(sc)] += 1
            members = [int(day["userId"])] + [int(w) for w in s["with"]]
            hour = T(s["start"]).hour
            stage = max(build.stage_between(a, b, day["date"])
                        for a in members for b in members if a != b)
            where = (day["userId"], day["date"], s["place"], sc["narr"][0])
            if stage not in sc["stages"]:
                bad("8.관계단계", where)
            if (sc["time"] == "am" and hour >= 11) or (sc["time"] == "ev" and hour < 17):
                bad("8.시간대", where)
            if sc["days"] == "weekday" and weekend:
                bad("8.요일", where)
            if sc["school"] == "same" and len({profiles.USERS[m]["school"] for m in members}) > 1:
                bad("8.학교", where)
            if kind not in ("talk", "group", "outdoor", dialogue.kind_of(s["place"])):
                bad("8.장소종류", where)
            if sc["at"] and not any(k in s["place"] for k in sc["at"]):
                bad("8.세부장소", where)
            if any(k in s["place"] for k in sc["not_at"]):
                bad("8.세부장소", where)
            cast = {who: int(l["userId"]) for (who, _, _), l in zip(sc["lines"], bubbles)}
            for role, allowed in sc["who"].items():
                if role in cast and dialogue.home_type(cast[role]) not in allowed:
                    bad("8.거주", where)
    # 장면 사이 문맥: 시간순으로 훑으며 만난 적 있는 사이와 각자 밝힌 설정을 쌓는다.
    events, done = [], set()
    for day in doc["days"]:
        for s in day["scenes"]:
            key = (day["date"], s["start"], s["place"],
                   frozenset([day["userId"]] + s.get("with", [])))
            if s["type"] == "dialogue" and key not in done:
                done.add(key)
                events.append((day["date"], s["start"], s,
                               [int(day["userId"])] + [int(w) for w in s["with"]]))
    events.sort(key=lambda e: (e[0], e[1]))
    met, said = set(), collections.defaultdict(dict)
    met_on = collections.defaultdict(set)
    for date, start, s, members in events:
        bubbles = [l for l in s["lines"] if l["t"] == "bubble"]
        found = by_lines.get(tuple(l["text"] for l in bubbles))
        pairs = [frozenset((a, b)) for a in members for b in members if a < b]
        if found:
            sc = found[1]
            cast = {who: int(l["userId"]) for (who, _, _), l in zip(sc["lines"], bubbles)}
            if sc["history"] and any(p not in met and "-".join(map(str, sorted(p))) not in build.CURVES
                                     for p in pairs):
                bad("9.첫만남인데과거전제", (date, s["place"], sc["narr"][0]))
            again = any(p in met_on[date] for p in pairs)
            if sc["opener"] and again:
                bad("9.재회인데인사", (date, s["place"], sc["narr"][0]))
            if sc["later"] and not again:
                bad("9.첫만남인데재회", (date, s["place"], sc["narr"][0]))
            for role, facts in sc["facts"].items():
                uid = cast.get(role)
                for key, value in facts.items():
                    if uid is not None and said[uid].get(key, value) != value:
                        bad("9.설정번복", (uid, key, said[uid][key], value, date))
                    if uid is not None:
                        said[uid].setdefault(key, value)
        met.update(pairs)
        met_on[date].update(pairs)

    import weather
    for day in doc["days"]:
        sky = weather.of(day["date"])
        for s in day["scenes"]:
            texts = [s.get("narration"), s.get("mind")] + [l["text"] for l in s.get("lines", [])]
            for text in texts:
                if text and not weather.text_ok(text, sky):
                    bad("10.날씨문장", (day["date"], sky, text))
            if s["type"] == "dialogue":
                found = by_lines.get(tuple(l["text"] for l in s["lines"] if l["t"] == "bubble"))
                if found and not weather.tag_ok(found[1]["weather"], sky):
                    bad("10.날씨대본", (day["date"], sky, found[1]["narr"][0]))

    for day in doc["days"]:
        dept = profiles.USERS[int(day["userId"])]["dept"]
        for s in day["scenes"]:
            for text in (s.get("narration"), s.get("mind")):
                if text and "학과" in text and dept not in text:
                    bad("8.남의학과", (day["userId"], day["date"], text))
    for uid, counter in seen.items():
        for count in counter.values():
            if count > 1:
                bad("8.대본재등장", uid)


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
