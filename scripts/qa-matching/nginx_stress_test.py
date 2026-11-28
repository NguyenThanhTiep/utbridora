"""
Nginx Static Asset Stress Test for UT.Bridora
Target: http://127.0.0.1:8088/ (Frontend Static Nginx)
"""

import time
import urllib.request
import urllib.error
import concurrent.futures
import statistics

TARGET_URL = "http://127.0.0.1:8088/"

def make_request(url):
    start = time.perf_counter()
    status = 0
    err = None
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "UTBridora-QA04-NginxTest/1.0"})
        with urllib.request.urlopen(req, timeout=5.0) as resp:
            status = resp.getcode()
            resp.read()
    except urllib.error.HTTPError as e:
        status = e.code
    except Exception as e:
        err = type(e).__name__
    elapsed = (time.perf_counter() - start) * 1000.0
    return status, elapsed, err

def run_nginx_test(concurrency=200, total=1000):
    print(f"\n[*] KIỂM THỬ TẢI FRONTEND NGINX (GET /)")
    print(f"[*] Số client đồng thời: {concurrency}")
    print(f"[*] Tổng số request: {total}")
    print("-" * 65)

    start_time = time.perf_counter()
    latencies = []
    statuses = {}
    errors = {}

    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(make_request, TARGET_URL) for _ in range(total)]
        for f in concurrent.futures.as_completed(futures):
            st, el, er = f.result()
            latencies.append(el)
            statuses[st] = statuses.get(st, 0) + 1
            if er:
                errors[er] = errors.get(er, 0) + 1

    total_time = time.perf_counter() - start_time
    rps = total / total_time if total_time > 0 else 0

    latencies.sort()
    avg = statistics.mean(latencies)
    p95 = latencies[int(len(latencies) * 0.95)]
    min_l = min(latencies)
    max_l = max(latencies)

    print(f"[-] Thời gian hoàn tất: {total_time:.2f} s")
    print(f"[-] Thông lượng (RPS):  {rps:.1f} req/sec")
    print(f"[-] Trạng thái HTTP:    {statuses}")
    if errors:
        print(f"[-] Lỗi timeout:        {errors}")
    print(f"[-] Độ trễ (Latency):")
    print(f"    - Nhanh nhất (Min): {min_l:.1f} ms")
    print(f"    - Trung bình (Avg): {avg:.1f} ms")
    print(f"    - P95:              {p95:.1f} ms")
    print(f"    - Chậm nhất (Max):  {max_l:.1f} ms")

if __name__ == "__main__":
    run_nginx_test()
