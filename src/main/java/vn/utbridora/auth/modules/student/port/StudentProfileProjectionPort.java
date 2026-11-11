package vn.utbridora.auth.modules.student.port;

import java.time.Instant;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonValue;
import vn.utbridora.auth.contracts.ActorContext;
import vn.utbridora.auth.contracts.files.FileReadPort.FileDto;
import vn.utbridora.auth.contracts.profile.ProfileAccessPort.Query;
import vn.utbridora.auth.modules.student.api.StudentProfileDtos.*;

/** BC24: T2 returns a closed projection after checking a fresh access decision. */
public interface StudentProfileProjectionPort {
    String schoolOf(ActorContext actor,String studentUserId,String termId);
    Projection readAllowed(ActorContext actor,Query query);
    record Projection(String studentUserId,long profileVersion,List<String> allowedFields,Instant validUntil,Data data) {}
    sealed interface Skill permits CanonicalSkill,CustomSkill {}
    record CanonicalSkill(String kind,SelfSkillView skill) implements Skill {
        public CanonicalSkill(SelfSkillView skill) {this("CANONICAL",skill);}
    }
    record CustomSkill(String kind,CustomSkillView skill) implements Skill {
        public CustomSkill(CustomSkillView skill) {this("CUSTOM",skill);}
    }
    /** Only these named fields can enter the JSON; excluded fields are omitted, allowed empty fields stay null/[]. */
    record Data(List<String> included,String displayName,String studentCode,String majorCode,List<Skill> skills,
                List<Project> projects,List<Certificate> certificates,FileDto cv,List<FileDto> evidence,String email,String phone) {
        public Data {included=List.copyOf(included);}
        @JsonValue public Map<String,Object> wire() {
            var result=new LinkedHashMap<String,Object>();
            for(String field:included) result.put(field,switch(field) {
                case "displayName"->displayName;case "studentCode"->studentCode;case "majorCode"->majorCode;
                case "skills"->skills;case "projects"->projects;case "certificates"->certificates;
                case "cv"->cv;case "evidence"->evidence;case "email"->email;case "phone"->phone;
                default->throw new IllegalArgumentException("Unsupported profile field");
            });
            return Collections.unmodifiableMap(result);
        }
    }
}
