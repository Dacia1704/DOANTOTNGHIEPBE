package com.tamdaptit.rentalmanagement.report.service.impl;

import com.cloudinary.Cloudinary;
import com.tamdaptit.rentalmanagement.report.service.ReportEvidenceStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CloudinaryReportEvidenceStorage implements ReportEvidenceStorage {
    private final Cloudinary cloudinary;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    @Override
    public void upload(String publicId,byte[] bytes) throws Exception {
        // Raw authenticated delivery keeps the original validated image bytes.
        var result=cloudinary.uploader().upload(bytes,Map.of(
            "public_id",publicId,"resource_type","raw","type","authenticated","overwrite",false));
        if(!publicId.equals(result.get("public_id"))) throw new java.io.IOException("Unexpected evidence identifier");
    }
    @Override
    public byte[] download(String publicId) throws Exception {
        String signed=cloudinary.privateDownload(publicId,null,Map.of(
            "resource_type","raw","type","authenticated","expires_at",System.currentTimeMillis()/1000+60));
        var response=http.send(HttpRequest.newBuilder(URI.create(signed)).timeout(Duration.ofSeconds(30)).GET().build(),
            HttpResponse.BodyHandlers.ofInputStream());
        try(var input=response.body()) {
            if(response.statusCode()!=200) throw new java.io.IOException("Evidence unavailable");
            byte[] bytes=input.readNBytes(5*1024*1024+1);
            if(bytes.length==0 || bytes.length>5*1024*1024) throw new java.io.IOException("Invalid evidence size");
            return bytes;
        }
    }
    @Override
    public void delete(String publicId) throws Exception {
        var result=cloudinary.uploader().destroy(publicId,Map.of(
            "resource_type","raw","type","authenticated","invalidate",true));
        if(!"ok".equals(result.get("result")) && !"not found".equals(result.get("result")))
            throw new java.io.IOException("Evidence cleanup failed");
    }
}
