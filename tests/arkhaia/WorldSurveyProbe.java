import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.*;
import org.bukkit.*;
import org.bukkit.plugin.java.JavaPlugin;
import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.block.Biome;

/**
 * Survey of a fresh Evergarden world with the production generator: a coordinate map of biomes, land and
 * every structure site, real-chunk transects from spawn (land, trees, plants), and structure spacing.
 */
public final class WorldSurveyProbe extends JavaPlugin {
    VoidscapePlugin garden;World world;VoidGenerator generator;BiomeTerrain terrain;
    final StringBuilder report=new StringBuilder();
    record Mark(String kind,int x,int z,Color color,int size){}

    /** Work is spread over ticks so the server watchdog never sees one long tick. */
    final Deque<Runnable> work=new ArrayDeque<>();
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();pump();}catch(Throwable e){finish(e);}},100);}
    void pump(){
        Bukkit.getScheduler().runTaskTimer(this,task->{
            try{
                long until=System.nanoTime()+40_000_000L;
                while(!work.isEmpty()&&System.nanoTime()<until)work.poll().run();
                if(work.isEmpty()){task.cancel();Files.writeString(Path.of("world-survey.txt"),report.toString());finish(null);}
            }catch(Throwable e){task.cancel();finish(e);}
        },1,1);
    }
    void line(String text){report.append(text).append('\n');getLogger().info(text);}

    void run()throws Exception{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");world=garden.world();
        generator=(VoidGenerator)world.getGenerator();terrain=garden.biomeTerrain();
        line("seed="+world.getSeed()+" biome-size="+terrain.scale()+" legacy-regions="+terrain.legacyRegions().size());
        overworldSanctuaries(7500);
        if("sanctuary".equals(System.getenv("SURVEY_ONLY")))return;
        List<Mark> marks=sites(6144);
        rejections(6144);
        spacing(marks);
        map(3072,8,marks);
        transects();
    }

    /**
     * Overworld sanctuaries as generation places them: count and nearest distance per kind inside a square around
     * spawn, region success per kind, and a check with the server's own structure locator that no village or
     * pillager outpost stands near any site. Biome sampling only; the locator generates structure starts.
     */
    void overworldSanctuaries(int half){
        World overworld=Bukkit.getWorlds().getFirst();var placement=garden.overworldSanctuaries().placement(overworld);
        if(placement==null){line("overworld-sanctuary: no placement for "+overworld.getName());return;}
        int size=placement.spacing*16;
        Map<OverworldSanctuaries.Kind,List<OverworldSanctuaries.Site>> inside=new EnumMap<>(OverworldSanctuaries.Kind.class);
        for(var k:OverworldSanctuaries.Kind.values())inside.put(k,new ArrayList<>());
        for(int gx=Math.floorDiv(-half,size);gx<=Math.floorDiv(half,size);gx++)for(int gz=Math.floorDiv(-half,size);gz<=Math.floorDiv(half,size);gz++){
            var site=placement.cell(gx,gz);
            if(site!=null&&Math.abs(site.x())<=half&&Math.abs(site.z())<=half)inside.get(site.kind()).add(site);
        }
        line(String.format(Locale.ROOT,"overworld-sanctuary seed=%d square=%dx%d cell=%d reach=%d",overworld.getSeed(),2*half,2*half,size,placement.reach()));
        int total=0;
        for(var k:OverworldSanctuaries.Kind.values()){
            var list=inside.get(k);total+=list.size();
            double nearest=list.stream().mapToDouble(s->Math.hypot(s.x(),s.z())).min().orElse(-1);
            // What each region size would give this kind alone (before other kinds claim cells).
            StringBuilder sim=new StringBuilder();
            for(int cells=2;cells<=8;cells++){
                int[] lo=placement.regionAt(k,cells,Math.floorDiv(-half,size),Math.floorDiv(-half,size)),hi=placement.regionAt(k,cells,Math.floorDiv(half,size),Math.floorDiv(half,size));
                int n=0;
                for(int rx=lo[0];rx<=hi[0];rx++)for(int rz=lo[1];rz<=hi[1];rz++){
                    var s=placement.simulate(k,cells,rx,rz,null);if(s!=null&&Math.abs(s.x())<=half&&Math.abs(s.z())<=half)n++;
                }
                sim.append(String.format(Locale.ROOT," %d:%2d",cells,n));
            }
            line(String.format(Locale.ROOT,"overworld-sanctuary %-13s count %2d (region %d) nearest-to-spawn %6.0f | alone by region size%s",k.id,list.size(),placement.regionCells(k),nearest,sim));
        }
        line("overworld-sanctuary total "+total);
        var structures=List.of(org.bukkit.generator.structure.Structure.VILLAGE_PLAINS,org.bukkit.generator.structure.Structure.VILLAGE_DESERT,
            org.bukkit.generator.structure.Structure.VILLAGE_SAVANNA,org.bukkit.generator.structure.Structure.VILLAGE_SNOWY,
            org.bukkit.generator.structure.Structure.VILLAGE_TAIGA,org.bukkit.generator.structure.Structure.PILLAGER_OUTPOST);
        int[] clash={0},checked={0};
        for(var list:inside.values())for(var site:list)work.add(()->{
            checked[0]++;
            for(var structure:structures){
                var found=overworld.locateNearestStructure(new Location(overworld,site.x(),80,site.z()),structure,16,false);
                if(found==null)continue;var at=found.getLocation();
                double d=Math.hypot(at.getX()-site.x(),at.getZ()-site.z());
                if(d<112+(structure==org.bukkit.generator.structure.Structure.PILLAGER_OUTPOST?48:96)){
                    clash[0]++;line(String.format(Locale.ROOT,"overworld-sanctuary CLASH %s at %d,%d with %s at %.0f blocks",site.kind().id,site.x(),site.z(),structure.getKey().getKey(),d));
                }
            }
        });
        // The predictor must contain every settlement the server locates.
        int[] located={0},predicted={0};
        Random random=new Random(5);
        for(int i=0;i<12;i++){int px=random.nextInt(2*half)-half,pz=random.nextInt(2*half)-half;work.add(()->{
            for(var structure:structures){
                var found=overworld.locateNearestStructure(new Location(overworld,px,80,pz),structure,24,false);if(found==null)continue;
                int cx=found.getLocation().getBlockX()>>4,cz=found.getLocation().getBlockZ()>>4;located[0]++;
                boolean outpost=structure==org.bukkit.generator.structure.Structure.PILLAGER_OUTPOST;
                boolean hit=VanillaSettlements.starts(overworld,overworld.vanillaBiomeProvider(),outpost,cx*16-1,cz*16-1,cx*16+16,cz*16+16)
                    .stream().anyMatch(s->Math.abs(s[0]-cx)<=1&&Math.abs(s[1]-cz)<=1);
                if(hit)predicted[0]++;else line("overworld-sanctuary predictor MISSED "+structure.getKey().getKey()+" at chunk "+cx+","+cz);
            }
        });}
        work.add(()->line(String.format(Locale.ROOT,"overworld-sanctuary settlements: %d sites checked, %d clashes | predictor matched %d of %d located settlements",
            checked[0],clash[0],predicted[0],located[0])));
    }

    List<Mark> sites(int reach){
        List<Mark> marks=new ArrayList<>();
        for(var s:garden.layout().within(0,0,reach))marks.add(new Mark("sanctum-dungeon",s.x(),s.z(),new Color(255,215,90),3));
        int ws=garden.skyWhales().spacingChunks()*16;
        for(int gx=Math.floorDiv(-reach,ws);gx<=Math.floorDiv(reach,ws);gx++)for(int gz=Math.floorDiv(-reach,ws);gz<=Math.floorDiv(reach,ws);gz++){
            var whale=garden.skyWhales().cell(gx,gz);if(whale!=null)marks.add(new Mark("sky-whale",whale.x(),whale.z(),new Color(90,200,255),5));
        }
        int ls=garden.skyLandmarks().spacingChunks()*16;
        for(int gx=Math.floorDiv(-reach,ls);gx<=Math.floorDiv(reach,ls);gx++)for(int gz=Math.floorDiv(-reach,ls);gz<=Math.floorDiv(reach,ls);gz++)
            for(var site:garden.skyLandmarks().cell(gx,gz))marks.add(new Mark("landmark-"+site.kind().name().toLowerCase(),site.x(),site.z(),new Color(120,255,160),5));
        int ts=garden.bossTemples().spacingChunks()*16;
        for(int gx=Math.floorDiv(-reach,ts);gx<=Math.floorDiv(reach,ts);gx++)for(int gz=Math.floorDiv(-reach,ts);gz<=Math.floorDiv(reach,ts);gz++){
            var temple=garden.bossTemples().cell(gx,gz);if(temple!=null)marks.add(new Mark("judge-temple",temple.x(),temple.z(),new Color(255,80,80),7));
        }
        int bs=garden.bossStructures().spacingChunks()*16;
        for(int gx=Math.floorDiv(-reach,bs);gx<=Math.floorDiv(reach,bs);gx++)for(int gz=Math.floorDiv(-reach,bs);gz<=Math.floorDiv(reach,bs);gz++)
            for(var site:garden.bossStructures().cell(gx,gz))marks.add(new Mark("boss-"+site.kind().name().toLowerCase(),site.x(),site.z(),
                site.kind()==BossStructureLayout.Kind.SERAPHON?new Color(255,240,255):site.kind()==BossStructureLayout.Kind.ARKHAIA?new Color(200,40,90):new Color(255,150,40),7));
        var restoration=garden.restorationLayout();
        if(restoration!=null){int rs=restoration.cellSize();
            for(int gx=Math.floorDiv(-reach,rs);gx<=Math.floorDiv(reach,rs);gx++)for(int gz=Math.floorDiv(-reach,rs);gz<=Math.floorDiv(reach,rs);gz++)
                for(var site:restoration.cell(gx,gz))marks.add(new Mark(site.altar()?"restoration-altar":"restoration-shrine",site.x(),site.z(),new Color(180,140,255),3));}
        int es=ExplorationLayout.CELL_SIZE;
        for(int gx=Math.floorDiv(-reach,es);gx<=Math.floorDiv(reach,es);gx++)for(int gz=Math.floorDiv(-reach,es);gz<=Math.floorDiv(reach,es);gz++){
            var site=garden.exploration().cell(gx,gz);if(site!=null)marks.add(new Mark("exploration-"+(site.kind().medium()?"medium":"small"),site.x(),site.z(),new Color(255,255,255),site.kind().medium()?4:3));
        }
        return marks;
    }

    /** Why candidates fail: exploration by biome and reason; big structures that won their roll but were dropped. */
    void rejections(int reach)throws Exception{
        Map<String,Integer> exploration=new TreeMap<>();int es=ExplorationLayout.CELL_SIZE;
        for(int gx=Math.floorDiv(-reach,es);gx<=Math.floorDiv(reach,es);gx++)for(int gz=Math.floorDiv(-reach,es);gz<=Math.floorDiv(reach,es);gz++){
            var inspection=garden.exploration().inspectCell(gx,gz);
            int cx=gx*es+es/2,cz=gz*es+es/2;String biome=terrain.kind(cx,cz).name();
            exploration.merge(biome+" "+inspection.result(),1,Integer::sum);
        }
        exploration.forEach((k,v)->line(String.format(Locale.ROOT,"exploration %-36s %5d",k,v)));
        var structures=garden.bossStructures();long seed=world.getSeed();
        int spacing=structures.spacingChunks();int size=spacing*16;
        int margin=(int)Math.ceil((120+structures.minimumGap()/2.0)/16);
        var conflicts=BossStructureLayout.class.getDeclaredMethod("conflictsWithExisting",BossStructureLayout.Site.class);conflicts.setAccessible(true);
        for(var kind:BossStructureLayout.Kind.values()){
            int candidates=0,nearSpawn=0,dungeon=0,other=0,kept=0;
            var saltField=BossStructureLayout.Kind.class.getDeclaredField("salt");saltField.setAccessible(true);long salt=saltField.getLong(kind);
            for(int gx=Math.floorDiv(-reach,size);gx<=Math.floorDiv(reach,size);gx++)for(int gz=Math.floorDiv(-reach,size);gz<=Math.floorDiv(reach,size);gz++){
                var random=new Random(DungeonLayout.mix(seed^salt^((long)gx*341873128712L)^((long)gz*132897987541L)));
                if(random.nextDouble()>=structures.chance(kind))continue;candidates++;
                int x=(gx*spacing+margin+random.nextInt(spacing-2*margin))*16,z=(gz*spacing+margin+random.nextInt(spacing-2*margin))*16;
                if(Math.hypot(x,z)<768){nearSpawn++;continue;}
                var site=new BossStructureLayout.Site(kind,x,z,false);
                boolean blockedByDungeon=false;
                for(var d:garden.layout().within(x,z,kind.radius+28+structures.minimumGap()+40))
                    if(Math.abs((long)x-d.x())<=kind.radius+d.radius()+structures.minimumGap()&&Math.abs((long)z-d.z())<=kind.radius+d.radius()+structures.minimumGap())blockedByDungeon=true;
                if(blockedByDungeon)dungeon++;
                else if((boolean)conflicts.invoke(structures,site))other++;
                else kept++;
            }
            line(String.format(Locale.ROOT,"boss %-9s chance %.3f per %d-block cell: won roll %3d, too near spawn %d, blocked by a sanctum dungeon %3d, other conflict %d, kept %d (before same-cell overlap)",
                kind,structures.chance(kind),size,candidates,nearSpawn,dungeon,other,kept));
        }
    }

    Color biome(BiomeTerrain.Kind kind){return switch(kind){case GARDENS->new Color(120,190,110);case GLACIER->new Color(205,230,245);case ANCIENT_FOREST->new Color(40,110,55);case LUNAR_CITY->new Color(150,140,190);};}

    void map(int half,int step,List<Mark> marks){
        int size=2*half/step;BufferedImage image=new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB);
        long[] counts=new long[2];
        for(int column=0;column<size;column++){int px=column;work.add(()->{for(int pz=0;pz<size;pz++){
            long land=0,total=0;
            int x=-half+px*step,z=-half+pz*step;var s=generator.surface(x,z);total++;
            Color color;
            if(!s.land())color=new Color(14,18,32);
            else{
                land++;double weight=terrain.newWeight(x,z);
                Color base=weight<.5?new Color(170,215,140):biome(terrain.kind(x,z));
                double shade=Math.clamp(.62+(s.top()-60)/160.0,.45,1.15);
                color=new Color(Math.clamp((int)(base.getRed()*shade),0,255),Math.clamp((int)(base.getGreen()*shade),0,255),Math.clamp((int)(base.getBlue()*shade),0,255));
            }
            image.setRGB(px,pz,color.getRGB());counts[0]+=land;counts[1]+=total;
        }});}
        work.add(()->{try{drawMap(image,size,half,step,marks,counts[0],counts[1]);}catch(Exception e){throw new RuntimeException(e);}});
    }
    void drawMap(BufferedImage image,int size,int half,int step,List<Mark> marks,long land,long total)throws Exception{
        Graphics2D g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        int c=size/2;g.setColor(new Color(255,255,255,90));
        for(int r:new int[]{384,776,1024,2048}){int pr=r/step;g.drawOval(c-pr,c-pr,pr*2,pr*2);g.drawString(r+"",c+pr+2,c);}
        for(Mark m:marks){
            if(Math.abs(m.x())>half||Math.abs(m.z())>half)continue;
            int px=(m.x()+half)/step,pz=(m.z()+half)/step;g.setColor(Color.BLACK);g.fillOval(px-m.size()-1,pz-m.size()-1,m.size()*2+2,m.size()*2+2);
            g.setColor(m.color());g.fillOval(px-m.size(),pz-m.size(),m.size()*2,m.size()*2);
        }
        g.setColor(Color.RED);g.fillRect(c-3,c-3,6,6);g.dispose();
        ImageIO.write(image,"png",Path.of("world-survey-map.png").toFile());
        line(String.format(Locale.ROOT,"map ±%d: land %.1f%%",half,100.0*land/total));
    }

    void spacing(List<Mark> marks){
        Map<String,List<Mark>> byKind=new TreeMap<>();for(Mark m:marks)byKind.computeIfAbsent(m.kind(),k->new ArrayList<>()).add(m);
        for(var e:byKind.entrySet()){
            double nearest=e.getValue().stream().mapToDouble(m->Math.hypot(m.x(),m.z())).min().orElse(-1);
            long inner=e.getValue().stream().filter(m->Math.hypot(m.x(),m.z())<=2048).count();
            line(String.format(Locale.ROOT,"%-28s count(±6144)=%4d within2048=%3d nearest-to-spawn=%6.0f per-km2=%.2f",e.getKey(),e.getValue().size(),inner,nearest,e.getValue().size()/(12.288*12.288)));
        }
        // How far a player walks to the nearest thing worth visiting, sampled by distance from spawn.
        for(int[] ring:new int[][]{{100,384},{384,776},{776,1024},{1024,2048},{2048,4096}}){
            Random random=new Random(ring[0]);List<Double> walk=new ArrayList<>();List<Double> walkBig=new ArrayList<>();
            for(int i=0;i<4000;i++){
                double a=random.nextDouble()*Math.PI*2,r=ring[0]+random.nextDouble()*(ring[1]-ring[0]);
                double x=Math.cos(a)*r,z=Math.sin(a)*r;double best=1e9,bestBig=1e9;
                for(Mark m:marks){double d=Math.hypot(m.x()-x,m.z()-z);best=Math.min(best,d);if(!m.kind().startsWith("restoration"))bestBig=Math.min(bestBig,d);}
                walk.add(best);walkBig.add(bestBig);
            }
            Collections.sort(walk);Collections.sort(walkBig);
            line(String.format(Locale.ROOT,"ring %4d-%4d: nearest site median %4.0f p90 %4.0f | excluding altars median %4.0f p90 %4.0f",
                ring[0],ring[1],walk.get(2000),walk.get(3600),walkBig.get(2000),walkBig.get(3600)));
        }
    }

    final Map<String,Integer> ground=new HashMap<>();
    void transects(){
        int[][] dirs={{1,0},{0,1},{-1,0},{0,-1}};String[] names={"east","south","west","north"};
        int bin=128,length=2560,bins=length/bin;
        double[][] landAll=new double[4][bins],treesAll=new double[4][bins],plantsAll=new double[4][bins],builtAll=new double[4][bins];
        for(int dd=0;dd<4;dd++){int d=dd;
            for(int st=0;st<length;st+=16){int step=st;
                for(int ln=-1;ln<=1;ln++){int lane=ln;work.add(()->{
                    int bx=dirs[d][0]*step+(dirs[d][1]!=0?lane*16:0),bz=dirs[d][1]*step+(dirs[d][0]!=0?lane*16:0);
                    Chunk chunk=world.getChunkAt(Math.floorDiv(bx,16),Math.floorDiv(bz,16));ChunkSnapshot snap=chunk.getChunkSnapshot(true,false,false);
                    int b=Math.min(bins-1,step/bin);
                    for(int x=0;x<16;x++)for(int z=0;z<16;z++){
                        int top=snap.getHighestBlockYAt(x,z);if(top<=world.getMinHeight())continue;
                        landAll[d][b]++;
                        int wx=chunk.getX()*16+x,wz=chunk.getZ()*16+z;
                        if(((wx|wz)&3)==0&&Math.hypot(wx,wz)>=512&&terrain.kind(wx,wz)!=BiomeTerrain.Kind.GLACIER){
                            var gs=generator.surface(wx,wz);String why;
                            if(!terrain.richScenery(wx,wz,0))why="not-rich";else if(!gs.land())why="no-land";
                            else if(!generator.canDecorate(wx,wz,2))why="reserved";
                            else why="top="+snap.getBlockType(x,gs.top(),z)+(gs.top()==top?"":" (real top "+snap.getBlockType(x,top,z)+")")+" above="+snap.getBlockType(x,Math.min(world.getMaxHeight()-1,gs.top()+1),z);
                            ground.merge(terrain.kind(wx,wz)+" "+why,1,Integer::sum);
                            if(terrain.kind(wx,wz)==BiomeTerrain.Kind.ANCIENT_FOREST)ground.merge("ANCIENT_FOREST near "+Math.floorDiv(wx,64)*64+","+Math.floorDiv(wz,64)*64+" weight="+String.format(Locale.ROOT,"%.2f",terrain.newWeight(wx,wz)),1,Integer::sum);
                        }
                        for(int y=Math.max(world.getMinHeight(),top-24);y<=Math.min(world.getMaxHeight()-1,top+1);y++){
                            Material m=snap.getBlockType(x,y,z);String n=m.name();
                            if(n.endsWith("_LOG")||n.endsWith("_LEAVES")||n.endsWith("_WOOD")||n.contains("MUSHROOM_BLOCK"))treesAll[d][b]++;
                            else if(Tag.FLOWERS.isTagged(m)||m==Material.SHORT_GRASS||m==Material.TALL_GRASS||m==Material.FERN||m==Material.LARGE_FERN||n.contains("MOSS_CARPET")||n.contains("BUSH"))plantsAll[d][b]++;
                            else if(n.contains("BRICK")||n.contains("QUARTZ")||n.contains("LANTERN")||n.contains("PLANKS")||n.contains("STAIRS")||n.contains("SLAB")||n.contains("WALL")||n.contains("AMETHYST")||n.contains("GLASS"))builtAll[d][b]++;
                        }
                    }
                    if(step%256==0)world.unloadChunkRequest(chunk.getX(),chunk.getZ());
                });}
            }
        }
        work.add(()->{
        ground.entrySet().stream().sorted(Map.Entry.<String,Integer>comparingByValue().reversed())
            .forEach(e->line(String.format(Locale.ROOT,"ground %6d %s",e.getValue(),e.getKey())));
        line("transects: 48-block-wide strips from spawn; per 128 blocks: land % | tree blocks per 1,000 columns | plants per 1,000 columns | built blocks per 1,000 columns");
        for(int b=0;b<bins;b++){
            StringBuilder row=new StringBuilder(String.format(Locale.ROOT,"%4d-%4d",b*bin,(b+1)*bin));
            double cols=128*48;
            for(int d=0;d<4;d++)row.append(String.format(Locale.ROOT," | %s land %3.0f%% tree %5.0f plant %4.0f built %4.0f",names[d].charAt(0),
                100*landAll[d][b]/cols,1000*treesAll[d][b]/Math.max(1,landAll[d][b]),1000*plantsAll[d][b]/Math.max(1,landAll[d][b]),1000*builtAll[d][b]/Math.max(1,landAll[d][b])));
            line(row.toString());
        }
        });
    }

    void finish(Throwable error){
        try{Files.writeString(Path.of("arkhaia-result.txt"),error==null?"PASS world survey":"FAIL "+error);}catch(Exception ignored){}
        if(error!=null)getLogger().log(java.util.logging.Level.SEVERE,"World survey failed",error);
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
