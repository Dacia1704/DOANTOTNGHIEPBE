package com.tamdaptit.rentalmanagement.rental.service;
public interface RentalPdfStorage {
    void upload(String publicId,byte[] bytes) throws Exception;
    byte[] download(String publicId) throws Exception;
    void delete(String publicId) throws Exception;
}
