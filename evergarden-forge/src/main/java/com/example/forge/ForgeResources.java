package com.example.forge;

import com.sun.net.httpserver.HttpServer;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Immutable, separately served forge pack; existing Evergarden/Advance Magic packs stay unchanged. */
public final class ForgeResources implements Listener,AutoCloseable {
    public static final UUID PACK_ID=UUID.fromString("d17e1d40-8932-4c65-9d9a-4fed0e351052");
    private final ForgePlugin plugin;
    private byte[] bytes;
    private String sha1="",path="",failure="";
    private Object host,delivery;
    private HttpServer standalone;
    private ExecutorService executor;
    ForgeResources(ForgePlugin plugin){this.plugin=plugin;}

    /** Before Geyser reads its custom item registry and resource packs. */
    void extract()throws Exception{
        Path output=plugin.getDataFolder().toPath().resolve("resource-packs");
        for(String name:List.of("forge-java.zip","forge-bedrock.mcpack","geyser-mappings.json","pack-hashes.json")){
            try(InputStream in=plugin.getResource("resource-packs/"+name)){
                if(in==null)throw new IOException("Missing forge resource: "+name);
                write(output.resolve(name),in.readAllBytes());
            }
        }
        bytes=Files.readAllBytes(output.resolve("forge-java.zip"));
        sha1=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
        Plugin geyser=Bukkit.getPluginManager().getPlugin("Geyser-Spigot");
        if(geyser==null)geyser=Bukkit.getPluginManager().getPlugin("Geyser");
        Path geyserFolder=geyser==null?findGeyser():geyser.getDataFolder().toPath();
        if(geyserFolder!=null&&plugin.getConfig().getBoolean("resource-pack.geyser-auto-install",true)){
            Path folder=geyserFolder;
            write(folder.resolve("packs/evergarden-forge.mcpack"),Files.readAllBytes(output.resolve("forge-bedrock.mcpack")));
            write(folder.resolve("custom_mappings/evergarden-forge.json"),Files.readAllBytes(output.resolve("geyser-mappings.json")));
            plugin.getLogger().info("Forge Bedrock pack and mappings installed before Geyser startup");
        }
    }
    private Path findGeyser()throws IOException{
        Path plugins=plugin.getDataFolder().toPath().toAbsolutePath().getParent();
        for(String name:List.of("Geyser-Spigot","Geyser"))if(Files.isDirectory(plugins.resolve(name)))return plugins.resolve(name);
        try(var paths=Files.list(plugins)){
            for(Path jar:paths.filter(p->p.getFileName().toString().endsWith(".jar")).toList())try(var zip=new java.util.jar.JarFile(jar.toFile())){
                var descriptor=zip.getEntry("plugin.yml");if(descriptor==null)continue;
                String yaml=new String(zip.getInputStream(descriptor).readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                if(yaml.matches("(?s).*\\bname:\\s*['\"]?Geyser-Spigot['\"]?\\s.*"))return plugins.resolve("Geyser-Spigot");
            }catch(IOException ignored){}
        }
        return null;
    }
    private static void write(Path target,byte[] data)throws IOException{
        if(Files.exists(target)&&Arrays.equals(Files.readAllBytes(target),data))return;
        Files.createDirectories(target.getParent());Path tmp=Files.createTempFile(target.getParent(),"forge-",".tmp");
        try{Files.write(tmp,data);Files.move(tmp,target,StandardCopyOption.REPLACE_EXISTING);}finally{Files.deleteIfExists(tmp);}
    }
    void start(){
        if(bytes==null||!plugin.getConfig().getBoolean("resource-pack.enabled",true))return;
        try{
            Plugin garden=Bukkit.getPluginManager().getPlugin("Evergarden");
            if(garden!=null&&garden.isEnabled()){
                host=garden.getClass().getMethod("packs").invoke(garden);
                path=(String)host.getClass().getMethod("publishAddition",byte[].class,String.class).invoke(host,bytes,sha1);
            }
            if(path.isEmpty()){
                host=null;path="/forge/"+sha1+".zip";
                standalone=HttpServer.create(new InetSocketAddress(plugin.getConfig().getString("resource-pack.bind","0.0.0.0"),
                    Math.clamp(plugin.getConfig().getInt("resource-pack.port",25569),0,65535)),0);
                byte[] pack=bytes;String endpoint=path;
                standalone.createContext("/forge/",exchange->{
                    try{
                        if(!exchange.getRequestURI().getPath().equals(endpoint)){exchange.sendResponseHeaders(404,-1);return;}
                        String method=exchange.getRequestMethod();
                        if(!method.equals("GET")&&!method.equals("HEAD")){exchange.sendResponseHeaders(405,-1);return;}
                        exchange.getResponseHeaders().set("Content-Type","application/zip");
                        exchange.getResponseHeaders().set("Cache-Control","public, max-age=31536000, immutable");
                        exchange.getResponseHeaders().set("Content-Length",Integer.toString(pack.length));
                        exchange.sendResponseHeaders(200,method.equals("HEAD")?-1:pack.length);
                        if(method.equals("GET"))exchange.getResponseBody().write(pack);
                    }finally{exchange.close();}
                });
                executor=Executors.newFixedThreadPool(2,r->{Thread thread=new Thread(r,"forge-pack-http");thread.setDaemon(true);return thread;});
                standalone.setExecutor(executor);standalone.start();
            }
            for(String owner:List.of("advance-magic","Evergarden")){
                Plugin base=Bukkit.getPluginManager().getPlugin(owner);if(base==null||!base.isEnabled())continue;
                try{
                    Class<?> manager=Class.forName("com.example.advancemagic.pack.delivery.ResourcePackManager",true,base.getClass().getClassLoader());
                    delivery=manager.getMethod("connect",Plugin.class,UUID.class).invoke(null,plugin,PACK_ID);break;
                }catch(ReflectiveOperationException|LinkageError ignored){}
            }
            Bukkit.getPluginManager().registerEvents(this,plugin);
            for(Player player:Bukkit.getOnlinePlayers())offer(player,false);
        }catch(Exception error){failure=error.toString();plugin.getLogger().warning("Forge resource pack: "+failure);}
    }
    public String url(Player player){
        String configured=plugin.getConfig().getString("resource-pack.public-url","").trim();
        if(!configured.isEmpty())return configured.replace("{sha1}",sha1);
        try{
            if(host!=null)return (String)host.getClass().getMethod("urlForPath",String.class,Player.class,String.class,String.class)
                .invoke(host,path,player,"",plugin.getConfig().getString("resource-pack.public-host",""));
            if(standalone==null)return "";
            String name=plugin.getConfig().getString("resource-pack.public-host","").trim();
            if(name.isEmpty()&&player!=null&&player.getVirtualHost()!=null)name=player.getVirtualHost().getHostString();
            if(name.isEmpty())return "";
            return new URI("http",null,name,standalone.getAddress().getPort(),path,null,null).toASCIIString();
        }catch(Exception error){return "";}
    }
    private boolean bedrock(Player player){
        Plugin floodgate=Bukkit.getPluginManager().getPlugin("floodgate");if(floodgate==null)return false;
        try{
            Class<?> api=Class.forName("org.geysermc.floodgate.api.FloodgateApi",true,floodgate.getClass().getClassLoader());
            return (boolean)api.getMethod("isFloodgatePlayer",UUID.class).invoke(api.getMethod("getInstance").invoke(null),player.getUniqueId());
        }catch(ReflectiveOperationException|LinkageError error){return false;}
    }
    public void offer(Player player,boolean force){
        if(!plugin.getConfig().getBoolean("resource-pack.enabled",true)||!player.isOnline()||bedrock(player))return;
        String url=url(player);if(url.isEmpty())return;
        try{
            if(delivery!=null)delivery.getClass().getMethod("offer",Player.class,String.class,String.class,String.class,boolean.class,boolean.class)
                .invoke(delivery,player,url,sha1,"Evergarden Forge · อัญมณีและวัสดุช่าง",false,force);
            else player.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(PACK_ID,URI.create(url),sha1)).required(false)
                .prompt(Component.text("Evergarden Forge · อัญมณีและวัสดุช่าง")).build());
        }catch(Exception error){failure=error.toString();plugin.getLogger().warning("Cannot offer forge pack: "+failure);}
    }
    @EventHandler public void join(PlayerJoinEvent event){
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(event.getPlayer().isOnline())offer(event.getPlayer(),false);},2);
    }
    public String status(){return failure.isEmpty()?"Forge pack SHA-1 "+sha1+" · "+(host!=null?"Evergarden host":standalone!=null?"port "+standalone.getAddress().getPort():"disabled"):failure;}
    @Override public void close(){
        if(delivery instanceof AutoCloseable closeable)try{closeable.close();}catch(Exception ignored){}
        if(standalone!=null)standalone.stop(0);if(executor!=null)executor.shutdownNow();HandlerList.unregisterAll(this);
    }
    /** Preserve item identity and player names; only add the model belonging to the forge token. */
    public static boolean model(ForgePlugin plugin,ItemStack item){
        if(item==null||item.getType().isAir())return false;
        GemService.Gem gem=plugin.gems().read(item);Rarity rarity=plugin.affixes().materialRarity(item);
        String model=gem!=null?"gem/"+gem.type().id()+"_"+gem.tier():rarity!=null?"material/"+rarity.id():plugin.gems().isDust(item)?"gem_dust":null;
        if(model==null)return false;
        NamespacedKey key=new NamespacedKey("evergarden_forge",model);
        if(key.equals(item.getItemMeta().getItemModel()))return false;
        item.editMeta(meta->meta.setItemModel(key));return true;
    }
}
