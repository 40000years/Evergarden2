package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import com.example.advancemagic.pack.delivery.MirrorLookup;
import com.example.advancemagic.pack.delivery.PackMirror;
import com.example.advancemagic.pack.delivery.ResourcePackManager;
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
import java.util.regex.*;
import java.util.zip.*;

/** A separately addressed addon: never changes the published relic pack or its hash. */
public final class JudgePackService implements Listener,AutoCloseable {
    public static final UUID PACK_ID=UUID.fromString("b198c85c-c302-4c62-a365-a358fa544509");
    private final VoidscapePlugin plugin;
    private final Map<UUID,PlayerResourcePackStatusEvent.Status> statuses=new HashMap<>();
    private ResourcePackManager.Client delivery;
    private String sha1="",failure="",modelInfo="unknown design · check the installed JAR";
    private String servedPath="";
    private byte[] bundled;
    private boolean nativePetsIncluded;
    private boolean modelEngineIncluded;
    /** The public copy of exactly the pack now served, offered first once verified. */
    private final MirrorLookup mirror;
    private long startedAt=System.nanoTime();
    /** How long a joining player's batch may wait for ModelEngine's first import after a restart. */
    private static final int HOLD_SECONDS=30;
    public JudgePackService(VoidscapePlugin plugin){
        this.plugin=plugin;
        mirror=new MirrorLookup(plugin,"judge",30,p->offer(p,false));
        try(InputStream input=plugin.getResource("judge-design.json")){
            if(input==null)throw new IOException("Bundled Judge design manifest is missing");
            var design=new com.google.gson.JsonParser().parse(new InputStreamReader(input,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            modelInfo=design.get("revision").getAsString()+" | "+design.get("name").getAsString()+" | "+
                design.get("models").getAsInt()+" models / "+design.get("components").getAsInt()+" sculpture components | Bedrock bundle: "+design.get("bedrock-version").getAsString();
            plugin.getLogger().info("Ancient Judge model: "+modelInfo);
        }catch(IOException|RuntimeException error){plugin.getLogger().warning("Cannot read Judge design manifest: "+error.getMessage());}
    }
    public String modelInfo(){return modelInfo;}
    /** Native pet sculptures use this pack and never consume ModelEngine blueprint slots. */
    public boolean nativePetsIncluded(){return nativePetsIncluded&&failure.isEmpty();}
    public void start(){
        startedAt=System.nanoTime();
        delivery=ResourcePackManager.connect(plugin,PACK_ID);
        if(!plugin.getConfig().getBoolean("world-boss.visuals.pack.enabled",true))return;
        try(InputStream source=plugin.getResource("resource-packs/judge-java.zip")){
            if(source==null)throw new IOException("Bundled Judge pack is missing");
            Map<String,byte[]> entries=new TreeMap<>();readZip(source.readAllBytes(),entries,false);
            try(InputStream pets=plugin.getResource("native-pets.zip")){
                if(pets==null)throw new IOException("Bundled native pet graphics are missing");
                readZip(pets.readAllBytes(),entries,true);
                for(var species:com.example.voidscape.pet.PetCatalog.Species.values()){
                    if(species.mythical())continue;
                    for(var stage:com.example.voidscape.pet.PetCatalog.Stage.values()){
                        String id=species.id+"_"+stage.id;
                        if(entries.keySet().stream().noneMatch(n->n.startsWith("assets/evergarden/items/pet_native/"+id+"/"))
                            ||!entries.containsKey("assets/evergarden/textures/item/pet_native/"+id+"_0.png"))
                            throw new IOException("Native pet graphics incomplete: "+id);
                    }
                }
            }
            // Menu portraits of every pet; their absence only costs the /pet menu its pictures.
            try(InputStream portraits=plugin.getResource("pet-portraits.zip")){
                if(portraits!=null)readZip(portraits.readAllBytes(),entries,true);
                else plugin.getLogger().warning("Pet menu portraits are missing from the plugin JAR; /pet menus will show placeholder icons.");
            }
            byte[] bytes=zip(entries);bundled=bytes;nativePetsIncluded=true;sha1=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(bytes));
            lookForMirror(entries);
            Path folder=plugin.getDataFolder().toPath().resolve("resource-packs");Files.createDirectories(folder);
            Path temp=Files.createTempFile(folder,"judge-",".tmp");
            try{Files.write(temp,bytes);Files.move(temp,folder.resolve("judge-java.zip"),StandardCopyOption.REPLACE_EXISTING);}
            finally{Files.deleteIfExists(temp);}
            if(plugin.packs()!=null)servedPath=plugin.packs().publishAddition(bytes,sha1);
            if(servedPath.isEmpty()&&plugin.getConfig().getString("world-boss.visuals.pack.url","").isBlank())
                throw new IOException("Shared Evergarden pack host is unavailable; Judge graphics need the existing resource-pack port.");
            if(!servedPath.isEmpty())plugin.getLogger().info("Ancient Judge graphics added to the shared Evergarden HTTP host; /evergarden boss pack shows the client URL and status.");
        }catch(IOException|GeneralSecurityException|IllegalArgumentException error){
            failure=error.getMessage();plugin.getLogger().warning("Judge pack unavailable: "+failure
                +(plugin.getConfig().getBoolean("world-boss.visuals.pack.required",true)
                    ?". Required Java pack delivery must be fixed before players can remain connected."
                    :". Java players retain the solid vanilla sculpture."));
        }
    }
    /** Publish the imported rig through the existing boss pack URL and port. */
    public boolean includeModelEngine(Path generated)throws IOException,GeneralSecurityException{
        if(bundled==null||plugin.packs()==null)return false;
        Map<String,byte[]> entries=new TreeMap<>();
        readZip(bundled,entries,false);
        Map<String,byte[]> engine=new TreeMap<>();
        readZip(Files.readAllBytes(generated),engine,true);
        engine.keySet().removeAll(unusedModelEngineMaps(generated.getParent()));
        contentNamedTextures(engine,generated.getParent());
        for(var e:engine.entrySet()){
            if(entries.containsKey(e.getKey())&&!Arrays.equals(entries.get(e.getKey()),e.getValue()))
                throw new IOException("Graphics addon collides with an existing asset: "+e.getKey());
            entries.put(e.getKey(),e.getValue());
        }
        if(entries.keySet().stream().noneMatch(n->n.startsWith("assets/modelengine/")&&n.contains("arkhaia")))
            throw new IOException("Generated pack contains no Arkhaia assets");
        byte[] merged=zip(entries);String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(merged));
        if(hash.equals(sha1))return true;
        String nextPath=plugin.packs().publishAddition(merged,hash);
        if(nextPath.isEmpty())return false;
        Path dest=plugin.getDataFolder().toPath().resolve("resource-packs/judge-java.zip");
        Path temp=Files.createTempFile(dest.getParent(),"arkhaia-",".tmp");
        try{Files.write(temp,merged);Files.move(temp,dest,StandardCopyOption.REPLACE_EXISTING);}
        finally{Files.deleteIfExists(temp);}
        servedPath=nextPath;sha1=hash;modelEngineIncluded=true;statuses.clear();
        lookForMirror(entries);
        for(Player player:Bukkit.getOnlinePlayers())resend(player);
        return true;
    }
    /**
     * ModelEngine numbers its custom_model_data fallback maps in import order, which its threads change
     * on every start. Rigs render through item_model unless Force-Custom-Model-Data is set, so these maps
     * are left out: the Judge pack then keeps one hash across restarts and clients reuse their copy.
     */
    static Set<String> unusedModelEngineMaps(Path engineFolder){
        var config=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(engineFolder.resolve("config.yml").toFile());
        if(config.getBoolean("Model-Engine.Force-Custom-Model-Data",false))return Set.of();
        Set<String> items=new TreeSet<>(config.getStringList("Model-Generator.Item-Models"));
        items.add(config.getString("Model-Generator.Item-Model","LEATHER_HORSE_ARMOR"));
        Set<String> unused=new TreeSet<>();
        for(String item:items){
            String name=item.toLowerCase(Locale.ROOT).replaceFirst("^minecraft:","");
            if(!name.matches("[a-z0-9_]+"))continue;
            unused.add("assets/minecraft/items/"+name+".json");
            unused.add("assets/minecraft/models/item/"+name+".json");
        }
        return unused;
    }
    /**
     * ModelEngine names a rig texture after its own file, and gives a UUID name to a second blueprint's texture
     * of the same name; which blueprint keeps the plain name depends on import order. Naming every rig texture
     * after its contents (the atlas reads the whole folder) makes the pack identical on every start.
     */
    static void contentNamedTextures(Map<String,byte[]> engine,Path engineFolder)throws IOException,GeneralSecurityException{
        var config=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(engineFolder.resolve("config.yml").toFile());
        String namespace=config.getString("Model-Generator.Namespace","modelengine");
        String folder=config.getString("Model-Generator.Texture-Path","entity/models").replaceAll("^/+|/+$","");
        if(!namespace.matches("[a-z0-9_.-]+")||!folder.matches("[a-z0-9_./-]+"))return;
        String prefix="assets/"+namespace+"/textures/"+folder+"/";
        Map<String,String> renamed=new HashMap<>();
        for(var e:engine.entrySet()){
            String file=e.getKey().startsWith(prefix)?e.getKey().substring(prefix.length()):"";
            if(!file.endsWith(".png")||file.contains("/"))continue;
            var digest=MessageDigest.getInstance("SHA-1");
            digest.update(e.getValue());digest.update((byte)0);
            digest.update(engine.getOrDefault(e.getKey()+".mcmeta",new byte[0]));
            renamed.put(file.substring(0,file.length()-4),HexFormat.of().formatHex(digest.digest()).substring(0,20));
        }
        if(renamed.isEmpty())return;
        Pattern reference=Pattern.compile("\""+Pattern.quote(namespace+":"+folder+"/")+"([^\"/]+)\"");
        Map<String,byte[]> result=new TreeMap<>();
        for(var e:engine.entrySet()){
            String name=e.getKey();byte[] body=e.getValue();
            String file=name.startsWith(prefix)?name.substring(prefix.length()):"";
            String suffix=file.endsWith(".png.mcmeta")?".png.mcmeta":file.endsWith(".png")?".png":null;
            String base=suffix==null?null:file.substring(0,file.length()-suffix.length());
            if(base!=null&&!file.contains("/")&&renamed.containsKey(base))name=prefix+renamed.get(base)+suffix;
            else if(name.startsWith("assets/"+namespace+"/models/")&&name.endsWith(".json")){
                Matcher match=reference.matcher(new String(body,java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder text=new StringBuilder();
                while(match.find())match.appendReplacement(text,Matcher.quoteReplacement("\""+namespace+":"+folder+"/"+renamed.getOrDefault(match.group(1),match.group(1))+"\""));
                match.appendTail(text);
                body=text.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
            }
            byte[] previous=result.put(name,body);
            if(previous!=null&&!Arrays.equals(previous,body))throw new IOException("ModelEngine textures collide after renaming: "+name);
        }
        engine.clear();engine.putAll(result);
    }
    /** Players offered Judge while this check runs wait for it, so ModelEngine's import never ships a server-only address. */
    private void lookForMirror(Map<String,byte[]> entries){
        boolean external=!plugin.getConfig().getString("world-boss.visuals.pack.url","").isBlank()||plugin.packs()==null;
        mirror.start(PackMirror.fingerprint(entries),external?List.of():plugin.packs().mirrors());
    }
    private static byte[] zip(Map<String,byte[]> entries)throws IOException{
        var buffer=new ByteArrayOutputStream();
        try(var zip=new ZipOutputStream(buffer)){
            for(var e:entries.entrySet()){var entry=new ZipEntry(e.getKey());entry.setTime(0);zip.putNextEntry(entry);zip.write(e.getValue());zip.closeEntry();}
        }
        return buffer.toByteArray();
    }
    private static void readZip(byte[] bytes,Map<String,byte[]> entries,boolean addon)throws IOException{
        try(var zip=new ZipInputStream(new ByteArrayInputStream(bytes))){
            for(ZipEntry e;(e=zip.getNextEntry())!=null;){
                String name=e.getName();if(e.isDirectory())continue;
                // Player-limb shaders are unrelated to this boss and affect global rendering.
                if(addon&&(!name.startsWith("assets/")||name.startsWith("assets/minecraft/shaders/")))continue;
                if(name.contains("..")||name.startsWith("/"))throw new IOException("Invalid pack entry");
                byte[] value=zip.readAllBytes();
                if(addon&&entries.containsKey(name)&&!Arrays.equals(entries.get(name),value))
                    throw new IOException("Graphics addon collides with an existing asset: "+name);
                entries.put(name,value);
            }
        }
    }
    private record Platform(boolean bedrock,long checked){}
    private static final Map<UUID,Platform> PLATFORMS=new java.util.concurrent.ConcurrentHashMap<>();
    /**
     * Pet and NPC visibility asks this for every viewer several times a second, so the reflective
     * lookup is cached: a Bedrock answer for the whole session, a Java answer for ten seconds in
     * case Floodgate finished registering the player after the first question.
     */
    public static boolean bedrock(Player p){
        long now=System.currentTimeMillis();
        Platform known=PLATFORMS.get(p.getUniqueId());
        if(known!=null&&(known.bedrock()||now-known.checked()<10_000))return known.bedrock();
        boolean result=lookup(p);PLATFORMS.put(p.getUniqueId(),new Platform(result,now));return result;
    }
    private static boolean lookup(Player p){
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
        if(!modelEngineIncluded&&!external.isEmpty())return validated(external);
        if(servedPath.isEmpty()||plugin.packs()==null)return "";
        String base=plugin.getConfig().getString("world-boss.visuals.pack.host.public-url","").trim();
        String host=plugin.getConfig().getString("world-boss.visuals.pack.host.public-host","").trim();
        return plugin.packs().urlForPath(servedPath,p,base,host);
    }
    private static String validated(String value){
        try{URI u=URI.create(value);return List.of("http","https").contains(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null&&u.getFragment()==null?u.toASCIIString():"";}
        catch(IllegalArgumentException error){return "";}
    }
    public void offer(Player p){
        offer(p,false);
    }
    public void resend(Player p){
        offer(p,true);
    }
    private void offer(Player p,boolean force){
        if(bedrock(p)||!plugin.getConfig().getBoolean("world-boss.visuals.pack.enabled",true))return;
        if(mirror.defer(p,delivery))return;
        List<String> urls=new ArrayList<>(),hashes=new ArrayList<>();
        // A verified public copy of this exact pack first, then this server's own host.
        PackMirror.Copy copy=mirror.copy();
        if(copy!=null){urls.add(copy.url());hashes.add(copy.sha1());}
        String url=url(p);
        if(!url.isEmpty()&&!sha1.isEmpty()){urls.add(url);hashes.add(sha1);}
        if(urls.isEmpty()){unavailable(p);return;}
        try{if(delivery!=null)delivery.offer(p,urls,hashes,"Evergarden: Crimson Judge + magic circles",
            plugin.getConfig().getBoolean("world-boss.visuals.pack.required",true),force);}
        catch(IllegalArgumentException error){plugin.getLogger().warning("Cannot offer Judge pack: "+error.getMessage());unavailable(p);}
    }
    private void unavailable(Player p){if(delivery!=null&&plugin.getConfig().getBoolean("world-boss.visuals.pack.required",true))delivery.unavailable(p);}
    /**
     * Judge goes out in the same batch as the Magic and Evergarden packs, so the client reloads once.
     * Just after a restart ModelEngine may still be importing; the batch then waits for it briefly.
     * If a download in the batch fails, the coordinator sends the packs again one at a time.
     */
    @EventHandler public void join(PlayerJoinEvent e){
        Player p=e.getPlayer();
        boolean importing=!modelEngineIncluded&&Bukkit.getPluginManager().isPluginEnabled("ModelEngine")
            &&System.nanoTime()-startedAt<180_000_000_000L;
        if(importing&&delivery!=null&&!bedrock(p)&&plugin.getConfig().getBoolean("world-boss.visuals.pack.enabled",true)){
            delivery.hold(p,HOLD_SECONDS);
            Bukkit.getScheduler().runTaskLater(plugin,()->{if(p.isOnline())offer(p);},HOLD_SECONDS*20L);
            return;
        }
        offer(p);
    }
    @EventHandler public void status(PlayerResourcePackStatusEvent e){
        if(!PACK_ID.equals(e.getID()))return;
        Player p=e.getPlayer();var status=e.getStatus();
        statuses.put(p.getUniqueId(),status);
        String name=status.name();
        // The coordinator is already sending it again (from the next address, or on its own).
        if(delivery!=null&&List.of("QUEUED","OFFERED","ACCEPTED","DOWNLOADED").contains(delivery.status(p)))return;
        if(name.startsWith("FAILED")||name.equals("INVALID_URL")||name.equals("DISCARDED")||name.equals("DECLINED"))
            plugin.getLogger().warning("Judge pack for "+p.getName()+": "+name+" from "+url(p)
                +". The client could not fetch it from this address; check that HTTP reaches the server port, or set world-boss.visuals.pack.required: false.");
    }
    @EventHandler public void quit(PlayerQuitEvent e){statuses.remove(e.getPlayer().getUniqueId());PLATFORMS.remove(e.getPlayer().getUniqueId());mirror.forget(e.getPlayer());}
    public void describe(CommandSender sender){
        sender.sendMessage("Model: "+modelInfo);
        sender.sendMessage("Arkhaia: "+(plugin.arkhaiaModels()!=null&&plugin.arkhaiaModels().ready()
            ?"Model Engine articulated rig (Java)":"compatible Java poses; Model Engine rig is not ready"));
        if(plugin.arkhaiaBedrock()!=null)sender.sendMessage("Arkhaia: "+plugin.arkhaiaBedrock().status());
        if(plugin.arkhaiaModels()!=null)sender.sendMessage("Arkhaia blueprint: "+plugin.arkhaiaModels().modelInfo());
        sender.sendMessage("Ancient Judge graphics | Required: "+plugin.getConfig().getBoolean("world-boss.visuals.pack.required",true)+" | SHA-1: "+sha1);
        String sharedHost=plugin.packs()==null?"off":plugin.packs().hostLabel();
        sender.sendMessage("Host: "+(sharedHost.equals("off")?"external/off":"shared "+sharedHost)+" | URL: "+url(sender instanceof Player p?p:null));
        sender.sendMessage("Public copy: "+(mirror.copy()!=null?mirror.copy().url():mirror.fingerprint().isEmpty()?"not used"
            :(mirror.pending()?"checking ":"none verified for ")+mirror.fingerprint().substring(0,12)));
        if(sender instanceof Player p){
            sender.sendMessage(bedrock(p)?"Bedrock: bundled Evergarden attachables via Geyser":"Client: "+(delivery==null?"NOT_OFFERED":delivery.status(p)));
            if(delivery!=null)sender.sendMessage(delivery.describe(p));
        }
        if(!failure.isEmpty())sender.sendMessage("Host error: "+failure);
        sender.sendMessage("Retry: /evergarden boss pack resend | File: plugins/Evergarden/resource-packs/judge-java.zip");
    }
    @Override public void close(){nativePetsIncluded=false;if(delivery!=null){delivery.close();delivery=null;}statuses.clear();}
}
