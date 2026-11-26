package com.tamdaptit.rentalmanagement.property.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateFloorRequest(
    @Min(0) Integer floorNumber,
    @NotEmpty List<@NotNull @Valid CreateRoomItemRequest> rooms
) {}
