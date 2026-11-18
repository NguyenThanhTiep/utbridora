package vn.utbridora.auth.files;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.text.Normalizer;
import java.util.*;
import java.util.zip.*;
import javax.xml.stream.*;
import vn.utbridora.auth.files.FileContracts.FilePurpose;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

/** Bounded, non-executing inspection. Nothing is extracted to a filesystem. */
final class FileSafetyValidator {
    static final long MAX_EXPANDED=100L*1024*1024;
    static final int MAX_ENTRIES=1000;
    static final String XLSX="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private FileSafetyValidator() {}
    static void validate(InputStream input,long size,String type,String name,FilePurpose purpose) throws IOException {
        if(size<1 || size>20L*1024*1024 || type==null || name==null) invalid();
        byte[] bytes=input.readNBytes(Math.toIntExact(size)+1);
        if(bytes.length!=size) invalid();
        String lower=name.toLowerCase(Locale.ROOT);
        if(type.equals("application/zip") || type.equals(XLSX)) {
            if(!lower.endsWith(type.equals(XLSX)?".xlsx":".zip")) invalid();
            archive(bytes,type.equals(XLSX),purpose);
        } else if(type.equals("text/csv")) {
            if(!lower.endsWith(".csv") || size>5L*1024*1024) invalid();
            csv(bytes,purpose==FilePurpose.PLACEMENT_IMPORT?1000:5000);
        } else {
            boolean valid=switch(type) {
                case "application/pdf" -> bytes.length>=8 && starts(bytes,new byte[]{37,80,68,70,45}) && lower.endsWith(".pdf");
                case "image/png" -> bytes.length>=16 && starts(bytes,new byte[]{(byte)137,80,78,71,13,10,26,10,0,0,0,13,73,72,68,82}) && lower.endsWith(".png");
                case "image/jpeg" -> bytes.length>=4 && starts(bytes,new byte[]{(byte)255,(byte)216,(byte)255}) && (lower.endsWith(".jpg") || lower.endsWith(".jpeg"));
                default -> false;
            };
            if(!valid) invalid();
        }
    }
    private record Entry(String name,long size,long crc,int method) {}
    static void archive(byte[] bytes,boolean xlsx,FilePurpose purpose) throws IOException {
        // Reject ZIP64, spanning, encryption and special files. These unsupported variants never reach CLEAN.
        int end=-1;
        for(int i=bytes.length-22;i>=Math.max(0,bytes.length-65557);i--)
            if(u32(bytes,i)==0x06054b50L && i+22+u16(bytes,i+20)==bytes.length) {end=i;break;}
        if(end<0 || u16(bytes,end+4)!=0 || u16(bytes,end+6)!=0) invalid();
        int count=u16(bytes,end+10);
        long offset=u32(bytes,end+16),length=u32(bytes,end+12);
        if(count<1 || count>MAX_ENTRIES || u16(bytes,end+8)!=count || offset+length!=end || offset<0 || offset>end) invalid();
        int cursor=(int)offset;long expanded=0;
        var entries=new LinkedHashMap<String,Entry>();
        for(int i=0;i<count;i++) {
            if(cursor+46>end || u32(bytes,cursor)!=0x02014b50L) invalid();
            int flags=u16(bytes,cursor+8),method=u16(bytes,cursor+10),names=u16(bytes,cursor+28),extra=u16(bytes,cursor+30),comment=u16(bytes,cursor+32);
            long size=u32(bytes,cursor+24),mode=u32(bytes,cursor+38)>>>16,local=u32(bytes,cursor+42);
            if((flags&0x2041)!=0 || (method!=0 && method!=8) || u16(bytes,cursor+34)!=0 || local>=offset
                || size==0xffffffffL || cursor+46L+names+extra+comment>end || names==0) invalid();
            int kind=(int)mode&0170000;
            if(kind!=0 && kind!=0100000 && kind!=0040000) invalid();
            String name=decode(Arrays.copyOfRange(bytes,cursor+46,cursor+46+names),(flags&0x800)!=0?StandardCharsets.UTF_8:Charset.forName("CP437"));
            safePath(name);
            String canonical=Normalizer.normalize(name,Normalizer.Form.NFC);
            if(entries.putIfAbsent(canonical,new Entry(name,size,u32(bytes,cursor+16),method))!=null) invalid();
            expanded+=size;if(expanded>MAX_EXPANDED) invalid();
            cursor+=46+names+extra+comment;
        }
        if(cursor!=end || !starts(bytes,new byte[]{80,75,3,4})) invalid();
        long total=0;var seen=new HashSet<String>();boolean workbook=false,worksheet=false,contentTypes=false;
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes),Charset.forName("CP437"))) {
            ZipEntry entry;
            while((entry=zip.getNextEntry())!=null) {
                String name=entry.getName(),canonical=Normalizer.normalize(name,Normalizer.Form.NFC);var expected=entries.get(canonical);
                if(expected==null || !name.equals(expected.name()) || !seen.add(canonical) || seen.size()>MAX_ENTRIES || entry.getMethod()!=expected.method()) invalid();
                var body=new ByteArrayOutputStream();byte[] buffer=new byte[8192];long entrySize=0;int read;
                boolean xml=xlsx && (name.endsWith(".xml") || name.endsWith(".rels"));
                while((read=zip.read(buffer))!=-1) {
                    entrySize+=read;total+=read;
                    if(total>MAX_EXPANDED || entrySize>expected.size()) invalid();
                    // Only XML and a small signature prefix are retained, never expanded binary payloads.
                    if(xml || body.size()<8) body.write(buffer,0,xml?read:Math.min(read,8-body.size()));
                }
                if(entrySize!=expected.size() || entry.getCrc()!=expected.crc()) invalid();
                byte[] sample=body.toByteArray();
                if(starts(sample,new byte[]{80,75,3,4}) || starts(sample,new byte[]{80,75,5,6}) || starts(sample,new byte[]{31,(byte)139})
                    || starts(sample,new byte[]{55,122,(byte)188,(byte)175,39,28}) || starts(sample,new byte[]{82,97,114,33})) invalid();
                if(xlsx) {
                    String lower=name.toLowerCase(Locale.ROOT);
                    if(lower.contains("vbaproject") || lower.startsWith("xl/externallinks/") || lower.startsWith("xl/embeddings/")) invalid();
                    if(xml) inspectXml(sample,name,purpose);
                    workbook|=name.equals("xl/workbook.xml");worksheet|=name.startsWith("xl/worksheets/") && name.endsWith(".xml");
                    contentTypes|=name.equals("[Content_Types].xml");
                }
                zip.closeEntry();
            }
        } catch(IOException malformed) {invalid();}
        if(seen.size()!=entries.size() || xlsx && (!workbook || !worksheet || !contentTypes || !entries.containsKey("_rels/.rels"))) invalid();
    }
    private static void inspectXml(byte[] bytes,String name,FilePurpose purpose) {
        try {
            var factory=XMLInputFactory.newFactory();factory.setProperty(XMLInputFactory.SUPPORT_DTD,false);factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES,false);
            factory.setXMLResolver((publicId,systemId,base,namespace)->{throw new XMLStreamException("External XML disabled");});
            var xml=factory.createXMLStreamReader(new ByteArrayInputStream(bytes));int rows=0;boolean root=false;
            try {
                while(xml.hasNext()) {
                    int event=xml.next();
                    if(event==XMLStreamConstants.DTD || event==XMLStreamConstants.ENTITY_REFERENCE) invalid();
                    if(event!=XMLStreamConstants.START_ELEMENT) continue;
                    if(!root) {
                        root=true;
                        if(name.equals("xl/workbook.xml") && !xml.getLocalName().equals("workbook")) invalid();
                        if(name.startsWith("xl/worksheets/") && !xml.getLocalName().equals("worksheet")) invalid();
                    }
                    if(name.startsWith("xl/worksheets/") && xml.getLocalName().equals("row") && ++rows>(purpose==FilePurpose.PLACEMENT_IMPORT?1001:5001)) invalid();
                    for(int i=0;i<xml.getAttributeCount();i++) {
                        String value=xml.getAttributeValue(i);
                        if(xml.getAttributeLocalName(i).equals("TargetMode") && value.equalsIgnoreCase("External") || value.toLowerCase(Locale.ROOT).contains("macroenabled")) invalid();
                    }
                }
            } finally {xml.close();}
            if(!root) invalid();
        } catch(XMLStreamException malformed) {invalid();}
    }
    private static void safePath(String name) {
        if(name.startsWith("/") || name.contains("\\") || name.contains(":") || name.codePoints().anyMatch(Character::isISOControl)) invalid();
        String[] parts=name.split("/",-1);
        for(int i=0;i<parts.length;i++) if(parts[i].equals(".") || parts[i].equals("..") || parts[i].isEmpty() && i!=parts.length-1) invalid();
    }
    static void csv(byte[] bytes,int maximumRows) {
        String text=decode(bytes,StandardCharsets.UTF_8);if(text.startsWith("\ufeff")) text=text.substring(1);
        if(text.isEmpty() || text.indexOf('\0')>=0) invalid();
        boolean quoted=false,closed=false,started=false;int columns=1,expected=-1,rows=0;var header=new ArrayList<String>();var cell=new StringBuilder();
        for(int i=0;i<=text.length();i++) {
            char c=i==text.length()?'\n':text.charAt(i);
            if(quoted) {
                if(c=='"') {if(i+1<text.length() && text.charAt(i+1)=='"') {cell.append('"');i++;}else {quoted=false;closed=true;}}
                else {if(i==text.length()) invalid();cell.append(c);}
                continue;
            }
            if(c=='"') {if(started || closed) invalid();quoted=true;started=true;continue;}
            if(c==',' || c=='\n' || c=='\r') {
                if(rows==0) header.add(cell.toString().trim());cell.setLength(0);started=false;closed=false;
                if(c==',') {columns++;continue;}
                if(c=='\r' && i+1<text.length() && text.charAt(i+1)=='\n') i++;
                if(i==text.length() && columns==1 && !started && i>0 && (text.charAt(i-1)=='\n' || text.charAt(i-1)=='\r')) break;
                if(expected<0) expected=columns;else if(columns!=expected) invalid();
                if(++rows>maximumRows+1) invalid();columns=1;
            } else {if(closed) invalid();started=true;cell.append(c);}
        }
        if(rows<2 || header.stream().anyMatch(String::isBlank) || new HashSet<>(header).size()!=header.size()) invalid();
    }
    private static boolean starts(byte[] bytes,byte[] prefix) {return bytes.length>=prefix.length && Arrays.equals(Arrays.copyOf(bytes,prefix.length),prefix);}
    private static String decode(byte[] bytes,Charset charset) {
        try {return charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();}
        catch(CharacterCodingException invalidEncoding) {invalid();return null;}
    }
    private static int u16(byte[] b,int offset) {return (b[offset]&255)|((b[offset+1]&255)<<8);}
    private static long u32(byte[] b,int offset) {return Integer.toUnsignedLong((b[offset]&255)|((b[offset+1]&255)<<8)|((b[offset+2]&255)<<16)|((b[offset+3]&255)<<24));}
    private static void invalid() {throw failure(422,"BUSINESS_VALIDATION");}
}
