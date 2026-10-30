package com.tamdaptit.rentalmanagement.property.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record CreateFloorRequest(@Min(0) Integer floorNumber,
        @NotEmpty List<@NotNull @Valid CreateRoomItemRequest> rooms) {}
