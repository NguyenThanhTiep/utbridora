package vn.utbridora.auth.modules.cooperation.domain;

import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;
import static org.junit.jupiter.api.Assertions.*;
import static vn.utbridora.auth.modules.cooperation.domain.DemandModels.*;

class DemandDomainTest {
    public static DemandInput input() {
        return new DemandInput("11111111-1111-4111-8111-111111111111",
            "22222222-2222-4222-8222-222222222222", "Java internship", 5,
            "Build APIs", null, null, Instant.parse("2027-01-01T00:00:00Z"),
            new RequiredCriteria(List.of("IT"), List.of(new SkillCriterion("JAVA", 2, null)),
                new DateRange(LocalDate.parse("2027-02-01"), LocalDate.parse("2027-05-01"))),
            List.of(), "academic-v1", "skills-v1");
    }
    private DemandInput change(String title, int quantity, RequiredCriteria required, List<SkillCriterion> preferred) {
        var d=input();
        return new DemandInput(d.schoolOrgId(),d.termId(),title,quantity,d.description(),null,null,
            d.deadline(),required,preferred,d.academicCatalogVersion(),d.taxonomyVersion());
    }
    @Test void completeDraftAllowsNullableOptionalFields() {
        assertEquals(input(), DemandValidator.validate(input()));
    }
    @ParameterizedTest @ValueSource(ints={0,-1,5001})
    void rejectsInvalidQuantity(int quantity) {
        var e=assertThrows(DemandFailure.class,()->DemandValidator.validate(change("Title",quantity,input().required(),List.of())));
        assertEquals(422,e.status());
        assertEquals("quantity",e.fields().getFirst().field());
    }
    @Test void rejectsSkillInBothRequiredAndPreferred() {
        assertThrows(DemandFailure.class,()->DemandValidator.validate(change("Title",5,input().required(),input().required().skills())));
    }
    @Test void rejectsReversedDateRange() {
        var r=input().required();
        var reversed=new RequiredCriteria(r.majorCodes(),r.skills(),new DateRange(r.dateRange().endDate(),r.dateRange().startDate()));
        assertThrows(DemandFailure.class,()->DemandValidator.validate(change("Title",5,reversed,List.of())));
    }
    @Test void validatedCriteriaDoNotRetainMutableLists() {
        var majors=new ArrayList<>(List.of("IT"));
        var r=input().required();
        var validated=DemandValidator.validate(change("Title",5,new RequiredCriteria(majors,r.skills(),r.dateRange()),List.of()));
        majors.add("SE");
        assertEquals(List.of("IT"),validated.required().majorCodes());
        assertThrows(UnsupportedOperationException.class,()->validated.required().majorCodes().add("CS"));
    }
    @Test void descriptiveEditPreservesCriteriaHashButQuantityEditChangesIt() {
        var original=input();
        assertEquals(DemandJson.criteriaHash(original),DemandJson.criteriaHash(change("New title",5,original.required(),List.of())));
        assertNotEquals(DemandJson.criteriaHash(original),DemandJson.criteriaHash(change("New title",6,original.required(),List.of())));
    }
    // liên quan đến việc thêm lọc svien theo gpa là MVP: Chấp nhận ngưỡng 1.0–4.0 theo bước 0.1 và version hóa thay đổi ngưỡng.
    @Test void validatesEmployerGpaThresholdChoices() {
        var source=input();
        var at25=new RequiredCriteria(source.required().majorCodes(),source.required().skills(),source.required().dateRange(),new java.math.BigDecimal("2.5"));
        var validated=DemandValidator.validate(change("Title",5,at25,List.of()));
        assertEquals(new java.math.BigDecimal("2.5"),validated.required().minGpa());
        assertNotEquals(DemandJson.criteriaHash(source),DemandJson.criteriaHash(change("Title",5,at25,List.of())));
        for(String invalid:List.of("0.9","2.55","4.1")) {
            var criteria=new RequiredCriteria(source.required().majorCodes(),source.required().skills(),source.required().dateRange(),new java.math.BigDecimal(invalid));
            assertEquals("required.minGpa",assertThrows(DemandFailure.class,()->DemandValidator.validate(change("Title",5,criteria,List.of()))).fields().getFirst().field());
        }
    }
    // liên quan đến việc thêm lọc svien theo gpa là MVP: Cũ client không gửi minGpa vẫn được hiểu là không lọc GPA.
    @Test void oldDemandRequestWithoutMinGpaDefaultsToNoGpaFilter() {
        var node=JsonMapper.builder().build().readTree(DemandJson.write(input()));
        ((tools.jackson.databind.node.ObjectNode)node.get("required")).remove("minGpa");
        var parsed=DemandJson.request(node,DemandInput.class);
        assertNull(parsed.required().minGpa());
    }
    @ParameterizedTest @ValueSource(strings={"numericTitle","missingTitle","unknownField","fractionalQuantity"})
    void strictRequestRejectsMalformedSchema(String mutation) {
        var node=JsonMapper.builder().build().createObjectNode();
        node.setAll((tools.jackson.databind.node.ObjectNode)JsonMapper.builder().build().readTree(DemandJson.write(input())));
        switch(mutation) {
            case "numericTitle" -> node.put("title",123);
            case "missingTitle" -> node.remove("title");
            case "unknownField" -> node.put("enterpriseOrgId","spoofed");
            case "fractionalQuantity" -> node.put("quantity",1.5);
        }
        assertEquals("SCHEMA_VALIDATION",assertThrows(DemandFailure.class,()->DemandJson.request(node,DemandInput.class)).code());
    }
}
