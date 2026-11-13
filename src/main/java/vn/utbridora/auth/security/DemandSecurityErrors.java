package vn.utbridora.auth.security;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import jakarta.servlet.http.*;

/** Route-specific new envelope. Legacy core endpoints retain their existing responses. */
final class DemandSecurityErrors {
    private record ErrorBody(String errorCode,String error,String message,String requestId,List<Object> fieldErrors,Integer retryAfterSeconds) {}
    private DemandSecurityErrors() {}
    static boolean applies(HttpServletRequest request) {
        String path=request.getServletPath();
        return java.util.List.of("/api/v1/enterprise/demands","/api/v1/enterprise/verifications","/api/v1/school/verifications",
            "/api/v1/files","/api/v1/taxonomy","/api/v1/students","/api/v1/terms","/api/v1/t3","/api/v1/matching","/api/v1/invitations","/api/v1/school/cooperations","/api/v1/enterprise/cooperations","/api/v1/school/proposals","/api/v1/school/profile-shares","/api/v1/school/terms","/api/v1/school/student-eligibilities","/api/v1/school/delegations","/api/v1/admin/school-delegations")
            .stream().anyMatch(prefix->path.equals(prefix) || path.startsWith(prefix+"/"));
    }
    static void write(HttpServletRequest request,HttpServletResponse response,int status,String code,String message) throws IOException {
        Object attribute=request.getAttribute("bridora.request_id");
        String id=attribute instanceof String s?s:UUID.randomUUID().toString();
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.setHeader("X-Request-ID",id);
        response.getWriter().write(new tools.jackson.databind.ObjectMapper().writeValueAsString(
            new ErrorBody(code,code,message,id,List.of(),null)));
    }
}
