package vn.utbridora.auth.modules.internship.api;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.utbridora.auth.modules.internship.infrastructure.*;
import static vn.utbridora.auth.operations.WorkflowValues.body;
@RestController @RequestMapping("/api/v1/t3")
public class WorkspaceTaskController {
    private final WorkspaceTaskActions tasks;
    public WorkspaceTaskController(WorkspaceTaskActions tasks) {this.tasks=tasks;}
    @PatchMapping("/assignments/{id}/status")
    public TaskDtos.AssignmentDto start(Authentication a,@PathVariable String id,@RequestHeader("Idempotency-Key") String key,@RequestBody String data) {return tasks.start(a,id,key,body(data,WorkspaceTaskActions.StartAssignment.class));}
    @GetMapping("/calendar")
    public WorkspaceRuntime.Page<WorkspaceTaskActions.CalendarEvent> calendar(Authentication a,@RequestParam Instant from,@RequestParam Instant until,@RequestParam(required=false) String caseId,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return tasks.calendar(a,from,until,caseId,page,size);}
}
