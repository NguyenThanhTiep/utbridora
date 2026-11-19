package vn.utbridora.auth.onboarding;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.*;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.files.*;
import vn.utbridora.auth.modules.student.application.AcademicCatalogService;
import vn.utbridora.auth.modules.student.port.StudentRosterProvisionPort;
import vn.utbridora.auth.operations.*;
import vn.utbridora.auth.student.port.TermReferencePort;
import vn.utbridora.auth.tokens.InvitationClaims;
import static vn.utbridora.auth.operations.WorkflowValues.*;
import static vn.utbridora.auth.onboarding.StudentImportContracts.*;

/** T1 staging/API owner. Preview never invokes ensureRoster or creates a claim/identity. */
@Service
@Transactional
public class StudentImportService {
    static final String PERMISSION="schoolRosterImport:manage";
    static final Set<String> TERMINAL=Set.of("PROVISIONED","ALREADY_LINKED","NEEDS_REVIEW","PERMANENT_FAILED");
    final JdbcTemplate jdbc;final SchoolDelegationAccess access;final AuthorizationPolicy permissions;
    final TermReferencePort terms;final AcademicCatalogService catalogs;final StudentRosterProvisionPort rosters;
    final InvitationClaims claims;final OperationLedger ledger;final AuditService audit;final Clock clock;final FileStorage storage;
    record Job(String id,String school,String term,String actor,String initiatedBy,Long security,String assignment,Long authority,
        String state,long version,String hash,String catalog,long termVersion,Instant expires,int total,int selected,
        String owner,long epoch,Instant lease,Instant started,Instant finished) {}
    public StudentImportService(JdbcTemplate jdbc,SchoolDelegationAccess access,AuthorizationPolicy permissions,
        TermReferencePort terms,AcademicCatalogService catalogs,StudentRosterProvisionPort rosters,InvitationClaims claims,
        OperationLedger ledger,AuditService audit,@Qualifier("onboardingClock") Clock clock,FileStorage storage) {
        this.jdbc=jdbc;this.access=access;this.permissions=permissions;this.terms=terms;this.catalogs=catalogs;this.rosters=rosters;
        this.claims=claims;this.ledger=ledger;this.audit=audit;this.clock=clock;this.storage=storage;
    }
    public void authorizePreview(Authentication auth,String termId) {scope(auth,termId);}
    private TermReferencePort.TermReference scope(Authentication auth,String termId) {
        actor(auth);var term=terms.findById(uuid(termId)).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        access.require(auth,term.schoolOrgId(),term.termId(),PERMISSION);
        if(term.status()!=TermReferencePort.Status.ACTIVE) throw failure(422,"REFERENCE_DATA_CHANGED");return term;
    }
    Job job(String id,boolean locked) {
        return jdbc.query("SELECT * FROM student_import_jobs WHERE id=?"+(locked?" FOR UPDATE":""),(r,n)->new Job(r.getString("id"),r.getString("school_org_id"),r.getString("term_id"),r.getString("actor_id"),r.getString("initiated_by"),(Long)r.getObject("initiated_security_version"),r.getString("authority_assignment_id"),(Long)r.getObject("authority_version"),r.getString("state"),r.getLong("version"),r.getString("file_hash"),r.getString("catalog_version"),r.getLong("term_version"),instant(r,"preview_expires_at"),r.getInt("total_rows"),r.getInt("selected_rows"),r.getString("lease_owner"),r.getLong("lease_epoch"),instant(r,"lease_until"),instant(r,"started_at"),instant(r,"finished_at")),uuid(id)).stream().findFirst().orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
    }
    Job authorized(Authentication auth,String id) {
        actor(auth);var candidate=job(id,false);
        try {access.require(auth,candidate.school(),candidate.term(),PERMISSION);}
        catch(OperationFailure denied) {if(denied.status==403) throw failure(404,"RESOURCE_NOT_FOUND");throw denied;}
        return job(id,true);
    }
    StudentRosterProvisionPort.EnsureRoster ensure(Job job,ImportSourceData data,String operation) {
        return new StudentRosterProvisionPort.EnsureRoster(job.school(),data.studentCode(),job.term(),data.majorCode(),data.email(),data.fullName(),data.cohortCode(),data.classCode(),job.catalog(),operation);
    }
    public ImportPreviewDto preview(Authentication auth,String termId,String key,RosterImportQuarantine.ScannedRosterFile file) {
        var term=scope(auth,termId);uuid(key);var context=ActorContext.fromAuthenticatedWorkflow(auth,term.schoolOrgId());
        return ledger.execute(new OperationLedger.Command(actor(auth),term.schoolOrgId(),"STUDENT_IMPORT_PREVIEW",key,Map.of("termId",term.termId(),"fileHash",file.sha256()),201),ImportPreviewDto.class,()->{
            var catalog=catalogs.forProfile(term.schoolOrgId(),term.termId());
            var majors=new HashSet<String>();catalog.entries().get(AcademicCatalogService.Kind.MAJOR).stream().filter(e->e.status().equals("ACTIVE")).forEach(e->majors.add(e.code()));
            var validated=ImportRowValidator.validate(file.table(),term.schoolOrgId(),term.termCode(),majors);
            String id=UUID.randomUUID().toString(),fileId=UUID.randomUUID().toString();Instant now=clock.instant();
            var stub=new Job(id,term.schoolOrgId(),term.termId(),actor(auth),null,null,null,null,"PREVIEW_READY",1,file.sha256(),catalog.version(),term.version(),now.plusSeconds(1800),validated.size(),0,null,0,null,null,null);
            var classified=new ArrayList<ImportRowValidator.Validated>();
            for(var row:validated) {
                if(!row.validationState().equals("VALID")) {classified.add(row);continue;}
                var result=rosters.inspectRoster(context,ensure(stub,row.sourceData(),UUID.randomUUID().toString()));
                var claim=claims.inspectEmail(row.sourceData().email()).orElse(null);
                boolean conflict=claim!=null && !claims.permitsRoster(claim,term.schoolOrgId(),result.rosterId()) && !result.status().equals("ALREADY_LINKED");
                classified.add(result.status().equals("NEEDS_REVIEW") || conflict
                    ?new ImportRowValidator.Validated(row.rowNo(),row.sourceData(),"NEEDS_REVIEW",List.of(conflict?"IDENTITY_CONFLICT":result.reasonCode())):row);
            }
            String object=file.promote(storage);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) {if(status==STATUS_ROLLED_BACK) storage.delete(object);}
            });
            jdbc.update("INSERT INTO files(id,owner_user_id,organization_id,storage_key,original_name,content_type,size_bytes,sha256,classification,scan_status,created_at,scan_attempt,scanner_reason_code) VALUES(?,?,?,?,?,?,?,?,'PRIVATE','CLEAN',?,?,'CLEAN')",fileId,actor(auth),term.schoolOrgId(),object,file.originalName(),file.contentType(),file.size(),file.sha256(),utc(now),file.scanAttempts());
            jdbc.update("INSERT INTO file_bindings(id,file_id,binding_kind,purpose,resource_id,created_at) VALUES(?,?,'IMPORT_JOB','ROSTER_IMPORT',?,?)",UUID.randomUUID().toString(),fileId,id,utc(now));
            jdbc.update("INSERT INTO student_import_jobs(id,school_org_id,term_id,actor_id,state,file_hash,staging_file_id,catalog_version,term_version,preview_expires_at,total_rows,created_at) VALUES(?,?,?,?,'PREVIEW_READY',?,?,?,?,?,?,?)",id,term.schoolOrgId(),term.termId(),actor(auth),file.sha256(),fileId,catalog.version(),term.version(),utc(stub.expires()),classified.size(),utc(now));
            var batch=new ArrayList<Object[]>();
            for(var row:classified) batch.add(new Object[]{UUID.randomUUID().toString(),id,row.rowNo(),OperationLedger.payloadHash(row.sourceData()),JSON.writeValueAsString(row.sourceData()),row.validationState(),JSON.writeValueAsString(row.errorCodes()),UUID.randomUUID().toString()});
            jdbc.batchUpdate("INSERT INTO student_import_rows(id,job_id,row_no,row_hash,normalized_snapshot,validation_state,error_codes,operation_id) VALUES(?,?,?,?,?,?,?,?)",batch);
            audit.workflow(actor(auth),"FILE_UPLOADED",fileId,term.schoolOrgId(),Map.of("purpose","ROSTER_IMPORT","contentType",file.contentType(),"sizeBytes",file.size(),"scanStatus","CLEAN"));
            audit.fileScanCompleted(fileId,term.schoolOrgId(),"CLEAN",file.scanAttempts(),"CLEAN");
            var dto=project(stub);
            audit.workflow(actor(auth),"STUDENT_IMPORT_PREVIEWED",id,term.schoolOrgId(),Map.of("totalRows",dto.totalRows(),"validRows",dto.validRows(),"invalidRows",dto.invalidRows(),"needsReviewRows",dto.previewNeedsReviewRows(),"previewVersion",1,"fileHash",file.sha256()));
            return new ImportPreviewDto(dto,rows(stub,0,25,null,null,null));
        },r->r.job().importJobId());
    }
    public ImportAccepted confirm(Authentication auth,String id,String key,ConfirmImport in) {
        var j=authorized(auth,id);uuid(key);version(in.expectedJobVersion());
        if(in.fileHash()==null || !in.fileHash().matches("[a-f0-9]{64}") || in.selectedRowIds()==null || in.selectedRowIds().size()>5000) throw failure(400,"SCHEMA_VALIDATION");
        in.selectedRowIds().forEach(WorkflowValues::uuid);
        return ledger.execute(new OperationLedger.Command(actor(auth),j.school(),"STUDENT_IMPORT_CONFIRM",key,Map.of("id",id,"request",in),202),ImportAccepted.class,()->{
            var current=job(id,true);
            if(current.state().equals("EXPIRED") || current.state().equals("PREVIEW_READY") && !clock.instant().isBefore(current.expires())) throw failure(409,"PREVIEW_EXPIRED");
            if(!current.state().equals("PREVIEW_READY") || current.version()!=in.expectedJobVersion()) throw failure(409,"STATE_CONFLICT");
            var term=terms.findById(j.term()).orElseThrow(()->failure(409,"PREVIEW_STALE"));
            var catalog=catalogs.forProfile(j.school(),j.term());
            if(!j.hash().equals(in.fileHash()) || term.version()!=j.termVersion() || term.status()!=TermReferencePort.Status.ACTIVE || !catalog.version().equals(j.catalog())) throw failure(409,"PREVIEW_STALE");
            var staged=jdbc.queryForList("SELECT id,row_hash,normalized_snapshot,validation_state,operation_id FROM student_import_rows WHERE job_id=? ORDER BY row_no,id FOR UPDATE",id);
            for(var row:staged) {
                var data=JSON.readValue(row.get("normalized_snapshot").toString(),ImportSourceData.class);
                if(!OperationLedger.payloadHash(data).equals(row.get("row_hash"))) throw failure(409,"PREVIEW_STALE");
                if(row.get("validation_state").equals("VALID")) {
                    var ref=rosters.inspectRoster(ActorContext.fromAuthenticatedWorkflow(auth,j.school()),ensure(j,data,row.get("operation_id").toString()));
                    var claim=claims.inspectEmail(data.email()).orElse(null);
                    if(ref.status().equals("NEEDS_REVIEW") || claim!=null && !claims.permitsRoster(claim,j.school(),ref.rosterId()) && !ref.status().equals("ALREADY_LINKED")) throw failure(409,"PREVIEW_STALE");
                }
            }
            var selected=new HashSet<>(in.selectedRowIds());
            if(selected.isEmpty() || selected.size()!=in.selectedRowIds().size() || selected.stream().anyMatch(rowId->staged.stream().noneMatch(r->r.get("id").equals(rowId) && r.get("validation_state").equals("VALID")))) throw failure(422,"INVALID_ROW_SELECTION");
            var stamp=access.capture(auth,j.school(),j.term(),PERMISSION);
            var updates=selected.stream().map(row->new Object[]{row,id}).toList();
            jdbc.batchUpdate("UPDATE student_import_rows SET selected=true,row_state='QUEUED',version=version+1 WHERE id=? AND job_id=?",updates);
            jdbc.update("UPDATE student_import_jobs SET state='PROCESSING',version=version+1,selected_rows=?,initiated_by=?,initiated_security_version=?,authority_assignment_id=?,authority_version=?,started_at=?,finished_at=NULL WHERE id=?",selected.size(),actor(auth),ActorContext.fromAuthenticatedWorkflow(auth,j.school()).currentSecurityVersion(),stamp.id(),stamp.version(),utc(clock.instant()),id);
            audit.workflow(actor(auth),"STUDENT_IMPORT_CONFIRMED",id,j.school(),Map.of("selectedCount",selected.size(),"jobVersion",j.version()+1,"operationId",uuid(key)));
            return new ImportAccepted(id,j.version()+1,"PROCESSING",selected.size());
        },ImportAccepted::importJobId);
    }
    public ImportJobDto get(Authentication auth,String id) {return project(authorized(auth,id));}
    public OperationLedger.HttpResult resume(Authentication auth,String id,String key,ResumeImport in) {
        var j=authorized(auth,id);uuid(key);version(in.expectedJobVersion());
        return ledger.execute(new OperationLedger.Command(actor(auth),j.school(),"STUDENT_IMPORT_RESUME",key,Map.of("id",id,"request",in),202),OperationLedger.HttpResult.class,()->{
            var current=job(id,true);
            if(current.state().equals("PROCESSING") && current.lease()!=null && clock.instant().isBefore(current.lease())) throw failure(409,"JOB_IN_PROGRESS");
            if(!current.state().equals("FAILED") || current.selected()==0 || current.version()!=in.expectedJobVersion()) throw failure(409,"STATE_CONFLICT");
            var dto=project(current);
            if(dto.processedRows()==current.selected()) return new OperationLedger.HttpResult(200,finalizeJob(current));
            var stamp=access.capture(auth,j.school(),j.term(),PERMISSION);
            jdbc.update("UPDATE student_import_rows SET row_state='QUEUED',attempt_count=0,next_attempt_at=NULL,error_codes=JSON_ARRAY(),version=version+1 WHERE job_id=? AND selected=true AND row_state IN ('QUEUED','PROCESSING','TRANSIENT_FAILED')",id);
            jdbc.update("UPDATE student_import_jobs SET state='PROCESSING',version=version+1,initiated_by=?,initiated_security_version=?,authority_assignment_id=?,authority_version=?,lease_owner=NULL,lease_until=NULL,lease_epoch=lease_epoch+1,finished_at=NULL,last_error_code=NULL WHERE id=?",actor(auth),ActorContext.fromAuthenticatedWorkflow(auth,j.school()).currentSecurityVersion(),stamp.id(),stamp.version(),id);
            audit.workflow(actor(auth),"STUDENT_IMPORT_RESUMED",id,j.school(),Map.of("retryRowCount",j.selected()-dto.processedRows(),"jobVersion",j.version()+1,"operationId",uuid(key)));
            return new OperationLedger.HttpResult(202,new ImportAccepted(id,j.version()+1,"PROCESSING",j.selected()));
        },r->id);
    }
    ImportJobDto finalizeJob(Job j) {
        var dto=project(j);
        if(j.selected()==0 || dto.processedRows()!=j.selected()) throw new IllegalStateException("Unfinished import cannot finalize");
        return ledger.finalizeStudentImport(j.id(),j.initiatedBy(),ImportJobDto.class,()->{
            String state=dto.needsReviewRows()+dto.permanentFailedRows()==0?"COMPLETED":"COMPLETED_WITH_ERRORS";
            jdbc.update("UPDATE student_import_jobs SET state=?,version=version+1,finished_at=?,lease_owner=NULL,lease_until=NULL,last_error_code=NULL WHERE id=?",state,utc(clock.instant()),j.id());
            var done=project(job(j.id(),true));
            audit.workflow(j.initiatedBy(),"STUDENT_IMPORT_COMPLETED",j.id(),j.school(),Map.of("jobState",state,"totalRows",done.totalRows(),"processedRows",done.processedRows(),"provisionedRows",done.provisionedRows(),"alreadyLinkedRows",done.alreadyLinkedRows(),"reviewRows",done.needsReviewRows(),"failedRows",done.permanentFailedRows()));return done;
        });
    }
    public Page<ImportRowDto> listRows(Authentication auth,String id,int page,int size,String validation,String state,String mail) {
        var j=authorized(auth,id);WorkflowValues.page(page,size);
        if(validation!=null && !Set.of("VALID","INVALID","NEEDS_REVIEW").contains(validation) || state!=null && !Set.of("NOT_SELECTED","QUEUED","PROCESSING","PROVISIONED","ALREADY_LINKED","NEEDS_REVIEW","PERMANENT_FAILED","TRANSIENT_FAILED").contains(state) || mail!=null && !Set.of("NOT_REQUIRED","NOT_READY","QUEUED","SENDING","RETRY_WAIT","SENT","FAILED","SUPERSEDED","CANCELLED").contains(mail)) throw failure(400,"INVALID_REQUEST");
        return rows(j,page,size,validation,state,mail);
    }
    ImportJobDto project(Job j) {
        var counts=jdbc.queryForList("SELECT validation_state,row_state,selected,COUNT(*) n FROM student_import_rows WHERE job_id=? GROUP BY validation_state,row_state,selected",j.id());
        var validation=new HashMap<String,Integer>();var states=new HashMap<String,Integer>();
        for(var c:counts) {int n=((Number)c.get("n")).intValue();validation.merge(c.get("validation_state").toString(),n,Integer::sum);if(Boolean.TRUE.equals(c.get("selected")) || c.get("selected") instanceof Number b && b.intValue()==1) states.merge(c.get("row_state").toString(),n,Integer::sum);}
        int provisioned=states.getOrDefault("PROVISIONED",0),linked=states.getOrDefault("ALREADY_LINKED",0),review=states.getOrDefault("NEEDS_REVIEW",0),failed=states.getOrDefault("PERMANENT_FAILED",0);
        return new ImportJobDto(j.id(),j.version(),j.state(),j.hash(),j.term(),j.expires(),j.total(),validation.getOrDefault("VALID",0),validation.getOrDefault("INVALID",0),validation.getOrDefault("NEEDS_REVIEW",0),j.selected(),states.getOrDefault("QUEUED",0),states.getOrDefault("PROCESSING",0),states.getOrDefault("TRANSIENT_FAILED",0),provisioned,linked,review,failed,provisioned+linked+review+failed,provisioned+linked,j.started(),j.finished(),clock.instant());
    }
    private Page<ImportRowDto> rows(Job j,int page,int size,String validation,String state,String mail) {
        String base=" FROM student_import_rows r LEFT JOIN invitation_claims c ON c.id=r.claim_id LEFT JOIN users u ON u.id=COALESCE(c.user_id,c.expected_user_id,r.user_id) LEFT JOIN activation_mail_outbox m ON m.claim_id=c.id AND m.generation=c.generation WHERE r.job_id=?";
        var args=new ArrayList<Object>();args.add(j.id());
        if(validation!=null) {base+=" AND r.validation_state=?";args.add(validation);}if(state!=null) {base+=" AND r.row_state=?";args.add(state);}
        String mailExpression="CASE WHEN r.row_state='PROVISIONED' THEN COALESCE(m.state,'NOT_READY') ELSE 'NOT_REQUIRED' END";
        if(mail!=null) {base+=" AND "+mailExpression+"=?";args.add(mail);}
        long total=jdbc.queryForObject("SELECT COUNT(*)"+base,Long.class,args.toArray());args.add(size);args.add(page*size);
        var content=jdbc.query("SELECT r.*,COALESCE(c.user_id,r.user_id) projected_user,c.version invitation_version,c.claim_state,u.status account_status,m.id mail_id,"+mailExpression+" mail_state"+base+" ORDER BY r.row_no,r.id LIMIT ? OFFSET ?",(r,n)->{
            String activation="NOT_REQUIRED";
            if(r.getString("row_state").equals("PROVISIONED")) activation="ACTIVE".equals(r.getString("account_status"))?"ACTIVE":"REVIEW_REQUIRED".equals(r.getString("claim_state")) || r.getString("account_status")!=null && !Set.of("PENDING_ACTIVATION","ACTIVE").contains(r.getString("account_status"))?"BLOCKED":"PENDING";
            return new ImportRowDto(r.getString("id"),r.getInt("row_no"),r.getLong("version"),JSON.readValue(r.getString("normalized_snapshot"),ImportSourceData.class),r.getString("validation_state"),r.getBoolean("selected"),r.getString("row_state"),List.of(JSON.readValue(r.getString("error_codes"),String[].class)),r.getString("roster_id"),r.getString("provisioning_effect_id"),r.getString("projected_user"),r.getString("provisioning_outcome"),activation,(Long)r.getObject("invitation_version"),r.getString("mail_id"),r.getString("mail_state"));
        },args.toArray());
        return new Page<>(content,page,size,total,(int)((total+size-1)/size));
    }
}
