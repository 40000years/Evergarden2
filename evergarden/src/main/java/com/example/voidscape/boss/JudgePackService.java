package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.pack.PackHttpServer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;

/** A separately addressed addon: never changes the published relic pack or its hash. */
public final class JudgePackService implements Listener,AutoCloseable {
    public static final UUID PACK_ID=UUID.fromString("b198c85c-c302-4c62-a365-a358fa544509");
    private final VoidscapePlugin plugin;
    private final Map<UUID,PlayerResourcePackStatusEvent.Status> statuses=new HashMap<>();
    private PackHttpServer http;
    private String sha1="",failure="",modelInfo="unknown design · check the installed JAR";
    public JudgePackService(VoidscapePlugin plugin){
        this.plugin=plugin;
        try(InputStream input=plugin.getResource("judge-design.json")){
            if(input==null)throw new IOException("Bundled Judge design manifest is missing");
            var design=new com.google.gson.JsonParser().parse(new InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            modelInfo=design.get("revision").getAsString()+" | "+design.get("name").getAsString()+" | "+
                design.get("models").getAsInt()+" models / "+design.get("components").getAsInt()+" sculpture components | Bedrock bundle: "+design.get("bedrock-version").getAsString();
            plugin.getLogger().info("Ancient Judge model: "+modelInfo);
        }catch(IOException|RuntimeException error){plugin.getLogger().warning("Cannot read Judge design manifest: "+error.getMessage());}
    }
    public String modelInfo(){return modelInfo;}
    public void start(){
        if(!plugin.getConfig().getBoolean("world-boss.visuals.pack.enabled",true))return;
        try(InputStream source=plugin.getResource("resource-packs/judge-java.zip")){
            if(source==null)throw new IOException("Bundled Judge pack is missing");
            byte[] bytes=source.readAllBytes();sha1=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
            Path folder=plugin.getDataFolder().toPath().resolve("resource-packs");Files.createDirectories(folder);
            Path temp=Files.createTempFile(folder,"judge-",".tmp");
            try{Files.write(temp,bytes);Files.move(temp,folder.resolve("judge-java.zip"),StandardCopyOption.REPLACE_EXISTING);}
            finally{Files.deleteIfExists(temp);}
            if(plugin.getConfig().getString("world-boss.visuals.pack.url","").isBlank()){
                http=new PackHttpServer(plugin.getConfig().getString("world-boss.visuals.pack.host.bind","0.0.0.0"),
                    plugin.getConfig().getInt("world-boss.visuals.pack.host.port",8189),bytes,sha1);
                plugin.getLogger().info("Ancient Judge graphics served on TCP "+http.port()+"; /evergarden boss pack shows the client URL and status.");
            }
        }catch(IOException|GeneralSecurityException|IllegalArgumentException error){
            failure=error.getMessage();plugin.getLogger().warning("Judge pack unavailable: "+failure+". Java players retain the solid vanilla sculpture.");
        }
    }
    public static boolean bedrock(Player p){
        for(String name:List.of("org.geysermc.floodgate.api.FloodgateApi","org.geysermc.geyser.api.GeyserApi"))try{
            boolean floodgate=name.contains("floodgate");
            if(Bukkit.getPluginManager().getPlugin(floodgate?"floodgate":"Geyser-Spigot")==null)continue;
            Class<?> type=Class.forName(name);
            Object api=type.getMethod(floodgate?"getInstance":"api").invoke(null);
            if((boolean)type.getMethod(floodgate?"isFloodgatePlayer":"isBedrockPlayer",UUID.class).invoke(api,p.getUniqueId()))return true;
        }catch(ReflectiveOperationException|LinkageError ignored){}
        return false;
    }
    public boolean applied(Player p){return statuses.get(p.getUniqueId())==PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED;}
    public String url(Player p){
        String external=plugin.getConfig().getString("world-boss.visuals.pack.url","").trim();
        if(!external.isEmpty())return validated(external);
        if(http==null)return "";
        String base=plugin.getConfig().getString("world-boss.visuals.pack.host.public-url","").trim();
        if(!base.isEmpty())return validated(base.replaceAll("/+$","")+http.path());
        String host=plugin.getConfig().getString("world-boss.visuals.pack.host.public-host","").trim();
        if(host.isBlank()&&p!=null&&p.getVirtualHost()!=null)host=p.getVirtualHost().getHostString();
        if(host.isBlank())return "";
        try{return validated(new URI("http",null,host,http.port(),http.path(),null,null).toASCIIString());}
        catch(URISyntaxException error){return "";}
    }
    private static String validated(String value){
        try{URI u=URI.create(value);return List.of("http","https").contains(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null&&u.getFragment()==null?u.toASCIIString():"";}
        catch(IllegalArgumentException error){return "";}
    }
    public void offer(Player p){
        if(bedrock(p)||sha1.isEmpty()||!plugin.getConfig().getBoolean("world-boss.visuals.pack.enabled",true))return;
        String url=url(p);if(url.isEmpty())return;
        try{p.addResourcePack(PACK_ID,url,HexFormat.of().parseHex(sha1),"Evergarden: Crimson Judge + magic circles",false);}
        catch(IllegalArgumentException error){plugin.getLogger().warning("Cannot offer Judge pack: "+error.getMessage());}
    }
    @EventHandler public void join(PlayerJoinEvent e){Bukkit.getScheduler().runTaskLater(plugin,()->{if(e.getPlayer().isOnline())offer(e.getPlayer());},8);}
    @EventHandler public void status(PlayerResourcePackStatusEvent e){if(PACK_ID.equals(e.getID()))statuses.put(e.getPlayer().getUniqueId(),e.getStatus());}
    @EventHandler public void quit(PlayerQuitEvent e){statuses.remove(e.getPlayer().getUniqueId());}
    public void describe(CommandSender sender){
        sender.sendMessage("Model: "+modelInfo);
        sender.sendMessage("Ancient Judge graphics | SHA-1: "+sha1);
        sender.sendMessage("Host: "+(http==null?"external/off":"TCP "+http.port())+" | URL: "+url(sender instanceof Player p?p:null));
        if(sender instanceof Player p)sender.sendMessage(bedrock(p)?"Bedrock: bundled Evergarden attachables via Geyser":"Client: "+statuses.getOrDefault(p.getUniqueId(),PlayerResourcePackStatusEvent.Status.DISCARDED));
        if(!failure.isEmpty())sender.sendMessage("Host error: "+failure);
        sender.sendMessage("Retry: /evergarden boss pack resend | File: plugins/Evergarden/resource-packs/judge-java.zip");
    }
    @Override public void close(){if(http!=null){http.close();http=null;}statuses.clear();}
}
