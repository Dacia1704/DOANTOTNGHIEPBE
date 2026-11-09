package com.tamdaptit.rentalmanagement.rental;
import com.tamdaptit.rentalmanagement.rental.dto.request.*;
import com.tamdaptit.rentalmanagement.rental.service.impl.RentalServiceImpl;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.*;
class RentalValidationTest {
    @Test void jsonFractionsAreRejectedRatherThanTruncated() {
        var json=tools.jackson.databind.json.JsonMapper.builder().build();
        assertEquals(12,json.readValue("{\"durationMonths\":12}",RentalRequestCreateRequest.class).durationMonths());
        assertThrows(tools.jackson.databind.DatabindException.class,()->json.readValue("{\"durationMonths\":1.5}",RentalRequestCreateRequest.class));
        assertThrows(tools.jackson.databind.DatabindException.class,()->json.readValue("{\"occupantCount\":1.5}",RentalRequestCreateRequest.class));
    }
    @Test void monthEndAndLeapYearAreClampedBeforeSubtractingOneDay() {
        assertEquals(LocalDate.of(2027,2,27),RentalServiceImpl.endDate(LocalDate.of(2027,1,31),1));
        assertEquals(LocalDate.of(2028,2,28),RentalServiceImpl.endDate(LocalDate.of(2028,1,31),1));
        assertEquals(LocalDate.of(2027,2,27),RentalServiceImpl.endDate(LocalDate.of(2026,2,28),12));
    }
    @Test void privacyCitizenIdContactAndDurationAreValidated() {
        try(var factory=Validation.buildDefaultValidatorFactory()) {
            var v=factory.getValidator();
            var invalid=new RentalRequestCreateRequest("l","r","A","bad","not-email","123","abc",LocalDate.now(),37,0,"",false);
            var fields=v.validate(invalid).stream().map(e->e.getPropertyPath().toString()).toList();
            for(String field:new String[]{"tenantFullName","tenantPhone","tenantEmail","tenantCitizenId","tenantPermanentAddress","durationMonths","occupantCount","acceptedPrivacyNotice"})assertTrue(fields.contains(field),field);
            assertFalse(v.validate(new RentalAgreementConfirmRequest(false,"not-a-hash")).isEmpty());
        }
    }
}
