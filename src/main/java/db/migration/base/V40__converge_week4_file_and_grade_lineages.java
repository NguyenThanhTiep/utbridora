package db.migration.base;

import db.convergence.Week4Schema;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.zip.CRC32;
import org.flywaydb.core.api.migration.*;

/** Forward-only convergence. Historical SQL and history records are never rewritten. */
public class V40__converge_week4_file_and_grade_lineages extends BaseJavaMigration {
    public static final String SCANNER="db/legacy/t1-week4/V38__durable_private_file_scans.sql";
    public static final String EVALUATION="db/migration/week4-t3/V38__t3_rubric_evaluation_history.sql";
    public static final String VISIBILITY="db/migration/week4-t3/V39__t3_grade_visibility.sql";
    private static final String SHAPES="db/convergence/week4-shapes.txt";
    @Override public Integer getChecksum(){var crc=new CRC32();for(String path:List.of(SCANNER,EVALUATION,VISIBILITY,SHAPES))crc.update(resource(path).getBytes(StandardCharsets.UTF_8));return (int)crc.getValue();}
    @Override public void migrate(Context context) throws Exception {
        Connection c=context.getConnection();var expected=expected();
        String scanner=Week4Schema.shape(c,List.of("files"));
        String evaluation=Week4Schema.shape(c,Week4Schema.EVALUATION);
        String visibility=Week4Schema.visibility(c);
        // All checks precede the first DDL. MySQL DDL does not roll back as one transaction.
        boolean needScanner=scanner.equals(expected.get("scanner-before"));
        if(!needScanner && !scanner.equals(expected.get("scanner")))throw invalid("scanner");
        boolean needEvaluation=evaluation.isEmpty();
        if(!needEvaluation && !evaluation.equals(expected.get("evaluation")))throw invalid("evaluation");
        boolean needVisibility=visibility.isEmpty();
        if(!needVisibility && !visibility.equals(expected.get("visibility")))throw invalid("visibility");
        if(needEvaluation && !needVisibility)throw invalid("visibility without evaluation");
        requireHistory(c,SCANNER,needScanner);
        requireHistory(c,EVALUATION,needEvaluation);
        requireHistory(c,VISIBILITY,needVisibility);
        if(needScanner)apply(c,SCANNER);
        if(needEvaluation)apply(c,EVALUATION);
        if(needVisibility)apply(c,VISIBILITY);
        if(!Week4Schema.shape(c,List.of("files")).equals(expected.get("scanner"))
            || !Week4Schema.shape(c,Week4Schema.EVALUATION).equals(expected.get("evaluation"))
            || !Week4Schema.visibility(c).equals(expected.get("visibility")))throw invalid("postcondition");
    }
    private static void requireHistory(Connection c,String path,boolean absent) throws SQLException {
        String name=path.substring(path.lastIndexOf('/')+1);
        // Flyway may retain a location-relative script path from an older checkout.
        // Validation owns checksum/description verification; do not rewrite the stored path.
        try(var s=c.prepareStatement("SELECT script FROM flyway_schema_history WHERE success=true");var r=s.executeQuery()) {
            int matches=0;while(r.next()){String script=r.getString(1).replace('\\','/');if(script.equals(name)||script.endsWith("/"+name))matches++;}
            if(matches>1 || (matches==1)==absent)throw invalid("history/schema disagreement for "+name);
        }
    }
    private static SQLException invalid(String family){return new SQLException("Unknown or partial week 4 schema: "+family+"; stop and inspect a backup; do not repair/reset history");}
    private static Map<String,String> expected(){
        var result=new HashMap<String,String>();
        for(String section:resource(SHAPES).split("(?m)^@@"))if(!section.isBlank()) {
            int end=section.indexOf('\n');result.put(section.substring(0,end),section.substring(end+1).stripTrailing());
        }
        if(!result.keySet().equals(Set.of("scanner-before","scanner","evaluation","visibility")))throw new IllegalStateException("Invalid immutable week4 schema reference");
        return result;
    }
    public static void apply(Connection c,String path) throws SQLException {
        // These immutable sources have no stored routines or semicolons inside literals.
        for(String sql:resource(path).replaceAll("(?m)^\\s*--[^\\r\\n]*","").split(";"))
            if(!sql.isBlank())try(var s=c.createStatement()){s.execute(sql);}
    }
    public static String resource(String path){
        try(var input=V40__converge_week4_file_and_grade_lineages.class.getClassLoader().getResourceAsStream(path)) {
            if(input==null)throw new IllegalStateException("Missing immutable source: "+path);
            return new String(input.readAllBytes(),StandardCharsets.UTF_8).replace("\r\n","\n");
        }catch(java.io.IOException e){throw new IllegalStateException(e);}
    }
}
