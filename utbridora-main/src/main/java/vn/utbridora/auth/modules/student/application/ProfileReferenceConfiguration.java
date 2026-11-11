package vn.utbridora.auth.modules.student.application;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods=false)
public class ProfileReferenceConfiguration {
    @Bean
    ProfileReferenceValidator profileReferences(AcademicCatalogService catalogs,
            vn.utbridora.auth.modules.taxonomy.port.TaxonomyReferencePort taxonomy) {
        return (school,input)->{
            if(input.majorCode()!=null || !input.orientationCodes().isEmpty() || !input.subjectCodes().isEmpty()) {
                var catalog=catalogs.forProfile(school,input.termId());
                if(input.majorCode()!=null) requireCode(catalog,AcademicCatalogService.Kind.MAJOR,input.majorCode(),null,"majorCode");
                for(String code:input.orientationCodes()) requireCode(catalog,AcademicCatalogService.Kind.ORIENTATION,code,input.majorCode(),"orientationCodes");
                for(String code:input.subjectCodes()) requireCode(catalog,AcademicCatalogService.Kind.SUBJECT,code,input.majorCode(),"subjectCodes");
            }
            java.util.Set<String> skills=new java.util.HashSet<>(),categories=new java.util.HashSet<>();
            input.skills().forEach(s->skills.add(s.skillCode()));
            input.projects().forEach(p->skills.addAll(p.skillCodes()));
            input.customSkills().forEach(s->categories.add(s.categoryCode()));
            if(skills.isEmpty() && categories.isEmpty()) return;
            var auth=org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
            if(auth==null || !(auth.getPrincipal() instanceof vn.utbridora.auth.security.AppPrincipal actor))
                throw StudentProfileException.of(401,"SESSION_EXPIRED","Phiên đăng nhập đã hết hiệu lực.");
            var refs=taxonomy.read(actor,skills,categories);
            if(!refs.activeSkillCodes().containsAll(skills)) throw invalid("skills");
            if(!refs.activeCategoryCodes().containsAll(categories)) throw invalid("customSkills");
        };
    }
    private static void requireCode(AcademicCatalogService.Snapshot catalog,AcademicCatalogService.Kind kind,
            String code,String major,String field) {
        boolean valid=catalog.entries().get(kind).stream().anyMatch(i->i.code().equals(code) && i.status().equals("ACTIVE")
            && (i.majorCodes().isEmpty() || (major!=null && i.majorCodes().contains(major))));
        if(!valid) throw invalid(field);
    }
    private static StudentProfileException invalid(String field) {
        return new StudentProfileException(422,"CATALOG_CODE_INVALID","Lựa chọn không thuộc danh mục đang hoạt động.",
            java.util.List.of(new vn.utbridora.auth.modules.student.api.StudentProfileDtos.FieldErrorView(field,"CATALOG_CODE_INVALID","Tải lại danh mục và chọn mã hợp lệ.")));
    }
}
