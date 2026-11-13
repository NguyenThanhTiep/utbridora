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
    @Test void legacyNullGpaCanBeReadButNoCriterionIsSilentlyDiscardedOrAcceptedFromBrowser() {
        var tree=(tools.jackson.databind.node.ObjectNode)JsonMapper.builder().build().readTree(DemandJson.write(input()));
        var required=(tools.jackson.databind.node.ObjectNode)tree.get("required");required.putNull("minGpa");
        assertEquals(input(),DemandJson.read(tree.toString(),DemandInput.class));
        assertEquals("SCHEMA_VALIDATION",assertThrows(DemandFailure.class,()->DemandJson.request(tree,DemandInput.class)).code());
        required.put("minGpa",new java.math.BigDecimal("3.0"));
        assertEquals("LEGACY_CRITERIA_REVIEW_REQUIRED",assertThrows(DemandFailure.class,()->DemandJson.read(tree.toString(),DemandInput.class)).code());
    }
    private DemandInput change(String title, int quantity, RequiredCriteria required, List<SkillCriterion> preferred) {
        var d=input();
        return new DemandInput(d.schoolOrgId(),d.termId(),title,quantity,d.description(),null,null,
            d.deadline(),required,preferred,d.academicCatalogVersion(),d.taxonomyVersion());
    }
    @Test void completeDraftAllowsNullableOptionalFields() {
        assertEquals(input(), DemandValidator.validate(input()));
    }
    @Test void rawRequestPreservesExactDecimalCriteriaAndHashDifferences() {
        var d=input();var exact=new java.math.BigDecimal("1000.12345678901234567890123456789");
        var required=new RequiredCriteria(d.required().majorCodes(),List.of(new SkillCriterion("JAVA",2,exact)),d.required().dateRange());
        var expected=change(d.title(),d.quantity(),required,List.of());
        String body=DemandJson.write(expected);
        var parsed=DemandJson.rawRequest(body,DemandInput.class);
        assertEquals(exact,parsed.required().skills().getFirst().minYearsPractice());
        assertEquals(DemandJson.criteriaHash(expected),DemandJson.criteriaHash(parsed));
        assertNotEquals(DemandJson.criteriaHash(parsed),DemandJson.criteriaHash(DemandJson.rawRequest(
            body.replace(exact.toString(),"1000.12345678901234567890123456788"),DemandInput.class)));
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
        assertEquals("SCHEMA_VALIDATION",assertThrows(DemandFailure.class,()->DemandJson.rawRequest(node.toString(),DemandInput.class)).code());
    }
}
