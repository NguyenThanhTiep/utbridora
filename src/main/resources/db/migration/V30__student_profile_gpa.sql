-- liên quan đến việc thêm lọc svien theo gpa là MVP: Thêm trường GPA có thể null vào hồ sơ sinh viên.
ALTER TABLE student_profiles
  ADD COLUMN gpa DECIMAL(3,2) NULL AFTER profile_version;
