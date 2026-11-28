package vn.utbridora.auth.modules.matching.application;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SchoolMatchingReportServiceTest {
    @Test void csvIsUtf8FriendlyQuotesMultilineTextAndNeutralizesSpreadsheetFormulas() {
        var row=new SchoolMatchingReportService.Row("run","demand","  =HYPERLINK(\"unsafe\")","Công ty, \"A\"\nChi nhánh","term","@TERM",
            Instant.parse("2026-11-28T00:00:00Z"),1,1,false,2,3,4);
        String csv=SchoolMatchingReportService.csv(List.of(row));
        assertTrue(csv.startsWith("\uFEFF\"Nhu cầu\""));
        assertTrue(csv.contains("\"'  =HYPERLINK(\"\"unsafe\"\")\""));
        assertTrue(csv.contains("\"Công ty, \"\"A\"\"\nChi nhánh\""));
        assertTrue(csv.contains("\"'@TERM\""));
        assertTrue(csv.endsWith("\"2\",\"3\",\"4\",\"9\",\"Tiêu chí đã thay đổi\"\r\n"));
        assertFalse(csv.contains("studentUserId"));
    }
    @Test void emptyCsvStillHasAHeader() {
        assertEquals(1,SchoolMatchingReportService.csv(List.of()).split("\r\n").length);
    }
}
