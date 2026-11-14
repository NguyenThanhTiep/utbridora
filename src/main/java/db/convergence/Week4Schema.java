package db.convergence;

import java.sql.*;
import java.util.*;

/** Exact structural preflight for the immutable week-4 SQL families; no data mutation. */
public final class Week4Schema {
    private Week4Schema() {}
    public static final List<String> EVALUATION=List.of("internship_rubric_templates","internship_evaluations","internship_evaluation_versions","internship_evaluation_files");
    public static final List<String> VISIBILITY=List.of("internship_evaluation_revision_decisions","internship_school_result_policies","internship_grade_visibility");
    private static final String PERMISSIONS="('t3:grades:publish','t3:result_policy:manage','t3:grade_visibility:manage')";
    public static String shape(Connection c,List<String> tables) throws SQLException {
        var rows=new ArrayList<String>();
        for(String table:tables) {
            collect(c,rows,"TABLE", "SELECT TABLE_NAME,ENGINE,TABLE_COLLATION FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?",table);
            collect(c,rows,"COLUMN", "SELECT TABLE_NAME,COLUMN_NAME,ORDINAL_POSITION,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,EXTRA,CHARACTER_SET_NAME,COLLATION_NAME,GENERATION_EXPRESSION FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?",table);
            collect(c,rows,"INDEX", "SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME,SUB_PART,INDEX_TYPE,IS_VISIBLE FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?",table);
            collect(c,rows,"KEY", "SELECT TABLE_NAME,CONSTRAINT_NAME,COLUMN_NAME,ORDINAL_POSITION,REFERENCED_TABLE_NAME,REFERENCED_COLUMN_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=?",table);
            collect(c,rows,"FK", "SELECT TABLE_NAME,CONSTRAINT_NAME,UPDATE_RULE,DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA=DATABASE() AND TABLE_NAME=?",table);
            collect(c,rows,"CHECK", "SELECT t.TABLE_NAME,t.CONSTRAINT_NAME,t.ENFORCED,c.CHECK_CLAUSE FROM information_schema.TABLE_CONSTRAINTS t JOIN information_schema.CHECK_CONSTRAINTS c ON c.CONSTRAINT_SCHEMA=t.CONSTRAINT_SCHEMA AND c.CONSTRAINT_NAME=t.CONSTRAINT_NAME WHERE t.TABLE_SCHEMA=DATABASE() AND t.TABLE_NAME=? AND t.CONSTRAINT_TYPE='CHECK'",table);
        }
        Collections.sort(rows);return String.join("\n",rows);
    }
    public static String visibility(Connection c) throws SQLException {
        var rows=new ArrayList<String>();
        collect(c,rows,"PERMISSION","SELECT code,resource,action,description FROM permissions WHERE code IN "+PERMISSIONS,null);
        collect(c,rows,"ROLE","SELECT role,permission_code FROM role_permissions WHERE permission_code IN "+PERMISSIONS,null);
        Collections.sort(rows);return shape(c,VISIBILITY)+(rows.isEmpty()?"":"\n"+String.join("\n",rows));
    }
    private static void collect(Connection c,List<String> rows,String prefix,String sql,String table) throws SQLException {
        try(var s=c.prepareStatement(sql)) {
            if(table!=null)s.setString(1,table);
            try(var r=s.executeQuery()) {while(r.next()) {
                var line=new StringBuilder(prefix);for(int i=1;i<=r.getMetaData().getColumnCount();i++) {
                    String value=r.getString(i);line.append('\t').append(value==null?"<NULL>":value.replace("\r\n","\n"));
                } rows.add(line.toString());
            }}
        }
    }
}
