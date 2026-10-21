package com.tamdaptit.rentalmanagement.controller;

import com.tamdaptit.rentalmanagement.common.*;
import com.tamdaptit.rentalmanagement.dto.address.*;
import com.tamdaptit.rentalmanagement.service.WardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Administrative units")
public class WardController {
    private final WardService service;

    @GetMapping("/provinces")
    public ApiResponse<List<ProvinceResponse>> provinces() {
        return ApiResponse.success(service.provinces());
    }

    @GetMapping("/wards")
    public ApiResponse<PageResponse<WardResponse>> list(
            @RequestParam(required = false) String provinceId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(provinceId, q, page, size));
    }

    @GetMapping("/wards/{id}")
    public ApiResponse<WardResponse> get(@PathVariable String id) {
        return ApiResponse.success(service.get(id));
    }

    @PostMapping("/wards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('WARD_WRITE')")
    public ApiResponse<WardResponse> create(@RequestBody @Valid WardRequest request) {
        return ApiResponse.<WardResponse>builder().code(201).message("Created").data(service.create(request)).build();
    }

    @PutMapping("/wards/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('WARD_WRITE')")
    public ApiResponse<WardResponse> update(@PathVariable String id, @RequestBody @Valid WardRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @DeleteMapping("/wards/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('WARD_DELETE')")
    public ApiResponse<Void> delete(@PathVariable String id) {
        service.delete(id);
        return ApiResponse.success("Deleted", null);
    }
}
