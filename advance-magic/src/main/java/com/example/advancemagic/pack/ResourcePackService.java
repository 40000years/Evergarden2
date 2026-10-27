package com.example.advancemagic.pack;

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
    public static final UUID PACK_ID=UUID.fromString("3e8e5b71-0600-4a42-a678-483a7cce5fb0");
    private static final List<String> FILES=List.of("advance-magic-java.zip","advance-magic-bedrock.mcpack",
            "geyser-mappings.json","pack-hashes.json");
    private final JavaPlugin plugin;
    private final Map<UUID,String> statuses=new HashMap<>();
    private PackHttpServer http;
    private ResourcePackManager.Client delivery;
    private byte[] extractedJavaPack;
    private String sha1="",failure="",geyserStatus="External Geyser: copy files from resource-packs/ manually.";
    /** The public copy of the embedded pack, offered before this server's own host once verified. */
    private final MirrorLookup mirror;
    public ResourcePackService(JavaPlugin plugin){this.plugin=plugin;mirror=new MirrorLookup(plugin,"advance-magic",30,p->offer(p,false));}

    /** Runs in onLoad, before Geyser reads mappings and packs. */
    public void extract() throws IOException {
        Path output=plugin.getDataFolder().toPath().resolve("resource-packs");
        for(String name:FILES) {
            try(InputStream input=plugin.getResource("resource-packs/"+name)) {
                if(input==null)throw new IOException("Missing embedded asset: "+name+". Rebuild with advance-magic/build.ps1.");
                byte[] bytes=input.readAllBytes();
                if(name.equals("advance-magic-java.zip"))extractedJavaPack=bytes;
                writeChanged(output.resolve(name),bytes);
            }
        }
        if(!plugin.getConfig().getBoolean("resource-pack.geyser.auto-install",true)) {
            geyserStatus="Bedrock auto-install disabled; copy the pack and mappings to the active Geyser instance.";
            return;
        }
        Path plugins=plugin.getDataFolder().toPath().toAbsolutePath().getParent();
        Path geyser=plugins.resolve("Geyser-Spigot");
        boolean present=Files.isDirectory(geyser);
        if(!present) {
            Path alternative=plugins.resolve("Geyser");
            if(Files.isDirectory(alternative)){geyser=alternative;present=true;}
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
                output.resolve("advance-magic-bedrock.mcpack"),"advance-magic.json","advance-magic-bedrock.mcpack");
            if(cleaned>0)plugin.getLogger().info("Cleaned "+cleaned+" duplicate Geyser files; originals saved in plugin-pack-backups.");
            writeChanged(geyser.resolve("packs/advance-magic-bedrock.mcpack"),Files.readAllBytes(output.resolve("advance-magic-bedrock.mcpack")));
            writeChanged(geyser.resolve("custom_mappings/advance-magic.json"),Files.readAllBytes(output.resolve("geyser-mappings.json")));
            boolean enabled=GeyserPackSetup.enableCustomContent(geyser);
            geyserStatus="Bedrock pack + mappings installed in "+geyser.toAbsolutePath()
                +(enabled?"; enabled custom content":"; custom content already enabled")
                +". Restart Geyser and reconnect.";
            plugin.getLogger().info(geyserStatus);
        }
    }
    private static void writeChanged(Path target,byte[] bytes) throws IOException {
        if(Files.exists(target)&&Arrays.equals(Files.readAllBytes(target),bytes))return;
        Files.createDirectories(target.getParent());
        Path staged=Files.createTempFile(target.getParent(),"advance-magic-",".tmp");
        try {
            Files.write(staged,bytes);
            try {Files.move(staged,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(staged,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(staged);}
    }
    public void start() {
        if(RequiredPackSettings.upgrade(plugin.getConfig(),false)){
            plugin.saveConfig();plugin.getLogger().info("Java resource pack is now required; existing download hosts are preserved.");
        }
        delivery=ResourcePackManager.connect(plugin,PACK_ID);
        try {
            byte[] pack=extractedJavaPack;
            if(pack==null)try(InputStream input=plugin.getResource("resource-packs/advance-magic-java.zip")) {
                if(input==null)throw new IOException("Embedded Java pack is missing");
                pack=input.readAllBytes();
            }
            sha1=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(pack));
            if(migratePackConfig(plugin.getConfig(),sha1)) {
                plugin.saveConfig();
                plugin.getLogger().info("Updated previous official Advance Magic pack settings to the current pack delivery defaults.");
            }
            if(!plugin.getConfig().getBoolean("resource-pack.enabled",true))return;
            String configuredUrl=plugin.getConfig().getString("resource-pack.url","").trim();
            if(!configuredUrl.isBlank()) {
                plugin.getLogger().info("Advance Magic pack configured to use external CDN: "+configuredUrl);
                return;
            }
            mirror.start(PackMirror.fingerprint(pack),plugin.getConfig().getStringList("resource-pack.mirrors"));
            if(!plugin.getConfig().getBoolean("resource-pack.host.enabled",false)) {
                failure="The bundled pack host is disabled and no external pack URL is configured.";
                plugin.getLogger().warning(failure);
                return;
            }
            if(sharesServerPort())try {
                http=new PackHttpServer(plugin,pack,sha1);
                plugin.getLogger().info("Bundled Java pack served through the Minecraft port; no extra TCP port is needed. /magic pack shows status.");
                return;
            }catch(IOException e){plugin.getLogger().warning(e.getMessage()+"; using the separate pack port instead.");}
            http=new PackHttpServer(plugin.getConfig().getString("resource-pack.host.bind","0.0.0.0"),
                    plugin.getConfig().getInt("resource-pack.host.port",8187),pack,sha1);
            plugin.getLogger().info("Bundled Java pack served on TCP "+http.port()+". Allow this port through your host/firewall; /magic pack shows status.");
        }catch(IOException|GeneralSecurityException|IllegalArgumentException e) {
            failure=e.getMessage();plugin.getLogger().warning("Pack host could not start: "+failure);
        }finally{extractedJavaPack=null;}
    }
    static boolean migratePackConfig(org.bukkit.configuration.file.FileConfiguration config,String bundledSha1) {
        String url=config.getString("resource-pack.url","").trim();
        var defaults=config.getDefaults();
        if(defaults==null)return false;
        String publishedUrl=defaults.getString("resource-pack.url","").trim();
        String publishedSha1=defaults.getString("resource-pack.sha1","").trim();
        String officialPack="https://raw\\.githubusercontent\\.com/40000years/(?:Afterdeath|Evergarden2)/(?:DEV|main|[a-fA-F0-9]{7,40})/advance-magic/dist/advance-magic-java\\.zip";
        // The legendary release is built locally; the old pinned GitHub pack
        // lacks its items and effect art. Upgrade only our known official URL,
        // retaining configured ports/public hosts and all administrator CDNs.
        if(publishedUrl.isEmpty()&&defaults.getBoolean("resource-pack.host.enabled",false)
                &&bundledSha1.matches("[a-fA-F0-9]{40}")&&url.matches(officialPack)) {
            config.set("resource-pack.url","");
            config.set("resource-pack.sha1","");
            config.set("resource-pack.host.enabled",true);
            return true;
        }
        // A changed embedded pack must not be paired with an older pinned download.
        if(publishedUrl.isEmpty()||!bundledSha1.equalsIgnoreCase(publishedSha1))return false;
        // Preserve administrator-owned CDN URLs and configured public hosts.
        boolean oldOfficial=url.matches(officialPack)&&!url.equals(publishedUrl);
        boolean oldBundledHost=url.isEmpty()&&config.getBoolean("resource-pack.host.enabled",false)
            &&config.getString("resource-pack.host.public-url","").isBlank()
            &&config.getString("resource-pack.host.public-host","").isBlank()
            &&config.getString("resource-pack.host.bind","0.0.0.0").equals("0.0.0.0")
            &&config.getInt("resource-pack.host.port",8187)==8187;
        if(!oldOfficial&&!oldBundledHost)return false;
        config.set("resource-pack.url",publishedUrl);
        config.set("resource-pack.sha1",publishedSha1);
        config.set("resource-pack.host.enabled",false);
        return true;
    }
    public boolean isBedrock(Player player) {
        for(String name:List.of("org.geysermc.floodgate.api.FloodgateApi","org.geysermc.geyser.api.GeyserApi")) {
            try {
                Class<?> type=Class.forName(name);boolean floodgate=name.contains("floodgate");
                Object api=type.getMethod(floodgate?"getInstance":"api").invoke(null);
                if((boolean)type.getMethod(floodgate?"isFloodgatePlayer":"isBedrockPlayer",UUID.class).invoke(api,player.getUniqueId()))return true;
            }catch(ReflectiveOperationException|LinkageError ignored){}
        }
        return false;
    }
    static String packDigest(org.bukkit.configuration.file.FileConfiguration config,String bundledSha1) {
        return config.getString("resource-pack.url","").isBlank()?bundledSha1
                :config.getString("resource-pack.sha1","").trim();
    }
    public String url(Player player) {
        String external=plugin.getConfig().getString("resource-pack.url","").trim();
        if(!external.isEmpty())return validateUrl(external);
        if(!plugin.getConfig().getBoolean("resource-pack.host.enabled",false)||http==null)return "";
        String base=plugin.getConfig().getString("resource-pack.host.public-url","").trim();
        if(!base.isEmpty())return validateUrl(base.replaceAll("/+$","")+http.path());
        String host=plugin.getConfig().getString("resource-pack.host.public-host","").trim();
        if(host.isBlank()&&player!=null) {
            InetSocketAddress address=player.getVirtualHost();
            if(address!=null)host=address.getHostString();
        }
        if(host.isBlank())return "";
        int port=http.sharesServerPort()?ServerPortPackHost.port(player):http.port();
        try{return validateUrl(new URI("http",null,host,port,http.path(),null,null).toASCIIString());}
        catch(URISyntaxException e){return "";}
    }
    /** A reverse proxy (public-url) keeps pointing at the separate pack port. */
    private boolean sharesServerPort() {
        return plugin.getConfig().getBoolean("resource-pack.host.use-server-port",true)
            &&plugin.getConfig().getString("resource-pack.host.public-url","").isBlank();
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
        if(!plugin.getConfig().getBoolean("resource-pack.enabled",true)||isBedrock(player))return;
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
            if(delivery!=null&&delivery.offer(player,urls,hashes,"Advance Magic: wands and flying staff",
                    plugin.getConfig().getBoolean("resource-pack.required",true),force))
                statuses.put(player.getUniqueId(),"QUEUED");
        }catch(IllegalArgumentException e){statuses.put(player.getUniqueId(),"INVALID_URL");unavailable(player);}
    }
    private void unavailable(Player player){if(delivery!=null&&plugin.getConfig().getBoolean("resource-pack.required",true))delivery.unavailable(player);}
    @EventHandler public void join(PlayerJoinEvent event){offer(event.getPlayer());}
    @EventHandler public void status(PlayerResourcePackStatusEvent event) {
        if(!PACK_ID.equals(event.getID()))return;
        String status=event.getStatus().name();statuses.put(event.getPlayer().getUniqueId(),status);
        // The coordinator is already sending it again (from the next address, or on its own).
        if(delivery!=null&&List.of("QUEUED","OFFERED","ACCEPTED","DOWNLOADED").contains(delivery.status(event.getPlayer())))return;
        if(status.startsWith("FAILED")||status.equals("INVALID_URL")||status.equals("DISCARDED")) {
            plugin.getLogger().warning("Advance Magic pack for "+event.getPlayer().getName()+": "+status+" from "+url(event.getPlayer())+". Check TCP port/public URL and /magic pack; Pack HTTP lines show whether the download reached this server.");
            event.getPlayer().sendMessage(ChatColor.YELLOW+(plugin.getConfig().getBoolean("resource-pack.required",true)
                ?"Advance Magic textures are required to play. Allow server resource packs and reconnect; ask an admin if loading fails."
                :"Advance Magic textures could not load. Wands still work; ask an admin to check /magic pack."));
        }
    }
    public boolean hasApplied(Player player){return "SUCCESSFULLY_LOADED".equals(statuses.get(player.getUniqueId()));}
    @EventHandler public void quit(PlayerQuitEvent event){statuses.remove(event.getPlayer().getUniqueId());mirror.forget(event.getPlayer());}
    public void describe(CommandSender sender) {
        sender.sendMessage(ChatColor.LIGHT_PURPLE+"Advance Magic resource pack");
        sender.sendMessage("Enabled: "+plugin.getConfig().getBoolean("resource-pack.enabled",true)+" | Required: "+plugin.getConfig().getBoolean("resource-pack.required",true)+" | Bundled host: "+(http==null?"off":http.sharesServerPort()?"Minecraft port":"TCP "+http.port()));
        sender.sendMessage("Bundled SHA-1: "+sha1);
        String url=url(sender instanceof Player p?p:null);sender.sendMessage("URL: "+(url.isEmpty()?"unavailable":url));
        sender.sendMessage("Public copy: "+(mirror.copy()!=null?mirror.copy().url():mirror.fingerprint().isEmpty()?"not used"
            :(mirror.pending()?"checking ":"none verified for ")+mirror.fingerprint().substring(0,12)));
        sender.sendMessage(geyserStatus);
        if(!failure.isEmpty())sender.sendMessage(ChatColor.RED+failure);
        if(sender instanceof Player p){
            String status=delivery==null?"NOT_OFFERED":delivery.status(p);
            sender.sendMessage("Your pack: "+(status.equals("NOT_OFFERED")?statuses.getOrDefault(p.getUniqueId(),"not offered (or Bedrock)"):status));
            if(delivery!=null)sender.sendMessage(delivery.describe(p));
        }
        sender.sendMessage("Files: plugins/advance-magic/resource-packs/ | Retry: /magic pack resend");
    }
    @Override public void close(){if(delivery!=null){delivery.close();delivery=null;}if(http!=null){http.close();http=null;}extractedJavaPack=null;statuses.clear();}
}
