package com.tamdaptit.rentalmanagement.review.entity;

import java.time.LocalDateTime;

import com.tamdaptit.rentalmanagement.identity.entity.User;
import com.tamdaptit.rentalmanagement.rental.entity.RentalAgreement;

/** Common review fields; each review keeps its existing independent table. */
public interface ReviewEntry {
    String getId();

    RentalAgreement getRentalAgreement();

    User getTenant();

    Boolean getHidden();

    User getHiddenBy();

    LocalDateTime getHiddenAt();

    String getHiddenReason();

    void setHidden(Boolean hidden);

    void setHiddenBy(User user);

    void setHiddenAt(LocalDateTime at);

    void setHiddenReason(String reason);
}
