import com.example.advancemagic.pack.PackHttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.security.*;
import java.time.Duration;
import java.util.*;

public final class PackHttpChecks {
    static int checks;
    static void check(boolean value,String label){if(!value)throw new AssertionError(label);checks++;}
    public static void main(String[] args) throws Exception {
        byte[] bytes=Files.readAllBytes(Path.of(args[0]));
        String sha1=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        int port;
        try(var client=HttpClient.newHttpClient();var server=new PackHttpServer("127.0.0.1",0,bytes,sha1)) {
            port=server.port();String base="http://127.0.0.1:"+port;URI uri=URI.create(base+server.path());
            var get=client.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).build(),HttpResponse.BodyHandlers.ofByteArray());
            check(get.statusCode()==200&&Arrays.equals(get.body(),bytes),"download is byte-identical to release pack");
            check(get.headers().firstValue("Content-Type").orElse("").equals("application/zip"),"ZIP content type");
            var head=client.send(HttpRequest.newBuilder(uri).method("HEAD",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofByteArray());
            check(head.statusCode()==200&&head.body().length==0&&head.headers().firstValueAsLong("Content-Length").orElse(-1)==bytes.length,"HEAD supports length without body");
            var cached=client.send(HttpRequest.newBuilder(uri).header("If-None-Match","\""+sha1+"\"").build(),HttpResponse.BodyHandlers.discarding());
            check(cached.statusCode()==304,"cache hit");
            for(String path:List.of("/","/config.yml","/advance-magic/../config.yml",server.path()+"/extra","/advance-magic/%2e%2e/config.yml")) {
                var response=client.send(HttpRequest.newBuilder(URI.create(base+path)).build(),HttpResponse.BodyHandlers.discarding());
                check(response.statusCode()==404,"no filesystem exposure: "+path);
            }
            var post=client.send(HttpRequest.newBuilder(uri).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding());
            check(post.statusCode()==405,"writes rejected");
            var requests=new ArrayList<java.util.concurrent.CompletableFuture<HttpResponse<byte[]>>>();
            for(int i=0;i<8;i++)requests.add(client.sendAsync(HttpRequest.newBuilder(uri).build(),HttpResponse.BodyHandlers.ofByteArray()));
            for(var request:requests)check(Arrays.equals(request.get().body(),bytes),"simultaneous joins download intact pack");
        }
        try(var rebound=new PackHttpServer("127.0.0.1",port,bytes,sha1)){check(rebound.port()==port,"disable releases listening port");}
        System.out.println("PASS: "+checks+" HTTP resource pack assertions");
    }
}
