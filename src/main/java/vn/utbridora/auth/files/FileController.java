package vn.utbridora.auth.files;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import vn.utbridora.auth.files.FileContracts.*;
import static vn.utbridora.auth.operations.WorkflowValues.failure;

@RestController @RequestMapping("/api/v1/files")
public class FileController {
    private final FileService files;
    public FileController(FileService files) {this.files=files;}
    @ModelAttribute void privateResponse(HttpServletResponse response) {
        response.setHeader("Cache-Control","private, no-store");response.setHeader("X-Content-Type-Options","nosniff");
    }
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE) @ResponseStatus(HttpStatus.ACCEPTED)
    public FileDto upload(Authentication actor,@RequestHeader("Idempotency-Key") String key,MultipartHttpServletRequest request) {
        if(!request.getMultiFileMap().keySet().equals(Set.of("file")) || request.getFiles("file").size()!=1
            || !request.getParameterMap().keySet().equals(Set.of("resourceType","resourceId","purpose"))
            || request.getParameterMap().values().stream().anyMatch(values->values.length!=1)) throw failure(400,"SCHEMA_VALIDATION");
        ResourceType type;FilePurpose purpose;
        try {type=ResourceType.valueOf(request.getParameter("resourceType"));purpose=FilePurpose.valueOf(request.getParameter("purpose"));}
        catch(IllegalArgumentException error) {throw failure(400,"SCHEMA_VALIDATION");}
        return files.upload(actor,key,new ResourceRef(type,request.getParameter("resourceId")),purpose,request.getFile("file"));
    }
    @GetMapping("/{fileId}/metadata") public FileDto metadata(Authentication actor,@PathVariable String fileId) {return files.metadata(actor,fileId);}
    @GetMapping("/{fileId}/content")
    public void content(Authentication actor,@PathVariable String fileId,@RequestHeader(name="Range",required=false) String range,HttpServletResponse response) throws IOException {
        try(var download=files.download(actor,fileId,range)) {
            response.setStatus(download.partial()?206:200);response.setContentType(download.file().contentType());response.setContentLengthLong(download.length());
            response.setHeader("Accept-Ranges","bytes");
            response.setHeader("Content-Disposition",ContentDisposition.attachment().filename(download.file().originalName(),StandardCharsets.UTF_8).build().toString());
            if(download.partial()) response.setHeader("Content-Range","bytes "+download.start()+"-"+(download.start()+download.length()-1)+"/"+download.file().sizeBytes());
            var output=response.getOutputStream();var buffer=ByteBuffer.allocate(8192);long remaining=download.length();
            while(remaining>0) {
                buffer.clear();buffer.limit((int)Math.min(buffer.capacity(),remaining));int read=download.channel().read(buffer);
                if(read<0) throw new IOException("Private file stream ended unexpectedly");
                output.write(buffer.array(),0,read);remaining-=read;
            }
        }
    }
}
