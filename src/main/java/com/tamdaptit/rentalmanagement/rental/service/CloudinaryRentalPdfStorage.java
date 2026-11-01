package com.tamdaptit.rentalmanagement.rental.service;

import com.cloudinary.Cloudinary;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

@Service @RequiredArgsConstructor
public class CloudinaryRentalPdfStorage implements RentalPdfStorage {
    private final Cloudinary cloudinary;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
    public void upload(String publicId,byte[] bytes) throws Exception {
        var result=cloudinary.uploader().upload(bytes,Map.of("public_id",publicId,"resource_type","raw","type","authenticated","overwrite",false));
        if(!publicId.equals(result.get("public_id"))) throw new java.io.IOException("Unexpected storage asset identifier");
    }
    public byte[] download(String publicId) throws Exception {
        String signed=cloudinary.privateDownload(publicId,null,Map.of("resource_type","raw","type","authenticated","expires_at",System.currentTimeMillis()/1000+60));
        var response=http.send(HttpRequest.newBuilder(URI.create(signed)).timeout(Duration.ofSeconds(30)).GET().build(),HttpResponse.BodyHandlers.ofInputStream());
        try(var body=response.body()) {
            if(response.statusCode()!=200) throw new java.io.IOException("Private document unavailable");
            byte[] bytes=body.readNBytes(10*1024*1024+1);
            if(bytes.length>10*1024*1024) throw new java.io.IOException("Document exceeds size limit");
            return bytes;
        }
    }
    public void delete(String publicId) throws Exception {
        var result=cloudinary.uploader().destroy(publicId,Map.of("resource_type","raw","type","authenticated","invalidate",true));
        if(!"ok".equals(result.get("result"))&&!"not found".equals(result.get("result"))) throw new java.io.IOException("Document cleanup failed");
    }
}
