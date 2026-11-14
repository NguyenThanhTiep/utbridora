package vn.utbridora.auth;

import db.convergence.Week4Schema;
import db.migration.base.V40__converge_week4_file_and_grade_lineages;
import java.nio.file.*;
import java.sql.*;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import static org.junit.jupiter.api.Assertions.*;

/** Rebuilds the reference from immutable SQL, independently of V40's preflight decisions. */
class Week4SchemaReferenceIT {
    @Test void canonicalSqlMatchesCommittedReference() throws Exception {
        try(var mysql=new MySQLContainer<>("mysql:8.4")) {
            mysql.start();Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("39").load().migrate();
            try(var c=DriverManager.getConnection(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword())) {
                String before=Week4Schema.shape(c,List.of("files"));
                V40__converge_week4_file_and_grade_lineages.apply(c,V40__converge_week4_file_and_grade_lineages.SCANNER);
                String reference="@@scanner-before\n"+before+"\n@@scanner\n"+Week4Schema.shape(c,List.of("files"))+"\n@@evaluation\n"+Week4Schema.shape(c,Week4Schema.EVALUATION)+"\n@@visibility\n"+Week4Schema.visibility(c)+"\n";
                Files.writeString(Path.of("target/week4-shapes.txt"),reference);
                assertEquals(V40__converge_week4_file_and_grade_lineages.resource("db/convergence/week4-shapes.txt"),reference);
            }
        }
    }
}
