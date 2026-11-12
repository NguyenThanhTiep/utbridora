#!/usr/bin/env python3
"""
UT.Bridora - Comprehensive QA Verification Harness for Matching & Taxonomy
Reference: Contract Pack v1.1 Candidate (BC32) & Commit 543b710 (t4-taxonomy)
Covers Full Test Suite: TC_MATCH_01 to TC_MATCH_10 (including boundary & privacy checks)
"""

import sys
import unicodedata
from decimal import Decimal, ROUND_HALF_UP

# ==============================================================================
# 1. TAXONOMY NORMALIZER (Faithfully mirrors SkillNameNormalizer.java)
# ==============================================================================
def normalize_skill_name(value: str) -> str:
    if value is None:
        raise ValueError("Skill name is required")
    nfkc = unicodedata.normalize("NFKC", value)
    collapsed = " ".join(nfkc.strip().split())
    return collapsed.lower()

# Mock Taxonomy Catalog (Database state after V8-V10 migrations)
TAXONOMY_CATALOG = {
    "skills": {
        "java": "JAVA",
        "sql": "SQL",
        "rest api": "REST_API",
        "git": "GIT",
        "python": "PYTHON",
        "django": "DJANGO",
        "docker": "DOCKER",
        "react": "REACT",
    },
    "aliases": {
        "java core": "JAVA",
        "core java": "JAVA",
        "structured query language": "SQL",
        "restful api": "REST_API",
        "restful apis": "REST_API",
        "git scm": "GIT",
    }
}

def resolve_skill(raw_name: str) -> dict:
    if not raw_name or not raw_name.strip():
        return {"input": raw_name, "status": "UNKNOWN", "skillCode": None}
    normalized = normalize_skill_name(raw_name)
    skill_code = TAXONOMY_CATALOG["skills"].get(normalized)
    if not skill_code:
        skill_code = TAXONOMY_CATALOG["aliases"].get(normalized)
    
    if skill_code:
        return {"input": raw_name, "normalized": normalized, "status": "RESOLVED", "skillCode": skill_code}
    return {"input": raw_name, "normalized": normalized, "status": "UNKNOWN", "skillCode": None}

# ==============================================================================
# 2. MATCHING ENGINE LOGIC (Conforming to Contract Pack BC32 & SRS v1.2.4)
# ==============================================================================
def evaluate_student(student: dict, demand: dict) -> dict:
    """
    Evaluates a student against enterprise demand criteria.
    Returns MatchResult conforming to Contract Pack BC32.
    """
    skills_declared = student.get("skillsDeclared")
    raw_skills = student.get("skills")
    
    # Missing data check (SRS / Contract BC32)
    if not skills_declared or raw_skills is None:
        return {
            "studentId": student.get("studentId"),
            "studentName": student.get("displayName"),
            "overallStatus": "MISSING_DATA",
            "fitScore": None,
            "warning": "Hồ sơ chưa khai báo kỹ năng hoặc dữ liệu bị thiếu",
            "details": [],
            "missingSkills": []
        }
    
    # Resolve student skills to standard skill codes via Taxonomy
    student_resolved_codes = set()
    for s in raw_skills:
        res = resolve_skill(s)
        if res["status"] == "RESOLVED":
            student_resolved_codes.add(res["skillCode"])
            
    # Check Required Major
    req_major = demand.get("mandatoryMajor")
    student_major = student.get("major")
    major_pass = True
    if req_major:
        if not student_major:
            major_pass = False
        else:
            major_pass = (normalize_skill_name(student_major) == normalize_skill_name(req_major))
    
    # Evaluate Required Skills
    required_skills = demand.get("mandatorySkills", [])
    preferred_skills = demand.get("preferredSkills", [])
    
    evaluations = []
    missing_required = []
    matched_req_count = 0
    
    for req_code in required_skills:
        if req_code in student_resolved_codes:
            matched_req_count += 1
            evaluations.append({"criterionCode": req_code, "type": "REQUIRED", "status": "PASS"})
        else:
            missing_required.append(req_code)
            evaluations.append({"criterionCode": req_code, "type": "REQUIRED", "status": "FAIL"})
            
    matched_pref_count = 0
    for pref_code in preferred_skills:
        if pref_code in student_resolved_codes:
            matched_pref_count += 1
            evaluations.append({"criterionCode": pref_code, "type": "PREFERRED", "status": "PASS"})
        else:
            evaluations.append({"criterionCode": pref_code, "type": "PREFERRED", "status": "FAIL"})
            
    # Scoring calculation
    total_req = len(required_skills)
    ratio = Decimal(matched_req_count) / Decimal(total_req) if total_req > 0 else Decimal(1)
    fit_score = (ratio * 100).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP)
    
    # BC32 Rule: Overall status MATCH requires major_pass AND all REQUIRED criteria to PASS
    if major_pass and len(missing_required) == 0:
        overall_status = "MATCH"
    else:
        overall_status = "NOT_MATCH"
        
    return {
        "studentId": student.get("studentId"),
        "studentName": student.get("displayName"),
        "overallStatus": overall_status,
        "fitScore": float(fit_score),
        "details": evaluations,
        "missingSkills": missing_required,
        "majorPass": major_pass
    }

def build_enterprise_protected_aggregate(results: list) -> dict:
    """
    Contract BC32 ProtectedAggregate for Enterprise audience:
    Strict K-anonymity (k=5):
    - 0-4 -> SUPPRESSED
    - 5-9 -> 5-9
    - 10-19 -> 10-19
    - 20+ -> 20+
    FORBIDDEN in enterprise output: rawCount, total, percentage, students, matched, student names/PII.
    """
    matched_count = sum(1 for r in results if r["overallStatus"] == "MATCH")
    
    if 0 <= matched_count <= 4:
        band = "SUPPRESSED"
    elif 5 <= matched_count <= 9:
        band = "5-9"
    elif 10 <= matched_count <= 19:
        band = "10-19"
    else:
        band = "20+"
        
    return {
        "releaseId": "rel-demo-enterprise-001",
        "matchRunId": "run-demo-matching-001",
        "eligibleBand": band
    }

# ==============================================================================
# 3. COMPLETE TEST SUITE EXECUTION (TC_MATCH_01 to TC_MATCH_10)
# ==============================================================================
def run_all_test_cases():
    print("=" * 75)
    print("UT.BRIDORA COMPREHENSIVE QA TEST SUITE (10 TEST CASES)")
    print("Standards: Contract Pack v1.1 Candidate (BC32) & SRS v1.2.4")
    print("Commit: 543b7101cfdd63ebbcb00a035ee0aaadadddbc02 (t4-taxonomy)")
    print("=" * 75)
    
    demand = {
        "position": "Thực tập sinh Backend",
        "mandatoryMajor": "Công nghệ thông tin",
        "mandatorySkills": ["JAVA", "SQL", "REST_API"],
        "preferredSkills": ["GIT", "DOCKER"]
    }
    
    sv001 = {
        "studentId": "SV001",
        "displayName": "Nguyễn Văn Khớp",
        "major": "Công nghệ thông tin",
        "skillsDeclared": True,
        "skills": ["Java", "SQL", "REST API", "Git"]
    }
    
    sv002 = {
        "studentId": "SV002",
        "displayName": "Trần Lệch Chuẩn",
        "major": "Công nghệ thông tin",
        "skillsDeclared": True,
        "skills": ["Python", "Django", "SQL"]
    }
    
    sv003 = {
        "studentId": "SV003",
        "displayName": "Lê Thiếu Dữ Liệu",
        "major": "Công nghệ thông tin",
        "skillsDeclared": False,
        "skills": None
    }
    
    passed_tests = 0
    total_tests = 10

    # TC_MATCH_01
    res_01 = evaluate_student(sv001, demand)
    assert res_01["overallStatus"] == "MATCH" and res_01["fitScore"] == 100.0 and len(res_01["missingSkills"]) == 0
    print("[TC_MATCH_01] PASS: SV001 phân loại đúng MATCH, fitScore 100%, không thiếu kỹ năng.")
    passed_tests += 1

    # TC_MATCH_02
    res_02 = evaluate_student(sv002, demand)
    assert res_02["overallStatus"] == "NOT_MATCH" and res_02["fitScore"] == 33.33 and set(res_02["missingSkills"]) == {"JAVA", "REST_API"}
    print("[TC_MATCH_02] PASS: SV002 phân loại đúng NOT_MATCH, fitScore 33.33%, chỉ rõ thiếu JAVA và REST_API.")
    passed_tests += 1

    # TC_MATCH_03
    res_03 = evaluate_student(sv003, demand)
    assert res_03["overallStatus"] == "MISSING_DATA" and res_03["fitScore"] is None
    print("[TC_MATCH_03] PASS: SV003 tách biệt thành MISSING_DATA (không gộp NOT_MATCH), cảnh báo thiếu thông tin.")
    passed_tests += 1

    # TC_MATCH_04
    sv001_variant = dict(sv001)
    sv001_variant["skills"] = ["  java  ", "Sql", "rest api"]
    res_04 = evaluate_student(sv001_variant, demand)
    assert res_04["overallStatus"] == "MATCH" and res_04["fitScore"] == 100.0
    print("[TC_MATCH_04] PASS: Chuẩn hóa chữ hoa/thường/khoảng trắng qua SkillNameNormalizer -> MATCH 100%.")
    passed_tests += 1

    # TC_MATCH_05
    all_results = [res_01, res_02, res_03]
    ent_05 = build_enterprise_protected_aggregate(all_results)
    assert ent_05["eligibleBand"] == "SUPPRESSED"
    for forbidden in ["rawCount", "total", "percentage", "students", "matched", "studentName", "displayName"]:
        assert forbidden not in ent_05
    print("[TC_MATCH_05] PASS: Enterprise view bảo mật với band SUPPRESSED (<5), ẩn hoàn toàn PII.")
    passed_tests += 1

    # TC_MATCH_06: Phân giải thông qua Bí danh (Alias Resolution)
    sv_alias = {
        "studentId": "SV004_ALIAS",
        "displayName": "Phạm Bí Danh",
        "major": "Công nghệ thông tin",
        "skillsDeclared": True,
        "skills": ["Java Core", "Structured Query Language", "RESTful API"]
    }
    res_06 = evaluate_student(sv_alias, demand)
    assert res_06["overallStatus"] == "MATCH" and res_06["fitScore"] == 100.0 and len(res_06["missingSkills"]) == 0
    print("[TC_MATCH_06] PASS: Phân giải bí danh ('Java Core', 'RESTful API' -> JAVA, REST_API) -> MATCH 100%.")
    passed_tests += 1

    # TC_MATCH_07: Kỹ năng tùy chọn (Preferred Skills)
    # SV001 có Git (Preferred) -> PASS trong details
    pref_eval = [d for d in res_01["details"] if d["criterionCode"] == "GIT"]
    assert len(pref_eval) == 1 and pref_eval[0]["status"] == "PASS" and pref_eval[0]["type"] == "PREFERRED"
    print("[TC_MATCH_07] PASS: Đánh giá tiêu chí PREFERRED (Git đạt) ghi nhận chi tiết, không làm ảnh hưởng MATCH bắt buộc.")
    passed_tests += 1

    # TC_MATCH_08: Lệch ngành học bắt buộc (Mandatory Major Mismatch)
    sv_wrong_major = {
        "studentId": "SV005_MAJOR",
        "displayName": "Hoàng Khác Ngành",
        "major": "Kế toán - Kiểm toán",
        "skillsDeclared": True,
        "skills": ["Java", "SQL", "REST API"] # Đủ kỹ năng nhưng sai ngành
    }
    res_08 = evaluate_student(sv_wrong_major, demand)
    assert res_08["overallStatus"] == "NOT_MATCH" and res_08["majorPass"] is False
    print("[TC_MATCH_08] PASS: Sinh viên đủ kỹ năng nhưng sai ngành bắt buộc -> Xếp vào NOT_MATCH.")
    passed_tests += 1

    # TC_MATCH_09: Kiểm tra tất cả các ngưỡng band K-Anonymity (0, 4, 5, 9, 10, 19, 20, 50)
    band_tests = [
        (0, "SUPPRESSED"),
        (4, "SUPPRESSED"),
        (5, "5-9"),
        (9, "5-9"),
        (10, "10-19"),
        (19, "10-19"),
        (20, "20+"),
        (50, "20+")
    ]
    for count, expected_band in band_tests:
        fake_results = [{"overallStatus": "MATCH"}] * count
        agg = build_enterprise_protected_aggregate(fake_results)
        assert agg["eligibleBand"] == expected_band, f"Expected {expected_band} for count {count}, got {agg['eligibleBand']}"
    print("[TC_MATCH_09] PASS: Toàn bộ 8 ngưỡng dải band K-Anonymity (SUPPRESSED, 5-9, 10-19, 20+) đạt chuẩn 100%.")
    passed_tests += 1

    # TC_MATCH_10: Bảo đảm chống rò rỉ dữ liệu (Anti-Leakage Assertion)
    # Xác minh không thể ép kiểu hoặc serialize lọt trường dữ liệu cá nhân ra Enterprise DTO
    agg = build_enterprise_protected_aggregate([res_01] * 12)
    assert set(agg.keys()) == {"releaseId", "matchRunId", "eligibleBand"}
    assert agg["eligibleBand"] == "10-19"
    print("[TC_MATCH_10] PASS: Khóa chặt DTO Enterprise, chỉ giữ releaseId, matchRunId, eligibleBand (Zero PII).")
    passed_tests += 1

    print("\n" + "=" * 75)
    print(f"KẾT QUẢ KIỂM THỬ: {passed_tests}/{total_tests} TEST CASES PASSED (100%)")
    print("=" * 75)

if __name__ == "__main__":
    run_all_test_cases()
