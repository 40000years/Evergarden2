package com.example.voidscape.pack;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import com.example.advancemagic.pack.delivery.MirrorLookup;
import com.example.advancemagic.pack.delivery.PackMirror;
import com.example.advancemagic.pack.delivery.ResourcePackManager;
import com.example.advancemagic.pack.delivery.RequiredPackSettings;
import com.example.advancemagic.pack.delivery.ServerPortPackHost;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.JarFile;

public final class ResourcePackService implements Listener, AutoCloseable {
    public static final UUID PACK_ID=UUID.fromString("c8f2b94e-4a35-4d1b-9b67-0d2a6ef4f821");
    /** Previous managed release URL, retained for identifying older installs. */
    public static final String DEFAULT_CDN_URL = "https://raw.githubusercontent.com/40000years/Evergarden2/0e6a864979528b50e00e48baa98e267993803ba4/evergarden/dist/evergarden-java.zip";
    private static final List<String> FILES=List.of("evergarden-java.zip","evergarden-bedrock.mcpack",
            "geyser-mappings.json","pack-hashes.json");
    private final JavaPlugin plugin;
    private final Map<UUID,String> statuses=new HashMap<>();
    private PackHttpServer http;
    private ResourcePackManager.Client delivery;
    private byte[] extractedJavaPack;
    private String bedrockPackInfo="Bedrock pack not extracted";
    private String sha1="",failure="",geyserStatus="External Geyser: copy files from resource-packs/ manually.";
    /** The public copy of the embedded pack, offered before this server's own host once verified. */
    private final MirrorLookup mirror;
    public ResourcePackService(JavaPlugin plugin){this.plugin=plugin;mirror=new MirrorLookup(plugin,"evergarden",30,p->offer(p,false));}

    /** Runs in onLoad, before Geyser reads mappings and packs. */
    public void extract() throws IOException {
        Path output=plugin.getDataFolder().toPath().resolve("resource-packs");
        for(String name:FILES) {
            try(InputStream input=plugin.getResource("resource-packs/"+name)) {
                if(input==null)throw new IOException("Missing embedded asset: "+name+". Rebuild with evergarden/build.ps1.");
                byte[] bytes=input.readAllBytes();
                if(name.equals("evergarden-java.zip"))extractedJavaPack=bytes;
                writeChanged(output.resolve(name),bytes);
            }
        }
        try(var zip=new java.util.zip.ZipFile(output.resolve("evergarden-bedrock.mcpack").toFile());
            var reader=new InputStreamReader(zip.getInputStream(zip.getEntry("manifest.json")),java.nio.charset.StandardCharsets.UTF_8)) {
            var header=new com.google.gson.JsonParser().parse(reader).getAsJsonObject().getAsJsonObject("header");
            bedrockPackInfo="Bundled Bedrock version: "+header.get("version")+" | UUID: "+header.get("uuid").getAsString();
        }
        if(!plugin.getConfig().getBoolean("resource-pack.geyser.auto-install",true)) {
            geyserStatus="Bedrock auto-install disabled; copy bundled pack + mappings to the active Geyser instance.";
            return;
        }
        Path plugins=plugin.getDataFolder().toPath().toAbsolutePath().getParent();
        Path geyser=plugins.resolve("Geyser-Spigot");
        boolean present=Files.isDirectory(geyser);
        if(!present) {
            Path altGeyser=plugins.resolve("Geyser");
            if(Files.isDirectory(altGeyser)){geyser=altGeyser;present=true;}
        }
        if(!present)try(var jars=Files.list(plugins)) {
            for(Path path:jars.filter(p->p.getFileName().toString().endsWith(".jar")).toList()) {
                try(JarFile jar=new JarFile(path.toFile())) {
                    var entry=jar.getJarEntry("plugin.yml");
                    if(entry!=null)try(var in=jar.getInputStream(entry)) {
                        String descriptor=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                        if(descriptor.matches("(?s).*\\bname:\\s*['\"]?Geyser-Spigot['\"]?\\s.*")){present=true;break;}
                    }
                }catch(IOException ignored){/* Other plugins may be being updated. */}
            }
        }
        if(present) {
            int cleaned=GeyserPackCleanup.clean(geyser,output.resolve("geyser-mappings.json"),
                output.resolve("evergarden-bedrock.mcpack"),"voidscape.json","voidscape-bedrock.mcpack");
            if(cleaned>0)plugin.getLogger().info("Cleaned "+cleaned+" duplicate Geyser files; originals saved in plugin-pack-backups.");
            writeChanged(geyser.resolve("packs/voidscape-bedrock.mcpack"),Files.readAllBytes(output.resolve("evergarden-bedrock.mcpack")));
            writeChanged(geyser.resolve("custom_mappings/voidscape.json"),Files.readAllBytes(output.resolve("geyser-mappings.json")));
            geyserStatus="Bedrock files copied to "+geyser.toAbsolutePath()+" (restart Geyser and reconnect to use).";
            plugin.getLogger().info(geyserStatus);
        }
    }
    private static void writeChanged(Path target,byte[] bytes) throws IOException {
        if(Files.exists(target)&&Arrays.equals(Files.readAllBytes(target),bytes))return;
        Files.createDirectories(target.getParent());
        Path staged=Files.createTempFile(target.getParent(),"evergarden-",".tmp");
        try {
            Files.write(staged,bytes);
            try {Files.move(staged,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(staged,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(staged);}
    }
    public void start() {
        if(RequiredPackSettings.upgrade(plugin.getConfig(),true)){
            plugin.saveConfig();plugin.getLogger().info("Evergarden and Judge Java resource packs are now required; existing download hosts are preserved.");
        }
        delivery=ResourcePackManager.connect(plugin,PACK_ID);
        try {
            byte[] pack=extractedJavaPack;
            if(pack==null)try(InputStream input=plugin.getResource("resource-packs/evergarden-java.zip")) {
                if(input==null)throw new IOException("Embedded Java pack is missing");
                pack=input.readAllBytes();
            }
            sha1=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(pack));
            if(migratePackConfig(plugin.getConfig(),sha1)) {
                plugin.saveConfig();
                plugin.getLogger().info("Updated previous official Evergarden pack settings to the current embedded pack; private CDNs and public host settings are preserved.");
            }
            String configuredUrl=plugin.getConfig().getString("resource-pack.url","").trim();
            if(configuredUrl.isBlank()&&plugin.getConfig().getBoolean("resource-pack.enabled",true)) {
                mirror.start(PackMirror.fingerprint(pack),mirrors());
            }
            boolean mainNeedsLocal=plugin.getConfig().getBoolean("resource-pack.enabled",true)
                &&configuredUrl.isBlank()&&plugin.getConfig().getBoolean("resource-pack.host.enabled",false);
            boolean judgeNeedsLocal=plugin.getConfig().getBoolean("world-boss.visuals.pack.enabled",true);
            if(!mainNeedsLocal&&!judgeNeedsLocal) {
                if(!configuredUrl.isBlank())plugin.getLogger().info("Evergarden pack configured to use external CDN: "+configuredUrl);
                else if(plugin.getConfig().getBoolean("resource-pack.enabled",true)) {
                    failure="The bundled pack host is disabled and no external pack URL is configured.";
                    plugin.getLogger().warning(failure);
                }
                return;
            }
            if(sharesServerPort())try {
                http=new PackHttpServer(plugin,pack,sha1);
                plugin.getLogger().info("Evergarden and Judge Java packs served through the Minecraft port; no extra TCP port is needed. /evergarden pack shows status.");
                return;
            }catch(IOException e){plugin.getLogger().warning(e.getMessage()+"; using the separate pack port instead.");}
            http=new PackHttpServer(plugin.getConfig().getString("resource-pack.host.bind","0.0.0.0"),
                    plugin.getConfig().getInt("resource-pack.host.port",8188),pack,sha1);
            plugin.getLogger().info("Shared Evergarden and Judge Java packs served on TCP "+http.port()+"; /evergarden pack shows status.");
        }catch(IOException|GeneralSecurityException|IllegalArgumentException e) {
            failure=e.getMessage();plugin.getLogger().warning("Pack host could not start: "+failure);
        }finally{extractedJavaPack=null;}
    }
    static boolean migratePackConfig(org.bukkit.configuration.file.FileConfiguration config,String bundledSha1) {
        String url=config.getString("resource-pack.url","").trim();
        // A shipped URL for an older release does not contain this JAR's new
        // item/effect art. Switch only our managed URLs to the actual bundle.
        // Configured ports, bind address, proxy and administrator CDNs remain
        // unchanged. An unavailable embedded pack must not trigger migration.
        if(bundledSha1==null||!bundledSha1.matches("[a-fA-F0-9]{40}"))return false;
        if(!url.matches("https://raw\\.githubusercontent\\.com/40000years/(?:Afterdeath|Evergarden2)/(?:DEV|main|[a-fA-F0-9]{7,40})/evergarden/dist/evergarden-java\\.zip"))return false;
        config.set("resource-pack.url","");
        config.set("resource-pack.host.enabled",true);
        config.set("resource-pack.sha1","");
        return true;
    }
    static String packDigest(org.bukkit.configuration.file.FileConfiguration config,String bundledSha1) {
        // The embedded HTTP server serves these exact bytes. A saved digest
        // from a former CDN must never be paired with that content-addressed URL.
        return config.getString("resource-pack.url","").isBlank()?bundledSha1
                :config.getString("resource-pack.sha1","").trim();
    }
    private boolean bedrock(Player player) {
        for(String name:List.of("org.geysermc.floodgate.api.FloodgateApi","org.geysermc.geyser.api.GeyserApi")) {
            try {
                Class<?> type=Class.forName(name);boolean floodgate=name.contains("floodgate");
                Object api=type.getMethod(floodgate?"getInstance":"api").invoke(null);
                if((boolean)type.getMethod(floodgate?"isFloodgatePlayer":"isBedrockPlayer",UUID.class).invoke(api,player.getUniqueId()))return true;
            }catch(ReflectiveOperationException|LinkageError ignored){}
        }
        return false;
    }
    public String url(Player player) {
        String external=plugin.getConfig().getString("resource-pack.url","").trim();
        if(!external.isEmpty())return validateUrl(external);
        if(!plugin.getConfig().getBoolean("resource-pack.host.enabled",false)||http==null)return "";
        return urlForPath(http.path(),player,"","");
    }
    /** Public copy templates shared by the relic and Judge packs; [] serves only from this server. */
    public List<String> mirrors(){return plugin.getConfig().getStringList("resource-pack.mirrors");}
    /** Register an immutable addon ZIP beside the bundled Evergarden ZIP. */
    public String publishAddition(byte[] bytes,String digest) {
        return http==null?"":http.addPack(bytes,digest);
    }
    public int hostPort(){return http==null?-1:http.port();}
    public String hostLabel(){return http==null?"off":http.sharesServerPort()?"Minecraft port":"TCP "+http.port();}
    /** A reverse proxy (public-url) keeps pointing at the separate pack port. */
    private boolean sharesServerPort() {
        return plugin.getConfig().getBoolean("resource-pack.host.use-server-port",true)
            &&plugin.getConfig().getString("resource-pack.host.public-url","").isBlank();
    }
    /** Build a URL for an addon using the same public listener as the main pack. */
    public String urlForPath(String path,Player player,String publicUrl,String publicHost) {
        if(http==null||path==null||!path.startsWith("/evergarden/"))return "";
        String base=publicUrl==null?"":publicUrl.trim();
        if(base.isBlank())base=plugin.getConfig().getString("resource-pack.host.public-url","").trim();
        if(!base.isBlank())return validateUrl(base.replaceAll("/+$","")+path);
        String host=publicHost==null?"":publicHost.trim();
        if(host.isBlank())host=plugin.getConfig().getString("resource-pack.host.public-host","").trim();
        if(host.isBlank()&&player!=null) {
            InetSocketAddress address=player.getVirtualHost();
            if(address!=null)host=address.getHostString();
        }
        if(host.isBlank())return "";
        int port=http.sharesServerPort()?ServerPortPackHost.port(player):http.port();
        try{return validateUrl(new URI("http",null,host,port,path,null,null).toASCIIString());}
        catch(URISyntaxException e){return "";}
    }
    private String validateUrl(String value) {
        try {
            URI uri=URI.create(value);
            if(!List.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)return "";
            return uri.toASCIIString();
        }catch(IllegalArgumentException e){return "";}
    }
    public void offer(Player player) {
        offer(player,false);
    }
    public void resend(Player player) {
        offer(player,true);
    }
    private void offer(Player player,boolean force) {
        if(!plugin.getConfig().getBoolean("resource-pack.enabled",true)||bedrock(player))return;
        // While the public copy is being checked the batch waits, so it never goes out with only this server's address.
        if(mirror.defer(player,delivery))return;
        List<String> urls=new ArrayList<>(),hashes=new ArrayList<>();
        // A verified public copy first, then this server's own host as the fallback.
        PackMirror.Copy copy=mirror.copy();
        if(copy!=null&&plugin.getConfig().getString("resource-pack.url","").isBlank()){urls.add(copy.url());hashes.add(copy.sha1());}
        String url=url(player);
        if(!url.isEmpty()){urls.add(url);hashes.add(packDigest(plugin.getConfig(),sha1));}
        if(urls.isEmpty()) {statuses.put(player.getUniqueId(),"NOT_OFFERED: configure the public host/URL");unavailable(player);return;}
        if(!hashes.stream().allMatch(h->h.matches("[a-fA-F0-9]{40}"))) {statuses.put(player.getUniqueId(),"INVALID_SHA1");unavailable(player);return;}
        try {
            if(delivery!=null&&delivery.offer(player,urls,hashes,"Evergarden: relics, keys and sanctuary creatures",
                    plugin.getConfig().getBoolean("resource-pack.required",true),force))
                statuses.put(player.getUniqueId(),"QUEUED");
        }catch(IllegalArgumentException e){statuses.put(player.getUniqueId(),"INVALID_URL");unavailable(player);}
    }
    private void unavailable(Player player){if(delivery!=null&&plugin.getConfig().getBoolean("resource-pack.required",true))delivery.unavailable(player);}
    @EventHandler public void join(PlayerJoinEvent event) {
        offer(event.getPlayer());
    }
    @EventHandler public void status(PlayerResourcePackStatusEvent event) {
        if(!PACK_ID.equals(event.getID()))return;
        String status=event.getStatus().name();statuses.put(event.getPlayer().getUniqueId(),status);
        // The coordinator is already sending it again (from the next address, or on its own).
        if(delivery!=null&&List.of("QUEUED","OFFERED","ACCEPTED","DOWNLOADED").contains(delivery.status(event.getPlayer())))return;
        if(status.startsWith("FAILED")||status.equals("INVALID_URL")||status.equals("DISCARDED")) {
            plugin.getLogger().warning("Evergarden pack for "+event.getPlayer().getName()+": "+status+" from "+url(event.getPlayer())+". Check TCP port/public URL and /evergarden pack; Pack HTTP lines show whether the download reached this server.");
            event.getPlayer().sendMessage(ChatColor.YELLOW+(plugin.getConfig().getBoolean("resource-pack.required",true)
                ?"Evergarden textures are required to play. Allow server resource packs and reconnect; ask an admin if loading fails."
                :"Evergarden textures could not load. Items still work; ask an admin to check /evergarden pack."));
        }
    }
    @EventHandler public void quit(PlayerQuitEvent event){statuses.remove(event.getPlayer().getUniqueId());mirror.forget(event.getPlayer());}
    public void describe(CommandSender sender) {
        sender.sendMessage(ChatColor.LIGHT_PURPLE+"Evergarden resource pack");
        sender.sendMessage("Enabled: "+plugin.getConfig().getBoolean("resource-pack.enabled",true)+" | Required: "+plugin.getConfig().getBoolean("resource-pack.required",true)+" | Bundled host: "+hostLabel());
        sender.sendMessage("Bundled SHA-1: "+sha1);
        String url=url(sender instanceof Player p?p:null);sender.sendMessage("URL: "+(url.isEmpty()?"unavailable":url));
        sender.sendMessage("Public copy: "+(mirror.copy()!=null?mirror.copy().url():mirror.fingerprint().isEmpty()?"not used"
            :(mirror.pending()?"checking ":"none verified for ")+mirror.fingerprint().substring(0,12)));
        sender.sendMessage(geyserStatus);
        sender.sendMessage(bedrockPackInfo);
        if(!failure.isEmpty())sender.sendMessage(ChatColor.RED+failure);
        if(sender instanceof Player p){
            sender.sendMessage("Your pack: "+clientStatus(p));
            if(delivery!=null)sender.sendMessage(delivery.describe(p));
        }
        else for(Player player:Bukkit.getOnlinePlayers()) {
            sender.sendMessage(player.getName()+": "+(bedrock(player)?"BEDROCK: delivered by Geyser; Java status unavailable":clientStatus(player)));
            if(!bedrock(player))sender.sendMessage("  URL: "+url(player));
        }
        sender.sendMessage("Files: plugins/Evergarden/resource-packs/ | Retry: /evergarden pack resend");
    }
    private String clientStatus(Player player){
        String status=delivery==null?"NOT_OFFERED":delivery.status(player);
        return status.equals("NOT_OFFERED")?statuses.getOrDefault(player.getUniqueId(),"not offered (or Bedrock)"):status;
    }
    @Override public void close(){if(delivery!=null){delivery.close();delivery=null;}if(http!=null){http.close();http=null;}extractedJavaPack=null;statuses.clear();}
}
