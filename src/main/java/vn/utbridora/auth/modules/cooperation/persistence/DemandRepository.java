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
            String criteriaHash,DemandInput data,Instant createdAt,Instant updatedAt,Instant deletedAt) {
        public DemandDto dto() { return new DemandDto(id,enterpriseOrgId,state,version,criteriaVersion,"VALID",data,null,deletedAt); }
    }
    public record Command(String hash,String response,Instant createdAt) {}
    public Command lockCommand(String actor,String org,String operation,String key,String hash,Instant now) {
        jdbc.update("INSERT INTO demand_commands(actor_id,enterprise_org_id,operation,idempotency_key,request_hash,response_json,resource_id,created_at) VALUES(?,?,?,?,?,NULL,NULL,?) ON DUPLICATE KEY UPDATE idempotency_key=idempotency_key",
                actor,org,operation,key,hash,Timestamp.from(now));
        return jdbc.queryForObject("SELECT request_hash,response_json,created_at FROM demand_commands WHERE actor_id=? AND enterprise_org_id=? AND operation=? AND idempotency_key=? FOR UPDATE",
                (rs,n) -> new Command(rs.getString(1),rs.getString(2),rs.getTimestamp(3).toInstant()),actor,org,operation,key);
    }
    public void completeCommand(String actor,String org,String operation,String key,DemandDto result) {
        jdbc.update("UPDATE demand_commands SET response_json=?,resource_id=? WHERE actor_id=? AND enterprise_org_id=? AND operation=? AND idempotency_key=?",
                DemandJson.write(result),result.demandId(),actor,org,operation,key);
    }
    public Optional<StoredDemand> findLocked(String id,String org) {
        return jdbc.query("SELECT * FROM demands WHERE id=? AND enterprise_org_id=? FOR UPDATE",this::row,id,org).stream().findFirst();
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
    public DemandCriteriaSnapshot currentSnapshot(StoredDemand demand) {
        String data=jdbc.queryForObject("SELECT r.snapshot_json FROM demand_revisions r JOIN demands d ON d.id=r.demand_id WHERE r.demand_id=? AND r.version=? AND d.enterprise_org_id=?",
                String.class,demand.id(),demand.version(),demand.enterpriseOrgId());
        return DemandJson.read(data,DemandCriteriaSnapshot.class);
    }
    public Page<DemandDto> list(String org,String term,String state,boolean includeDeleted,int page,int size) {
        String where=" WHERE enterprise_org_id=?"; var args=new ArrayList<Object>(); args.add(org);
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
                DemandJson.read(rs.getString("data_json"),DemandInput.class),rs.getTimestamp("created_at").toInstant(),rs.getTimestamp("updated_at").toInstant(),deleted==null?null:deleted.toInstant());
    }
}
