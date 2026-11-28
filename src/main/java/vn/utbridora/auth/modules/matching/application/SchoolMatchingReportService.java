package vn.utbridora.auth.modules.matching.application;

import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.utbridora.auth.audit.AuditService;
import vn.utbridora.auth.authorization.SchoolDelegationAccess;
import vn.utbridora.auth.operations.OperationFailure;
import static vn.utbridora.auth.operations.WorkflowValues.*;

/** Aggregate reporting over persisted matching snapshots; never runs matching or reads student PII. */
@Service
public class SchoolMatchingReportService {
    public record Totals(long demands,long compared,long matched,long missingData,long notMatched) {}
    public record Row(String matchRunId,String demandId,String title,String enterpriseName,String termId,String termCode,
                      Instant completedAt,long demandVersion,long criteriaVersion,boolean criteriaCurrent,
                      long matched,long missingData,long notMatched) {}
    public record Report(String schoolOrgId,List<Row> content,int page,int size,long totalElements,int totalPages,Totals totals,Instant generatedAt) {}
    private record Query(String cte,List<Object> args) {}
    private static final String PERMISSION="matching:school:detail_read";
    private final JdbcTemplate jdbc;
    private final SchoolDelegationAccess access;
    private final AuditService audit;
    public SchoolMatchingReportService(JdbcTemplate jdbc,SchoolDelegationAccess access,AuditService audit) {
        this.jdbc=jdbc;this.access=access;this.audit=audit;
    }

    @Transactional
    public Report report(Authentication auth,String school,String term,String search,int page,int size) {
        school=uuid(school);page(page,size);var query=query(auth,school,term,search);
        var totals=totals(query);var args=new ArrayList<>(query.args());args.add(size);args.add(page*size);
        var rows=jdbc.query(query.cte()+" SELECT * FROM report_rows ORDER BY completed_at DESC,id DESC LIMIT ? OFFSET ?",mapper,args.toArray());
        audit.writeScoped(actor(auth),"MATCH_AGGREGATE_VIEWED","match_run",null,"SUCCESS",school,null);
        return new Report(school,rows,page,size,totals.demands(),Math.toIntExact((totals.demands()+size-1)/size),totals,Instant.now());
    }

    @Transactional
    public String export(Authentication auth,String school,String term,String search) {
        school=uuid(school);var query=query(auth,school,term,search);
        if(totals(query).demands()>10000) throw failure(422,"REPORT_TOO_LARGE");
        var rows=jdbc.query(query.cte()+" SELECT * FROM report_rows ORDER BY completed_at DESC,id DESC",mapper,query.args().toArray());
        String csv=csv(rows);
        // Joins this transaction: a failed audit prevents the file from being returned.
        audit.writeScoped(actor(auth),"MATCH_EXPORT_ATTEMPTED","match_run",null,"SUCCESS",school,null);
        return csv;
    }

    private Query query(Authentication auth,String school,String term,String search) {
        school=uuid(school);term=term==null || term.isBlank()?null:uuid(term);
        String q=search==null || search.isBlank()?"":text(search,150);
        access.require(auth,school,null,PERMISSION);
        var allowed=new ArrayList<String>();
        if(term!=null) {access.require(auth,school,term,PERMISSION);allowed.add(term);}
        else for(String candidate:jdbc.queryForList("SELECT DISTINCT term_id FROM school_delegations WHERE user_id=? AND school_org_id=? AND active=true",String.class,actor(auth),school)) {
            try {access.require(auth,school,candidate,PERMISSION);allowed.add(candidate);}
            catch(OperationFailure denied) {if(denied.status!=403 && denied.status!=404) throw denied;}
        }
        if(allowed.isEmpty()) throw failure(404,"RESOURCE_NOT_FOUND");
        var args=new ArrayList<Object>();args.add(school);args.addAll(allowed);args.add(q);args.add(q);
        // Pick exactly one SCHOOL run per demand before counting/paging. Older snapshots are not added together.
        // Pre-origin snapshots remain readable only if they were never an enterprise protected release.
        String cte="""
            WITH ranked AS (
              SELECT r.*,REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.data_json,'$.run.completedAt')),'Z','') completed_at,
                ROW_NUMBER() OVER(PARTITION BY r.demand_id ORDER BY
                  REPLACE(JSON_UNQUOTE(JSON_EXTRACT(r.data_json,'$.run.completedAt')),'Z','') DESC,r.id DESC) rn
              FROM match_runs r LEFT JOIN matching_run_origins o ON o.run_id=r.id
              WHERE r.school_org_id=? AND r.term_id IN (%s)
                AND JSON_UNQUOTE(JSON_EXTRACT(r.data_json,'$.run.state'))='COMPLETED'
                AND (o.audience='SCHOOL' OR (o.run_id IS NULL AND NOT EXISTS
                  (SELECT 1 FROM matching_privacy_releases p WHERE p.match_run_id=r.id)))
            ), source_rows AS (
              SELECT r.id,r.demand_id,r.term_id,r.data_json stored_json,r.completed_at,t.term_code,e.name enterprise_name,
                COALESCE(JSON_UNQUOTE(JSON_EXTRACT(v.data_json,'$.title')),
                  CASE WHEN d.version=CAST(JSON_UNQUOTE(JSON_EXTRACT(r.data_json,'$.run.demandVersion')) AS UNSIGNED)
                    THEN JSON_UNQUOTE(JSON_EXTRACT(d.data_json,'$.title')) END,'Nhu cầu thực tập') title,
                d.criteria_hash current_hash,d.criteria_version current_criteria_version,
                COUNT(m.student_user_id) compared,
                COALESCE(SUM(m.overall_status='MATCH'),0) matched,
                COALESCE(SUM(m.overall_status='MISSING_DATA'),0) missing_data,
                COALESCE(SUM(m.overall_status='NOT_MATCH'),0) not_matched
              FROM ranked r JOIN demands d ON d.id=r.demand_id AND d.school_org_id=r.school_org_id AND d.term_id=r.term_id
                JOIN organizations e ON e.id=r.enterprise_org_id JOIN terms t ON t.id=r.term_id AND t.school_org_id=r.school_org_id
                LEFT JOIN demand_revisions v ON v.demand_id=d.id AND v.version=CAST(JSON_UNQUOTE(JSON_EXTRACT(r.data_json,'$.run.demandVersion')) AS UNSIGNED)
                LEFT JOIN match_results m ON m.run_id=r.id
              WHERE r.rn=1 AND d.soft_deleted_at IS NULL
              GROUP BY r.id,r.demand_id,r.term_id,r.data_json,r.completed_at,t.term_code,e.name,v.data_json,d.version,d.data_json,d.criteria_hash,d.criteria_version
            ), report_rows AS (SELECT * FROM source_rows WHERE (?='' OR LOCATE(LOWER(?),LOWER(CONCAT_WS(' ',title,enterprise_name,term_code)))>0))
            """.formatted(String.join(",",Collections.nCopies(allowed.size(),"?")));
        return new Query(cte,args);
    }

    private Totals totals(Query query) {
        return jdbc.queryForObject(query.cte()+" SELECT COUNT(*) demands,COALESCE(SUM(compared),0) compared,COALESCE(SUM(matched),0) matched,COALESCE(SUM(missing_data),0) missing_data,COALESCE(SUM(not_matched),0) not_matched FROM report_rows",
            (r,n)->new Totals(r.getLong("demands"),r.getLong("compared"),r.getLong("matched"),r.getLong("missing_data"),r.getLong("not_matched")),query.args().toArray());
    }
    private static final RowMapper<Row> mapper=(r,n)->{
        var run=JSON.readValue(r.getString("stored_json"),MatchingService.StoredRun.class).run();
        return new Row(r.getString("id"),r.getString("demand_id"),r.getString("title"),r.getString("enterprise_name"),r.getString("term_id"),r.getString("term_code"),
            run.completedAt(),run.demandVersion(),run.criteriaVersion(),Objects.equals(run.criteriaHash(),r.getString("current_hash")) && run.criteriaVersion()==r.getLong("current_criteria_version"),
            r.getLong("matched"),r.getLong("missing_data"),r.getLong("not_matched"));
    };

    static String csv(List<Row> rows) {
        var out=new StringBuilder("\uFEFF");
        line(out,List.of("Nhu cầu","Doanh nghiệp","Kỳ thực tập","Thời điểm đối chiếu (UTC)","Đáp ứng","Thiếu dữ liệu","Không đáp ứng","Tổng lượt hồ sơ","Tình trạng tiêu chí"));
        for(var row:rows) line(out,List.of(row.title(),row.enterpriseName(),row.termCode(),row.completedAt().toString(),
            String.valueOf(row.matched()),String.valueOf(row.missingData()),String.valueOf(row.notMatched()),String.valueOf(row.matched()+row.missingData()+row.notMatched()),
            row.criteriaCurrent()?"Tiêu chí hiện hành":"Tiêu chí đã thay đổi"));
        return out.toString();
    }
    private static void line(StringBuilder out,List<String> values) {
        out.append(values.stream().map(value->{
            String text=value==null?"":value;
            String first=text.stripLeading();
            if(!first.isEmpty() && "=+-@".indexOf(first.charAt(0))>=0 || text.startsWith("\t") || text.startsWith("\r") || text.startsWith("\n")) text="'"+text;
            return "\""+text.replace("\"","\"\"")+"\"";
        }).collect(java.util.stream.Collectors.joining(","))).append("\r\n");
    }
}
