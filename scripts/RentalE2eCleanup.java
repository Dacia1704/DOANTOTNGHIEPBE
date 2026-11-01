import com.cloudinary.Cloudinary;
import java.sql.*;
import java.util.*;

/** Source-file utility: only drops a generated local test schema and its own authenticated contract assets. */
class RentalE2eCleanup {
    public static void main(String[] args) throws Exception {
        String schema=System.getenv("RENTAL_E2E_SCHEMA");
        if(schema==null||!schema.matches("rental_(e2e|context)_[a-f0-9]{32}"))throw new IllegalArgumentException("A generated test schema is required");
        String url="jdbc:mysql://localhost:3306/"+schema+"?allowPublicKeyRetrieval=true&useSSL=false";
        try(var connection=DriverManager.getConnection(url,"root",System.getenv("RENTAL_LOCAL_DB_PASSWORD"));var statement=connection.createStatement()) {
            List<String> ids=new ArrayList<>();
            try(var rows=statement.executeQuery("SELECT public_id FROM rental_pdf_assets")){while(rows.next())ids.add(rows.getString(1));}
            if(!ids.isEmpty()) {
                var cloudinary=new Cloudinary(Map.of("cloud_name",System.getenv("RENTAL_TEST_CLOUD_NAME"),"api_key",System.getenv("RENTAL_TEST_CLOUD_KEY"),"api_secret",System.getenv("RENTAL_TEST_CLOUD_SECRET"),"secure",true));
                for(String id:ids) {
                    if(!id.matches("rental-management/contracts/[a-f0-9-]{36}/[a-f0-9-]{36}\\.pdf"))throw new IllegalStateException("Unexpected asset ID in test schema");
                    var result=cloudinary.uploader().destroy(id,Map.of("resource_type","raw","type","authenticated","invalidate",true));
                    if(!Set.of("ok","not found").contains(result.get("result")))throw new IllegalStateException("Cloudinary cleanup did not succeed");
                }
            }
            statement.execute("DROP DATABASE `"+schema+"`");
            System.out.println("Removed isolated test schema and "+ids.size()+" private test assets.");
        }
    }
}
