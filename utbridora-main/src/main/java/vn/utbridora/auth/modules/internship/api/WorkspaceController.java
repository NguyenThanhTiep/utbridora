package vn.utbridora.auth.modules.internship.api;

import java.time.Instant;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;
import vn.utbridora.auth.modules.internship.infrastructure.WorkspaceRuntime;
import vn.utbridora.auth.modules.internship.infrastructure.WorkspaceRuntime.*;
import vn.utbridora.auth.modules.internship.infrastructure.WorkspaceAuthority;
import vn.utbridora.auth.modules.internship.infrastructure.CaseRuntime;
import vn.utbridora.auth.modules.internship.api.CohortGroupDtos.*;
import vn.utbridora.auth.modules.internship.api.TaskDtos.*;

@RestController
@RequestMapping("/api/v1/t3")
public class WorkspaceController {
    private final WorkspaceRuntime runtime;
    private final JsonMapper json=JsonMapper.builder().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT).disable(tools.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS).build();
    public WorkspaceController(WorkspaceRuntime runtime) {this.runtime=runtime;}
    @GetMapping("/context-assignments/me") public List<WorkspaceAuthority.Assignment> assignments(Authentication actor) {return runtime.assignments(actor);}
    @GetMapping("/cohorts") public Page<CohortDto> cohorts(Authentication actor,@RequestParam(required=false) UUID termId,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return runtime.cohorts(actor,termId,page,size);}
    @PostMapping("/cohorts") @ResponseStatus(HttpStatus.CREATED)
    public CohortDto create(Authentication actor,@RequestHeader("Idempotency-Key") String key,@RequestBody String body) {return runtime.createCohort(actor,key,read(body,CreateCohort.class));}
    @PostMapping("/cohorts/{id}/members:batch") public BatchResult intake(Authentication actor,@PathVariable UUID id,
        @RequestHeader("Idempotency-Key") String key,@RequestBody String body) {return runtime.intake(actor,id,key,read(body,BatchCases.class));}
    public record AutoGroup(String mode,Integer groupSize,UUID planId,Long expectedCohortVersion) {}
    @PostMapping("/cohorts/{id}/groups:auto") public Object grouping(Authentication actor,@PathVariable UUID id,
        @RequestHeader("Idempotency-Key") String key,@RequestBody String body) {
        AutoGroup request=read(body,AutoGroup.class);
        if(request.expectedCohortVersion()==null || request.expectedCohortVersion()<1 || request.expectedCohortVersion()>9_007_199_254_740_991L) throw new IllegalArgumentException("Invalid version");
        if("PREVIEW".equals(request.mode()) && request.groupSize()!=null && request.planId()==null)
            return runtime.preview(actor,id,key,request.groupSize(),request.expectedCohortVersion());
        if("CONFIRM".equals(request.mode()) && request.planId()!=null && request.groupSize()==null)
            return runtime.confirm(actor,id,key,request.planId(),request.expectedCohortVersion());
        throw new IllegalArgumentException("Invalid grouping mode");
    }
    @GetMapping("/cohorts/{id}/groups") public Page<GroupDto> groups(Authentication actor,@PathVariable UUID id,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return runtime.groups(actor,id,false,page,size);}
    @GetMapping("/mentors/me/groups") public Page<GroupDto> mentorGroups(Authentication actor,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {return runtime.groups(actor,null,true,page,size);}
    @PostMapping("/groups/{id}/members") @ResponseStatus(HttpStatus.CREATED)
    public MembershipResult add(Authentication actor,@PathVariable UUID id,@RequestHeader("Idempotency-Key") String key,@RequestBody String body) {return runtime.add(actor,id,key,read(body,AssignGroupMember.class));}
    @PostMapping("/groups/{id}/members:move") public MembershipResult move(Authentication actor,@PathVariable UUID id,
        @RequestHeader("Idempotency-Key") String key,@RequestBody String body) {return runtime.move(actor,id,key,read(body,MoveCase.class));}
    @GetMapping("/cases/{id}") public CaseRuntime.CaseRef caseRef(Authentication actor,@PathVariable UUID id) {return runtime.caseRef(actor,id);}
    @PostMapping("/tasks") @ResponseStatus(HttpStatus.CREATED)
    public TaskDto task(Authentication actor,@RequestHeader("Idempotency-Key") String key,@RequestBody String body) {return runtime.createTask(actor,key,read(body,CreateTask.class));}
    @GetMapping("/tasks/{id}") public TaskDto task(Authentication actor,@PathVariable UUID id) {return runtime.readTask(actor,id);}
    @GetMapping("/tasks") public Page<TaskDto> tasks(Authentication actor,@RequestParam(required=false) UUID groupId,
        @RequestParam(required=false) UUID caseId,@RequestParam(required=false) String state,@RequestParam(required=false) Instant dueFrom,
        @RequestParam(required=false) Instant dueUntil,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size) {
        return runtime.tasks(actor,groupId,caseId,state,dueFrom,dueUntil,page,size);
    }
    private <T> T read(String body,Class<T> type) {
        if(body==null || body.length()>2_000_000) throw new IllegalArgumentException("Invalid request");
        try {
            var root=json.readTree(body);
            if(root==null || !root.isObject()) throw new IllegalArgumentException("Invalid request");
            for(var field:type.getRecordComponents()) {
                String name=field.getName();var value=root.get(name);
                boolean optional=name.equals("lateAllowed") || type==AutoGroup.class && (name.equals("planId") || name.equals("groupSize"));
                boolean nullable=type==BatchCases.class && (name.equals("previewId") || name.equals("fileHash"));
                if(value==null && !optional || value!=null && value.isNull() && !nullable) throw new IllegalArgumentException("Missing required field");
                if(value!=null && !value.isNull() && field.getType()==UUID.class && (!value.isString() || !uuid(value.asString()))) throw new IllegalArgumentException("Invalid UUID");
                if(value!=null && (name.equals("caseIds") || name.equals("placementIds"))) {
                    if(!value.isArray()) throw new IllegalArgumentException("Invalid IDs");
                    for(var id:value) if(!id.isString() || !uuid(id.asString())) throw new IllegalArgumentException("Invalid UUID");
                }
            }
            return json.treeToValue(root,type);
        } catch(tools.jackson.core.JacksonException failure) {throw new IllegalArgumentException("Invalid request");}
    }
    private static boolean uuid(String value) {return value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");}
}
