package com.example.voidscape.item;

import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Remove retired recipe-book keys before Minecraft deserializes a player's recipe book. */
public final class RetiredRecipeData implements Listener {
    private final JavaPlugin plugin;
    private final List<Path> folders=new ArrayList<>();
    public RetiredRecipeData(JavaPlugin plugin){this.plugin=plugin;}
    public void start() throws IOException {
        Path root=Bukkit.getWorldContainer().toPath().toRealPath();
        try(var worlds=Files.list(root)){
            for(Path world:worlds.filter(Files::isDirectory).toList()){
                Path folder=world.resolve("playerdata");
                if(Files.isDirectory(folder)&&folder.toRealPath().startsWith(root))folders.add(folder.toRealPath());
            }
        }
        int changed=0;
        for(Path folder:folders)try(var files=Files.list(folder)){
            for(Path file:files.filter(p->p.getFileName().toString().matches("[0-9a-fA-F-]{36}\\.dat(?:_old)?")).toList()){
                try{if(cleanFile(file))changed++;}catch(IOException error){plugin.getLogger().warning("Cannot migrate recipe book in "+file+": "+error.getMessage());}
            }
        }
        if(changed>0)plugin.getLogger().info("Removed retired recipe-book keys from "+changed+" player data files before login; original files backed up.");
    }
    @EventHandler(priority=EventPriority.LOWEST) public synchronized void login(AsyncPlayerPreLoginEvent event){
        if(Bukkit.getPlayer(event.getUniqueId())!=null)return;
        for(Path folder:folders)for(String suffix:List.of(".dat",".dat_old")){
            Path file=folder.resolve(event.getUniqueId()+suffix);
            if(Files.isRegularFile(file))try{cleanFile(file);}catch(IOException error){plugin.getLogger().warning("Cannot migrate recipe book for "+event.getUniqueId()+": "+error.getMessage());}
        }
    }
    private synchronized boolean cleanFile(Path file) throws IOException {
        if(Files.isSymbolicLink(file))return false;
        byte[] original=Files.readAllBytes(file),decoded;
        try(var input=new GZIPInputStream(new ByteArrayInputStream(original))){decoded=input.readNBytes(64*1024*1024+1);}
        if(decoded.length>64*1024*1024)throw new IOException("Player NBT exceeds migration limit");
        byte[] cleaned=cleanNbt(decoded);if(Arrays.equals(decoded,cleaned))return false;
        Path backup=plugin.getDataFolder().toPath().resolve("retired-recipe-backups").resolve(file.getParent().getParent().getFileName().toString()).resolve(file.getFileName()+"."+System.currentTimeMillis()+".bak");
        Files.createDirectories(backup.getParent());Files.write(backup,original,StandardOpenOption.CREATE_NEW);
        Path temporary=Files.createTempFile(file.getParent(),"recipe-cleanup-",".tmp");
        try{
            try(var out=new GZIPOutputStream(Files.newOutputStream(temporary))){out.write(cleaned);}
            if(!Arrays.equals(original,Files.readAllBytes(file)))throw new IOException("Player data changed during migration; left current file untouched");
            try{Files.move(temporary,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException unsupported){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temporary);}
        return true;
    }
    public static byte[] cleanNbt(byte[] bytes) throws IOException {
        var parser=new Parser(bytes);int type=parser.input.readUnsignedByte();
        if(type!=10)throw new IOException("Expected player compound NBT");
        parser.input.readUTF();int header=parser.position();byte[] body=parser.payload(10,List.of(),0);
        if(parser.input.available()!=0)throw new IOException("Trailing player NBT bytes");
        var out=new ByteArrayOutputStream();out.write(bytes,0,header);out.write(body);return out.toByteArray();
    }
    private static boolean retired(String id){
        return (id.startsWith("voidscape:")||id.startsWith("evergarden:"))&&RemovedRelics.RECIPES.contains(id.substring(id.indexOf(':')+1));
    }
    private static final class Parser {
        final byte[] bytes;final DataInputStream input;
        Parser(byte[] bytes){this.bytes=bytes;input=new DataInputStream(new ByteArrayInputStream(bytes));}
        int position() throws IOException{return bytes.length-input.available();}
        int length() throws IOException{int length=input.readInt();if(length<0||length>64*1024*1024)throw new IOException("Invalid NBT array/list length");return length;}
        void skip(long amount) throws IOException{if(amount>input.available())throw new EOFException();input.skipNBytes(amount);}
        byte[] payload(int type,List<String> path,int depth) throws IOException {
            if(depth>128)throw new IOException("NBT nesting too deep");int start=position();
            switch(type){
                case 0 -> {}
                case 1 -> skip(1);case 2 -> skip(2);case 3,5 -> skip(4);case 4,6 -> skip(8);
                case 7 -> skip(length());case 11 -> skip(4L*length());case 12 -> skip(8L*length());
                case 8 -> input.readUTF();
                case 9 -> {
                    int child=input.readUnsignedByte(),count=length();
                    boolean target=child==8&&path.size()==2&&path.get(0).equals("recipeBook")&&Set.of("recipes","toBeDisplayed").contains(path.get(1));
                    var entries=new ByteArrayOutputStream();int keep=0;
                    for(int index=0;index<count;index++){
                        int entry=position();
                        if(target){String id=input.readUTF();if(retired(id))continue;entries.write(bytes,entry,position()-entry);keep++;}
                        else payload(child,path,depth+1);
                    }
                    if(target){var out=new ByteArrayOutputStream();var writer=new DataOutputStream(out);writer.writeByte(8);writer.writeInt(keep);entries.writeTo(out);return out.toByteArray();}
                }
                case 10 -> {
                    var out=new ByteArrayOutputStream();
                    for(;;){int header=position(),child=input.readUnsignedByte();if(child==0){out.write(0);break;}
                        String name=input.readUTF();out.write(bytes,header,position()-header);
                        List<String> childPath=new ArrayList<>(path);childPath.add(name);out.write(payload(child,childPath,depth+1));
                    }
                    return out.toByteArray();
                }
                default -> throw new IOException("Unknown NBT tag "+type);
            }
            return Arrays.copyOfRange(bytes,start,position());
        }
    }
}
