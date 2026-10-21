package com.tamdaptit.rentalmanagement.mapper;

import com.tamdaptit.rentalmanagement.dto.auth.request.RegisterRequest;
import com.tamdaptit.rentalmanagement.entity.User;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE
)
public interface UserMapper {

    /**
     * Chuyen RegisterRequest -> User.
     * Password duoc encode rieng o service (khong map truc tiep).
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "email", source = "email")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "fullName", source = "fullName")
    @Mapping(target = "citizenId", source = "citizenId")
    @Mapping(target = "status", constant = "ACTIVE")
    User toUser(RegisterRequest request);
}
