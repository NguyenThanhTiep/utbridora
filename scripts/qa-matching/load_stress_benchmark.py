"""
QA Performance & Stress Testing Benchmark for UT.Bridora
Target: http://127.0.0.1:8088
Author: QA04
"""

import sys
import time
import urllib.request
import urllib.error
import concurrent.futures
import statistics

TARGET_URL = "http://127.0.0.1:8088/api/v1/auth/csrf"
FRONTEND_URL = "http://127.0.0.1:8088/"

def make_request(url, timeout=5.0):
    start = time.perf_counter()
    status = 0
    err = None
    try:
        req = urllib.request.Request(url, headers={"User-Agent": "UTBridora-QA04-StressTest/1.0"})
        with urllib.request.urlopen(req, timeout=timeout) as response:
            status = response.getcode()
            response.read() # drain
    except urllib.error.HTTPError as e:
        status = e.code
    except Exception as e:
        err = type(e).__name__
    elapsed = (time.perf_counter() - start) * 1000.0 # ms
    return status, elapsed, err

def run_phase(name, url, concurrency, total_requests):
    print(f"\n========================================================")
    print(f"[*] Chạy pha: {name}")
    print(f"[*] Mục tiêu: {url}")
    print(f"[*] Số client đồng thời (Concurrency): {concurrency}")
    print(f"[*] Tổng số request gửi: {total_requests}")
    print(f"--------------------------------------------------------")
    
    start_time = time.perf_counter()
    latencies = []
    status_counts = {}
    error_counts = {}

    with concurrent.futures.ThreadPoolExecutor(max_workers=concurrency) as executor:
        futures = [executor.submit(make_request, url) for _ in range(total_requests)]
        for f in concurrent.futures.as_completed(futures):
            status, elapsed, err = f.result()
            latencies.append(elapsed)
            if status > 0:
                status_counts[status] = status_counts.get(status, 0) + 1
            if err:
                error_counts[err] = error_counts.get(err, 0) + 1

    total_time = time.perf_counter() - start_time
    rps = total_requests / total_time if total_time > 0 else 0
    success = status_counts.get(200, 0)
    failed = total_requests - success

    latencies.sort()
    avg_lat = statistics.mean(latencies) if latencies else 0
    median_lat = statistics.median(latencies) if latencies else 0
    p90_lat = latencies[int(len(latencies) * 0.90)] if latencies else 0
    p95_lat = latencies[int(len(latencies) * 0.95)] if latencies else 0
    p99_lat = latencies[int(len(latencies) * 0.99)] if latencies else 0
    min_lat = min(latencies) if latencies else 0
    max_lat = max(latencies) if latencies else 0

    print(f"[-] Thống kê kết quả:")
    print(f"    - Thời gian hoàn tất:  {total_time:.2f} s")
    print(f"    - Thông lượng (RPS):   {rps:.1f} requests/sec")
    print(f"    - Thành công (200 OK): {success}/{total_requests} ({success/total_requests*100:.1f}%)")
    print(f"    - Lỗi / Timeout:       {failed}/{total_requests}")
    if error_counts:
        print(f"    - Chi tiết lỗi:        {error_counts}")
    print(f"[-] Độ trễ (Latency):")
    print(f"    - Nhanh nhất (Min):    {min_lat:.1f} ms")
    print(f"    - Trung bình (Avg):    {avg_lat:.1f} ms")
    print(f"    - Median (P50):        {median_lat:.1f} ms")
    print(f"    - P90:                 {p90_lat:.1f} ms")
    print(f"    - P95:                 {p95_lat:.1f} ms")
    print(f"    - P99:                 {p99_lat:.1f} ms")
    print(f"    - Chậm nhất (Max):     {max_lat:.1f} ms")
    
    return {
        "phase": name,
        "concurrency": concurrency,
        "total": total_requests,
        "rps": rps,
        "success_rate": success / total_requests * 100,
        "avg_latency": avg_lat,
        "p95_latency": p95_lat,
        "max_latency": max_lat,
        "failed": failed
    }

def main():
    print("=================================================================")
    print(" BẮT ĐẦU CHƯƠNG TRÌNH KIỂM THỬ SỨC CHỊU TẢI (STRESS TEST) - UT.BRIDORA")
    print("=================================================================")
    
    # Kịch bản tải tăng dần để tìm Breaking Point
    scenarios = [
        ("Mức 1: Tải thường (Normal Load)", 10, 100),
        ("Mức 2: Tải cao (High Load)", 50, 250),
        ("Mức 3: Tải rất cao (Stress Load)", 100, 500),
        ("Mức 4: Tải cực đại (Saturation Test)", 200, 800),
        ("Mức 5: Tải phá vỡ (Breaking Point Test)", 350, 1000),
    ]

    results = []
    for name, concurrency, total in scenarios:
        res = run_phase(name, TARGET_URL, concurrency, total)
        results.append(res)
        time.sleep(1) # nghỉ 1 giây giữa các pha để socket hạ nhiệt

    print("\n=================================================================")
    print(" BẢNG TỔNG HỢP KIỂM THỬ CHỊU TẢI (CAPACITY MATRIX)")
    print("=================================================================")
    print(f"{'Pha kiểm thử':<38} | {'Đồng thời':<9} | {'RPS':<8} | {'P95 (ms)':<9} | {'Tỉ lệ PASS':<10} | {'Đánh giá'}")
    print("-" * 95)
    for r in results:
        eval_str = "Tốt (Mượt mà)"
        if r['success_rate'] < 95 or r['p95_latency'] > 2000:
            eval_str = "NGHẼN (Bottleneck)"
        elif r['success_rate'] < 80 or r['p95_latency'] > 5000:
            eval_str = "SẬP / QUÁ TẢI (Breaking Point)"
        elif r['p95_latency'] > 500:
            eval_str = "Chậm dần (Warning)"
            
        print(f"{r['phase']:<38} | {r['concurrency']:<9} | {r['rps']:<8.1f} | {r['p95_latency']:<9.1f} | {r['success_rate']:<9.1f}% | {eval_str}")
    print("=================================================================\n")

if __name__ == "__main__":
    main()
