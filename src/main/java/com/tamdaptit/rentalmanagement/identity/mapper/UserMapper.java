package com.tamdaptit.rentalmanagement.identity.mapper;

import com.tamdaptit.rentalmanagement.auth.dto.request.RegisterRequest;
import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
public interface UserMapper {

    /**
     * Chuyen {@link RegisterRequest} sang {@link User}.
     * Mat khau duoc encode rieng o service, khong map truc tiep tu request.
     */
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "email", source = "email")
    @Mapping(target = "phone", source = "phone")
    @Mapping(target = "fullName", source = "fullName")
    @Mapping(target = "citizenId", source = "citizenId")
    @Mapping(target = "status", expression = "java(com.tamdaptit.rentalmanagement.identity.entity.enums.UserStatus.ACTIVE)")
    User toUser(RegisterRequest request);
}
