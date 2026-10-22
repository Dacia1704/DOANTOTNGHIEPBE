package com.tamdaptit.rentalmanagement.service;


public interface UserDeletionGuard {
    boolean hasReferences(String userId);

}
