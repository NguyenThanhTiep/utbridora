"""
BCrypt Stress Test for UT.Bridora
Simulates concurrent login attempts (CPU-bound test)
"""

import sys
import time
import urllib.request
import urllib.error
import json
import concurrent.futures

LOGIN_URL = "http://127.0.0.1:8088/api/v1/auth/login"

def attempt_login(i):
    start = time.perf_counter()
    status = 0
    err = None
    data = json.dumps({"username": f"user_{i}@test.com", "password": "wrongpassword123"}).encode('utf-8')
    try:
        req = urllib.request.Request(
            LOGIN_URL,
            data=data,
            headers={
                "User-Agent": "UTBridora-QA04-StressTest/1.0",
                "Content-Type": "application/json"
            }
        )
        with urllib.request.urlopen(req, timeout=10.0) as resp:
            status = resp.getcode()
    except urllib.error.HTTPError as e:
        status = e.code # 401 Unauthorized expected
    except Exception as e:
        err = type(e).__name__
    elapsed = (time.perf_counter() - start) * 1000.0
    return status, elapsed, err

def test_bcrypt_load(concurrency=30, total=60):
    print(f"\n[*] KIỂM THỬ TẢI XÁC THỰC BCRYPT (POST /api/v1/auth/login)")
    print(f"[*] Số client đồng thời: {concurrency}")
    print(f"[*] Tổng số lần thử login: {total}")
    print(f"[*] BCrypt Strength cấu hình trong Spring Boot: 12 (4096 iterations/core)")
    print("-" * 65)

    start_time = time.perf_counter()
    latencies = []
    statuses = {}
    errors = {}

    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(attempt_login, i) for i in range(total)]
        for f in concurrent.futures.as_completed(futures):
            st, el, er = f.result()
            latencies.append(el)
            statuses[st] = statuses.get(st, 0) + 1
            if er:
                errors[er] = errors.get(er, 0) + 1

    total_time = time.perf_counter() - start_time
    rps = total / total_time if total_time > 0 else 0

    latencies.sort()
    avg = sum(latencies) / len(latencies)
    p95 = latencies[int(len(latencies) * 0.95)]
    min_l = min(latencies)
    max_l = max(latencies)

    print(f"[-] Thời gian hoàn tất: {total_time:.2f} s")
    print(f"[-] Thông lượng xử lý:  {rps:.1f} login/sec")
    print(f"[-] Trạng thái HTTP:    {statuses}")
    if errors:
        print(f"[-] Lỗi timeout:        {errors}")
    print(f"[-] Độ trễ (Latency):")
    print(f"    - Nhanh nhất (Min): {min_l:.1f} ms")
    print(f"    - Trung bình (Avg): {avg:.1f} ms")
    print(f"    - P95:              {p95:.1f} ms")
    print(f"    - Chậm nhất (Max):  {max_l:.1f} ms")

if __name__ == "__main__":
    test_bcrypt_load()
