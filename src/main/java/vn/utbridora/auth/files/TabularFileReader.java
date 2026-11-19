package vn.utbridora.auth.files;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.stream.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Bounded, non-executing table reader; retains cell type rather than guessing Excel identifiers. */
public final class TabularFileReader {
    public enum Kind { TEXT, NUMBER, FORMULA, EMPTY }
    public record Cell(String value,Kind kind) {
        public static Cell text(String value) {return new Cell(value,Kind.TEXT);}
        public boolean blank() {return kind!=Kind.FORMULA && (value==null || value.isBlank());}
    }
    public record Row(int rowNo,List<Cell> cells) {}
    public record Table(List<String> headers,List<Row> rows) {}
    private static final int MAX_PHYSICAL_ROWS=100_000,MAX_COLUMNS=32;
    private TabularFileReader() {}
    public static Table read(byte[] bytes,String filename,String contentType,int maximumRows) {
        if(bytes.length==0 || bytes.length>5*1024*1024) throw failure(bytes.length==0?422:413,bytes.length==0?"FILE_STRUCTURE_INVALID":"FILE_TOO_LARGE");
        List<List<Cell>> records;
        String name=filename==null?"":filename.toLowerCase(Locale.ROOT);
        if(name.endsWith(".csv") && "text/csv".equals(contentType)) records=csv(bytes);
        else if(name.endsWith(".xlsx") && FileSafetyValidator.XLSX.equals(contentType)) records=xlsx(bytes);
        else throw failure(422,"UNSUPPORTED_FILE_FORMAT");
        while(records.size()>1 && records.getLast().stream().allMatch(Cell::blank)) records.removeLast();
        if(records.size()<2 || records.getFirst().isEmpty()) throw invalid();
        if(records.size()-1>maximumRows) throw failure(422,"IMPORT_ROW_LIMIT_EXCEEDED");
        var header=records.getFirst();
        if(header.size()>MAX_COLUMNS || header.stream().anyMatch(c->c.kind()!=Kind.TEXT || c.blank())) throw invalid();
        var headers=header.stream().map(c->c.value().trim()).toList();
        if(new HashSet<>(headers).size()!=headers.size()) throw invalid();
        var rows=new ArrayList<Row>();
        for(int i=1;i<records.size();i++) {
            var cells=new ArrayList<>(records.get(i));
            if(cells.size()>headers.size() && cells.subList(headers.size(),cells.size()).stream().anyMatch(c->!c.blank())) throw invalid();
            while(cells.size()<headers.size()) cells.add(new Cell(null,Kind.EMPTY));
            if(cells.size()>headers.size()) cells.subList(headers.size(),cells.size()).clear();
            rows.add(new Row(i+1,List.copyOf(cells)));
        }
        return new Table(headers,List.copyOf(rows));
    }
    private static List<List<Cell>> csv(byte[] bytes) {
        String text;
        try {text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException malformed) {throw invalid();}
        if(text.startsWith("\ufeff")) text=text.substring(1);
        if(text.indexOf('\0')>=0) throw invalid();
        var rows=new ArrayList<List<Cell>>();var row=new ArrayList<Cell>();var value=new StringBuilder();
        boolean quoted=false,closed=false;
        for(int i=0;i<=text.length();i++) {
            char c=i==text.length()?'\n':text.charAt(i);
            if(quoted) {
                if(i==text.length()) throw invalid();
                if(c=='"') {if(i+1<text.length() && text.charAt(i+1)=='"') {value.append('"');i++;} else {quoted=false;closed=true;}}
                else value.append(c);
            } else if(c=='"') {
                if(!value.isEmpty() || closed) throw invalid();quoted=true;
            } else if(c==',' || c=='\r' || c=='\n') {
                if(i==text.length() && value.isEmpty() && row.isEmpty() && i>0 && (text.charAt(i-1)=='\n' || text.charAt(i-1)=='\r')) break;
                row.add(Cell.text(value.toString()));value.setLength(0);closed=false;
                if(row.size()>MAX_COLUMNS) throw invalid();
                if(c!=',') {rows.add(row);row=new ArrayList<>();if(rows.size()>MAX_PHYSICAL_ROWS) throw invalid();if(c=='\r' && i+1<text.length() && text.charAt(i+1)=='\n') i++;}
            } else {if(closed) throw invalid();value.append(c);}
        }
        if(rows.isEmpty()) throw invalid();
        int width=rows.getFirst().size();
        for(var r:rows) if(r.size()!=width && r.stream().anyMatch(c->!c.blank())) throw invalid();
        return rows;
    }
    private static XMLStreamReader xml(byte[] bytes) throws XMLStreamException {
        var factory=XMLInputFactory.newFactory();factory.setProperty(XMLInputFactory.SUPPORT_DTD,false);factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,false);
        factory.setXMLResolver((a,b,c,d)->{throw new XMLStreamException("External entities disabled");});
        return factory.createXMLStreamReader(new ByteArrayInputStream(bytes));
    }
    private static List<List<Cell>> xlsx(byte[] bytes) {
        try {
            FileSafetyValidator.archive(bytes,true,FileContracts.FilePurpose.ROSTER_IMPORT,MAX_PHYSICAL_ROWS);
            var parts=new HashMap<String,byte[]>();
            try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry entry;while((entry=zip.getNextEntry())!=null) {
                    if(entry.getName().equals("xl/sharedStrings.xml") || entry.getName().equals("xl/workbook.xml") || entry.getName().startsWith("xl/worksheets/") && entry.getName().endsWith(".xml")) parts.put(entry.getName(),zip.readNBytes(100*1024*1024+1));
                    zip.closeEntry();
                }
            }
            var sheets=parts.keySet().stream().filter(n->n.startsWith("xl/worksheets/")).toList();
            if(sheets.size()!=1) throw invalid();
            int declaredSheets=0;var workbook=xml(parts.get("xl/workbook.xml"));
            try {while(workbook.hasNext()) if(workbook.next()==XMLStreamConstants.START_ELEMENT && workbook.getLocalName().equals("sheet")) declaredSheets++;} finally {workbook.close();}
            if(declaredSheets!=1) throw invalid();
            var strings=new ArrayList<String>();
            if(parts.containsKey("xl/sharedStrings.xml")) {
                var reader=xml(parts.get("xl/sharedStrings.xml"));StringBuilder item=null;
                try {while(reader.hasNext()) {
                    int event=reader.next();
                    if(event==XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals("si")) item=new StringBuilder();
                    else if(event==XMLStreamConstants.START_ELEMENT && reader.getLocalName().equals("t") && item!=null) item.append(reader.getElementText());
                    else if(event==XMLStreamConstants.END_ELEMENT && reader.getLocalName().equals("si")) {strings.add(item.toString());item=null;if(strings.size()>250_000) throw invalid();}
                }} finally {reader.close();}
            }
            var rows=new ArrayList<List<Cell>>();List<Cell> row=null;int col=-1;String type=null,value=null;boolean formula=false;StringBuilder inline=null;
            var reader=xml(parts.get(sheets.getFirst()));
            try {while(reader.hasNext()) {
                int event=reader.next();String local=event==XMLStreamConstants.START_ELEMENT || event==XMLStreamConstants.END_ELEMENT?reader.getLocalName():"";
                if(event==XMLStreamConstants.START_ELEMENT) switch(local) {
                    case "mergeCell","mergeCells" -> throw invalid();
                    case "row" -> {
                        int index=number(reader.getAttributeValue(null,"r"),rows.size()+1);
                        if(index<1 || index>MAX_PHYSICAL_ROWS || index<=rows.size()) throw invalid();
                        while(rows.size()<index-1) rows.add(new ArrayList<>());
                        row=new ArrayList<>();col=-1;
                    }
                    case "c" -> {
                        if(row==null) throw invalid();String ref=reader.getAttributeValue(null,"r");
                        col=ref==null?row.size():column(ref);if(col<row.size() || col>=MAX_COLUMNS) throw invalid();
                        while(row.size()<col) row.add(new Cell(null,Kind.EMPTY));
                        type=reader.getAttributeValue(null,"t");value=null;formula=false;inline=new StringBuilder();
                    }
                    case "f" -> formula=true;
                    case "v" -> value=reader.getElementText();
                    case "t" -> {if(inline!=null) inline.append(reader.getElementText());}
                    default -> {}
                } else if(event==XMLStreamConstants.END_ELEMENT && local.equals("c")) {
                    if(row==null) throw invalid();
                    Kind kind=formula?Kind.FORMULA:Kind.NUMBER;
                    if("s".equals(type)) {int index=number(value,-1);if(index<0 || index>=strings.size()) throw invalid();value=strings.get(index);kind=formula?Kind.FORMULA:Kind.TEXT;}
                    else if("inlineStr".equals(type)) {value=inline.toString();kind=formula?Kind.FORMULA:Kind.TEXT;}
                    else if("str".equals(type)) kind=formula?Kind.FORMULA:Kind.TEXT;
                    if(value==null && !formula) kind=Kind.EMPTY;
                    row.add(new Cell(value,kind));inline=null;
                } else if(event==XMLStreamConstants.END_ELEMENT && local.equals("row")) {rows.add(row);row=null;}
            }} finally {reader.close();}
            return rows;
        } catch(vn.utbridora.auth.operations.OperationFailure invalidArchive) {throw invalid();}
        catch(IOException | XMLStreamException | IllegalArgumentException malformed) {throw invalid();}
    }
    private static int number(String value,int fallback) {return value==null?fallback:Integer.parseInt(value);}
    private static int column(String reference) {
        if(!reference.matches("[A-Z]{1,3}[1-9][0-9]{0,6}")) throw invalid();int index=0;
        for(char c:reference.toCharArray()) {if(c<'A' || c>'Z') break;index=index*26+c-'A'+1;}
        return index-1;
    }
    private static vn.utbridora.auth.operations.OperationFailure invalid() {return failure(422,"FILE_STRUCTURE_INVALID");}
}
