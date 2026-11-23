package vn.utbridora.auth.modules.cooperation.application;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.utbridora.auth.authorization.AdvisoryAuthority;
import vn.utbridora.auth.modules.cooperation.integration.*;
import vn.utbridora.auth.modules.cooperation.persistence.DemandRepository;
import vn.utbridora.auth.modules.cooperation.domain.DemandModels.DemandInput;
import vn.utbridora.auth.operations.OperationLedger;
import static vn.utbridora.auth.operations.WorkflowValues.*;

@Service @Transactional(propagation=Propagation.MANDATORY)
public class DemandSemanticSnapshots implements DemandSemanticSnapshotPort {
    private record CooperationRef(String id,Long version,String state) {}
    @org.springframework.beans.factory.annotation.Value("${app.t5.demand.enabled:true}") private boolean enabled=true;
    private final JdbcTemplate jdbc;private final DemandRepository demands;private final DemandReferencePort references;private final AdvisoryAuthority authority;
    public DemandSemanticSnapshots(JdbcTemplate jdbc,DemandRepository demands,DemandReferencePort references,AdvisoryAuthority authority) {this.jdbc=jdbc;this.demands=demands;this.references=references;this.authority=authority;}
    public Snapshot capture(Authentication auth,String demand,long version,long criteria,String hash,Instant until) {
        if(!enabled) throw failure(503,"DEPENDENCY_UNAVAILABLE");
        var d=demands.findForSchool(uuid(demand)).orElseThrow(()->failure(404,"RESOURCE_NOT_FOUND"));
        authority.require(auth,d.data().schoolOrgId(),d.data().termId(),true);
        if(d.deletedAt()!=null || d.version()!=version || d.criteriaVersion()!=criteria || !d.criteriaHash().equals(hash)) throw failure(409,"AI_ADVISORY_SOURCE_STALE");
        validateReferences(d);
        var revisions=jdbc.queryForList("SELECT data_json FROM demand_revisions WHERE demand_id=? AND version=?",String.class,demand,version);
        if(revisions.size()!=1) throw failure(409,"AI_ADVISORY_SOURCE_STALE");
        var data=JSON.readValue(revisions.getFirst(),DemandInput.class);
        var cooperation=cooperation(d);if(cooperation.id()!=null && cooperation.version()==null)throw failure(409,"AI_ADVISORY_SOURCE_STALE");
        String title=clean(data.title()),description=clean(data.description());
        var s=new Snapshot(UUID.randomUUID().toString(),demand,data.schoolOrgId(),data.termId(),version,criteria,hash,title,description,OperationLedger.payloadHash(List.of("demand-semantic-v1",version,criteria,title,description,cooperation)),Instant.now(),until);
        jdbc.update("INSERT INTO demand_semantic_snapshots(id,data_json,source_context_json,expires_at) VALUES(?,?,?,?)",s.id(),JSON.writeValueAsString(s),JSON.writeValueAsString(cooperation),utc(until));return s;
    }
    public Snapshot read(Authentication auth,String id) {if(!enabled) throw failure(503,"DEPENDENCY_UNAVAILABLE");var s=jdbc.query("SELECT data_json FROM demand_semantic_snapshots WHERE id=?",(r,n)->JSON.readValue(r.getString(1),Snapshot.class),uuid(id)).stream().findFirst().orElseThrow(()->failure(409,"AI_ADVISORY_SOURCE_STALE"));authority.requireEither(auth,s.schoolOrgId(),s.termId());return s;}
    public boolean current(Authentication auth,String id) {
        var s=read(auth,id);var d=demands.findForSchool(s.demandId()).orElse(null);
        if(d==null || d.deletedAt()!=null || d.version()!=s.demandVersion() || d.criteriaVersion()!=s.criteriaVersion() || !d.criteriaHash().equals(s.criteriaHash()) || !Instant.now().isBefore(s.validUntil())) return false;
        var captured=JSON.readValue(jdbc.queryForObject("SELECT source_context_json FROM demand_semantic_snapshots WHERE id=?",String.class,id),CooperationRef.class);
        if(!captured.equals(cooperation(d)))return false;
        try {validateReferences(d);return true;}catch(vn.utbridora.auth.operations.OperationFailure stale){if(stale.code.equals("AI_ADVISORY_SOURCE_STALE"))return false;throw stale;}
    }
    public void purge(String id) {jdbc.update("DELETE FROM demand_semantic_snapshots WHERE id=? AND expires_at<=UTC_TIMESTAMP(6)",id);}
    private static String clean(String s) {return s==null?"":java.text.Normalizer.normalize(s,java.text.Normalizer.Form.NFC).replaceAll("(?U)\\s+"," ").strip();}
    private void validateReferences(DemandRepository.StoredDemand d) {
        try {references.validate(d.enterpriseOrgId(),d.data());}
        catch(vn.utbridora.auth.modules.cooperation.domain.DemandFailure unavailable) {throw failure(unavailable.status()==503?503:409,unavailable.status()==503?"DEPENDENCY_UNAVAILABLE":"AI_ADVISORY_SOURCE_STALE");}
    }
    private CooperationRef cooperation(DemandRepository.StoredDemand d) {
        if(d.cooperationId()==null)return new CooperationRef(null,null,null);
        return jdbc.query("SELECT id,version,state FROM cooperations WHERE id=? AND demand_id=? AND school_org_id=? AND term_id=? FOR SHARE",(r,n)->new CooperationRef(r.getString(1),r.getLong(2),r.getString(3)),d.cooperationId(),d.id(),d.data().schoolOrgId(),d.data().termId()).stream().findFirst().orElse(new CooperationRef(d.cooperationId(),null,"MISSING"));
    }
}
