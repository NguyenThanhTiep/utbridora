CREATE TABLE internship_mentor_handovers (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 case_id VARCHAR(36) NOT NULL,
 task_group_id VARCHAR(36) NOT NULL,
 mentor_assignment_id VARCHAR(36) NOT NULL,
 hr_assignment_id VARCHAR(36) NOT NULL,
 granted_by VARCHAR(36) NOT NULL,
 reason_code VARCHAR(64) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 UNIQUE(case_id,task_group_id,mentor_assignment_id),
 FOREIGN KEY(case_id) REFERENCES internship_cases(id),
 FOREIGN KEY(task_group_id) REFERENCES internship_groups(id),
 FOREIGN KEY(mentor_assignment_id) REFERENCES internship_context_assignments(id),
 FOREIGN KEY(hr_assignment_id) REFERENCES internship_context_assignments(id),
 FOREIGN KEY(granted_by) REFERENCES users(id)
) ENGINE=InnoDB;
