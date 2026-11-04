package com.tamdaptit.rentalmanagement.review;

import com.cloudinary.Cloudinary;
import com.tamdaptit.rentalmanagement.report.service.impl.CloudinaryReportEvidenceStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in transport test: only synthetic pixels; always deletes its own random asset. */
@EnabledIfEnvironmentVariable(named="REVIEW_REPORT_TEST_CLOUDINARY",matches="true")
class CloudinaryReportEvidenceStorageTest {
    @Test void authenticatedImageRoundTripsAndUnsignedUrlsDoNotServeBytes() throws Exception {
        var cloudinary=new Cloudinary(Map.of("cloud_name",System.getenv("REVIEW_REPORT_TEST_CLOUD_NAME"),
            "api_key",System.getenv("REVIEW_REPORT_TEST_CLOUD_KEY"),
            "api_secret",System.getenv("REVIEW_REPORT_TEST_CLOUD_SECRET"),"secure",true));
        var storage=new CloudinaryReportEvidenceStorage(cloudinary);
        String id="rental-management/report-evidences/test/"+UUID.randomUUID()+".png";
        var out=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);
        byte[] pixels=out.toByteArray();
        try {
            storage.upload(id,pixels);assertArrayEquals(pixels,storage.download(id));
            for(String type:List.of("authenticated","upload")) {
                String unsigned=cloudinary.url().resourceType("raw").type(type).generate(id);
                var result=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(unsigned))
                    .timeout(Duration.ofSeconds(20)).GET().build(),HttpResponse.BodyHandlers.discarding());
                assertTrue(Set.of(401,403,404).contains(result.statusCode()),"Unsigned/public evidence URL must deny access");
            }
        } finally {storage.delete(id);}
    }
}
