package com.tamdaptit.rentalmanagement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RoomAmenityId implements Serializable {

    @Column(name = "room_id", length = 36)
    String roomId;

    @Column(name = "amenity_id", length = 36)
    String amenityId;
}