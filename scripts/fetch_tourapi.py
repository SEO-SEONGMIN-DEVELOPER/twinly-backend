import csv
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

BASE = "https://apis.data.go.kr/B551011/KorService2"
DATA_DIR = Path(os.environ.get("TWINLY_DATA_DIR", Path.home() / "twinly-data"))
KEY_FILE = DATA_DIR / "tourapi.key"

AREAS_CSV = DATA_DIR / "tourapi-areas.csv"
LDONG_CSV = DATA_DIR / "tourapi-ldong.csv"
LCLS_CSV = DATA_DIR / "tourapi-lcls.csv"
PLACES_CSV = DATA_DIR / "tourapi-places.csv"
MERGED_CSV = DATA_DIR / "tourapi-places-merged.csv"

CONTENT_TYPES = {
    "12": "관광지",
    "14": "문화시설",
    "15": "축제공연행사",
    "25": "여행코스",
    "28": "레포츠",
    "32": "숙박",
    "38": "쇼핑",
    "39": "음식점",
}
PLACE_FIELDS = ["contentid", "contenttypeid", "contenttypename", "title", "addr1", "addr2",
                "areacode", "sigungucode", "lDongRegnCd", "lDongSignguCd", "cat1", "cat2", "cat3",
                "lclsSystm1", "lclsSystm2", "lclsSystm3",
                "mapx", "mapy", "firstimage", "tel", "modifiedtime",
                "eventstartdate", "eventenddate"]

LIST_ROWS = 10000
QUOTA_EXCEEDED = "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"


class QuotaExceeded(Exception):
    pass


def load_key():
    key = os.environ.get("TOUR_API_KEY")
    if not key and KEY_FILE.exists():
        key = KEY_FILE.read_text(encoding="utf-8").strip()
    if not key:
        sys.exit(f"TOUR_API_KEY 환경변수 또는 {KEY_FILE} 파일이 필요합니다.")
    return urllib.parse.unquote(key) if "%" in key else key


def call(key, op, retries=3, **params):
    query = {"serviceKey": key, "MobileOS": "ETC", "MobileApp": "Twinly", "_type": "json"}
    query.update(params)
    url = f"{BASE}/{op}?{urllib.parse.urlencode(query)}"
    last = None
    for attempt in range(retries):
        try:
            with urllib.request.urlopen(url, timeout=60) as res:
                text = res.read().decode("utf-8")
            body = json.loads(text)
            header = body["response"]["header"]
            code, msg = header["resultCode"], header["resultMsg"]
            if code != "0000":
                if QUOTA_EXCEEDED in msg:
                    raise QuotaExceeded(msg)
                raise RuntimeError(f"{op} {params}: {code} {msg}")
            data = body["response"]["body"]
            items = data.get("items") or {}
            item = items.get("item", []) if isinstance(items, dict) else []
            return item, int(data.get("totalCount", 0))
        except QuotaExceeded:
            raise
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", errors="replace")
            try:
                head = json.loads(body)["OpenAPI_ServiceResponse"]["cmmMsgHeader"]
            except (json.JSONDecodeError, KeyError):
                last = e
                time.sleep(1.5 * (attempt + 1))
                continue
            if head.get("returnReasonCode") == "22":
                raise QuotaExceeded(head.get("errMsg", ""))
            raise RuntimeError(f"{op}: {head.get('returnReasonCode')} {head.get('errMsg')} ({head.get('returnAuthMsg')})")
        except (urllib.error.URLError, json.JSONDecodeError, KeyError, TimeoutError) as e:
            last = e
            time.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"{op} {params}: 재시도 실패 ({last})")


def write_csv(path, fields, rows, mode="w"):
    exists = path.exists() and mode == "a"
    with open(path, mode, newline="", encoding="utf-8-sig") as f:
        w = csv.DictWriter(f, fieldnames=fields, extrasaction="ignore")
        if not exists:
            w.writeheader()
        w.writerows(rows)


def read_csv(path):
    if not path.exists():
        return []
    with open(path, newline="", encoding="utf-8-sig") as f:
        return list(csv.DictReader(f))


def cmd_areas(key):
    sidos, _ = call(key, "areaCode2", numOfRows=100, pageNo=1)
    rows = []
    for s in sidos:
        rows.append({"areacode": s["code"], "sigungucode": "", "sido": s["name"], "sigungu": ""})
        guns, _ = call(key, "areaCode2", areaCode=s["code"], numOfRows=100, pageNo=1)
        for g in guns:
            rows.append({"areacode": s["code"], "sigungucode": g["code"], "sido": s["name"], "sigungu": g["name"]})
        time.sleep(0.1)
    write_csv(AREAS_CSV, ["areacode", "sigungucode", "sido", "sigungu"], rows)
    print(f"지역코드 {len(sidos)}개 시도, {len(rows) - len(sidos)}개 시군구 -> {AREAS_CSV}")


def cmd_ldong(key):
    sidos, _ = call(key, "ldongCode2", numOfRows=100, pageNo=1)
    rows = []
    for s in sidos:
        rows.append({"regn": s["code"], "signgu": "", "sido": s["name"], "sigungu": ""})
        guns, _ = call(key, "ldongCode2", lDongRegnCd=s["code"], numOfRows=500, pageNo=1)
        for g in guns:
            rows.append({"regn": s["code"], "signgu": g["code"], "sido": s["name"], "sigungu": g["name"]})
        time.sleep(0.1)
    write_csv(LDONG_CSV, ["regn", "signgu", "sido", "sigungu"], rows)
    print(f"법정동코드 {len(sidos)}개 시도, {len(rows) - len(sidos)}개 시군구 -> {LDONG_CSV}")


def cmd_lcls(key):
    rows = []
    top, _ = call(key, "lclsSystmCode2", numOfRows=100, pageNo=1)
    for a in top:
        rows.append({"level": 1, "code": a["code"], "name": a["name"], "parent": ""})
        mids, _ = call(key, "lclsSystmCode2", lclsSystm1=a["code"], numOfRows=100, pageNo=1)
        for b in mids:
            rows.append({"level": 2, "code": b["code"], "name": b["name"], "parent": a["code"]})
            smalls, _ = call(key, "lclsSystmCode2", lclsSystm1=a["code"], lclsSystm2=b["code"], numOfRows=200, pageNo=1)
            for c in smalls:
                rows.append({"level": 3, "code": c["code"], "name": c["name"], "parent": b["code"]})
            time.sleep(0.05)
    write_csv(LCLS_CSV, ["level", "code", "name", "parent"], rows)
    print(f"분류코드 {len(rows)}개 -> {LCLS_CSV}")


def cmd_places(key):
    calls = 0
    total_rows = 0
    first = True
    for ct, name in CONTENT_TYPES.items():
        page, got = 1, 0
        while True:
            if ct == "15":
                items, total = call(key, "searchFestival2", eventStartDate="20000101", numOfRows=LIST_ROWS, pageNo=page, arrange="C")
            else:
                items, total = call(key, "areaBasedList2", contentTypeId=ct, numOfRows=LIST_ROWS, pageNo=page, arrange="C")
            calls += 1
            for it in items:
                it["contenttypename"] = name
            write_csv(PLACES_CSV, PLACE_FIELDS, items, mode="w" if first else "a")
            first = False
            got += len(items)
            total_rows += len(items)
            print(f"{name}: {got}/{total} (호출 {calls})")
            if got >= total or not items:
                break
            page += 1
            time.sleep(0.2)
    print(f"장소 {total_rows}건, 호출 {calls}건 -> {PLACES_CSV}")


def cmd_merge():
    areas = read_csv(AREAS_CSV)
    sido_old = {a["areacode"]: a["sido"] for a in areas if not a["sigungucode"]}
    gun_old = {(a["areacode"], a["sigungucode"]): a["sigungu"] for a in areas if a["sigungucode"]}
    ldong = read_csv(LDONG_CSV)
    sido_new = {a["regn"]: a["sido"] for a in ldong if not a["signgu"]}
    gun_new = {(a["regn"], a["signgu"]): a["sigungu"] for a in ldong if a["signgu"]}
    lcls = {c["code"]: c["name"] for c in read_csv(LCLS_CSV)}
    rows = []
    for p in read_csv(PLACES_CSV):
        old_name = sido_old.get(p["areacode"], "")
        p["sido"] = sido_new.get(p["lDongRegnCd"]) or next((n for n in sido_new.values() if n.startswith(old_name)), old_name) if old_name or p["lDongRegnCd"] else ""
        p["sigungu"] = gun_new.get((p["lDongRegnCd"], p["lDongSignguCd"])) or gun_old.get((p["areacode"], p["sigungucode"]), "")
        p["lcls1"] = lcls.get(p["lclsSystm1"], "")
        p["lcls2"] = lcls.get(p["lclsSystm2"], "")
        p["lcls3"] = lcls.get(p["lclsSystm3"], "")
        rows.append(p)
    fields = ["contentid", "contenttypeid", "contenttypename", "title", "sido", "sigungu", "addr1", "addr2",
              "lcls1", "lcls2", "lcls3", "mapx", "mapy", "firstimage", "tel", "eventstartdate", "eventenddate",
              "modifiedtime", "areacode", "sigungucode", "lDongRegnCd", "lDongSignguCd",
              "cat1", "cat2", "cat3", "lclsSystm1", "lclsSystm2", "lclsSystm3"]
    write_csv(MERGED_CSV, fields, rows)
    no_sido = sum(1 for r in rows if not r["sido"])
    no_lcls = sum(1 for r in rows if not r["lcls3"])
    print(f"병합 {len(rows)}건 (시도 미매핑 {no_sido}건, 소분류 미매핑 {no_lcls}건) -> {MERGED_CSV}")


def main():
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    if len(sys.argv) < 2 or sys.argv[1] not in ("areas", "places", "merge", "all"):
        sys.exit("사용법: fetch_tourapi.py areas|places|merge|all")
    cmd = sys.argv[1]
    if cmd == "merge":
        cmd_merge()
        return
    key = load_key()
    if cmd in ("areas", "all"):
        cmd_areas(key)
        cmd_ldong(key)
        cmd_lcls(key)
    if cmd in ("places", "all"):
        cmd_places(key)
    if cmd == "all":
        cmd_merge()


if __name__ == "__main__":
    main()
