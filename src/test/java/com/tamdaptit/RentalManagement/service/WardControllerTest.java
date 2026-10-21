package com.tamdaptit.RentalManagement.service;

import com.tamdaptit.rentalmanagement.controller.WardController;
import com.tamdaptit.rentalmanagement.dto.address.*;
import com.tamdaptit.rentalmanagement.exception.GlobalExceptionHandler;
import com.tamdaptit.rentalmanagement.service.WardService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class WardControllerTest {
    private final WardService service = mock(WardService.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new WardController(service))
            .setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test void validatesRequestBeforeCreatingWard() throws Exception {
        mvc.perform(post("/api/wards").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"1\",\"name\":\" \",\"provinceId\":\"\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test void createsWardWithHttp201() throws Exception {
        var request = new WardRequest("00004", "Ba Dinh", "province-id");
        when(service.create(request)).thenReturn(
                new WardResponse("ward-id", "00004", "Ba Dinh", "province-id", "01", "Ha Noi"));
        mvc.perform(post("/api/wards").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"00004\",\"name\":\"Ba Dinh\",\"provinceId\":\"province-id\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.data.code").value("00004"));
    }

    @Test void mapsGetPutAndDeleteToWardId() throws Exception {
        var request = new WardRequest("00004", "Ba Dinh", "province-id");
        var response = new WardResponse("ward-id", "00004", "Ba Dinh", "province-id", "01", "Ha Noi");
        when(service.get("ward-id")).thenReturn(response);
        when(service.update("ward-id", request)).thenReturn(response);
        mvc.perform(get("/api/wards/ward-id")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("ward-id"));
        mvc.perform(put("/api/wards/ward-id").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"00004\",\"name\":\"Ba Dinh\",\"provinceId\":\"province-id\"}"))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/wards/ward-id")).andExpect(status().isOk());
        verify(service).update("ward-id", request);
        verify(service).delete("ward-id");
    }
}
