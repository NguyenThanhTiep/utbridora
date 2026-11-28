package vn.utbridora.auth.modules.cooperation.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.utbridora.auth.modules.cooperation.domain.DemandJson;
import static vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;

/** JDBC participates in the existing transaction manager; every query includes the selected org. */
@Repository
public class DemandRepository {
    private final JdbcTemplate jdbc;
    public DemandRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public record StoredDemand(String id,String enterpriseOrgId,String state,long version,long criteriaVersion,
            String criteriaHash,DemandInput data,Instant createdAt,Instant updatedAt,Instant deletedAt,String cooperationId) {
        public StoredDemand(String id,String org,String state,long version,long criteriaVersion,String hash,DemandInput data,Instant created,Instant updated,Instant deleted) {
            this(id,org,state,version,criteriaVersion,hash,data,created,updated,deleted,null);
        }
        public DemandDto dto() { return new DemandDto(id,enterpriseOrgId,state,version,criteriaVersion,"VALID",data,cooperationId,deletedAt); }
    }
    public Optional<StoredDemand> findLocked(String id,String org) {
        return jdbc.query("SELECT * FROM demands WHERE id=? AND enterprise_org_id=? FOR UPDATE",this::row,id,org).stream().findFirst();
    }
    public Optional<StoredDemand> findForRead(String id,String org) {
        return jdbc.query("SELECT * FROM demands WHERE id=? AND enterprise_org_id=?",this::row,id,org).stream().findFirst();
    }
    public Optional<StoredDemand> findForSchool(String id) {
        return jdbc.query("SELECT * FROM demands WHERE id=? FOR UPDATE",this::row,id).stream().findFirst();
    }
    public void insert(StoredDemand d) {
        jdbc.update("INSERT INTO demands(id,enterprise_org_id,school_org_id,term_id,state,validation_state,version,criteria_version,criteria_hash,data_json,created_at,updated_at,soft_deleted_at) VALUES(?,?,?,?,?,'VALID',?,?,?,?,?,?,NULL)",
                d.id(),d.enterpriseOrgId(),d.data().schoolOrgId(),d.data().termId(),d.state(),d.version(),d.criteriaVersion(),d.criteriaHash(),DemandJson.write(d.data()),Timestamp.from(d.createdAt()),Timestamp.from(d.updatedAt()));
    }
    public int update(StoredDemand d,long previousVersion) {
        return jdbc.update("UPDATE demands SET version=?,criteria_version=?,criteria_hash=?,data_json=?,updated_at=? WHERE id=? AND enterprise_org_id=? AND version=?",
                d.version(),d.criteriaVersion(),d.criteriaHash(),DemandJson.write(d.data()),Timestamp.from(d.updatedAt()),d.id(),d.enterpriseOrgId(),previousVersion);
    }
    public void addRevision(StoredDemand d,DemandCriteriaSnapshot snapshot,String actor,String operation) {
        jdbc.update("INSERT INTO demand_revisions(demand_id,version,criteria_version,criteria_hash,data_json,snapshot_json,actor_id,operation,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                d.id(),d.version(),d.criteriaVersion(),d.criteriaHash(),DemandJson.write(d.data()),DemandJson.write(snapshot),actor,operation,Timestamp.from(d.updatedAt()));
    }
    /** Caller holds the demand row, the common fence for lifecycle, submit and nomination. */
    public boolean lifecycleUnlocked(String id) {
        return lifecycleUnlocked(id,false);
    }
    /** NEEDS_CHANGES explicitly returns the demand to DRAFT for the enterprise to revise. */
    public boolean draftEditable(String id) {return lifecycleUnlocked(id,true);}
    private boolean lifecycleUnlocked(String id,boolean editing) {
        if (!jdbc.queryForList("SELECT id FROM cooperations WHERE demand_id=? AND state NOT IN ('DRAFT','REJECTED'"+(editing?",'NEEDS_CHANGES'":"")+") FOR UPDATE",String.class,id).isEmpty()) return false;
        if (!jdbc.queryForList("SELECT p.id FROM placements p JOIN cooperations c ON c.id=p.cooperation_id WHERE c.demand_id=? AND p.state<>'CANCELLED' FOR UPDATE",String.class,id).isEmpty()) return false;
        return jdbc.queryForList("SELECT i.id FROM proposal_items i JOIN proposal_batches b ON b.id=i.batch_id JOIN cooperations c ON c.id=b.cooperation_id WHERE c.demand_id=? AND i.state NOT IN ('DECLINED','EXPIRED','REVOKED') FOR UPDATE",String.class,id).isEmpty();
    }
    public int applyLifecycle(StoredDemand d,boolean softDelete) {
        if (softDelete) {
            return jdbc.update("UPDATE demands SET soft_deleted_at=UTC_TIMESTAMP(6),version=version+1,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND enterprise_org_id=? AND version=? AND soft_deleted_at IS NULL",
                    d.id(),d.enterpriseOrgId(),d.version());
        }
        return jdbc.update("UPDATE demands SET state='DRAFT',version=version+1,soft_deleted_at=NULL,updated_at=UTC_TIMESTAMP(6) WHERE id=? AND enterprise_org_id=? AND version=?",
                d.id(),d.enterpriseOrgId(),d.version());
    }
    public DemandCriteriaSnapshot currentSnapshot(StoredDemand demand) {
        String data=jdbc.queryForObject("SELECT r.snapshot_json FROM demand_revisions r JOIN demands d ON d.id=r.demand_id WHERE r.demand_id=? AND r.version=? AND d.enterprise_org_id=?",
                String.class,demand.id(),demand.version(),demand.enterpriseOrgId());
        return DemandJson.read(data,DemandCriteriaSnapshot.class);
    }
    public Page<DemandDto> list(String org,String term,String state,boolean includeDeleted,int page,int size) {
        return list(org,term,state,includeDeleted,page,size,null);
    }
    public Page<DemandDto> list(String org,String term,String state,boolean includeDeleted,int page,int size,String school) {
        String where=" WHERE enterprise_org_id=?"; var args=new ArrayList<Object>(); args.add(org);
        if(school!=null) { where+=" AND school_org_id=?"; args.add(school); }
        if(term!=null) { where+=" AND term_id=?"; args.add(term); }
        if(state!=null) { where+=" AND state=?"; args.add(state); }
        if(!includeDeleted) where+=" AND soft_deleted_at IS NULL";
        long total=jdbc.queryForObject("SELECT COUNT(*) FROM demands"+where,Long.class,args.toArray());
        args.add(size); args.add((long)page*size);
        List<DemandDto> rows=jdbc.query("SELECT * FROM demands"+where+" ORDER BY created_at DESC,id ASC LIMIT ? OFFSET ?",this::row,args.toArray()).stream().map(StoredDemand::dto).toList();
        return new Page<>(rows,page,size,total,(int)((total+size-1)/size));
    }
    private StoredDemand row(ResultSet rs,int row) throws SQLException {
        Timestamp deleted=rs.getTimestamp("soft_deleted_at");
        return new StoredDemand(rs.getString("id"),rs.getString("enterprise_org_id"),rs.getString("state"),rs.getLong("version"),rs.getLong("criteria_version"),rs.getString("criteria_hash"),
                DemandJson.read(rs.getString("data_json"),DemandInput.class),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),deleted==null?null:deleted.toInstant(),rs.getString("cooperation_id"));
    }
}
