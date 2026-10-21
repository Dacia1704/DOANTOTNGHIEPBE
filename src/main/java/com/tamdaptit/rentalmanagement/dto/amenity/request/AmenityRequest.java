package com.tamdaptit.rentalmanagement.dto.amenity.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AmenityRequest {

    @NotBlank(message = "Tên tiện ích không được để trống")
    @Size(max = 100, message = "Tên tiện ích không được vượt quá 100 ký tự")
    String name;

    @Size(max = 255, message = "Icon không được vượt quá 255 ký tự")
    String icon;
}
