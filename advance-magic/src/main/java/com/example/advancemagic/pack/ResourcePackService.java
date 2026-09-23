package com.example.advancemagic.pack;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.JarFile;

public final class ResourcePackService implements Listener, AutoCloseable {
    public static final UUID PACK_ID=UUID.fromString("3e8e5b71-0600-4a42-a678-483a7cce5fb0");
    public static final String DEFAULT_CDN_URL = "https://raw.githubusercontent.com/40000years/Evergarden2/07dcac3/advance-magic/dist/advance-magic-java.zip";
    private static final String CURRENT_SHA1 = "fb436f7d2dd4f0fdf145a63fc2d1796166c5d70e";
    private static final List<String> FILES=List.of("advance-magic-java.zip","advance-magic-bedrock.mcpack",
            "geyser-mappings.json","pack-hashes.json","wand-preview.html","advance-magic-guide-th.png");
    private final JavaPlugin plugin;
    private final Map<UUID,String> statuses=new HashMap<>();
    private PackHttpServer http;
    private String sha1="",failure="",geyserStatus="External Geyser: copy files from resource-packs/ manually.";
    public ResourcePackService(JavaPlugin plugin){this.plugin=plugin;}

    /** Runs in onLoad, before Geyser reads mappings and packs. */
    public void extract() throws IOException {
        Path output=plugin.getDataFolder().toPath().resolve("resource-packs");
        for(String name:FILES) {
            try(InputStream input=plugin.getResource("resource-packs/"+name)) {
                if(input==null)throw new IOException("Missing embedded asset: "+name+". Rebuild with advance-magic/build.ps1.");
                writeChanged(output.resolve(name),input.readAllBytes());
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
        try(InputStream input=plugin.getResource("resource-packs/advance-magic-java.zip")) {
            if(input==null)throw new IOException("Embedded Java pack is missing");
            byte[] pack=input.readAllBytes();
            sha1=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(pack));
            if(migratePackConfig(plugin.getConfig())) {
                plugin.saveConfig();
                plugin.getLogger().info("Updated the official Advance Magic pack URL and SHA-1 for this release.");
            }
            if(!plugin.getConfig().getBoolean("resource-pack.enabled",true))return;
            String configuredUrl=plugin.getConfig().getString("resource-pack.url","").trim();
            if(!configuredUrl.isBlank()) {
                plugin.getLogger().info("Advance Magic pack configured to use external CDN: "+configuredUrl);
                return;
            }
            if(!plugin.getConfig().getBoolean("resource-pack.host.enabled",false)) {
                plugin.getLogger().info("Advance Magic local pack host disabled; serving Java pack via GitHub CDN.");
                return;
            }
            http=new PackHttpServer(plugin.getConfig().getString("resource-pack.host.bind","0.0.0.0"),
                    plugin.getConfig().getInt("resource-pack.host.port",8187),pack,sha1);
            plugin.getLogger().info("Bundled Java pack served on TCP "+http.port()+". Allow this port through your host/firewall; /magic pack shows status.");
        }catch(IOException|GeneralSecurityException|IllegalArgumentException e) {
            failure=e.getMessage();plugin.getLogger().warning("Pack host could not start: "+failure+". Falling back to GitHub CDN.");
        }
    }
    static boolean migratePackConfig(org.bukkit.configuration.file.FileConfiguration config) {
        String url=config.getString("resource-pack.url","").trim();
        String hash=config.getString("resource-pack.sha1","").trim();
        // Keep already-downloaded Afterdeath jars intact; upgrade their persisted config only
        // when this Evergarden2 plugin is installed. Private pack URLs remain administrator-owned.
        String officialPack="https://raw\\.githubusercontent\\.com/40000years/(?:Afterdeath|Evergarden2)/(?:DEV|main|[a-fA-F0-9]{7,40})/advance-magic/dist/advance-magic-java\\.zip";
        if(!url.matches(officialPack))return false;
        if(url.equals(DEFAULT_CDN_URL)&&hash.equalsIgnoreCase(CURRENT_SHA1))return false;
        config.set("resource-pack.url",DEFAULT_CDN_URL);
        config.set("resource-pack.sha1",CURRENT_SHA1);
        return true;
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
        if(!plugin.getConfig().getBoolean("resource-pack.host.enabled",false)) {
            return DEFAULT_CDN_URL;
        }
        if(http==null)return DEFAULT_CDN_URL;
        String base=plugin.getConfig().getString("resource-pack.host.public-url","").trim();
        if(!base.isEmpty())return validateUrl(base.replaceAll("/+$","")+http.path());
        String host=plugin.getConfig().getString("resource-pack.host.public-host","").trim();
        if(host.isBlank()&&player!=null) {
            InetSocketAddress address=player.getVirtualHost();
            if(address!=null)host=address.getHostString();
        }
        if(host.isBlank())return DEFAULT_CDN_URL;
        try{return validateUrl(new URI("http",null,host,http.port(),http.path(),null,null).toASCIIString());}
        catch(URISyntaxException e){return DEFAULT_CDN_URL;}
    }
    private String validateUrl(String value) {
        try {
            URI uri=URI.create(value);
            if(!List.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)return "";
            return uri.toASCIIString();
        }catch(IllegalArgumentException e){return "";}
    }
    public void offer(Player player) {
        if(!plugin.getConfig().getBoolean("resource-pack.enabled",true)||bedrock(player))return;
        String url=url(player);
        if(url.isEmpty()) {statuses.put(player.getUniqueId(),"NOT_OFFERED: configure the public host/URL");return;}
        String digest=plugin.getConfig().getString("resource-pack.sha1","").trim();
        if(digest.isEmpty())digest=sha1;
        if(!digest.matches("[a-fA-F0-9]{40}")) {statuses.put(player.getUniqueId(),"INVALID_SHA1");return;}
        try {
            player.addResourcePack(PACK_ID,url,HexFormat.of().parseHex(digest),"Advance Magic: 15 custom wand textures",
                    plugin.getConfig().getBoolean("resource-pack.required",false));
            statuses.put(player.getUniqueId(),"OFFERED");
        }catch(IllegalArgumentException e){statuses.put(player.getUniqueId(),"INVALID_URL");}
    }
    @EventHandler public void status(PlayerResourcePackStatusEvent event) {
        if(!PACK_ID.equals(event.getID()))return;
        String status=event.getStatus().name();statuses.put(event.getPlayer().getUniqueId(),status);
        if(status.startsWith("FAILED")||status.equals("INVALID_URL")||status.equals("DISCARDED")) {
            plugin.getLogger().warning("Advance Magic pack for "+event.getPlayer().getName()+": "+status+". Check TCP port/public URL and /magic pack.");
            event.getPlayer().sendMessage(ChatColor.YELLOW+"Advance Magic textures could not load. Wands still work; ask an admin to check /magic pack.");
        }
    }
    @EventHandler public void quit(PlayerQuitEvent event){statuses.remove(event.getPlayer().getUniqueId());}
    public void describe(CommandSender sender) {
        sender.sendMessage(ChatColor.LIGHT_PURPLE+"Advance Magic resource pack");
        sender.sendMessage("Enabled: "+plugin.getConfig().getBoolean("resource-pack.enabled",true)+" | Host: "+(http==null?"off (CDN active)":"TCP "+http.port()));
        sender.sendMessage("Bundled SHA-1: "+sha1);
        String url=url(sender instanceof Player p?p:null);sender.sendMessage("URL: "+(url.isEmpty()?"CDN default":url));
        sender.sendMessage(geyserStatus);
        if(!failure.isEmpty())sender.sendMessage(ChatColor.RED+failure);
        if(sender instanceof Player p)sender.sendMessage("Your pack: "+statuses.getOrDefault(p.getUniqueId(),"not offered (or Bedrock)"));
        sender.sendMessage("Files: plugins/advance-magic/resource-packs/ | Retry: /magic pack resend");
    }
    @Override public void close(){if(http!=null){http.close();http=null;}statuses.clear();}
}
