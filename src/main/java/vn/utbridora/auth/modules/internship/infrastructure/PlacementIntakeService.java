package vn.utbridora.auth.modules.internship.infrastructure;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.contracts.placement.PlacementReadPort;
import vn.utbridora.auth.files.RosterImportQuarantine.ScannedRosterFile;
import vn.utbridora.auth.files.TabularFileReader;
import vn.utbridora.auth.modules.internship.domain.Cohort;
import vn.utbridora.auth.operations.OperationLedger;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** CP-WS1 staging only. Every confirmed Placement is re-read by T5 and the existing intake writer. */
@Service
@Transactional
public class PlacementIntakeService {
    public record PlacementPreviewRow(int rowNo,UUID placementId,String status,String errorCode) {}
    public record PlacementPreview(UUID previewId,long cohortVersion,String fileHash,Instant expiresAt,List<PlacementPreviewRow> rows) {}
    private final JdbcTemplate jdbc;private final JdbcWorkspaceSession session;private final WorkspaceAuthority authority;
    private final PlacementReadPort source;private final OperationLedger ledger;
    public PlacementIntakeService(JdbcTemplate jdbc,JdbcWorkspaceSession session,WorkspaceAuthority authority,PlacementReadPort source,OperationLedger ledger) {
        this.jdbc=jdbc;this.session=session;this.authority=authority;this.source=source;this.ledger=ledger;
    }
    public Cohort authorize(Authentication actor,UUID id,long expected) {
        version(expected);var cohort=session.cohort(id);if(cohort==null) throw failure(404,"RESOURCE_NOT_FOUND");
        authority.requireHr(actor,cohort.scope(),null);
        if(cohort.version()!=expected) throw failure(409,"VERSION_CONFLICT");return cohort;
    }
    public PlacementPreview preview(Authentication actor,UUID id,String key,long expected,ScannedRosterFile file) {
        var cohort=authorize(actor,id,expected);
        return ledger.execute(new OperationLedger.Command(actor(actor),cohort.scope().enterpriseOrgId().toString(),"T3_PLACEMENT_PREVIEW",key,
            Map.of("cohortId",id,"expectedCohortVersion",expected,"fileHash",file.sha256()),200),PlacementPreview.class,()->{
                var table=file.table();
                if(table.rows().size()>1000) throw failure(422,"PLACEMENT_ROW_LIMIT_EXCEEDED");
                if(table.headers().isEmpty() || table.headers().stream().anyMatch(h->!Set.of("placementId","studentUserId").contains(h))) throw failure(422,"FILE_STRUCTURE_INVALID");
                List<PlacementPreviewRow> rows=new ArrayList<>();Set<UUID> seen=new HashSet<>();
                for(var row:table.rows()) {
                    UUID placement=null;String error=null;
                    try {
                        var values=new HashMap<String,String>();
                        for(int i=0;i<row.cells().size();i++) {
                            var cell=row.cells().get(i);
                            if(i>=table.headers().size()) {if(!cell.blank()) throw failure(422,"INVALID_IDENTIFIER");continue;}
                            if(!cell.blank() && cell.kind()!=TabularFileReader.Kind.TEXT) throw failure(422,"INVALID_IDENTIFIER");
                            values.put(table.headers().get(i),cell.blank()?"":cell.value().trim());
                        }
                        String direct=values.getOrDefault("placementId",""),student=values.getOrDefault("studentUserId","");
                        if(direct.isEmpty()==student.isEmpty()) throw failure(422,"INVALID_IDENTIFIER");
                        PlacementReadPort.PlacementView p;
                        if(!direct.isEmpty()) {placement=UUID.fromString(uuid(direct));p=source.readCurrentForGuard(placement.toString()).orElse(null);}
                        else {
                            var found=source.findCurrentForStudent(uuid(student),cohort.scope().schoolOrgId().toString(),cohort.scope().enterpriseOrgId().toString(),cohort.scope().termId().toString());
                            if(found.size()>1) throw failure(422,"AMBIGUOUS_STUDENT");
                            p=found.isEmpty()?null:found.getFirst();placement=p==null?null:UUID.fromString(p.placementId());
                        }
                        if(p==null || !eligible(cohort,p) || !seen.add(placement)) {placement=null;error="PLACEMENT_UNAVAILABLE";}
                    } catch(vn.utbridora.auth.operations.OperationFailure invalid) {
                        if(invalid.status>=500) throw invalid;
                        error=Set.of("AMBIGUOUS_STUDENT","PLACEMENT_UNAVAILABLE").contains(invalid.code)?invalid.code:"INVALID_IDENTIFIER";placement=null;
                    }
                    rows.add(new PlacementPreviewRow(row.rowNo(),placement,error==null?"VALID":"INVALID",error));
                }
                UUID preview=UUID.randomUUID();Instant expires=Instant.now().plusSeconds(900);
                var result=new PlacementPreview(preview,cohort.version(),file.sha256(),expires,List.copyOf(rows));
                jdbc.update("INSERT INTO internship_placement_previews(id,cohort_id,actor_id,cohort_version,file_hash,rows_json,expires_at) VALUES(?,?,?,?,?,?,?)",preview.toString(),id.toString(),actor(actor),cohort.version(),file.sha256(),JSON.writeValueAsString(result),utc(expires));
                return result;
            },r->r.previewId().toString());
    }
    public void requireSelection(Authentication actor,Cohort cohort,UUID preview,String hash,List<UUID> ids) {
        if(preview==null && hash==null) return;
        if(preview==null || hash==null || !hash.matches("[0-9a-f]{64}")) throw failure(409,"PLACEMENT_PREVIEW_STALE");
        var rows=jdbc.queryForList("SELECT rows_json,expires_at FROM internship_placement_previews WHERE id=? AND cohort_id=? AND actor_id=? FOR UPDATE",preview.toString(),cohort.id().toString(),actor(actor));
        if(rows.size()!=1) throw failure(409,"PLACEMENT_PREVIEW_STALE");
        var value=JSON.readValue(rows.getFirst().get("rows_json").toString(),PlacementPreview.class);
        Object expiry=rows.getFirst().get("expires_at");Instant expires=expiry instanceof java.time.LocalDateTime local?local.toInstant(ZoneOffset.UTC):((java.sql.Timestamp)expiry).toInstant();
        if(!value.fileHash().equals(hash) || value.cohortVersion()!=cohort.version() || !value.expiresAt().isAfter(Instant.now()) || !expires.isAfter(Instant.now())) throw failure(409,"PLACEMENT_PREVIEW_STALE");
        var valid=value.rows().stream().filter(r->r.status().equals("VALID")).map(PlacementPreviewRow::placementId).collect(java.util.stream.Collectors.toSet());
        if(!valid.containsAll(ids)) throw failure(409,"PLACEMENT_PREVIEW_STALE");
    }
    private boolean eligible(Cohort c,PlacementReadPort.PlacementView p) {
        if(!c.scope().schoolOrgId().toString().equals(p.schoolOrgId()) || !c.scope().enterpriseOrgId().toString().equals(p.enterpriseOrgId()) || !c.scope().termId().toString().equals(p.termId()) || !Set.of("APPROVED","ACTIVE").contains(p.state()) || p.approvedAt()==null) return false;
        return jdbc.queryForObject("SELECT COUNT(*) FROM internship_cases i WHERE i.placement_id=? AND i.state IN ('WAITING_START','IN_PROGRESS') AND NOT EXISTS(SELECT 1 FROM internship_cohort_cases b WHERE b.case_id=i.id AND b.cohort_id<>?)",Integer.class,p.placementId(),c.id().toString())==1;
    }
}
