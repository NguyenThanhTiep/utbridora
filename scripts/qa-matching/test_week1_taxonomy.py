#!/usr/bin/env python3
"""
UT.Bridora - QA Test Suite for Week 1 (Tuần 1): Skill Taxonomy, Aliases, Version & Port
Scope:
- Thống nhất danh mục mã kỹ năng chuẩn (Canonical Skill Code).
- Quản lý tên gọi khác (Skill Aliases).
- Quản lý phiên bản danh mục kỹ năng (Taxonomy Versioning).
- Đảm bảo Port taxonomy (SkillTaxonomyPort) dùng chung cho T2 (Student) và T5 (Enterprise):
  T2 và T5 đọc cùng một mã kỹ năng; đổi cách viết không làm đổi mã chuẩn.
"""

import unicodedata

# ==============================================================================
# 1. TAXONOMY NORMALIZER (Faithfully mirrors SkillNameNormalizer.java)
# ==============================================================================
def normalize(value: str) -> str:
    if value is None:
        raise ValueError("Skill name is required")
    # Form NFKC: chuẩn hóa ký tự Unicode tương thích
    nfkc = unicodedata.normalize("NFKC", value)
    # Gộp nhiều khoảng trắng liên tiếp và trim hai đầu
    collapsed = " ".join(nfkc.strip().split())
    # Chữ thường độc lập ngôn ngữ
    return collapsed.lower()

# ==============================================================================
# 2. MOCK TAXONOMY REPOSITORY & PORT (Mirrors SkillTaxonomyService.java)
# ==============================================================================
class MockTaxonomyCatalog:
    def __init__(self):
        self.version = 1  # taxonomy_version in taxonomy_catalog_state
        # skills: normalized_name -> skillCode
        self.skills = {
            "java": {"code": "JAVA", "name": "Java", "status": "ACTIVE"},
            "sql": {"code": "SQL", "name": "SQL", "status": "ACTIVE"},
            "rest api": {"code": "REST_API", "name": "REST API", "status": "ACTIVE"},
            "python": {"code": "PYTHON", "name": "Python", "status": "ACTIVE"},
            "git": {"code": "GIT", "name": "Git", "status": "ACTIVE"},
        }
        # aliases: normalized_alias -> canonical_normalized_name
        self.aliases = {
            "java core": "java",
            "core java": "java",
            "structured query language": "sql",
            "restful api": "rest api",
            "restful apis": "rest api",
            "git scm": "git",
        }

    def bump_version(self):
        self.version += 1

    def add_alias(self, alias_name: str, skill_code: str):
        norm_alias = normalize(alias_name)
        # Check target skill
        target = None
        for norm_name, data in self.skills.items():
            if data["code"] == skill_code:
                target = norm_name
                break
        if not target:
            raise ValueError(f"Skill {skill_code} not found")
        self.aliases[norm_alias] = target
        self.bump_version()

    def get_taxonomy_version(self) -> str:
        return f"skills-v{self.version}"

    # Port implementation: SkillTaxonomyPort.resolve(actor, values)
    def resolve(self, values: list) -> dict:
        if not values or len(values) > 100:
            raise ValueError("Provide between 1 and 100 skill names")
        
        items = []
        for val in values:
            if val is None or len(val) > 128:
                raise ValueError("Invalid skill name length")
            key = normalize(val)
            if not key:
                raise ValueError("Skill name is required")
            
            # 1. Look up canonical name
            skill = self.skills.get(key)
            # 2. Look up alias
            if not skill and key in self.aliases:
                canonical_key = self.aliases[key]
                skill = self.skills.get(canonical_key)
            
            if skill and skill["status"] == "ACTIVE":
                items.append({
                    "input": val,
                    "normalizedInput": key,
                    "status": "RESOLVED",
                    "skillCode": skill["code"],
                    "skillName": skill["name"]
                })
            else:
                items.append({
                    "input": val,
                    "normalizedInput": key,
                    "status": "UNKNOWN",
                    "skillCode": None,
                    "skillName": None
                })
                
        return {
            "taxonomyVersion": self.get_taxonomy_version(),
            "items": items
        }

# ==============================================================================
# 3. TEST CASES CHO TUẦN 1
# ==============================================================================
def run_week1_tests():
    print("=" * 75)
    print("UT.BRIDORA - TEST SUITE TUẦN 1: TAXONOMY, ALIAS, VERSION & PORT")
    print("Mục tiêu: T2 và T5 đọc cùng 1 mã kỹ năng, đổi cách viết không đổi mã chuẩn")
    print("=" * 75)
    
    catalog = MockTaxonomyCatalog()
    passed = 0
    total = 6

    # --------------------------------------------------------------------------
    # TC_WEEK1_01: Mã kỹ năng chuẩn duy nhất (Canonical Skill Code)
    # --------------------------------------------------------------------------
    print("\n[TC_WEEK1_01] Kiểm tra phân giải mã kỹ năng chuẩn (Canonical Code):")
    res = catalog.resolve(["Java", "SQL", "REST API"])
    for item in res["items"]:
        print(f"  Input: '{item['input']}' -> Skill Code: '{item['skillCode']}' ({item['status']})")
    assert res["items"][0]["skillCode"] == "JAVA"
    assert res["items"][1]["skillCode"] == "SQL"
    assert res["items"][2]["skillCode"] == "REST_API"
    print("  => PASS: Cả 3 kỹ năng đều phân giải về đúng mã chuẩn duy nhất.")
    passed += 1

    # --------------------------------------------------------------------------
    # TC_WEEK1_02: Đổi cách viết tên không làm đổi mã chuẩn (Case / Space / Unicode)
    # --------------------------------------------------------------------------
    print("\n[TC_WEEK1_02] Đổi cách viết hoa/thường, khoảng trắng, Unicode:")
    variants = ["  java  ", "JAVA", "JaVa", "Ｊａｖａ"]  # Bao gồm Unicode full-width
    res_var = catalog.resolve(variants)
    for item in res_var["items"]:
        print(f"  Input: '{item['input']}' -> normalized: '{item['normalizedInput']}' -> Code: '{item['skillCode']}'")
        assert item["skillCode"] == "JAVA"
        assert item["status"] == "RESOLVED"
    print("  => PASS: Bất kể cách viết, đều chuẩn hóa về 'java' và trả về mã chuẩn 'JAVA'.")
    passed += 1

    # --------------------------------------------------------------------------
    # TC_WEEK1_03: Phân giải qua Tên gọi khác (Skill Aliases Resolution)
    # --------------------------------------------------------------------------
    print("\n[TC_WEEK1_03] Phân giải tên gọi khác (Aliases):")
    alias_inputs = ["Java Core", "core java", "Structured Query Language", "RESTful API", "git scm"]
    res_alias = catalog.resolve(alias_inputs)
    expected_codes = ["JAVA", "JAVA", "SQL", "REST_API", "GIT"]
    for i, item in enumerate(res_alias["items"]):
        print(f"  Alias '{item['input']}' -> Skill Code: '{item['skillCode']}'")
        assert item["skillCode"] == expected_codes[i]
        assert item["status"] == "RESOLVED"
    print("  => PASS: Tất cả các alias đã được map chính xác về mã kỹ năng gốc tương ứng.")
    passed += 1

    # --------------------------------------------------------------------------
    # TC_WEEK1_04: Port Taxonomy dùng chung cho T2 (Student) và T5 (Enterprise)
    # --------------------------------------------------------------------------
    print("\n[TC_WEEK1_04] Kiểm tra Port Taxonomy T2/T5 dùng chung:")
    # T2: Sinh viên nhập tên kỹ năng tự do trong hồ sơ cá nhân
    t2_student_input = ["java core", "RESTful API", "sql"]
    t2_resolved = catalog.resolve(t2_student_input)
    t2_codes = [item["skillCode"] for item in t2_resolved["items"]]
    
    # T5: Doanh nghiệp đặt tiêu chí tuyển dụng
    t5_enterprise_input = ["JAVA", "REST API", "Structured Query Language"]
    t5_resolved = catalog.resolve(t5_enterprise_input)
    t5_codes = [item["skillCode"] for item in t5_resolved["items"]]
    
    print(f"  T2 (Sinh viên) nhập:     {t2_student_input} -> Mã chuẩn: {t2_codes}")
    print(f"  T5 (Doanh nghiệp) nhập: {t5_enterprise_input} -> Mã chuẩn: {t5_codes}")
    assert set(t2_codes) == set(t5_codes) == {"JAVA", "REST_API", "SQL"}
    print("  => PASS: T2 và T5 đọc ra tập mã kỹ năng chuẩn HOÀN TOÀN TRÙNG KHỚP! Phục vụ matching chính xác.")
    passed += 1

    # --------------------------------------------------------------------------
    # TC_WEEK1_05: Quản lý phiên bản danh mục (Taxonomy Versioning)
    # --------------------------------------------------------------------------
    print("\n[TC_WEEK1_05] Kiểm tra quản lý phiên bản (Taxonomy Versioning):")
    init_ver = catalog.get_taxonomy_version()
    print(f"  Phiên bản ban đầu: {init_ver}")
    assert init_ver == "skills-v1"
    
    # Thêm 1 alias mới vào danh mục
    catalog.add_alias("Spring Framework", "JAVA")
    new_ver = catalog.get_taxonomy_version()
    print(f"  Sau khi thêm alias 'Spring Framework' -> {new_ver}")
    assert new_ver == "skills-v2"
    
    # Verify alias mới đã hoạt động và mang đúng version mới
    check_new = catalog.resolve(["Spring Framework"])
    assert check_new["taxonomyVersion"] == "skills-v2"
    assert check_new["items"][0]["skillCode"] == "JAVA"
    print(f"  Resolve với alias mới: '{check_new['items'][0]['input']}' -> '{check_new['items'][0]['skillCode']}' tại version '{check_new['taxonomyVersion']}'")
    print("  => PASS: Phiên bản taxonomy tự động tăng (bump version), dữ liệu đồng bộ chính xác.")
    passed += 1

    # --------------------------------------------------------------------------
    # TC_WEEK1_06: Xử lý kỹ năng không xác định (UNKNOWN Handling)
    # --------------------------------------------------------------------------
    print("\n[TC_WEEK1_06] Kỹ năng không có trong danh mục (UNKNOWN):")
    res_unknown = catalog.resolve(["Kỹ năng ảo không tồn tại 123"])
    print(f"  Input lạ: status='{res_unknown['items'][0]['status']}', code={res_unknown['items'][0]['skillCode']}")
    assert res_unknown["items"][0]["status"] == "UNKNOWN"
    assert res_unknown["items"][0]["skillCode"] is None
    print("  => PASS: Trả về UNKNOWN an toàn, không tự ý sửa đổi danh mục chuẩn.")
    passed += 1

    # --------------------------------------------------------------------------
    # TỔNG KẾT
    # --------------------------------------------------------------------------
    print("\n" + "=" * 75)
    print(f"KẾT QUẢ KIỂM THỬ TUẦN 1: {passed}/{total} TEST CASES PASSED (100%)")
    print("=" * 75)

if __name__ == "__main__":
    run_week1_tests()

