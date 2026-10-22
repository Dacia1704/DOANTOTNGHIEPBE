package com.tamdaptit.rentalmanagement.identity.service;

public interface UserDeletionGuard {
    boolean hasReferences(String userId);

}
