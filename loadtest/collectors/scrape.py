"""대상 앱의 /actuator/prometheus 를 주기적으로 긁어 CSV 로 흘려보낸다.

부하 생성기에서 실행한다. SIGTERM 을 받을 때까지 계속 돈다.
누적 카운터(acquire_seconds, gc_pause)는 원본 그대로 남긴다.
구간 평균은 분석할 때 차분으로 계산해야 정확하다.
"""
import re
import signal
import sys
import time
import urllib.request

URL = sys.argv[1] if len(sys.argv) > 1 else "http://10.0.23.25:8081/actuator/prometheus"
INTERVAL = float(sys.argv[2]) if len(sys.argv) > 2 else 5.0

# 이름 -> (프로메테우스 metric, 라벨 필터 substring 또는 None, 합산 여부)
FIELDS = [
    ("system_cpu",        "system_cpu_usage",                        None,            False),
    ("process_cpu",       "process_cpu_usage",                       None,            False),
    ("tomcat_busy",       "tomcat_threads_busy_threads",             None,            False),
    ("tomcat_current",    "tomcat_threads_current_threads",          None,            False),
    ("tomcat_max",        "tomcat_threads_config_max_threads",       None,            False),
    ("hikari_active",     "hikaricp_connections_active",             None,            False),
    ("hikari_idle",       "hikaricp_connections_idle",               None,            False),
    ("hikari_pending",    "hikaricp_connections_pending",            None,            False),
    ("hikari_max",        "hikaricp_connections_max",                None,            False),
    ("hikari_acq_sum",    "hikaricp_connections_acquire_seconds_sum", None,           False),
    ("hikari_acq_count",  "hikaricp_connections_acquire_seconds_count", None,         False),
    ("hikari_timeout",    "hikaricp_connections_timeout_total",      None,            False),
    ("gc_pause_sum",      "jvm_gc_pause_seconds_sum",                None,            True),
    ("gc_pause_count",    "jvm_gc_pause_seconds_count",              None,            True),
    ("heap_used",         "jvm_memory_used_bytes",                   'area="heap"',   True),
    ("heap_max",          "jvm_memory_max_bytes",                    'area="heap"',   True),
    ("threads_live",      "jvm_threads_live_threads",                None,            False),
    ("req_active",        "http_server_requests_active_seconds_gcount", None,          True),
]

running = True


def stop(*_):
    global running
    running = False


signal.signal(signal.SIGTERM, stop)
signal.signal(signal.SIGINT, stop)


def parse(text):
    values = {}
    for name, metric, label, summed in FIELDS:
        total, found = 0.0, False
        pattern = re.compile(rf"^{re.escape(metric)}(\{{[^}}]*\}})? ([-\d.eE+]+)$", re.M)
        for m in pattern.finditer(text):
            labels = m.group(1) or ""
            if label and label not in labels:
                continue
            try:
                v = float(m.group(2))
            except ValueError:
                continue
            found = True
            if summed:
                total += v
            else:
                total = v
                break
        values[name] = total if found else ""
    return values


print("ts," + ",".join(f[0] for f in FIELDS), flush=True)

while running:
    started = time.time()
    try:
        with urllib.request.urlopen(URL, timeout=4) as res:
            row = parse(res.read().decode())
        print(f"{time.time():.3f}," + ",".join(str(row[f[0]]) for f in FIELDS), flush=True)
    except Exception as e:
        print(f"{time.time():.3f},ERROR:{type(e).__name__}", flush=True)

    delay = INTERVAL - (time.time() - started)
    if delay > 0:
        time.sleep(delay)
