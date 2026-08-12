import json, collections, datetime, sys
tag = sys.argv[1]
agg = collections.defaultdict(list)
t0 = None
with open("/tmp/k6-raw.json") as f:
    for line in f:
        if '"http_req_duration"' not in line:
            continue
        try:
            d = json.loads(line)
        except Exception:
            continue
        if d.get("type") != "Point":
            continue
        dd = d.get("data", {})
        if dd.get("tags", {}).get("name") != tag:
            continue
        ts = datetime.datetime.fromisoformat(dd["time"][:19])
        if t0 is None:
            t0 = ts
        agg[int((ts - t0).total_seconds()) // 15 * 15].append(dd["value"])

print(f"{'경과':>5} {'RPS':>6} {'p50':>10} {'p95':>10} {'p99':>10}")
for b in sorted(agg):
    v = sorted(agg[b]); n = len(v)
    p = lambda q: v[min(int(n * q), n - 1)]
    print(f"{b:5d} {n/15:6.0f} {p(0.5):8.1f}ms {p(0.95):8.1f}ms {p(0.99):8.1f}ms")
