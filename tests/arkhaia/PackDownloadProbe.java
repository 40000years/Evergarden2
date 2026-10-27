import com.example.voidscape.VoidscapePlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.ZipInputStream;

/**
 * Downloads the three Java packs exactly as a client would after joining: from the URLs the plugins offer,
 * through the Minecraft port, all at once, then again one by one. Each body must hash to the SHA-1 in its
 * content-addressed URL and be a ZIP with pack.mcmeta. Waits for ModelEngine to merge its rig into Judge first.
 */
public final class PackDownloadProbe extends JavaPlugin {
    final StringBuilder report=new StringBuilder();
    void line(String s){report.append(s).append('\n');getLogger().info(s);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,this::collect,20*45);}
    void collect(){
        try{
            var garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
            var magic=(com.example.advancemagic.AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
            for(var cfg:List.of(garden.getConfig(),magic.getConfig()))cfg.set("resource-pack.host.public-host","127.0.0.1");
            Map<String,String> urls=new LinkedHashMap<>();
            urls.put("advance-magic",magic.packs().url(null));
            urls.put("evergarden",garden.packs().url(null));
            urls.put("judge",garden.judgePack().url(null));
            line("ModelEngine present: "+(Bukkit.getPluginManager().getPlugin("ModelEngine")!=null));
            urls.forEach((k,v)->line("url "+k+" = "+v));
            CompletableFuture.runAsync(()->{
                try{
                    var http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
                    for(String round:List.of("together","one-by-one")){
                        Map<String,Future<String>> results=new LinkedHashMap<>();
                        var pool=Executors.newFixedThreadPool(round.equals("together")?3:1);
                        for(var e:urls.entrySet())results.put(e.getKey(),pool.submit(()->fetch(http,e.getValue())));
                        for(var e:results.entrySet())line(round+" "+e.getKey()+": "+e.getValue().get());
                        pool.shutdown();
                    }
                    finish(report.toString().contains("FAIL")?"FAIL see pack report":"PASS packs");
                }catch(Exception e){line("FAIL "+e);finish("FAIL "+e);}
            });
        }catch(Exception e){line("FAIL "+e);finish("FAIL "+e);}
    }
    String fetch(HttpClient http,String url){
        try{
            if(url==null||url.isEmpty())return "FAIL no url";
            long t=System.nanoTime();
            var request=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60))
                .header("User-Agent","Minecraft Java/26.3").header("X-Minecraft-Username","Probe")
                .header("X-Minecraft-UUID","00000000000000000000000000000001").header("X-Minecraft-Version","26.3")
                .header("X-Minecraft-Version-ID","26.3").header("X-Minecraft-Pack-Format","80").GET().build();
            var response=http.send(request,HttpResponse.BodyHandlers.ofByteArray());
            byte[] body=response.body();
            String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(body));
            String expected=url.substring(url.lastIndexOf('/')+1).replace(".zip","");
            boolean mcmeta=false;int entries=0;
            try(var zip=new ZipInputStream(new java.io.ByteArrayInputStream(body))){for(var z=zip.getNextEntry();z!=null;z=zip.getNextEntry()){entries++;if(z.getName().equals("pack.mcmeta"))mcmeta=true;}}
            boolean ok=response.statusCode()==200&&sha.equalsIgnoreCase(expected)&&mcmeta;
            return (ok?"OK":"FAIL")+" http "+response.statusCode()+" "+body.length+" bytes "+entries+" entries mcmeta="+mcmeta
                +" sha "+(sha.equalsIgnoreCase(expected)?"matches":"MISMATCH "+sha+" vs "+expected)+String.format(Locale.ROOT," %.1fs",(System.nanoTime()-t)/1e9);
        }catch(Exception e){return "FAIL "+e;}
    }
    void finish(String result){
        try{Files.writeString(Path.of("pack-report.txt"),report.toString());Files.writeString(Path.of("arkhaia-result.txt"),result);}catch(Exception ignored){}
        Bukkit.getScheduler().runTask(this,()->Bukkit.shutdown());
    }
}
