package vn.utbridora.auth.files;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.onboarding.ImportRowValidator;
import static org.junit.jupiter.api.Assertions.*;

class TabularFileReaderTest {
    static final String HEADER="studentCode,email,fullName,majorCode,termCode";
    static TabularFileReader.Table csv(String value) {return TabularFileReader.read(value.getBytes(StandardCharsets.UTF_8),"students.csv","text/csv",5000);}
    @Test void quotedRecordsBomLeadingZerosCaseAndTrailingEmptyRowsArePreserved() {
        var table=csv("\ufeff"+HEADER+"\r\n000Ab,a@example.test,\"Name\nWith \"\"Quote\"\"\",IT,T1\r\n000ab,b@example.test,Other Name,IT,T1\r\n,,,,\r\n");
        assertEquals(2,table.rows().size());assertEquals(2,table.rows().getFirst().rowNo());assertEquals(3,table.rows().getLast().rowNo());
        assertEquals("000Ab",table.rows().getFirst().cells().getFirst().value());assertEquals("Name\nWith \"Quote\"",table.rows().getFirst().cells().get(2).value());
        assertEquals("000ab",table.rows().getLast().cells().getFirst().value());
    }
    @Test void rejectsMalformedEncodingQuotesHeadersAndTooManyRecords() {
        assertEquals("FILE_STRUCTURE_INVALID",assertThrows(OperationFailure.class,()->TabularFileReader.read(new byte[]{(byte)0xc3,0x28},"x.csv","text/csv",5000)).code);
        for(String value:List.of(HEADER+"\n1,a@x.test,\"unclosed,IT,T1",HEADER+",studentCode\n1,a@x.test,Name,IT,T1,1",HEADER+"\n1,a@x.test,Name,IT,T1,extra")) assertEquals("FILE_STRUCTURE_INVALID",assertThrows(OperationFailure.class,()->csv(value)).code);
        StringBuilder large=new StringBuilder(HEADER+"\n");for(int i=0;i<5001;i++) large.append(i).append(",a@x.test,Name,IT,T1\n");
        assertEquals("IMPORT_ROW_LIMIT_EXCEEDED",assertThrows(OperationFailure.class,()->csv(large.toString())).code);
    }
    @Test void duplicateEmailMarksBothRowsInvalidButStudentCodesAreCaseSensitive() {
        var values=ImportRowValidator.validate(csv(HEADER+"\n001A, A@EXAMPLE.TEST ,First Name,IT,T1\n001a,a@example.test,Second Name,IT,T1\n001B,b@example.test,Third Name,IT,T1"),"school","T1",Set.of("IT"));
        assertTrue(values.get(0).errorCodes().contains("DUPLICATE_IN_FILE"));assertTrue(values.get(1).errorCodes().contains("DUPLICATE_IN_FILE"));assertEquals("VALID",values.get(2).validationState());
        var caseOnly=ImportRowValidator.validate(csv(HEADER+"\n001A,a@x.test,First Name,IT,T1\n001a,b@x.test,Second Name,IT,T1"),"school","T1",Set.of("IT"));
        assertTrue(caseOnly.stream().allMatch(r->r.validationState().equals("VALID")));
    }
    @Test void foreignSchoolAndUnknownOrForbiddenColumnsFailTheWholeFile() {
        assertEquals(403,assertThrows(OperationFailure.class,()->ImportRowValidator.validate(csv(HEADER+",schoolOrgId\n1,a@x.test,First Name,IT,T1,other"),"school","T1",Set.of("IT"))).status);
        for(String column:List.of("schoolApproved","unknown")) assertEquals("FILE_STRUCTURE_INVALID",assertThrows(OperationFailure.class,()->ImportRowValidator.validate(csv(HEADER+","+column+"\n1,a@x.test,First Name,IT,T1,true"),"school","T1",Set.of("IT"))).code);
    }
    @Test void numericExcelCodeCannotBeGuessedAndFormulaIsNotEvaluated() throws Exception {
        var table=TabularFileReader.read(xlsx("<row r=\"2\"><c r=\"A2\"><v>1</v></c><c r=\"B2\" t=\"inlineStr\"><is><t>a@x.test</t></is></c><c r=\"C2\" t=\"inlineStr\"><is><t>First Name</t></is></c><c r=\"D2\" t=\"inlineStr\"><is><t>IT</t></is></c><c r=\"E2\" t=\"str\"><f>EXTERNAL()</f><v>T1</v></c></row>"),"x.xlsx",FileSafetyValidator.XLSX,5000);
        var row=ImportRowValidator.validate(table,"school","T1",Set.of("IT")).getFirst();
        assertNull(row.sourceData().studentCode());assertNull(row.sourceData().termCode());assertTrue(row.errorCodes().containsAll(List.of("STUDENT_CODE_MUST_BE_TEXT","TERM_CODE_INVALID")));
        assertEquals("FILE_STRUCTURE_INVALID",assertThrows(OperationFailure.class,()->TabularFileReader.read(xlsx("<mergeCells count=\"1\"><mergeCell ref=\"A2:B2\"/></mergeCells>"),"x.xlsx",FileSafetyValidator.XLSX,5000)).code);
    }
    private static byte[] xlsx(String data) throws IOException {
        var parts=new LinkedHashMap<String,String>();parts.put("[Content_Types].xml","<Types/>");parts.put("_rels/.rels","<Relationships/>");parts.put("xl/workbook.xml","<workbook><sheets><sheet name=\"Students\" sheetId=\"1\"/></sheets></workbook>");
        var header=new StringBuilder("<row r=\"1\">");int column=0;for(String name:HEADER.split(",")) header.append("<c r=\"").append((char)('A'+column++)).append("1\" t=\"inlineStr\"><is><t>").append(name).append("</t></is></c>");header.append("</row>");
        parts.put("xl/worksheets/sheet1.xml","<worksheet><sheetData>"+header+data+"</sheetData></worksheet>");
        var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)) {for(var part:parts.entrySet()) {zip.putNextEntry(new ZipEntry(part.getKey()));zip.write(part.getValue().getBytes(StandardCharsets.UTF_8));zip.closeEntry();}}return bytes.toByteArray();
    }
}
