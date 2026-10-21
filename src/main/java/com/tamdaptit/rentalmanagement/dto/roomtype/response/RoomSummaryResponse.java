package com.tamdaptit.rentalmanagement.dto.roomtype.response;

import com.tamdaptit.rentalmanagement.entity.enums.RoomRentalStatus;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomSummaryResponse {

    String id;
    BigDecimal price;
    BigDecimal areaM2;
    Integer maxOccupants;
    String addressLine;
    String provinceName;
    String wardName;
    RoomRentalStatus rentalStatus;
}
