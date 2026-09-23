package com.example.voidscape.pack;

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
    public static final UUID PACK_ID=UUID.fromString("c8f2b94e-4a35-4d1b-9b67-0d2a6ef4f821");
    public static final String DEFAULT_CDN_URL = "https://raw.githubusercontent.com/40000years/Evergarden2/2408a45/evergarden/dist/evergarden-java.zip";
    public static final UUID AETERNUM_PACK_ID=UUID.fromString("8d2af8f1-f85c-4b4e-8a37-a55a359ce496");
    public static final String AETERNUM_PACK_URL="https://raw.githubusercontent.com/40000years/Evergarden2/2408a45/evergarden/dist/Aeternum-Foods-26.x.zip";
    public static final String AETERNUM_PACK_SHA1="f7137350c381dfb933f96e869bfaced4a292bcff";
    private static final List<String> FILES=List.of("evergarden-java.zip","evergarden-bedrock.mcpack",
            "geyser-mappings.json","pack-hashes.json");
    private final JavaPlugin plugin;
    private final Map<UUID,String> statuses=new HashMap<>();
    private final Map<UUID,String> aeternumStatuses=new HashMap<>();
    private PackHttpServer http;
    private String bedrockPackInfo="Bedrock pack not extracted";
    private String aeternumBedrockStatus="Aeternum Bedrock: auto-install disabled, plugin absent, or no local Geyser.";
    private String sha1="",failure="",geyserStatus="External Geyser: copy files from resource-packs/ manually.";
    public ResourcePackService(JavaPlugin plugin){this.plugin=plugin;}

    /** Runs in onLoad, before Geyser reads mappings and packs. */
    public void extract() throws IOException {
        Path output=plugin.getDataFolder().toPath().resolve("resource-packs");
        for(String name:FILES) {
            try(InputStream input=plugin.getResource("resource-packs/"+name)) {
                if(input==null)throw new IOException("Missing embedded asset: "+name+". Rebuild with evergarden/build.ps1.");
                writeChanged(output.resolve(name),input.readAllBytes());
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
            if(plugin.getConfig().getBoolean("compatibility.aeternum-seasons.resource-pack.enabled",true)
                    &&plugin.getConfig().getBoolean("compatibility.aeternum-seasons.bedrock.auto-install",true)
                    &&AeternumBedrockInstaller.isPresent(plugins)) {
                try {
                    Path bundled=output.resolve("aeternum-bedrock");
                    for(String name:AeternumBedrockInstaller.FILES) {
                        try(InputStream input=plugin.getResource("aeternum-bedrock/"+name)) {
                            if(input==null)throw new IOException("Missing bundled Aeternum asset: "+name);
                            writeChanged(bundled.resolve(name),input.readAllBytes());
                        }
                    }
                    int installed=AeternumBedrockInstaller.install(geyser,bundled);
                    aeternumBedrockStatus="Aeternum Bedrock: pack + item/block mappings ready in "+geyser.toAbsolutePath()
                            +" ("+installed+" files installed; existing files preserved).";
                    plugin.getLogger().info(aeternumBedrockStatus);
                }catch(IOException e) {
                    aeternumBedrockStatus="Aeternum Bedrock auto-install failed: "+e.getMessage();
                    plugin.getLogger().warning(aeternumBedrockStatus);
                }
            }
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
        try(InputStream input=plugin.getResource("resource-packs/evergarden-java.zip")) {
            if(input==null)throw new IOException("Embedded Java pack is missing");
            byte[] pack=input.readAllBytes();
            sha1=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(pack));
            if(migrateAeternumPackConfig(plugin.getConfig())) {
                plugin.saveConfig();
                plugin.getLogger().info("Updated Aeternum food pack to the verified GitHub mirror (no Modrinth CDN required).");
            }
            if(migratePackConfig(plugin.getConfig())) {
                plugin.saveConfig();
                plugin.getLogger().info("Updated the official Evergarden pack URL and SHA-1 to match this release.");
            }
            if(!plugin.getConfig().getBoolean("resource-pack.enabled",true))return;
            String configuredUrl=plugin.getConfig().getString("resource-pack.url","").trim();
            if(!configuredUrl.isBlank()) {
                plugin.getLogger().info("Evergarden pack configured to use external CDN: "+configuredUrl);
                return;
            }
            if(!plugin.getConfig().getBoolean("resource-pack.host.enabled",false)) {
                plugin.getLogger().info("Evergarden local pack host disabled; serving Java pack via GitHub CDN.");
                return;
            }
            http=new PackHttpServer(plugin.getConfig().getString("resource-pack.host.bind","0.0.0.0"),
                    plugin.getConfig().getInt("resource-pack.host.port",8188),pack,sha1);
            plugin.getLogger().info("Bundled Java pack served on TCP "+http.port()+". Allow this port through your host/firewall; /evergarden pack shows status.");
        }catch(IOException|GeneralSecurityException|IllegalArgumentException e) {
            failure=e.getMessage();plugin.getLogger().warning("Pack host could not start: "+failure+". Using GitHub CDN fallback.");
        }
    }
    static boolean migratePackConfig(org.bukkit.configuration.file.FileConfiguration config) {
        String url=config.getString("resource-pack.url","").trim();
        // Only migrate our published GitHub packs; private CDN/host settings remain administrator-owned.
        if(!url.matches("https://raw\\.githubusercontent\\.com/40000years/Afterdeath/(?:DEV|main|[a-fA-F0-9]{7,40})/evergarden/dist/evergarden-java\\.zip"))return false;
        if(url.equals(DEFAULT_CDN_URL))return false;
        config.set("resource-pack.url",DEFAULT_CDN_URL);
        config.set("resource-pack.sha1",""); // Calculated from the embedded ZIP by offer().
        return true;
    }
    static boolean migrateAeternumPackConfig(org.bukkit.configuration.file.FileConfiguration config) {
        String key="compatibility.aeternum-seasons.resource-pack";
        String url=config.getString(key+".url",AETERNUM_PACK_URL).trim();
        boolean oldModrinth=url.equals("https://cdn.modrinth.com/data/4hkZZzlQ/versions/VveNYYee/Aeternum-Foods-26.x.zip");
        boolean oldGitHub=url.matches("https://raw\\.githubusercontent\\.com/40000years/Afterdeath/(?:DEV|main|[a-fA-F0-9]{7,40})/evergarden/dist/Aeternum-Foods-26\\.x\\.zip");
        if(!oldModrinth&&!oldGitHub)return false;
        config.set(key+".url",AETERNUM_PACK_URL);
        config.set(key+".sha1",AETERNUM_PACK_SHA1);
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
            player.addResourcePack(PACK_ID,url,HexFormat.of().parseHex(digest),"Evergarden: relics, keys and sanctuary creatures",
                    plugin.getConfig().getBoolean("resource-pack.required",false));
            statuses.put(player.getUniqueId(),"OFFERED");
        }catch(IllegalArgumentException e){statuses.put(player.getUniqueId(),"INVALID_URL");}
        offerAeternum(player);
    }
    private boolean aeternumEnabled() {
        return plugin.getConfig().getBoolean("compatibility.aeternum-seasons.resource-pack.enabled",true)
                && Bukkit.getPluginManager().isPluginEnabled("AeternumSeasons");
    }
    private void offerAeternum(Player player) {
        if(!aeternumEnabled())return;
        String url=validateUrl(plugin.getConfig().getString("compatibility.aeternum-seasons.resource-pack.url",AETERNUM_PACK_URL).trim());
        String digest=plugin.getConfig().getString("compatibility.aeternum-seasons.resource-pack.sha1",AETERNUM_PACK_SHA1).trim();
        if(url.isEmpty()||!digest.matches("[a-fA-F0-9]{40}")) {
            aeternumStatuses.put(player.getUniqueId(),"INVALID_URL_OR_SHA1");
            plugin.getLogger().warning("Invalid Aeternum food pack URL/SHA-1; check compatibility.aeternum-seasons.resource-pack.");
            return;
        }
        // Add last: Aeternum owns shared food carriers such as honey_bottle.
        // Our elixir uses voidscape:void_elixir, independent of that override.
        player.addResourcePack(AETERNUM_PACK_ID,url,HexFormat.of().parseHex(digest),
                "Aeternum Seasons: crops and foods",plugin.getConfig().getBoolean("resource-pack.required",false));
        aeternumStatuses.put(player.getUniqueId(),"OFFERED");
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(event.getPlayer().isOnline())offer(event.getPlayer());},2);
    }
    @EventHandler public void status(PlayerResourcePackStatusEvent event) {
        if(AETERNUM_PACK_ID.equals(event.getID())) {
            String status=event.getStatus().name();aeternumStatuses.put(event.getPlayer().getUniqueId(),status);
            if(status.startsWith("FAILED")||status.equals("INVALID_URL")||status.equals("DISCARDED")) {
                plugin.getLogger().warning("Aeternum food pack for "+event.getPlayer().getName()+": "+status);
                event.getPlayer().sendMessage(ChatColor.YELLOW+"Aeternum crop textures could not load. Check /evergarden pack and retry /evergarden pack resend.");
            }
            return;
        }
        if(!PACK_ID.equals(event.getID()))return;
        String status=event.getStatus().name();statuses.put(event.getPlayer().getUniqueId(),status);
        if(status.startsWith("FAILED")||status.equals("INVALID_URL")||status.equals("DISCARDED")) {
            plugin.getLogger().warning("Evergarden pack for "+event.getPlayer().getName()+": "+status+". Check TCP port/public URL and /evergarden pack.");
            event.getPlayer().sendMessage(ChatColor.YELLOW+"Evergarden textures could not load. Items still work; ask an admin to check /evergarden pack.");
        }
    }
    @EventHandler public void quit(PlayerQuitEvent event){statuses.remove(event.getPlayer().getUniqueId());aeternumStatuses.remove(event.getPlayer().getUniqueId());}
    public void describe(CommandSender sender) {
        sender.sendMessage(ChatColor.LIGHT_PURPLE+"Evergarden resource pack");
        sender.sendMessage("Enabled: "+plugin.getConfig().getBoolean("resource-pack.enabled",true)+" | Host: "+(http==null?"off (CDN active)":"TCP "+http.port()));
        sender.sendMessage("Bundled SHA-1: "+sha1);
        String url=url(sender instanceof Player p?p:null);sender.sendMessage("URL: "+(url.isEmpty()?"CDN default":url));
        sender.sendMessage(geyserStatus);
        sender.sendMessage(bedrockPackInfo);
        sender.sendMessage(aeternumBedrockStatus);
        sender.sendMessage("Aeternum Java food pack: "+(aeternumEnabled()?"enabled (added after Evergarden)":"off or plugin absent"));
        if(aeternumEnabled()) {
            if(sender instanceof Player p)sender.sendMessage("Your Aeternum pack: "+aeternumStatuses.getOrDefault(p.getUniqueId(),"not offered (or Bedrock)"));
            else for(Player p:Bukkit.getOnlinePlayers())sender.sendMessage(p.getName()+" Aeternum pack: "+aeternumStatuses.getOrDefault(p.getUniqueId(),"not offered (or Bedrock)"));
        }
        if(!failure.isEmpty())sender.sendMessage(ChatColor.RED+failure);
        if(sender instanceof Player p)sender.sendMessage("Your pack: "+statuses.getOrDefault(p.getUniqueId(),"not offered (or Bedrock)"));
        else for(Player player:Bukkit.getOnlinePlayers()) {
            sender.sendMessage(player.getName()+": "+(bedrock(player)?"BEDROCK: delivered by Geyser; Java status unavailable":statuses.getOrDefault(player.getUniqueId(),"NOT_OFFERED")));
            if(!bedrock(player))sender.sendMessage("  URL: "+url(player));
        }
        sender.sendMessage("Files: plugins/Evergarden/resource-packs/ | Retry: /evergarden pack resend");
    }
    @Override public void close(){if(http!=null){http.close();http=null;}statuses.clear();aeternumStatuses.clear();}
}
