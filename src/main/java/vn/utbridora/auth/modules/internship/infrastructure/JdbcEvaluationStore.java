package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.dao.DuplicateKeyException;
import vn.utbridora.auth.modules.internship.api.EvaluationDtos.*;
import vn.utbridora.auth.modules.internship.domain.EvaluationPolicy;
import vn.utbridora.auth.modules.internship.infrastructure.WorkspaceRuntime.Page;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Writes immutable snapshots and CAS pointers inside the caller's transaction. No template writer. */
@Repository
public class JdbcEvaluationStore {
    public record Root(UUID id, UUID caseId, Long currentVersion, Long publishedVersion) {}
    private final JdbcTemplate jdbc;
    public JdbcEvaluationStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Root find(UUID id, boolean lock) {
        return jdbc.query("SELECT * FROM internship_evaluations WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                (r,n) -> new Root(UUID.fromString(r.getString("id")), UUID.fromString(r.getString("case_id")),
                        r.getObject("current_version", Long.class), r.getObject("published_version", Long.class)),
                id.toString()).stream().findFirst().orElse(null);
    }
    public UUID idForCase(UUID caseId) {
        return jdbc.queryForList("SELECT id FROM internship_evaluations WHERE case_id=?", String.class, caseId.toString())
                .stream().map(UUID::fromString).findFirst().orElse(null);
    }
    public EvaluationDto current(Root root) {
        if (root == null || root.currentVersion() == null) throw failure(503, "DEPENDENCY_UNAVAILABLE");
        return version(root, root.currentVersion());
    }
    public EvaluationDto version(Root root, long version) {
        var result = jdbc.queryForList("SELECT snapshot_json FROM internship_evaluation_versions WHERE evaluation_id=? AND version=?",
                String.class, root.id().toString(), version);
        if (result.isEmpty()) throw failure(404, "RESOURCE_NOT_FOUND");
        var value=decode(root, result.getFirst());
        if (value.version()!=version) throw failure(503,"DEPENDENCY_UNAVAILABLE");
        return value;
    }
    private EvaluationDto decode(Root root, String json) {
        var dto = JSON.readValue(json, EvaluationDto.class);
        if (!root.id().equals(dto.evaluationId()) || !root.caseId().equals(dto.caseId())
                || dto.total100().compareTo(EvaluationPolicy.total(dto.template(), dto.scores())) != 0)
            throw failure(503, "DEPENDENCY_UNAVAILABLE");
        return dto;
    }
    public Page<EvaluationDto> history(Root root, int page, int size) {
        page(page,size);
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM internship_evaluation_versions WHERE evaluation_id=?", Long.class, root.id().toString());
        var rows = jdbc.queryForList("SELECT snapshot_json FROM internship_evaluation_versions WHERE evaluation_id=? ORDER BY version DESC LIMIT ? OFFSET ?",
                String.class, root.id().toString(), size, (long)page*size).stream().map(s -> decode(root,s)).toList();
        return new Page<>(rows,page,size,count,(int)Math.ceilDiv(count,(long)size));
    }
    public RubricTemplate template(UUID school, UUID term, UUID id, long version) {
        var rows = jdbc.queryForList("SELECT template_json FROM internship_rubric_templates WHERE school_org_id=? AND term_id=? AND id=? AND template_version=? FOR SHARE",
                String.class, school.toString(),term.toString(),id.toString(),version);
        if (rows.isEmpty()) {
            if (jdbc.queryForObject("SELECT COUNT(*) FROM internship_rubric_templates WHERE school_org_id=? AND term_id=? AND id=?",
                    Long.class, school.toString(),term.toString(),id.toString()) > 0) throw failure(409,"VERSION_CONFLICT");
            throw failure(404,"RESOURCE_NOT_FOUND");
        }
        var template = JSON.readValue(rows.getFirst(), RubricTemplate.class);
        if (!template.templateId().equals(id) || template.templateVersion() != version) throw failure(503,"DEPENDENCY_UNAVAILABLE");
        return template;
    }
    public Page<RubricTemplate> templates(UUID school, UUID term, int page, int size) {
        page(page,size);
        String where = " FROM internship_rubric_templates t WHERE school_org_id=? AND term_id=? AND template_version=(SELECT MAX(x.template_version) FROM internship_rubric_templates x WHERE x.id=t.id AND x.school_org_id=t.school_org_id AND x.term_id=t.term_id)";
        long count = jdbc.queryForObject("SELECT COUNT(*)"+where,Long.class,school.toString(),term.toString());
        var rows = jdbc.queryForList("SELECT template_json"+where+" ORDER BY id ASC LIMIT ? OFFSET ?",String.class,
                school.toString(),term.toString(),size,(long)page*size).stream().map(s -> JSON.readValue(s,RubricTemplate.class)).toList();
        return new Page<>(rows,page,size,count,(int)Math.ceilDiv(count,(long)size));
    }
    public void create(EvaluationDto value, UUID actor, Instant now) {
        try { jdbc.update("INSERT INTO internship_evaluations(id,case_id) VALUES (?,?)",value.evaluationId().toString(),value.caseId().toString()); }
        catch (DuplicateKeyException duplicate) { throw failure(409,"EVALUATION_ALREADY_EXISTS"); }
        insert(value,actor,now);
        if (jdbc.update("UPDATE internship_evaluations SET current_version=? WHERE id=? AND current_version IS NULL",value.version(),value.evaluationId().toString()) != 1)
            throw failure(409,"VERSION_CONFLICT");
    }
    public void append(EvaluationDto value, long expected, UUID actor, Instant now) {
        if (value.version() != expected+1) throw failure(409,"VERSION_CONFLICT");
        insert(value,actor,now);
        if (jdbc.update("UPDATE internship_evaluations SET current_version=? WHERE id=? AND current_version=?",
                value.version(),value.evaluationId().toString(),expected) != 1) throw failure(409,"VERSION_CONFLICT");
    }
    private void insert(EvaluationDto v, UUID actor, Instant now) {
        jdbc.update("INSERT INTO internship_evaluation_versions(evaluation_id,version,revision_no,state,template_id,template_version,snapshot_json,total100,created_by,created_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                v.evaluationId().toString(),v.version(),v.revisionNo(),v.state().name(),v.template().templateId().toString(),
                v.template().templateVersion(),JSON.writeValueAsString(v),v.total100(),actor.toString(),utc(now));
        v.scores().stream().flatMap(s -> s.evidenceFileIds().stream()).distinct().sorted().forEach(id ->
            jdbc.update("INSERT INTO internship_evaluation_files(evaluation_id,evaluation_version,file_id) VALUES (?,?,?)",v.evaluationId().toString(),v.version(),id.toString()));
    }
    public void publish(EvaluationDto value,long expected,UUID actor,Instant now) {
        if(value.state()!=EvaluationState.PUBLISHED) throw failure(409,"STATE_CONFLICT");
        append(value,expected,actor,now);
        if(jdbc.update("UPDATE internship_evaluations SET published_version=? WHERE id=? AND current_version=?",
                value.version(),value.evaluationId().toString(),value.version())!=1) throw failure(409,"VERSION_CONFLICT");
    }
    public boolean attached(UUID evaluation, String fileId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM internship_evaluation_files WHERE evaluation_id=? AND file_id=?",Long.class,evaluation.toString(),fileId)>0;
    }
}
