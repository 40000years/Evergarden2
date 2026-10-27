import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.*;
import org.bukkit.*;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.*;
import java.util.*;

/** Real Paper-generated chunks and fixed blueprints in the disposable server only. */
public final class BiomeWorldProbe extends JavaPlugin {
    private int checks;
    private VoidscapePlugin plugin;
    private World world;
    private VoidGenerator generator;
    private void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;getLogger().info("PASS "+message);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{
            plugin=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");world=plugin.world();
            world.setGameRule(GameRule.RANDOM_TICK_SPEED,0);generator=(VoidGenerator)world.getGenerator();
            check(generator!=null&&generator.terrain()!=null,"live Evergarden world uses the new four-biome generator");
            if(Files.exists(Path.of("biome-world-checkpoint.txt")))restart();else probe();
            Files.writeString(Path.of("biome-world-result.txt"),"PASS "+checks+" checks: live terrain, exact materials, determinism, blueprint preservation, floating/attached terrain and restart\n");
        }catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"BIOME WORLD CHECK FAILED",error);try{Files.writeString(Path.of("biome-world-result.txt"),"FAIL "+error);}catch(Exception ignored){}}
        finally{Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);}
    },40);}
    private void probe()throws Exception {
        long seed=world.getSeed();
        var legacy=new VoidGenerator(seed,plugin.layout(),plugin.skyWhales(),true,plugin.skyLandmarks(),true,true,
                plugin.restorationLayout(),plugin.bossTemples(),true,plugin.bossStructures());
        for(int x=-368;x<=368;x+=16)for(int z=-368;z<=368;z+=16){
            if(Math.hypot(x,z)>384)continue;
            if(!generator.surface(x,z).equals(legacy.surface(x,z)))throw new AssertionError("Starter terrain changed "+x+","+z);
        }
        check(true,"spawn island and starter region retain exact legacy surface samples");
        for(var site:DungeonLayout.STARTER_SITES){
            int endZ=site.z()+(site.z()<0?28:-28);
            for(int p=0;p<=100;p++){
                int x=(int)Math.round(site.x()*p/100.0),z=(int)Math.round(endZ*p/100.0);
                var s=generator.surface(x,z);
                if(!s.land()||!s.path()||s.top()!=95)throw new AssertionError("Broken starter causeway "+x+","+z+" "+s);
            }
        }
        check(true,"all three starter causeways remain continuous at y95");
        var migrated=new BiomeTerrain(seed,3072,Set.of(SkyPlacementHistory.key(-6,5)));
        var preserving=new VoidGenerator(seed,plugin.layout(),plugin.skyWhales(),true,plugin.skyLandmarks(),true,true,
                plugin.restorationLayout(),plugin.bossTemples(),true,plugin.bossStructures(),migrated);
        for(int x=-3072;x<=-2561;x+=31)for(int z=2560;z<=3071;z+=31)
            if(!preserving.surface(x,z).equals(legacy.surface(x,z)))throw new AssertionError("Explored legacy surface changed at "+x+","+z);
        check(true,"explored region surface matches every sampled legacy field");
        var locations=new ArrayList<String>();
        var orderChunks=new ArrayList<long[]>();
        for(var kind:BiomeTerrain.Kind.values()){
            var target=generator.terrain().locate(kind,0,0);check(target!=null,"locator finds "+kind);
            int[] point=findClear(kind,target.x(),target.z());int cx=Math.floorDiv(point[0],16),cz=Math.floorDiv(point[1],16);
            var actual=world.getChunkAt(cx,cz).getChunkSnapshot(false,true,false);
            var raw=raw(cx,cz,123);int tested=0;
            for(int lx=0;lx<16;lx++)for(int lz=0;lz<16;lz++){
                int x=cx*16+lx,z=cz*16+lz;var s=generator.surface(x,z);
                if(!s.land()||generator.terrain().newWeight(x,z)<1||!generator.canDecorate(x,z,1))continue;
                // Under the surface, there are no flowers, physics or persistent-tree decorators.
                int y=s.top()-5;var type=actual.getBlockType(lx,y,lz);
                if(type!=raw.getType(lx,y,lz))throw new AssertionError("Live material mismatch "+kind+" "+x+","+y+","+z+": "+type+" / "+raw.getType(lx,y,lz));
                if(actual.getBiome(lx,s.top(),lz)!=generator.terrain().kind(x,z).vanilla())throw new AssertionError("Live biome provider mismatch "+kind);
                tested++;
            }
            check(tested>150,kind+": "+tested+" live columns match exact generator materials and biome ids");
            orderChunks.add(new long[]{cx,cz});orderChunks.add(new long[]{cx+1,cz});orderChunks.add(new long[]{cx,cz+1});
            int y=generator.surface(point[0],point[1]).top()+22;
            world.getBlockAt(point[0],y,point[1]).setType(Material.GOLD_BLOCK,false);
            locations.add(kind.id+","+point[0]+","+y+","+point[1]+","+rawHash(raw));
        }
        var forward=new HashMap<String,Long>();for(var c:orderChunks)forward.put(c[0]+","+c[1],rawHash(raw((int)c[0],(int)c[1],1)));
        Collections.reverse(orderChunks);
        for(var c:orderChunks)if(forward.get(c[0]+","+c[1])!=rawHash(raw((int)c[0],(int)c[1],999)))throw new AssertionError("Chunk generation depends on order/Random "+Arrays.toString(c));
        check(true,"12 neighboring chunks match in reverse generation order with different Random inputs");
        structures();
        exportScenery();
        world.save();
        Files.writeString(Path.of("biome-world-checkpoint.txt"),String.join("\n",locations));
        Files.writeString(Path.of("biome-history-before.txt"),generator.terrain().legacyRegions().toString());
    }
    private int[] findClear(BiomeTerrain.Kind kind,int x,int z){
        for(int r=0;r<=512;r+=16)for(int dx=-r;dx<=r;dx+=16)for(int dz=-r;dz<=r;dz+=16){
            int px=x+dx,pz=z+dz;var s=generator.surface(px,pz);
            if(s.land()&&generator.terrain().kind(px,pz)==kind&&generator.terrain().newWeight(px,pz)==1&&generator.canDecorate(px,pz,32))return new int[]{px,pz};
        }
        throw new AssertionError("No unreserved land around "+kind);
    }
    private void structures(){
        for(var kind:SkyLandmarkLayout.Kind.values()){
            var site=plugin.skyLandmarks().nearest(kind,0,0,16);check(site!=null,"existing landmark locator remains available: "+kind);
            checkBlueprint(kind.id,site.x(),site.z(),70,200,(d,cx,cz)->kind.blueprint().render(d,cx,cz,site.x(),site.z()));
        }
        var whale=plugin.skyWhales().nearest(0,0,16);check(whale!=null,"existing whale locator remains available");
        checkBlueprint("sky-whale",whale.x(),whale.z(),SkyWhale.MIN_Y,SkyWhale.MAX_Y,(d,cx,cz)->SkyWhale.render(d,cx,cz,whale));
        var temple=plugin.bossTemples().nearest(0,0,16);check(temple!=null,"existing ancient-temple locator remains available");
        checkBlueprint("ancient-temple",temple.x(),temple.z(),WorldBossTemple.MIN_Y,WorldBossTemple.MAX_Y,(d,cx,cz)->WorldBossTemple.blueprint().render(d,cx,cz,temple.x(),temple.z()));
        for(var kind:BossStructureLayout.Kind.values()){
            var site=plugin.bossStructures().nearest(kind,0,0,32);check(site!=null,"existing architecture locator remains available: "+kind);
            checkBlueprint(kind.id,site.x(),site.z(),36,kind.maxY,(d,cx,cz)->site.blueprint().render(d,cx,cz,site.x(),site.z()));
        }
        int floating=0,attached=0,migrationChecks=0;
        var seen=new HashSet<String>();
        outer:for(int gx=-9;gx<=9;gx++)for(int gz=-9;gz<=9;gz++){
            for(var anchor:generator.structureTerrain().nearby(gx*512+256,gz*512+256,0)){
                if(!seen.add(anchor.id()+","+anchor.x()+","+anchor.z()))continue;
                if(generator.terrain().newWeight(anchor.x(),anchor.z())<1)continue;
                int tested=0;
                for(int dx=-anchor.radiusX();dx<=anchor.radiusX();dx+=8)for(int dz=-anchor.radiusZ();dz<=anchor.radiusZ();dz+=8){
                    int lowest=anchor.underside().lowest(dx,dz);if(lowest==Integer.MAX_VALUE)continue;
                    int x=anchor.x()+dx,z=anchor.z()+dz;var natural=generator.terrain().sample(x,z);
                    var shaped=generator.structureTerrain().shape(x,z,natural.top(),natural.depth(),List.of(anchor));
                    if(anchor.floating()&&shaped.top()>lowest-anchor.clearance())throw new AssertionError("Buried floating underside "+anchor.id());
                    if(!anchor.floating()&&shaped.top()!=Math.min(lowest-1,anchor.floorY()-1))throw new AssertionError("Attached terrain does not join safe foundation "+anchor.id()+" "+shaped+" lowest="+lowest);
                    if(anchor.floating()&&migrationChecks<8){
                        int rx=Math.floorDiv(x,512),rz=Math.floorDiv(z,512);
                        for(int[] region:new int[][]{{rx-1,rz},{rx+1,rz},{rx,rz-1},{rx,rz+1}}){
                            var migrationTerrain=new BiomeTerrain(world.getSeed(),3072,Set.of(SkyPlacementHistory.key(region[0],region[1])));
                            double weight=migrationTerrain.newWeight(x,z);if(weight<=.01||weight>=.99)continue;
                            var migrating=new VoidGenerator(world.getSeed(),plugin.layout(),plugin.skyWhales(),true,plugin.skyLandmarks(),true,true,
                                    plugin.restorationLayout(),plugin.bossTemples(),true,plugin.bossStructures(),migrationTerrain);
                            var migratedSurface=migrating.surface(x,z);
                            if(migratedSurface.top()>lowest-anchor.clearance())throw new AssertionError("Migration blend buries floating underside "+anchor.id()+" weight="+weight+" top="+migratedSurface.top()+" lowest="+lowest);
                            migrationChecks++;break;
                        }
                    }
                    tested++;
                }
                if(tested==0)throw new AssertionError("No underside columns "+anchor.id());
                if(anchor.floating())floating++;else attached++;
                if(floating>=4&&attached>=4)break outer;
            }
        }
        check(floating>=4&&attached>=4,"both floating clearances and attached foundation joins validate ("+floating+" floating, "+attached+" attached)");
        check(migrationChecks>=4,"legacy skirt blend preserves floating blueprint clearance ("+migrationChecks+" columns)");
    }
    @FunctionalInterface private interface Render{void apply(ChunkData data,int cx,int cz);}
    private void checkBlueprint(String name,int x,int z,int minY,int maxY,Render render){
        int cx=Math.floorDiv(x,16),cz=Math.floorDiv(z,16);long solids=0;
        for(int[] offset:new int[][]{{0,0},{-1,0},{0,-1},{1,1}}){
            int a=cx+offset[0],b=cz+offset[1];var expected=Bukkit.getServer().createChunkData(world);render.apply(expected,a,b);
            // The existing generator deliberately furnishes altars after its architecture.
            // Retain that exact overlay in the expected structure, including its air edits.
            for(var shrine:plugin.restorationLayout().cell(Math.floorDiv(a*16,plugin.restorationLayout().cellSize()),Math.floorDiv(b*16,plugin.restorationLayout().cellSize())))
                RestorationShrine.render(expected,a,b,shrine);
            var actual=raw(a,b,11);
            for(int lx=0;lx<16;lx++)for(int lz=0;lz<16;lz++)for(int y=minY;y<=maxY;y++){
                Material wanted=expected.getType(lx,y,lz);if(wanted.isAir())continue;
                if(actual.getType(lx,y,lz)!=wanted)throw new AssertionError("Blueprint overwritten "+name+" at "+(a*16+lx)+","+y+","+(b*16+lz)+" expected="+wanted+" actual="+actual.getType(lx,y,lz));
                solids++;
            }
        }
        check(solids>200,name+": "+solids+" fixed blueprint blocks survive terrain generation in central and seam chunks");
    }
    private void restart()throws Exception{
        Set<Long> captured=SkyPlacementHistory.captureWorld(Bukkit.getWorldContainer().toPath(),Bukkit.getWorlds().getFirst().getWorldFolder().toPath(),world.getName(),32);
        for(String line:Files.readAllLines(Path.of("biome-world-checkpoint.txt"))){
            var v=line.split(",");int x=Integer.parseInt(v[1]),y=Integer.parseInt(v[2]),z=Integer.parseInt(v[3]);
            check(world.getBlockAt(x,y,z).getType()==Material.GOLD_BLOCK,v[0]+": player marker survives real restart");
            check(rawHash(raw(Math.floorDiv(x,16),Math.floorDiv(z,16),123))==Long.parseLong(v[4]),v[0]+": restart retains exact generator output");
            check(captured.contains(SkyPlacementHistory.key(Math.floorDiv(x,512),Math.floorDiv(z,512))),v[0]+": migration history discovers actual saved region through Paper dimension paths");
        }
        check(Files.readString(Path.of("biome-history-before.txt")).equals(generator.terrain().legacyRegions().toString()),"newly generated region files do not become legacy regions on restart");
    }
    private void exportScenery()throws Exception{
        Path directory=Path.of("scenery");Files.createDirectories(directory);var manifest=new ArrayList<String>();
        for(var kind:BiomeTerrain.Kind.values()){
            int[] anchor=sceneryAnchor(kind);int x0=anchor[0]-64,z0=anchor[1]-64,width=128,height=384,minY=-64;
            Material[] materials=Material.values();short[] blocks=new short[width*width*height];Arrays.fill(blocks,(short)Material.AIR.ordinal());
            for(int cx=Math.floorDiv(x0,16);cx<=Math.floorDiv(x0+width-1,16);cx++)for(int cz=Math.floorDiv(z0,16);cz<=Math.floorDiv(z0+width-1,16);cz++){
                var chunk=raw(cx,cz,17);
                for(int lx=0;lx<16;lx++)for(int lz=0;lz<16;lz++){
                    int x=cx*16+lx-x0,z=cz*16+lz-z0;if(x<0||x>=width||z<0||z>=width)continue;
                    for(int y=0;y<height;y++){
                        Material material=chunk.getType(lx,y+minY,lz);blocks[(x*width+z)*height+y]=(short)material.ordinal();
                        if(kind==BiomeTerrain.Kind.LUNAR_CITY&&material==Material.WATER
                                &&(!(chunk.getBlockData(lx,y+minY,lz) instanceof org.bukkit.block.data.Levelled level)||level.getLevel()!=0))
                            throw new AssertionError("Lunar pool has a flowing/non-source water block at "+(cx*16+lx)+","+(y+minY)+","+(cz*16+lz));
                    }
                }
            }
            int count=0;var palette=new EnumMap<Material,Integer>(Material.class);
            try(var writer=Files.newBufferedWriter(directory.resolve(kind.id+".csv"))){
                for(int x=0;x<width;x++)for(int z=0;z<width;z++)for(int y=0;y<height;y++){
                    int index=(x*width+z)*height+y;var material=materials[blocks[index]];if(material.isAir())continue;
                    boolean exposed=x==0||x==width-1||z==0||z==width-1||y==0||y==height-1;
                    if(!exposed)exposed=materials[blocks[index-height*width]].isAir()||materials[blocks[index+height*width]].isAir()
                            ||materials[blocks[index-height]].isAir()||materials[blocks[index+height]].isAir()
                            ||materials[blocks[index-1]].isAir()||materials[blocks[index+1]].isAir();
                    if(!exposed)continue;
                    writer.write((x-64)+","+(y+minY)+","+(z-64)+","+material.name()+"\n");count++;palette.merge(material,1,Integer::sum);
                }
            }
            if(kind==BiomeTerrain.Kind.ANCIENT_FOREST)check(palette.getOrDefault(Material.DARK_OAK_LOG,0)>300,"ancient giant-tree scenery contains exposed massive trunk/branches");
            if(kind==BiomeTerrain.Kind.LUNAR_CITY)check(palette.getOrDefault(Material.QUARTZ_BLOCK,0)+palette.getOrDefault(Material.SMOOTH_QUARTZ,0)>200,"lunar scenery contains actual generated city architecture");
            if(kind==BiomeTerrain.Kind.LUNAR_CITY)checkPools(blocks,width,height,materials);
            if(kind==BiomeTerrain.Kind.GLACIER)check(palette.getOrDefault(Material.BLUE_ICE,0)>1500,"glacier scenery contains extensive exposed blue ice");
            manifest.add(kind.id+","+anchor[0]+","+anchor[1]+","+count);
            getLogger().info("Scenery "+kind+": world "+anchor[0]+","+anchor[1]+"; "+count+" exposed actual blocks");
        }
        Files.writeString(directory.resolve("anchors.csv"),"kind,world_x,world_z,exposed_blocks\n"+String.join("\n",manifest));
        checkFissurePlaza();
    }
    private void checkPools(short[] blocks,int width,int height,Material[] materials){
        int water=0;
        for(int x=0;x<width;x++)for(int z=0;z<width;z++)for(int y=0;y<height;y++){
            int index=(x*width+z)*height+y;if(materials[blocks[index]]!=Material.WATER)continue;
            if(x==0||x==width-1||z==0||z==width-1||y==0)throw new AssertionError("Lunar pool reaches scenery crop edge");
            if(!materials[blocks[index-1]].isSolid())throw new AssertionError("Lunar reflecting pool has an open bottom at "+x+","+y+","+z);
            for(int offset:new int[]{-height*width,height*width,-height,height}){
                Material side=materials[blocks[index+offset]];
                if(side!=Material.WATER&&!side.isSolid())throw new AssertionError("Lunar reflecting pool can spill sideways at "+x+","+y+","+z+" neighbor="+side);
            }
            water++;
        }
        check(water>50,"all "+water+" reflecting-pool source blocks have solid bottoms and sealed sides");
    }
    private void checkFissurePlaza(){
        // A bounded geometry fixture excludes unrelated rare landmarks. It keeps
        // the real seed, continental sampler and complete scenery generator.
        VoidGenerator real=generator;
        try{
        long seed=world.getSeed();var fixtureLayout=new DungeonLayout(seed,32,0);
        var fixtureWhales=new SkyWhaleLayout(seed,fixtureLayout,32,0);
        var fixtureLandmarks=new SkyLandmarkLayout(seed,fixtureLayout,fixtureWhales,32,0);
        generator=new VoidGenerator(seed,fixtureLayout,fixtureWhales,false,fixtureLandmarks,false,false,null,null,false,null,
                new BiomeTerrain(seed,3072,Set.of()));
        int[] anchor=sceneryAnchor(BiomeTerrain.Kind.LUNAR_CITY,true);int floor=generator.surface(anchor[0],anchor[1]).top()+1;
        var chunks=new HashMap<Long,ChunkData>();int bridged=0,water=0;
        for(int dx=-24;dx<=24;dx++)for(int dz=-24;dz<=24;dz++){
            if(Math.hypot(dx,dz)>24)continue;int x=anchor[0]+dx,z=anchor[1]+dz;Material tile=sample(chunks,x,floor,z);
            if(!generator.surface(x,z).land()){
                if(tile!=Material.WATER&&!tile.isSolid())throw new AssertionError("An accepted lunar district leaves a hole in its plaza above a natural fissure at "+x+","+floor+","+z);
                if(!sample(chunks,x,floor-1,z).isSolid())throw new AssertionError("Fissure plaza foundation is unsupported at "+x+","+z);
                bridged++;
            }
            if(tile==Material.WATER){
                if(!sample(chunks,x,floor-1,z).isSolid())throw new AssertionError("Fissure district pool has an open bottom");
                for(int[] d:new int[][]{{-1,0},{1,0},{0,-1},{0,1}}){
                    Material side=sample(chunks,x+d[0],floor,z+d[1]);
                    if(side!=Material.WATER&&!side.isSolid())throw new AssertionError("Fissure district pool leaks sideways at "+x+","+z);
                }
                int cx=Math.floorDiv(x,16),cz=Math.floorDiv(z,16);var data=chunks.get(SkyPlacementHistory.key(cx,cz)).getBlockData(Math.floorMod(x,16),floor,Math.floorMod(z,16));
                if(!(data instanceof org.bukkit.block.data.Levelled level)||level.getLevel()!=0)throw new AssertionError("Fissure district contains flowing water");
                water++;
            }
        }
        check(bridged>0&&water>50,"accepted lunar plaza bridges "+bridged+" natural fissure columns and retains "+water+" contained pool sources");
        }finally{generator=real;}
    }
    private Material sample(Map<Long,ChunkData> chunks,int x,int y,int z){
        int cx=Math.floorDiv(x,16),cz=Math.floorDiv(z,16);var data=chunks.computeIfAbsent(SkyPlacementHistory.key(cx,cz),ignored->raw(cx,cz,17));
        return data.getType(Math.floorMod(x,16),y,Math.floorMod(z,16));
    }
    private int[] sceneryAnchor(BiomeTerrain.Kind kind){return sceneryAnchor(kind,false);}
    private int[] sceneryAnchor(BiomeTerrain.Kind kind,boolean requireFissure){
        var origin=generator.terrain().locate(kind,0,0);int size=kind==BiomeTerrain.Kind.GLACIER?224:kind==BiomeTerrain.Kind.ANCIENT_FOREST?112:128;
        int ax=Math.floorDiv(origin.x(),size),az=Math.floorDiv(origin.z(),size);
        for(int r=0;r<=24;r++)for(int gx=ax-r;gx<=ax+r;gx++)for(int gz=az-r;gz<=az+r;gz++){
            if(r>0&&Math.abs(gx-ax)!=r&&Math.abs(gz-az)!=r)continue;
            int x,z,radius;long hash;
            if(kind==BiomeTerrain.Kind.GLACIER){hash=generator.terrain().hash(gx*224+1013,gz*224+619);if(((hash>>>15)%4)!=0)continue;x=gx*224+75+(int)Long.remainderUnsigned(hash,74);z=gz*224+75+(int)Long.remainderUnsigned(hash>>>23,74);radius=52;}
            else if(kind==BiomeTerrain.Kind.ANCIENT_FOREST){hash=generator.terrain().hash(gx*112-809,gz*112+277);if(((hash>>>12)%5)==0)continue;x=gx*112+30+(int)Long.remainderUnsigned(hash,52);z=gz*112+30+(int)Long.remainderUnsigned(hash>>>23,52);radius=32;}
            else{x=gx*128+64;z=gz*128+64;hash=generator.terrain().hash(x+1637,z-929);if(((hash>>>11)%5)==0)continue;radius=60;}
            var s=generator.surface(x,z);if(generator.terrain().kind(x,z)!=kind||!s.land()||!generator.canDecorate(x,z,radius))continue;
            if(kind==BiomeTerrain.Kind.GLACIER){
                boolean alongX=((hash>>>7)%2)==0;int half=40+(int)((hash>>>29)%7),archHeight=29+(int)((hash>>>39)%10);
                var a=generator.surface(x+(alongX?-half:0),z+(alongX?0:-half));
                var b=generator.surface(x+(alongX?half:0),z+(alongX?0:half));
                if(!a.land()||!b.land()||Math.max(s.top(),Math.max(a.top(),b.top()))+archHeight+11>300)continue;
            }
            int checkRadius=kind==BiomeTerrain.Kind.LUNAR_CITY?49:20;int slope=kind==BiomeTerrain.Kind.LUNAR_CITY?12:15;
            boolean grounded=true;
            if(kind!=BiomeTerrain.Kind.GLACIER)for(int dx:new int[]{-checkRadius,0,checkRadius})for(int dz:new int[]{-checkRadius,0,checkRadius}){
                var edge=generator.surface(x+dx,z+dz);if(!edge.land()||edge.path()||edge.pond()||Math.abs(edge.top()-s.top())>slope)grounded=false;
            }
            if(grounded&&requireFissure){
                boolean fissure=false;
                for(int dx=-24;dx<=24&&!fissure;dx++)for(int dz=-24;dz<=24;dz++)
                    if(Math.hypot(dx,dz)<=24&&!generator.surface(x+dx,z+dz).land()){fissure=true;break;}
                if(!fissure)continue;
            }
            if(grounded)return new int[]{x,z};
        }
        throw new AssertionError("No scenery feature in "+kind);
    }
    private ChunkData raw(int cx,int cz,long random){var data=Bukkit.getServer().createChunkData(world);generator.generateNoise(world,new Random(random),cx,cz,data);return data;}
    private static long rawHash(ChunkData data){
        long hash=0xcbf29ce484222325L;
        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=Math.max(data.getMinHeight(),-64);y<Math.min(data.getMaxHeight(),320);y++){
            hash^=data.getType(x,y,z).ordinal();hash*=0x100000001b3L;
        }
        return hash;
    }
}
