package vn.utbridora.auth.files;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.junit.jupiter.api.Test;
import vn.utbridora.auth.operations.OperationFailure;
import vn.utbridora.auth.files.FileContracts.FilePurpose;
import static org.junit.jupiter.api.Assertions.*;

class FileSafetyValidatorTest {
    static byte[] zip(Map<String,byte[]> entries) throws IOException {
        var bytes=new ByteArrayOutputStream();try(var zip=new ZipOutputStream(bytes)) {
            for(var entry:entries.entrySet()) {zip.putNextEntry(new ZipEntry(entry.getKey()));zip.write(entry.getValue());zip.closeEntry();}
        }return bytes.toByteArray();
    }
    static byte[] bytes(String value) {return value.getBytes(StandardCharsets.UTF_8);}
    static void valid(byte[] bytes,String type,String name,FilePurpose purpose) throws IOException {FileSafetyValidator.validate(new ByteArrayInputStream(bytes),bytes.length,type,name,purpose);}
    static void invalid(byte[] bytes,String type,String name,FilePurpose purpose) {
        assertEquals(422,assertThrows(OperationFailure.class,()->valid(bytes,type,name,purpose)).status);
    }
    @Test void permitsOrdinaryArchiveWithoutExtractingOrExecutingCode() throws Exception {
        valid(zip(Map.of("src/main.py",bytes("print('never executed')"),"notes.txt",bytes("synthetic"))),"application/zip","submission.zip",FilePurpose.SUBMISSION);
    }
    @Test void rejectsTraversalAbsoluteWindowsAndControlPaths() throws Exception {
        for(String name:new String[]{"../outside","/absolute","C:/outside","folder\\outside","folder/./file","folder//file","bad\nname"})
            invalid(zip(Map.of(name,bytes("synthetic"))),"application/zip","submission.zip",FilePurpose.SUBMISSION);
    }
    @Test void rejectsUnixSymlinkCentralAttributesEvenWhenLocalEntryLooksRegular() throws Exception {
        byte[] archive=zip(Map.of("link",bytes("../../outside")));
        for(int i=0;i<archive.length-46;i++) if(archive[i]==80 && archive[i+1]==75 && archive[i+2]==1 && archive[i+3]==2) {
            int mode=0120777;archive[i+40]=(byte)mode;archive[i+41]=(byte)(mode>>8);break;
        }
        invalid(archive,"application/zip","submission.zip",FilePurpose.SUBMISSION);
    }
    @Test void rejectsNestedArchivesAndTruncatedOrSpoofedZip() throws Exception {
        byte[] inner=zip(Map.of("file.txt",bytes("synthetic")));
        invalid(zip(Map.of("nested.bin",inner)),"application/zip","submission.zip",FilePurpose.SUBMISSION);
        invalid(Arrays.copyOf(inner,inner.length-1),"application/zip","submission.zip",FilePurpose.SUBMISSION);
        invalid(inner,"application/zip","submission.pdf",FilePurpose.SUBMISSION);
        invalid(bytes("PK fake"),"application/zip","submission.zip",FilePurpose.SUBMISSION);
    }
    @Test void enforcesEntryAndExpandedSizeBudgets() throws Exception {
        var many=new LinkedHashMap<String,byte[]>();for(int i=0;i<1001;i++) many.put("file"+i,new byte[0]);
        invalid(zip(many),"application/zip","submission.zip",FilePurpose.SUBMISSION);
        byte[] small=zip(Map.of("synthetic.txt",bytes("small")));
        for(int i=0;i<small.length-46;i++) if(small[i]==80 && small[i+1]==75 && small[i+2]==1 && small[i+3]==2) {
            int size=100*1024*1024+1;for(int j=0;j<4;j++)small[i+24+j]=(byte)(size>>>(8*j));break;
        }
        invalid(small,"application/zip","submission.zip",FilePurpose.SUBMISSION);
    }
    static Map<String,byte[]> workbook() {
        var entries=new LinkedHashMap<String,byte[]>();
        entries.put("[Content_Types].xml",bytes("<Types/>"));entries.put("_rels/.rels",bytes("<Relationships/>"));
        entries.put("xl/workbook.xml",bytes("<workbook/>"));entries.put("xl/worksheets/sheet1.xml",bytes("<worksheet><sheetData><row r='1'/><row r='2'/></sheetData></worksheet>"));return entries;
    }
    @Test void acceptsReadOnlyXlsxStructureAndRejectsMacrosExternalLinksAndEntities() throws Exception {
        valid(zip(workbook()),FileSafetyValidator.XLSX,"roster.xlsx",FilePurpose.ROSTER_IMPORT);
        for(String attack:new String[]{"<!DOCTYPE worksheet [<!ENTITY x SYSTEM 'file:///private'>]><worksheet>&x;</worksheet>","<worksheet><relationship TargetMode='External' Target='https://example.test'/></worksheet>","<worksheet>"}) {
            var entries=workbook();entries.put("xl/worksheets/sheet1.xml",bytes(attack));invalid(zip(entries),FileSafetyValidator.XLSX,"roster.xlsx",FilePurpose.ROSTER_IMPORT);
        }
        var entries=workbook();entries.put("xl/vbaProject.bin",bytes("synthetic"));invalid(zip(entries),FileSafetyValidator.XLSX,"roster.xlsx",FilePurpose.ROSTER_IMPORT);
        invalid(zip(Map.of("not-xlsx",bytes("synthetic"))),FileSafetyValidator.XLSX,"roster.xlsx",FilePurpose.ROSTER_IMPORT);
    }
    @Test void validatesUtf8QuotedCsvAndPurposeSpecificRowLimit() throws Exception {
        valid(bytes("\ufeffcode,name\r\n1,\"A, B\"\r\n2,\"C\"\" D\"\r\n"),"text/csv","roster.csv",FilePurpose.ROSTER_IMPORT);
        for(String bad:new String[]{"a,a\n1,2",",b\n1,2","a,b\n1","a,b\n1,\"unterminated","a\n\"closed\"trailing","a\nnull\0byte"})
            invalid(bytes(bad),"text/csv","roster.csv",FilePurpose.ROSTER_IMPORT);
        invalid(new byte[]{(byte)0xc3,0x28},"text/csv","roster.csv",FilePurpose.ROSTER_IMPORT);
        String roster="code\n"+"synthetic\n".repeat(1001);
        valid(bytes(roster),"text/csv","roster.csv",FilePurpose.ROSTER_IMPORT);
        invalid(bytes(roster),"text/csv","placement.csv",FilePurpose.PLACEMENT_IMPORT);
    }
}
