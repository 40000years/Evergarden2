package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.*;
import java.util.*;

/** Recover actual landmarks from older placement rules; candidate positions alone grant no protection. */
final class LegacyLandmarkProtection {
    private record Sample(int x,int y,int z,Material material) {}
    private final VoidscapePlugin plugin;
    private final SkyWhaleLayout possibleWhales;
    private final SkyLandmarkLayout withWhales,withoutWhales;
    private final int spacing,size;
    private final Path file;
    private final Map<Long,List<SkyLandmarkLayout.Site>> candidates=new HashMap<>(),known=new HashMap<>();
    private final Map<SkyLandmarkLayout.Kind,List<Sample>> samples=new EnumMap<>(SkyLandmarkLayout.Kind.class);
    private final Set<SkyLandmarkLayout.Site> rejected=new HashSet<>();
    LegacyLandmarkProtection(VoidscapePlugin plugin){
        this.plugin=plugin;spacing=plugin.skyWhales().spacingChunks();size=spacing*16;
        long seed=plugin.world().getSeed();
        // The saved rate can no longer accept an old cell. Recover positions independently of its roll.
        possibleWhales=new SkyWhaleLayout(seed,plugin.layout(),spacing,1);
        withWhales=new SkyLandmarkLayout(seed,plugin.layout(),possibleWhales,spacing,1);
        withoutWhales=new SkyLandmarkLayout(seed,plugin.layout(),new SkyWhaleLayout(seed,plugin.layout(),spacing,0),spacing,1);
        file=plugin.getDataFolder().toPath().resolve("protected-structures.yml");
        load();
    }
    private long cellKey(int x,int z){return SkyPlacementHistory.key(Math.floorDiv(x,size),Math.floorDiv(z,size));}
    private boolean enabled(SkyLandmarkLayout.Site site){return plugin.getConfig().getBoolean("structures."+site.kind().id+".enabled",true);}
    boolean protects(Block block){
        int x=block.getX(),z=block.getZ();long key=cellKey(x,z);
        for(var site:known.getOrDefault(key,List.of()))if(site.contains(x,z,0)&&enabled(site))return true;
        int gx=Math.floorDiv(x,size),gz=Math.floorDiv(z,size);
        for(var site:candidates.computeIfAbsent(key,ignored->candidates(gx,gz))){
            if(!site.contains(x,z,0)||!enabled(site)||rejected.contains(site))continue;
            if(!matches(site)){rejected.add(site);continue;}
            remember(site);save();
            plugin.getLogger().info("Recovered existing "+site.kind().id+" protection at "+site.x()+","+site.z());
            return true;
        }
        return false;
    }
    private List<SkyLandmarkLayout.Site> candidates(int gx,int gz){
        Set<SkyLandmarkLayout.Site> result=new LinkedHashSet<>();
        result.addAll(withWhales.cell(gx,gz));result.addAll(withoutWhales.cell(gx,gz));
        var whale=possibleWhales.cell(gx,gz);
        if(whale==null)return List.copyOf(result);
        List<SkyLandmarkLayout.Site> positions=new ArrayList<>();
        for(int x=5;x<spacing-5;x++)for(int z=5;z<spacing-5;z++){
            int bx=(gx*spacing+x)*16,bz=(gz*spacing+z)*16;
            if(Math.hypot(bx,bz)<480)continue;
            double wx=Math.max(0,Math.abs(bx-whale.x())-72)/115.0,wz=Math.max(0,Math.abs(bz-whale.z())-72)/65.0;
            if(wx*wx+wz*wz<=1)continue;
            boolean clear=true;
            for(var temple:plugin.layout().nearby(bx,bz))if(Math.abs(bx-temple.x())<104&&Math.abs(bz-temple.z())<104){clear=false;break;}
            if(clear)positions.add(new SkyLandmarkLayout.Site(SkyLandmarkLayout.Kind.OBSERVATORY,bx,bz));
        }
        Collections.shuffle(positions,new Random(DungeonLayout.mix(plugin.world().getSeed()^0x534b59474152444eL^(long)gx*341873128712L^(long)gz*132897987541L)));
        // Both historical pair rules shipped: the original OR and its later AND correction.
        for(boolean strict:new boolean[]{false,true}){
            outer:for(var first:positions)for(var second:positions){
                boolean dx=Math.abs(first.x()-second.x())>124,dz=Math.abs(first.z()-second.z())>124;
                if(strict?dx&&dz:dx||dz){
                    result.add(first);result.add(new SkyLandmarkLayout.Site(SkyLandmarkLayout.Kind.HANGING_GARDEN,second.x(),second.z()));break outer;
                }
            }
        }
        return List.copyOf(result);
    }
    private List<Sample> samples(SkyLandmarkLayout.Kind kind){
        int[][] points=kind==SkyLandmarkLayout.Kind.HANGING_GARDEN?new int[][]{
            {0,160,0},{-7,163,-7},{7,163,7},{-7,165,-7},{7,170,7},{-7,170,7},{7,165,-7},
            {0,182,0},{0,178,0},{0,185,0},{0,171,0},{0,173,0},{1,180,1},{0,76,5}
        }:new int[][]{
            {0,128,-15},{12,128,-15},{23,128,-15},{28,128,-15},{26,134,-15},{-26,134,-15},
            {0,134,-41},{0,155,-41},{0,182,-15},{-18,140,-33},{9,140,-39},{0,124,12}
        };
        List<Sample> result=new ArrayList<>();var blueprint=kind.blueprint();
        for(int[] p:points){var material=blueprint.at(p[0],p[1],p[2]);if(!material.isAir())result.add(new Sample(p[0],p[1],p[2],material));}
        return List.copyOf(result);
    }
    private boolean matches(SkyLandmarkLayout.Site site){
        var signature=samples.computeIfAbsent(site.kind(),this::samples);int matches=0;
        // Read only existing chunks; testing an empty historical candidate must never generate a structure.
        for(var sample:signature){
            int x=site.x()+sample.x(),z=site.z()+sample.z();
            if(!plugin.world().isChunkGenerated(Math.floorDiv(x,16),Math.floorDiv(z,16)))return false;
            if(plugin.world().getBlockAt(x,sample.y(),z).getType()==sample.material())matches++;
        }
        return signature.size()>=8&&matches>=Math.max(8,(signature.size()*2+2)/3);
    }
    private void remember(SkyLandmarkLayout.Site site){
        var sites=known.computeIfAbsent(cellKey(site.x(),site.z()),ignored->new ArrayList<>());
        if(!sites.contains(site))sites.add(site);
    }
    private void load(){
        if(!Files.isRegularFile(file))return;
        var saved=YamlConfiguration.loadConfiguration(file.toFile());
        if(!plugin.world().getUID().toString().equals(saved.getString("world-id")))return;
        var regions=saved.getConfigurationSection("regions");if(regions==null)return;
        for(String id:regions.getKeys(false))try{
            var kind=SkyLandmarkLayout.Kind.valueOf(regions.getString(id+".kind",""));
            remember(new SkyLandmarkLayout.Site(kind,regions.getInt(id+".x"),regions.getInt(id+".z")));
        }catch(IllegalArgumentException bad){plugin.getLogger().warning("Ignored invalid protected structure record "+id);}
    }
    private void save(){
        var saved=new YamlConfiguration();saved.set("world-id",plugin.world().getUID().toString());
        for(var sites:known.values())for(var site:sites){
            String path="regions."+site.kind().id+"_"+site.x()+"_"+site.z();
            saved.set(path+".kind",site.kind().name());saved.set(path+".x",site.x());saved.set(path+".z",site.z());
        }
        Path temporary=file.resolveSibling(file.getFileName()+".tmp");
        try{
            saved.save(temporary.toFile());
            try{Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException ignored){Files.move(temporary,file,StandardCopyOption.REPLACE_EXISTING);}
        }catch(Exception error){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot persist recovered structure protection",error);}
    }
}
