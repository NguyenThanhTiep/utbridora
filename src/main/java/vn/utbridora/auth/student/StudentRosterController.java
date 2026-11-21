package vn.utbridora.auth.student;

import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.student.dto.StudentRosterDto;

@RestController
@RequestMapping("/api/v1/school/student-rosters")
public class StudentRosterController {
    private final StudentRosterQueryService rosters;

    public StudentRosterController(StudentRosterQueryService rosters) {
        this.rosters = rosters;
    }

    @ModelAttribute
    void privateResponse(HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("X-Content-Type-Options", "nosniff");
    }

    @GetMapping
    public List<StudentRosterDto> list(
        @RequestParam String schoolOrgId,
        @RequestParam(required = false) String termCode
    ) {
        List<StudentRosterDto> items = rosters.findBySchool(schoolOrgId);
        if (termCode == null || termCode.isBlank()) {
            return items;
        }
        String needle = termCode.trim();
        return items.stream().filter(item -> needle.equals(item.termCode())).toList();
    }
}
