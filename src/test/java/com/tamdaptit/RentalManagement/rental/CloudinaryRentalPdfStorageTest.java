package com.tamdaptit.rentalmanagement.rental;
import com.cloudinary.Cloudinary;
import com.tamdaptit.rentalmanagement.rental.service.CloudinaryRentalPdfStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in live SDK check. Uploads synthetic bytes with a random ID and always deletes its own asset. */
@EnabledIfEnvironmentVariable(named="RENTAL_TEST_CLOUDINARY",matches="true")
class CloudinaryRentalPdfStorageTest {
    @Test void authenticatedRawFileRoundTripsAndAnonymousDeliveryIsDenied() throws Exception {
        var cloudinary=new Cloudinary(Map.of("cloud_name",System.getenv("RENTAL_TEST_CLOUD_NAME"),"api_key",System.getenv("RENTAL_TEST_CLOUD_KEY"),"api_secret",System.getenv("RENTAL_TEST_CLOUD_SECRET"),"secure",true));
        var storage=new CloudinaryRentalPdfStorage(cloudinary);
        String id="rental-management/contracts/test/"+UUID.randomUUID()+".pdf";
        byte[] bytes="%PDF-1.4\n% Synthetic storage transport check; no personal data.\n%%EOF".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        try {
            storage.upload(id,bytes);assertArrayEquals(bytes,storage.download(id));
            String anonymous=cloudinary.url().resourceType("raw").type("authenticated").generate(id);
            var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(anonymous)).timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.discarding());
            assertTrue(Set.of(401,403,404).contains(response.statusCode()),"Unsigned authenticated URL must not serve document bytes");
        } finally { storage.delete(id); }
    }
}
