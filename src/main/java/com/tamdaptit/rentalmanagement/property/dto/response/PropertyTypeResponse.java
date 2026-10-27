package com.tamdaptit.rentalmanagement.property.dto.response;

import java.time.LocalDateTime;

public record PropertyTypeResponse(String id, String name, String description,
        LocalDateTime createdAt, LocalDateTime updatedAt) {}
