package com.example.voidscape.world;

import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import org.bukkit.util.noise.SimplexNoiseGenerator;
import java.util.*;

/**
 * The Celestial Cascade, Evergarden's highest landmark. A crown island at y 272 carries a terraced spring, a mirror
 * lake, two rivers that leave its rim as waterfalls into pools on lower islets, and a moated Temple of the Source
 * holding the treasure. The Heart of the Sky crystal hovers over the temple dome up to y 318. A bubble-column lift
 * rises from a ground pool beside the southern promontory; the gate balcony above it is a safe dive back into that
 * pool, and glow-berry vines let players climb from each islet back up.
 *
 * Every water body is sealed and every fall lands in a pool, so the water stays put once the game ticks it.
 * The geometry is a dense grid of block-data strings, frozen into per-chunk packed lists. Sites sit at a chunk
 * centre (x and z are 8 mod 16), which keeps the three treasure chests in one chunk.
 */
public final class CelestialCascade {
    public static final int RADIUS=100,MIN_Y=180,MAX_Y=319;
    /** Grass level of the crown island and the water level of its lake, rivers, moat and gate basin. */
    public static final int GROUND=272,WATER=271;
    /** Treasure: three chests facing north, each on a gold block and backed by chiseled quartz. */
    public static final int CHEST_Y=275,CHEST_Z=22;
    public static final int[] CHEST_X={-2,0,2};
    public static final String CHEST_FLOOR="GOLD_BLOCK",CHEST_BACK="CHISELED_QUARTZ_BLOCK";
    /** Bubble-column lift and the square ground pool it rises from (water spans the half-size each way). */
    public static final int LIFT_X=0,LIFT_Z=48,POOL_X=0,POOL_Z=50,POOL_HALF=5;
    /** Where the admin teleport lands: the gate basin's north rim. */
    public static final int ARRIVAL_X=0,ARRIVAL_Y=273,ARRIVAL_Z=41;

    private static final class Holder{static final CelestialCascade VALUE=new Builder().build();}
    public static CelestialCascade blueprint(){return Holder.VALUE;}

    /** Visits every block of the blueprint, for exporters and probes. */
    public interface Visitor{void block(int x,int y,int z,String state);}

    private final String[] palette;
    private final BlockData[] data;
    /** Per relative chunk: (local x << 28) | (local z << 24) | ((y - MIN_Y) << 16) | palette index. */
    private final Map<Long,int[]> chunks;
    private final int count,lowest,highest;

    private CelestialCascade(String[] palette,Map<Long,int[]> chunks,int count,int lowest,int highest){
        this.palette=palette;this.data=new BlockData[palette.length];this.chunks=chunks;
        this.count=count;this.lowest=lowest;this.highest=highest;
    }
    private static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    public int blockCount(){return count;}
    public int lowestY(){return lowest;}
    public int highestY(){return highest;}

    public void forEach(Visitor visitor){
        for(var e:chunks.entrySet()){
            int rx=(int)(e.getKey()>>32),rz=(int)(long)e.getKey();
            for(int v:e.getValue())visitor.block(rx*16+(v>>>28)-8,((v>>>16)&0xff)+MIN_Y,rz*16+((v>>>24)&0xf)-8,palette[v&0xffff]);
        }
    }

    /** Renders the part of a site in chunk (cx, cz); the site centre must sit at x, z = 8 mod 16. */
    public void render(ChunkData target,int cx,int cz,int siteX,int siteZ){
        int[] blocks=chunks.get(key(cx-Math.floorDiv(siteX,16),cz-Math.floorDiv(siteZ,16)));
        if(blocks==null)return;
        for(int v:blocks){
            int index=v&0xffff,y=((v>>>16)&0xff)+MIN_Y;
            if(y<target.getMinHeight()||y>=target.getMaxHeight())continue;
            BlockData state=data[index];
            if(state==null)data[index]=state=Bukkit.createBlockData("minecraft:"+palette[index]);
            target.setBlock(v>>>28,y,(v>>>24)&0xf,state);
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    private static final class Builder {
        private static final int W=RADIUS*2+1,H=MAX_Y-MIN_Y+1;
        private final short[] cells=new short[W*W*H];
        private final List<String> palette=new ArrayList<>(List.of("air"));
        private final Map<String,Short> ids=new HashMap<>();
        private final SimplexNoiseGenerator n1=new SimplexNoiseGenerator(0x43454c4553544941L),
                n2=new SimplexNoiseGenerator(0x4341534341444531L),n3=new SimplexNoiseGenerator(0x5354524154414c31L);
        /** Highest terrain (grass, path or bed) per column of every island; MIN_VALUE where there is none. */
        private final int[][] top=new int[W][W];
        /** Columns of the crown island and their distance to its rim. */
        private final boolean[][] crown=new boolean[W][W];
        private final double[][] rim=new double[W][W];
        /** Water level per column of every carved water body, or MIN_VALUE. */
        private final int[][] level=new int[W][W];
        /** Columns a decoration must leave alone: paths, plaza, bridges, pools' surroundings. */
        private final boolean[][] reserved=new boolean[W][W];
        /** Fall columns and the lift/dive shafts: nothing may be built into them. */
        private final List<int[]> shafts=new ArrayList<>();
        private int liftTop;
        private final int[] lipWest=new int[2],lipEast=new int[2];

        Builder(){for(int[] row:top)Arrays.fill(row,Integer.MIN_VALUE);for(int[] row:level)Arrays.fill(row,Integer.MIN_VALUE);}

        // ---- grid ---------------------------------------------------------------------------------------------
        private static boolean inside(int x,int y,int z){return Math.abs(x)<=RADIUS&&Math.abs(z)<=RADIUS&&y>=MIN_Y&&y<=MAX_Y;}
        private static int index(int x,int y,int z){return ((x+RADIUS)*W+(z+RADIUS))*H+(y-MIN_Y);}
        private void set(int x,int y,int z,String state){
            if(!inside(x,y,z))throw new IllegalArgumentException("Celestial Cascade bounds: "+x+","+y+","+z+" "+state);
            cells[index(x,y,z)]=state==null?0:ids.computeIfAbsent(state,s->{palette.add(s);return (short)(palette.size()-1);});
        }
        private void clear(int x,int y,int z){if(inside(x,y,z))cells[index(x,y,z)]=0;}
        private String get(int x,int y,int z){
            if(!inside(x,y,z))return null;short v=cells[index(x,y,z)];return v==0?null:palette.get(v);
        }
        private boolean empty(int x,int y,int z){return get(x,y,z)==null;}
        private void fill(int x,int y,int z,String state){if(inside(x,y,z)&&empty(x,y,z))set(x,y,z,state);}
        private static String base(String state){int i=state.indexOf('[');return i<0?state:state.substring(0,i);}
        private static final Set<String> PLANTS=Set.of("short_grass","tall_grass","fern","large_fern","pink_petals","wildflowers",
                "allium","azure_bluet","oxeye_daisy","cornflower","lily_of_the_valley","white_tulip","pink_tulip","blue_orchid",
                "azalea","flowering_azalea","bush","firefly_bush","moss_carpet","lily_pad","vine","glow_lichen","cave_vines",
                "cave_vines_plant","hanging_roots","spore_blossom","pointed_dripstone","amethyst_cluster","lantern","end_rod",
                "lilac","peony","rose_bush","big_dripleaf","seagrass","iron_chain","birch_fence");
        private static final Set<String> EARTH=Set.of("stone","andesite","tuff","calcite","diorite","deepslate","cobbled_deepslate",
                "dripstone_block","mossy_cobblestone","moss_block","dirt","rooted_dirt","coarse_dirt","smooth_basalt","granite",
                "grass_block","clay","polished_tuff");
        private boolean plant(String s){return s!=null&&PLANTS.contains(base(s));}
        private boolean earth(String s){return s!=null&&EARTH.contains(base(s));}
        private boolean water(String s){
            if(s==null)return false;String b=base(s);
            return b.equals("water")||b.equals("bubble_column")||b.equals("seagrass")||s.contains("waterlogged=true");
        }
        private boolean source(String s){return s!=null&&(s.equals("water")||s.startsWith("bubble_column")||s.equals("seagrass")||s.contains("waterlogged=true"));}
        private boolean solid(String s){return s!=null&&!plant(s)&&!water(s);}
        private boolean solid(int x,int y,int z){return solid(get(x,y,z));}

        // ---- noise --------------------------------------------------------------------------------------------
        private static double hash(int x,int y,int z,int salt){
            return (DungeonLayout.mix(x*341873128712L+y*73428767L+z*132897987541L+salt*0x9e3779b97f4a7c15L)&0xffffff)/(double)0x1000000;
        }
        private static double sq(double v){return v*v;}
        private static double smooth(double v){v=Math.clamp(v,0,1);return v*v*(3-2*v);}
        private int topAt(int x,int z){return Math.abs(x)>RADIUS||Math.abs(z)>RADIUS?Integer.MIN_VALUE:top[x+RADIUS][z+RADIUS];}
        private int levelAt(int x,int z){return Math.abs(x)>RADIUS||Math.abs(z)>RADIUS?Integer.MIN_VALUE:level[x+RADIUS][z+RADIUS];}
        private boolean reservedAt(int x,int z){return Math.abs(x)<=RADIUS&&Math.abs(z)<=RADIUS&&reserved[x+RADIUS][z+RADIUS];}
        private void reserve(int x,int z){if(Math.abs(x)<=RADIUS&&Math.abs(z)<=RADIUS)reserved[x+RADIUS][z+RADIUS]=true;}
        private static double segment(double px,double pz,double ax,double az,double bx,double bz){
            double dx=bx-ax,dz=bz-az,t=Math.clamp(((px-ax)*dx+(pz-az)*dz)/(dx*dx+dz*dz),0,1);
            return Math.hypot(px-ax-t*dx,pz-az-t*dz);
        }
        private static double polyline(double px,double pz,double[][] points){
            double best=Double.MAX_VALUE;
            for(int i=1;i<points.length;i++)best=Math.min(best,segment(px,pz,points[i-1][0],points[i-1][1],points[i][0],points[i][1]));
            return best;
        }

        // ---- the build ----------------------------------------------------------------------------------------
        CelestialCascade build(){
            crownIsland();
            spring();
            temple();
            gate();
            islets();
            crystalBridge();
            heart();
            trees();
            rimGarden();
            flora();
            dressing();
            clouds();
            floatingRocks();
            falls();
            seal();
            return freeze();
        }

        // ---- crown island -------------------------------------------------------------------------------------
        private boolean crownShape(int x,int z){
            double a=Math.atan2(z,x);
            double rad=1+.07*Math.sin(3*a+.7)+.05*Math.sin(5*a+2.1)+.05*n1.noise(x/20.0,z/20.0);
            if(Math.hypot(x/46.0,z/40.0)<=rad)return true;
            // The southern promontory carries the lift gate; its tip stops short of the dive shaft.
            return z<=50&&segment(x,z,0,28,0,44)<=6.5+.6*n1.noise(x/5.0,z/5.0);
        }
        private static final double[][] RIVER_EAST={{8,-3},{17,1},{27,-2},{37,2},{48,1},{80,1}};
        private static final double[][] RIVER_WEST={{-8,-4},{-17,-8},{-27,-4},{-37,-7},{-48,-5},{-80,-5}};
        private static final double[][] FEEDER={{5,3},{6,9}};
        /** Leaves the moat toward the south-east rim, in view of the gate. */
        private static final double[][] RIVER_SOUTHEAST={{10,28},{17,31},{24,32},{31,36},{70,50}};
        private static final int TEMPLE_X=0,TEMPLE_Z=20;
        private double lake(int x,int z){return sq(x/10.0)+sq((z+3)/7.5)+.18*n3.noise(x/7.0,z/7.0);}
        /** The northern crag: a rugged peak about 26 blocks over the crown, ridged by noise. */
        private double crag(int x,int z){
            double q=sq((x+3)/19.0)+sq((z+27)/13.0);
            if(q>=1)return 0;
            double ridge=1-Math.abs(n2.noise(x/7.0,z/7.0));
            return 24*Math.pow(1-q,.85)+4.5*ridge*(1-q)+2*n3.noise(x/3.0,z/3.0)*(1-q);
        }

        private void crownIsland(){
            for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++)crown[x+RADIUS][z+RADIUS]=crownShape(x,z);
            // Chamfer distance to the rim.
            double big=1e9;
            for(int i=0;i<W;i++)for(int j=0;j<W;j++)rim[i][j]=crown[i][j]?big:0;
            for(int pass=0;pass<2;pass++){
                int from=pass==0?0:W-1,to=pass==0?W:-1,step=pass==0?1:-1;
                for(int i=from;i!=to;i+=step)for(int j=from;j!=to;j+=step){
                    if(rim[i][j]==0)continue;
                    double v=rim[i][j];
                    int[][] near=pass==0?new int[][]{{-1,0},{0,-1},{-1,-1},{-1,1}}:new int[][]{{1,0},{0,1},{1,1},{1,-1}};
                    for(int[] n:near){
                        int a=i+n[0],b=j+n[1];
                        double w=n[0]!=0&&n[1]!=0?1.4142:1;
                        v=Math.min(v,(a<0||b<0||a>=W||b>=W)?w:rim[a][b]+w);
                    }
                    rim[i][j]=v;
                }
            }
            // Water bodies: lake, rivers, moat with its feeder, all at the crown water level.
            int[][] depth=new int[W][W];
            for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++){
                int i=x+RADIUS,j=z+RADIUS;if(!crown[i][j])continue;
                double q=lake(x,z);
                double east=polyline(x,z,RIVER_EAST),west=polyline(x,z,RIVER_WEST),feed=polyline(x,z,FEEDER);
                // Rivers widen toward the rim and run right to it, so their wide lips are fed.
                double widen=1.2*(1-smooth(rim[i][j]/10));
                double eastHw=1.7+widen,westHw=1.5+widen,southeast=polyline(x,z,RIVER_SOUTHEAST),southeastHw=1.2+widen*.8;
                if(rim[i][j]<1.5&&east>eastHw&&west>westHw&&southeast>southeastHw)continue;
                double moat=Math.abs(Math.hypot(x-TEMPLE_X,z-TEMPLE_Z)-12.5);
                int d=0;
                if(q<1||Math.hypot(x+1,z+10.5)<2.4)d=Math.clamp(1+(int)Math.round(3*(1-Math.min(q,1))),1,4);
                if(east<=eastHw)d=Math.max(d,east<eastHw-.8?2:1);
                if(west<=westHw)d=Math.max(d,west<westHw-.8?2:1);
                if(southeast<=southeastHw)d=Math.max(d,southeast<southeastHw-.7?2:1);
                if(feed<=1.0||moat<=1.3)d=Math.max(d,2);
                if(d>0){depth[i][j]=d;level[i][j]=WATER;}
            }
            for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++){
                int i=x+RADIUS,j=z+RADIUS;if(!crown[i][j])continue;
                double r=rim[i][j];
                double h=GROUND+crag(x,z);
                double plaza=Math.hypot(x-TEMPLE_X,z-TEMPLE_Z),gate=Math.hypot(x,z-44);
                double flat=Math.max(1-smooth((plaza-11)/4),1-smooth((gate-6)/4));
                double roll=Math.clamp(1.1*n2.noise(x/15.0,z/15.0)+.5*n3.noise(x/6.0,z/6.0),-1,2);
                h+=roll*(1-flat);
                if(r<3.5)h-=(3.5-r)*.6;
                int t=(int)Math.round(h);
                // Every bank stands at least one block over the water beside it.
                boolean bank=false;
                for(int dx=-2;dx<=2&&!bank;dx++)for(int dz=-2;dz<=2;dz++)if(levelAt(x+dx,z+dz)!=Integer.MIN_VALUE){bank=true;break;}
                if(bank&&r>=1.5)t=Math.max(t,GROUND);
                if(level[i][j]!=Integer.MIN_VALUE)t=WATER;
                top[i][j]=t;
            }
            for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++){
                int i=x+RADIUS,j=z+RADIUS;if(!crown[i][j])continue;
                double r=rim[i][j];int t=top[i][j];
                // An inverted mountain: the underside falls away about 5 blocks for every 4 inward from the rim.
                double drop=5+1.25*r+12*Math.exp(-(x*x+z*z)/(2*12.0*12.0))+4*n2.noise(x/9.0,z/9.0)+2.5*n3.noise(x/4.0,z/4.0)
                        +7*Math.abs(n1.noise(x/5.5,z/5.5))*smooth(r/6);
                int bottom=Math.max(MIN_Y+6,Math.min(t,GROUND)-(int)drop);
                int bed=level[i][j]==Integer.MIN_VALUE?t:WATER-depth[i][j];
                // Steep crag slopes show bare, mossy rock instead of turf.
                int slope=0;
                for(int[] n:new int[][]{{1,0},{-1,0},{0,1},{0,-1}}){int o=topAt(x+n[0],z+n[1]);if(o!=Integer.MIN_VALUE)slope=Math.max(slope,Math.abs(o-t));}
                boolean cliff=slope>=2&&crag(x,z)>3;
                for(int y=bottom;y<=bed;y++)set(x,y,z,cliff&&bed-y<2?cragRock(x,y,z):crownMaterial(x,y,z,bed,level[i][j]!=Integer.MIN_VALUE,r));
                if(level[i][j]!=Integer.MIN_VALUE)for(int y=bed+1;y<=WATER;y++)set(x,y,z,"water");
            }
            // Hanging spires below the crown, ending in dripstone points.
            int[][] spires={{18,-10,26,7},{-20,8,22,6},{8,24,18,5},{-12,-20,20,6},{26,14,15,4},{-28,-12,16,5},{2,-30,14,5},{-6,6,34,8}};
            for(int[] s:spires){
                int x0=s[0],z0=s[1];int startY=-1;
                for(int y=MIN_Y;y<=GROUND;y++)if(solid(x0,y,z0)){startY=y;break;}
                if(startY<0)continue;
                for(int t=0;t<s[2];t++){
                    double r=s[3]*Math.pow(1-t/(double)s[2],.85)+.4;int y=startY+3-t;
                    if(y<MIN_Y+3)break;
                    for(int dx=-s[3];dx<=s[3];dx++)for(int dz=-s[3];dz<=s[3];dz++)
                        if(dx*dx+dz*dz<=r*r)fill(x0+dx,y,z0+dz,rockAt(x0+dx,y,z0+dz,true));
                }
            }
            // Amethyst geodes straddling the underside, so it cuts them open.
            geode(-14,-6,7.5);geode(21,12,6.5);geode(12,-22,6);
        }
        private int underside(int x,int z){
            for(int y=MIN_Y;y<=MAX_Y;y++)if(earth(get(x,y,z)))return y;
            return Integer.MIN_VALUE;
        }
        private String crownMaterial(int x,int y,int z,int t,boolean wet,double r){
            int d=t-y;
            if(wet){
                if(d==0)return hash(x,y,z,11)<.04?"sea_lantern":hash(x,y,z,12)<.55?"clay":"moss_block";
                if(d<3)return "dirt";
                return rockAt(x,y,z,false);
            }
            if(d==0)return n3.noise(x/5.0,z/5.0)>.42?"moss_block":"grass_block";
            if(d<3+(int)(2*hash(x,0,z,13)))return hash(x,y,z,14)<.18?"rooted_dirt":hash(x,y,z,15)<.1?"coarse_dirt":"dirt";
            return rockAt(x,y,z,false);
        }
        private String cragRock(int x,int y,int z){
            double v=n3.noise(x/4.0,y/3.0,z/4.0),h=hash(x,y,z,22);
            if(h<.22)return "mossy_cobblestone";
            return v<-.35?"calcite":v<.05?"stone":v<.4?"andesite":"tuff";
        }
        /**
         * Rock that darkens with depth in soft blobs (stone and andesite above, tuff and deepslate below,
         * dripstone at the very bottom), crossed by a few thin, wavy calcite veins.
         */
        private String rockAt(int x,int y,int z,boolean spire){
            return rockAt(x,y,z,spire,(262-y+6*n2.noise(x/18.0,z/18.0))/62.0);
        }
        /** Depth runs 0 at the island's surface to 1 at its lowest tip. */
        private String rockAt(int x,int y,int z,boolean spire,double depthFraction){
            double h=hash(x,y,z,21);
            double depth=Math.clamp(depthFraction,0,1);
            if(spire&&h<.4)return depth>.7?"dripstone_block":"tuff";
            if(Math.abs(n1.noise(x/34.0,y/2.6,z/34.0))<.045&&depth<.85)return "calcite";
            double v=n3.noise(x/12.0,y/7.0,z/12.0)*.5+.5;
            double t=depth*.85+v*.3;
            // Pale, celestial stone: white and light grey above, warming to tuff and dripstone only at the tips.
            if(t<.25)return h<.06?"mossy_cobblestone":v<.45?"stone":"andesite";
            if(t<.45)return v<.4?"calcite":v<.7?"stone":"diorite";
            if(t<.62)return v<.35?"calcite":v<.65?"andesite":"stone";
            if(t<.8)return v<.4?"tuff":v<.7?"calcite":"stone";
            if(t<.95)return v<.45?"dripstone_block":"tuff";
            return v<.5?"dripstone_block":"deepslate";
        }
        private void geode(int cx,int cz,double r){
            int bottom=underside(cx,cz);if(bottom==Integer.MIN_VALUE)return;
            int cy=bottom+(int)Math.round(r*.35);
            List<int[]> hollow=new ArrayList<>();
            for(int x=(int)-r;x<=r;x++)for(int y=(int)-r;y<=r;y++)for(int z=(int)-r;z<=r;z++){
                double d=Math.sqrt(x*x+y*y+z*z+0.0)+.6*n3.noise((cx+x)/3.0,(cy+y)/3.0,(cz+z)/3.0);
                int px=cx+x,py=cy+y,pz=cz+z;
                if(d>r||!earth(get(px,py,pz)))continue;
                if(d>r-1)set(px,py,pz,"smooth_basalt");
                else if(d>r-2)set(px,py,pz,"calcite");
                else if(d>r-3)set(px,py,pz,hash(px,py,pz,31)<.14?"budding_amethyst":"amethyst_block");
                else{clear(px,py,pz);hollow.add(new int[]{px,py,pz});}
            }
            String[][] faces={{"0","1","0","down"},{"0","-1","0","up"},{"1","0","0","west"},{"-1","0","0","east"},{"0","0","1","north"},{"0","0","-1","south"}};
            for(int[] p:hollow){
                if(hash(p[0],p[1],p[2],32)>.3)continue;
                for(String[] f:faces){
                    int ax=p[0]+Integer.parseInt(f[0]),ay=p[1]+Integer.parseInt(f[1]),az=p[2]+Integer.parseInt(f[2]);
                    String s=get(ax,ay,az);
                    if(s!=null&&(s.equals("amethyst_block")||s.equals("budding_amethyst"))){
                        set(p[0],p[1],p[2],"amethyst_cluster[facing="+f[3]+"]");break;
                    }
                }
            }
        }

        // ---- spring: a grotto, two terrace pools and the lake ------------------------------------------------
        private void spring(){
            int l1=topAt(-3,-18)-2,l2=Math.min(topAt(-1,-13)-1,l1-4);l2=Math.max(l2,WATER+3);l1=Math.max(l1,l2+4);
            pool(-3,-18,2.4,l1,2,"mossy_stone_bricks");
            pool(-1,-13.5,2.4,l2,2,"mossy_stone_bricks");
            // Grotto: the spring leaves a cave under an arch of mossy brick.
            for(int x=-4;x<=-2;x++)for(int z=-24;z<=-21;z++){
                for(int y=l1+1;y<=l1+3;y++)clear(x,y,z);
                set(x,l1,z,"water");set(x,l1-1,z,"water");level[x+RADIUS][z+RADIUS]=l1;
                set(x,l1-2,z,"moss_block");
            }
            for(int y=l1;y<=l1+3;y++){set(-5,y,-21,"mossy_stone_bricks");set(-1,y,-21,"mossy_stone_bricks");}
            for(int x=-5;x<=-1;x++)set(x,l1+4,-21,x==-3?"chiseled_stone_bricks":"mossy_stone_bricks");
            set(-6,l1+4,-21,"mossy_stone_brick_stairs[facing=east,half=top]");set(0,l1+4,-21,"mossy_stone_brick_stairs[facing=west,half=top]");
            set(-3,l1+3,-23,"lantern[hanging=true]");
            for(int y=l1+1;y<=l1+3;y++){fill(-5,y,-23,"mossy_cobblestone");fill(-1,y,-23,"mossy_cobblestone");}
            spill(-3,-18,-1,-13.5,2.4,l1,l2);
            spill(-1,-13.5,-1,-9.5,2.4,l2,WATER);
            // Lake life: lily pads, seagrass, dripleaves and lit beds were laid by the fill.
            for(int x=-14;x<=14;x++)for(int z=-14;z<=8;z++){
                if(levelAt(x,z)!=WATER||!"water".equals(get(x,WATER,z))||lake(x,z)>=1)continue;
                double h=hash(x,0,z,41);
                boolean shallow=!"water".equals(get(x,WATER-1,z));
                if(h<.07)set(x,WATER+1,z,"lily_pad");
                else if(h<.1&&shallow){set(x,WATER,z,"big_dripleaf_stem[facing=north,waterlogged=true]");set(x,WATER+1,z,"big_dripleaf[facing=north,tilt=none]");}
                if(!shallow&&hash(x,1,z,42)<.22){
                    int y=WATER;while(y>MIN_Y&&"water".equals(get(x,y-1,z)))y--;
                    if(y<WATER)set(x,y,z,"seagrass");
                }
            }
        }
        /** A round pool of the given depth; banks rise to one block over the water where the ground is lower. */
        private void pool(double cx,double cz,double r,int lvl,int depthBlocks,String rimStone){
            int ext=(int)Math.ceil(r+2);
            for(int x=(int)Math.floor(cx)-ext;x<=(int)Math.ceil(cx)+ext;x++)for(int z=(int)Math.floor(cz)-ext;z<=(int)Math.ceil(cz)+ext;z++){
                double d=Math.hypot(x-cx,z-cz);
                if(d<=r){
                    for(int y=lvl+1;y<=lvl+4;y++)if(!water(get(x,y,z)))clear(x,y,z);
                    for(int y=lvl-depthBlocks+1;y<=lvl;y++)set(x,y,z,"water");
                    set(x,lvl-depthBlocks,z,hash(x,0,z,51)<.2?"sea_lantern":"moss_block");
                    for(int y=lvl-depthBlocks-1;y>=lvl-depthBlocks-3;y--)fill(x,y,z,"stone");
                    if(Math.abs(x)<=RADIUS&&Math.abs(z)<=RADIUS){level[x+RADIUS][z+RADIUS]=lvl;top[x+RADIUS][z+RADIUS]=lvl;}
                    reserve(x,z);
                }else if(d<=r+1.3){
                    reserve(x,z);
                    if(!solid(x,lvl,z)){set(x,lvl,z,rimStone);for(int y=lvl-1;y>=lvl-depthBlocks-1;y--)fill(x,y,z,rimStone);}
                    if(!solid(x,lvl+1,z)&&topAt(x,z)<=lvl)set(x,lvl+1,z,rimStone.replace("bricks","brick_slab[type=bottom]").replace("cobblestone","cobblestone_slab[type=bottom]"));
                    else if(topAt(x,z)<=lvl&&!solid(x,lvl+1,z))set(x,lvl+1,z,"moss_block");
                }
            }
        }
        /** A pool spills over its rim toward (tx, tz): one lip cell, then a fall shaft down to the lower water. */
        private void spill(double cx,double cz,double tx,double tz,double r,int from,int to){
            double dx=tx-cx,dz=tz-cz,len=Math.hypot(dx,dz);
            // The lip is the first cell outside the pool, side by side with the last pool cell toward the target.
            int px=(int)Math.round(cx),pz=(int)Math.round(cz);
            for(double s=0;s<r+3;s+=.1){
                int x=(int)Math.round(cx+dx/len*s),z=(int)Math.round(cz+dz/len*s);
                if(Math.hypot(x-cx,z-cz)>r)break;
                px=x;pz=z;
            }
            int lx=px,lz=pz;
            while(Math.hypot(lx-cx,lz-cz)<=r){
                if(Math.abs(dx)>=Math.abs(dz))lx+=dx>0?1:-1;else lz+=dz>0?1:-1;
            }
            for(int y=from+1;y<=from+3;y++)if(!water(get(lx,y,lz)))clear(lx,y,lz);
            shafts.add(new int[]{lx,lz,from,to});
        }

        // ---- the temple of the source -------------------------------------------------------------------------
        private void temple(){
            int cx=TEMPLE_X,cz=TEMPLE_Z;
            for(int x=-11;x<=11;x++)for(int z=-11;z<=11;z++){
                double r=Math.hypot(x,z);int px=cx+x,pz=cz+z;
                if(r>9.6)continue;
                reserve(px,pz);
                set(px,GROUND,pz,"smooth_quartz");
                double a=Math.toDegrees(Math.atan2(z,x))+360;int sector=(int)(a/22.5)%16;
                String floor;
                if(r>8.5){
                    String face=Math.abs(x)>Math.abs(z)?(x>0?"west":"east"):(z>0?"north":"south");
                    floor="quartz_stairs[facing="+face+",half=bottom]";
                }else if(r>7.6)floor="quartz_bricks";
                else if(r>5.6&&r<=6.3&&sector%4==1)floor="sea_lantern";
                else if(r>4.5&&r<=5.2)floor="prismarine_bricks";
                else floor=sector%2==0?"smooth_quartz":"calcite";
                set(px,GROUND+1,pz,floor);
                clear(px,GROUND+2,pz);
                top[px+RADIUS][pz+RADIUS]=GROUND+1;
            }
            // Eight columns, an entablature ring and a ribbed dome with a gold halo.
            for(int k=0;k<8;k++){
                double a=Math.toRadians(22.5+45*k);int px=cx+(int)Math.round(7*Math.cos(a)),pz=cz+(int)Math.round(7*Math.sin(a));
                set(px,GROUND+2,pz,"chiseled_quartz_block");
                for(int y=GROUND+3;y<=GROUND+11;y++)set(px,y,pz,"quartz_pillar[axis=y]");
                set(px,GROUND+12,pz,"chiseled_quartz_block");
                set(px,GROUND+14,pz,"gold_block");
            }
            for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++){
                double r=Math.hypot(x,z);
                if(r>=6.3&&r<=7.8)set(cx+x,GROUND+13,cz+z,"smooth_quartz");
                if(r>=6.6&&r<=7.6&&!"gold_block".equals(get(cx+x,GROUND+14,cz+z)))set(cx+x,GROUND+14,cz+z,"quartz_bricks");
            }
            for(int k=0;k<8;k++){
                double a=Math.toRadians(45*k);
                set(cx+(int)Math.round(7*Math.cos(a)),GROUND+12,cz+(int)Math.round(7*Math.sin(a)),"lantern[hanging=true]");
            }
            // Eight ribs curve up to an open oculus ring; a gold keystone on four light spokes holds the lantern
            // chain and points two rods at the Heart of the Sky.
            int oculus=GROUND+21;
            for(int k=0;k<8;k++){
                double a=Math.toRadians(22.5+45*k);
                for(double s=0;s<=1.0001;s+=.02){
                    double rr=2.6+4.4*Math.cos(s*Math.PI/2);int y=GROUND+14+(int)Math.round(7*Math.sin(s*Math.PI/2));
                    set(cx+(int)Math.round(rr*Math.cos(a)),y,cz+(int)Math.round(rr*Math.sin(a)),"smooth_quartz");
                }
            }
            for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++){
                double r=Math.hypot(x,z);
                if(Math.abs(r-2.7)<=.55)set(cx+x,oculus,cz+z,"chiseled_quartz_block");
                if(Math.abs(r-2.5)<=.5)set(cx+x,oculus+1,cz+z,"gold_block");
            }
            double haloR=2.6+4.4*Math.cos(Math.PI/4);int haloY=GROUND+14+(int)Math.round(7*Math.sin(Math.PI/4));
            for(double a=0;a<360;a+=2)fill(cx+(int)Math.round(haloR*Math.cos(Math.toRadians(a))),haloY,cz+(int)Math.round(haloR*Math.sin(Math.toRadians(a))),"gold_block");
            set(cx,oculus,cz,"gold_block");
            set(cx+1,oculus,cz,"end_rod[facing=east]");set(cx-1,oculus,cz,"end_rod[facing=west]");
            set(cx,oculus,cz+1,"end_rod[facing=south]");set(cx,oculus,cz-1,"end_rod[facing=north]");
            set(cx,oculus+1,cz,"end_rod[facing=up]");set(cx,oculus+2,cz,"end_rod[facing=up]");
            for(int y=oculus-1;y>=GROUND+16;y--)set(cx,y,cz,"iron_chain[axis=y]");
            set(cx,GROUND+15,cz,"lantern[hanging=true]");
            // The altar: a dais of quartz brick, three chest places on gold, a chiseled screen behind them.
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)if(Math.hypot(x,z)<=3.3)set(cx+x,GROUND+2,cz+z,"quartz_bricks");
            for(int x:CHEST_X){set(x,CHEST_Y-1,CHEST_Z,"gold_block");clear(x,CHEST_Y,CHEST_Z);clear(x,CHEST_Y+1,CHEST_Z);}
            for(int x=-3;x<=3;x++){
                set(x,CHEST_Y-1,CHEST_Z+1,"quartz_bricks");set(x,CHEST_Y,CHEST_Z+1,"chiseled_quartz_block");
                set(x,CHEST_Y+1,CHEST_Z+1,"smooth_quartz_slab[type=bottom]");
            }
            set(0,CHEST_Y+1,CHEST_Z+1,"quartz_pillar[axis=y]");set(0,CHEST_Y+2,CHEST_Z+1,"sea_lantern");
            set(-3,CHEST_Y-1,CHEST_Z,"lantern[hanging=false]");set(3,CHEST_Y-1,CHEST_Z,"lantern[hanging=false]");
            // Bridges over the moat, flush with the ground, with lantern posts.
            for(int[] b:new int[][]{{cz-13,cz-11},{cz+11,cz+13}}){
                for(int z=b[0]-1;z<=b[1]+1;z++)for(int x=-1;x<=1;x++){
                    if(levelAt(x,z)==WATER){set(x,GROUND,z,"birch_planks");reserve(x,z);}
                }
                for(int x:new int[]{-2,2}){
                    set(x,GROUND,b[0]-1,"stripped_birch_log[axis=y]");set(x,GROUND+1,b[0]-1,"stripped_birch_log[axis=y]");set(x,GROUND+2,b[0]-1,"lantern[hanging=false]");
                    set(x,GROUND,b[1]+1,"stripped_birch_log[axis=y]");set(x,GROUND+1,b[1]+1,"stripped_birch_log[axis=y]");set(x,GROUND+2,b[1]+1,"lantern[hanging=false]");
                }
            }
            path(new double[][]{{0,10},{0,4.6}},1.2);
            path(new double[][]{{0,30},{1,36},{0,41}},1.2);
            path(new double[][]{{5,-8},{12,-15},{19,-21},{26,-28},{32,-34}},1.1);
            path(new double[][]{{-9,6},{-18,12},{-27,18}},1.0);
        }
        /** Dirt-path walks with lantern posts beside them. */
        private void path(double[][] points,double hw){
            int minX=Integer.MAX_VALUE,maxX=Integer.MIN_VALUE,minZ=Integer.MAX_VALUE,maxZ=Integer.MIN_VALUE;
            for(double[] p:points){minX=Math.min(minX,(int)p[0]);maxX=Math.max(maxX,(int)p[0]);minZ=Math.min(minZ,(int)p[1]);maxZ=Math.max(maxZ,(int)p[1]);}
            for(int x=minX-3;x<=maxX+3;x++)for(int z=minZ-3;z<=maxZ+3;z++){
                if(polyline(x,z,points)>hw)continue;
                int t=topAt(x,z);if(t==Integer.MIN_VALUE||levelAt(x,z)!=Integer.MIN_VALUE)continue;
                String s=get(x,t,z);if(s==null||!(s.equals("grass_block")||s.equals("moss_block")))continue;
                set(x,t,z,hash(x,t,z,61)<.22?"mossy_stone_bricks":"dirt_path");clear(x,t+1,z);reserve(x,z);
            }
            double run=0;
            for(int i=1;i<points.length;i++){
                double dx=points[i][0]-points[i-1][0],dz=points[i][1]-points[i-1][1],len=Math.hypot(dx,dz);
                for(double s=0;s<len;s+=1){
                    run+=1;if(((int)run)%9!=4)continue;
                    double side=((int)run/9)%2==0?1:-1;
                    int px=(int)Math.round(points[i-1][0]+dx*s/len-dz/len*(hw+1.3)*side),pz=(int)Math.round(points[i-1][1]+dz*s/len+dx/len*(hw+1.3)*side);
                    int t=topAt(px,pz);
                    if(t==Integer.MIN_VALUE||levelAt(px,pz)!=Integer.MIN_VALUE||reservedAt(px,pz)||!solid(px,t,pz))continue;
                    set(px,t+1,pz,"stripped_birch_log[axis=y]");set(px,t+2,pz,"stripped_birch_log[axis=y]");set(px,t+3,pz,"lantern[hanging=false]");
                    reserve(px,pz);
                }
            }
        }

        // ---- the gate: basin, lift shaft and the leap balcony -------------------------------------------------
        private void gate(){
            pool(0,45.5,2.6,WATER,2,"mossy_stone_bricks");
            liftTop=WATER;
            int bottom=MIN_Y;
            for(int y=MIN_Y;y<=WATER;y++)set(LIFT_X,y,LIFT_Z,"bubble_column[drag=false]");
            for(int y=MIN_Y;y<=WATER;y++)for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                if(dx==0&&dz==0)continue;
                int x=LIFT_X+dx,z=LIFT_Z+dz;
                if(y>=WATER-1&&water(get(x,y,z)))continue;
                if(!solid(x,y,z))set(x,y,z,Math.floorMod(y,8)==0?"white_stained_glass":"light_blue_stained_glass");
            }
            // Calcite collars around the shaft every 24 blocks below the island.
            for(int y=MIN_Y+8;y<GROUND-20;y+=24)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)
                if(Math.max(Math.abs(dx),Math.abs(dz))==2)fill(LIFT_X+dx,y,LIFT_Z+dz,(dx==0||dz==0)?"sea_lantern":"calcite");
            // The balcony: a quartz deck past the promontory tip, glass rails, and an open end over the ground pool.
            for(int z=51;z<=54;z++)for(int x=-2;x<=2;x++){set(x,GROUND,z,"smooth_quartz_slab[type=top]");reserve(x,z);clear(x,GROUND+1,z);}
            for(int z=51;z<=54;z++)for(int x:new int[]{-2,2})set(x,GROUND+1,z,z==51||z==54?"chiseled_quartz_block":"white_stained_glass");
            for(int x:new int[]{-2,2}){set(x,GROUND+2,51,"lantern[hanging=false]");set(x,GROUND+2,54,"lantern[hanging=false]");}
            for(int x=-1;x<=1;x++)set(x,GROUND-1,51,"quartz_stairs[facing=north,half=top]");
            for(int z=41;z<=50;z++)for(int x=-3;x<=3;x++)reserve(x,z);
            // Keep the dive column clear: from the open end straight down to the pool.
            for(int x=-2;x<=2;x++)shafts.add(new int[]{x,55,GROUND+2,bottom-1});
        }

        // ---- satellite islets ---------------------------------------------------------------------------------
        private record Islet(int x,int z,int top,double rx,double rz,int depth,int salt){}
        private final List<Islet> islets=new ArrayList<>();
        private void islets(){
            int[] west=lip(RIVER_WEST),east=lip(RIVER_EAST),southeast=lip(RIVER_SOUTHEAST);
            List<int[]> westLips=lips(RIVER_WEST,west),eastLips=lips(RIVER_EAST,east),southeastLips=lips(RIVER_SOUTHEAST,southeast);
            // Petal islet catches the south-east stream below the gate's view.
            Islet petal=new Islet(southeast[0]+9,southeast[1]+5,236,11,9,20,5);
            islet(petal);
            catchPool(southeastLips,RIVER_SOUTHEAST,petal.top-1,4.4);
            for(int[] l:southeastLips)shafts.add(new int[]{l[0],l[1],WATER,petal.top-1});
            lipWest[0]=west[0];lipWest[1]=west[1];lipEast[0]=east[0];lipEast[1]=east[1];
            // Lily islet catches the west river; its pool spills off the islet's south edge to the cherry islet.
            Islet lily=new Islet(west[0]-8,west[1]-4,245,12,8.5,24,1);
            islet(lily);
            double[] lilyPool=catchPool(westLips,RIVER_WEST,lily.top-1,4.6);
            for(int[] l:westLips)shafts.add(new int[]{l[0],l[1],WATER,lily.top-1});
            Islet cherry=new Islet(west[0]-1,west[1]+13,213,12,11,20,2);
            islet(cherry);
            pool(west[0]-2,west[1]+8,4.2,cherry.top-1,2,"mossy_cobblestone");
            spill(lilyPool[0],lilyPool[1],lilyPool[0],lilyPool[1]+8,lilyPool[2],lily.top-1,cherry.top-1);
            // Basin islet catches the great east fall in its quartz-rimmed moon pool.
            Islet basin=new Islet(east[0]+12,east[1]+2,219,15,13,26,3);
            islet(basin);
            double[] moon=catchPool(eastLips,RIVER_EAST,basin.top-1,5.2);
            for(int x=-9;x<=9;x++)for(int z=-9;z<=9;z++){
                double d=Math.hypot(x,z);int px=(int)Math.round(moon[0])+x,pz=(int)Math.round(moon[1])+z;
                if(d>moon[2]+.2&&d<=moon[2]+1.4){set(px,basin.top,pz,(int)(Math.toDegrees(Math.atan2(z,x))+360)%45<10?"chiseled_quartz_block":"smooth_quartz");clear(px,basin.top+1,pz);}
            }
            for(int[] l:eastLips)shafts.add(new int[]{l[0],l[1],WATER,basin.top-1});
            // Crystal islet: an open geode bowl and crystal spires, reached by the rope bridge.
            Islet crystal=new Islet(44,-52,263,10,9,22,4);
            islet(crystal);
            crystalBowl(crystal);
            for(Islet i:islets)decorateIslet(i);
            // Glow-berry vines to climb back up: from the crown to lily and basin, from lily to cherry.
            climb(lily,null,3);climb(basin,null,3);climb(cherry,lily,2);climb(petal,null,2);
        }
        /** First cell outside the crown along a river's centre line. */
        private int[] lip(double[][] river){
            double[] a=river[river.length-3],b=river[river.length-1];
            double dx=b[0]-a[0],dz=b[1]-a[1],len=Math.hypot(dx,dz);
            for(double s=0;s<len*1.5;s+=.25){
                int x=(int)Math.round(a[0]+dx/len*s),z=(int)Math.round(a[1]+dz/len*s);
                if(Math.abs(x)>RADIUS||Math.abs(z)>RADIUS)break;
                if(!crown[x+RADIUS][z+RADIUS])return new int[]{x,z};
            }
            throw new IllegalStateException("River never leaves the crown");
        }
        /**
         * A pool that catches every lip of a fall: centred a little past the lips along the river, wide enough to
         * hold them all. Returns its centre and radius.
         */
        private double[] catchPool(List<int[]> lips,double[][] river,int lvl,double minRadius){
            double mx=0,mz=0;for(int[] l:lips){mx+=l[0];mz+=l[1];}mx/=lips.size();mz/=lips.size();
            double[] a=river[river.length-2],b=river[river.length-1];
            double dx=b[0]-a[0],dz=b[1]-a[1],len=Math.hypot(dx,dz);
            double cx=mx+dx/len*1.6,cz=mz+dz/len*1.6,r=minRadius;
            for(int[] l:lips)r=Math.max(r,Math.hypot(l[0]-cx,l[1]-cz)+1.1);
            pool(cx,cz,r,lvl,lvl>GROUND-60?2:3,"mossy_cobblestone");
            return new double[]{cx,cz,r};
        }
        /** Every open-air cell beside the river's rim water, within a few blocks of its centre lip: one wide fall. */
        private List<int[]> lips(double[][] river,int[] centre){
            List<int[]> result=new ArrayList<>();
            for(int x=centre[0]-5;x<=centre[0]+5;x++)for(int z=centre[1]-5;z<=centre[1]+5;z++){
                if(Math.abs(x)>RADIUS||Math.abs(z)>RADIUS||crown[x+RADIUS][z+RADIUS])continue;
                if(Math.hypot(x-centre[0],z-centre[1])>3.6||polyline(x,z,river)>3.2)continue;
                boolean fed=false;
                for(int[] n:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})if("water".equals(get(x+n[0],WATER,z+n[1])))fed=true;
                if(fed)result.add(new int[]{x,z});
            }
            if(result.isEmpty())result.add(centre);
            return result;
        }
        private void islet(Islet is){
            islets.add(is);
            int ext=(int)Math.ceil(Math.max(is.rx,is.rz)*1.15)+1;
            for(int x=-ext;x<=ext;x++)for(int z=-ext;z<=ext;z++){
                double a=Math.atan2(z,x);
                double rad=1+.08*Math.sin(3*a+is.salt)+.06*n1.noise((is.x+x)/9.0,(is.z+z)/9.0);
                double r=Math.hypot(x/is.rx,z/is.rz)/rad;
                if(r>1)continue;
                int px=is.x+x,pz=is.z+z;
                int t=is.top+(int)Math.round((1-r)*1.6*n2.noise(px/7.0,pz/7.0)+(r>.8?-(r-.8)*5:0));
                int depthHere=(int)(3+is.depth*Math.pow(1-r,.95)+4*Math.abs(n3.noise(px/4.0,pz/4.0))+2*n2.noise(px/6.0,pz/6.0));
                for(int y=t-depthHere;y<=t;y++){
                    int d=t-y;
                    String m=d==0?(n3.noise(px/5.0,pz/5.0)>.45?"moss_block":"grass_block"):d<3?"dirt":rockAt(px,y,pz,false,d/(is.depth+8.0));
                    if(inside(px,y,pz))set(px,y,pz,m);
                }
                if(topAt(px,pz)==Integer.MIN_VALUE||topAt(px,pz)<t)top[px+RADIUS][pz+RADIUS]=t;
            }
            // A pointed root under every islet.
            int startY=is.top-is.depth;
            for(int t=0;t<is.depth*0.7;t++){
                double r=is.rx*.32*Math.pow(1-t/(is.depth*.7),.9)+.3;
                for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++)if(dx*dx+dz*dz<=r*r)fill(is.x+dx,startY-t,is.z+dz,rockAt(is.x+dx,startY-t,is.z+dz,true,.75+t/(is.depth*2.8)));
            }
        }
        private void decorateIslet(Islet is){
            switch(is.salt){
                case 1->{willow(is.x-6,is.z-3,7,101);bush(is.x+2,is.z-7,2.2,"flowering_azalea_leaves[persistent=true]");}
                case 2->{cherry(is.x-5,is.z+4,7,201);cherry(is.x+6,is.z+3,6,202);}
                case 3->{cherry(is.x+7,is.z-5,8,301);cherry(is.x+5,is.z+7,7,302);willow(is.x-2,is.z+9,6,303);
                    for(int[] p:new int[][]{{is.x+9,is.z},{is.x-1,is.z-10}}){
                        int t=topAt(p[0],p[1]);if(t==Integer.MIN_VALUE||reservedAt(p[0],p[1]))continue;
                        set(p[0],t+1,p[1],"stripped_birch_log[axis=y]");set(p[0],t+2,p[1],"stripped_birch_log[axis=y]");set(p[0],t+3,p[1],"lantern[hanging=false]");
                    }}
                case 5->{cherry(is.x+4,is.z+2,6,501);bush(is.x-4,is.z+5,1.8,"flowering_azalea_leaves[persistent=true]");}
                default->{}
            }
        }
        private void crystalBowl(Islet is){
            for(int x=-6;x<=6;x++)for(int y=-5;y<=1;y++)for(int z=-6;z<=6;z++){
                double d=Math.sqrt(x*x+y*y*1.6+z*z);int px=is.x+x,py=is.top+y,pz=is.z+z;
                if(d>6.2||!inside(px,py,pz))continue;
                if(d>5.3){if(solid(px,py,pz))set(px,py,pz,"calcite");}
                else if(d>4.4){if(solid(px,py,pz))set(px,py,pz,hash(px,py,pz,71)<.2?"budding_amethyst":"amethyst_block");}
                else if(py<=is.top){clear(px,py,pz);if("amethyst_block".equals(get(px,py-1,pz))&&hash(px,py,pz,72)<.35)set(px,py,pz,"amethyst_cluster[facing=up]");}
            }
            for(int x=-7;x<=7;x++)for(int z=-7;z<=7;z++)reserve(is.x+x,is.z+z);
            int[][] spires={{0,0,13},{-3,2,8},{3,-2,9},{2,3,6},{-2,-3,7}};
            for(int[] s:spires){
                int base=is.top-3;
                for(int t=0;t<s[2];t++){
                    double r=1.6*(1-t/(double)s[2])+.45;int y=base+t;
                    for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                        double d=Math.hypot(dx,dz);if(d>r)continue;
                        set(is.x+s[0]+dx,y,is.z+s[1]+dz,d<.6?(t%4==1?"sea_lantern":"amethyst_block"):t%2==0?"purple_stained_glass":"light_blue_stained_glass");
                    }
                }
                set(is.x+s[0],base+s[2],is.z+s[1],"amethyst_cluster[facing=up]");
            }
        }
        /** Glow-berry vines from the island above (null: the crown) down to an islet's grass. */
        private void climb(Islet below,Islet above,int count){
            List<int[]> spots=new ArrayList<>();
            for(int x=-(int)below.rx;x<=below.rx;x++)for(int z=-(int)below.rz;z<=below.rz;z++){
                int px=below.x+x,pz=below.z+z,t=topAt(px,pz);
                if(t==Integer.MIN_VALUE||levelAt(px,pz)!=Integer.MIN_VALUE||reservedAt(px,pz)||!solid(px,t,pz))continue;
                int ceiling=-1;
                for(int y=t+4;y<=MAX_Y;y++)if(solid(px,y,pz)){ceiling=y;break;}
                if(ceiling<0)continue;
                boolean fromCrown=above==null?crown[px+RADIUS][pz+RADIUS]:Math.hypot((px-above.x)/above.rx,(pz-above.z)/above.rz)<1.05;
                if(!fromCrown||!earth(get(px,ceiling,pz)))continue;
                spots.add(new int[]{px,pz,t,ceiling});
            }
            spots.sort(Comparator.comparingDouble(s->hash(s[0],0,s[1],81)));
            int made=0;List<int[]> used=new ArrayList<>();
            for(int[] s:spots){
                if(made>=count)break;
                boolean far=true;for(int[] u:used)if(Math.hypot(u[0]-s[0],u[1]-s[1])<4)far=false;
                if(!far)continue;
                boolean clearShaft=true;for(int y=s[2]+1;y<s[3];y++)if(!empty(s[0],y,s[1]))clearShaft=false;
                if(!clearShaft)continue;
                for(int y=s[3]-1;y>s[2];y--)set(s[0],y,s[1],y==s[2]+1?"cave_vines[age=25,berries=true]":"cave_vines_plant[berries="+(hash(s[0],y,s[1],82)<.45)+"]");
                used.add(s);made++;
            }
        }

        // ---- the rope bridge to the crystal islet -------------------------------------------------------------
        private void crystalBridge(){
            double ax=24,az=-26,bx=38,bz=-46;
            // Start where the crown ends along the line, finish on the islet's grass.
            double dx=bx-ax,dz=bz-az,len=Math.hypot(dx,dz);
            double sx=ax,sz=az;
            for(double s=0;s<len;s+=.25){int x=(int)Math.round(ax+dx/len*s),z=(int)Math.round(az+dz/len*s);if(!crown[x+RADIUS][z+RADIUS]){sx=ax+dx/len*(s-1);sz=az+dz/len*(s-1);break;}}
            double ex=bx,ez=bz;int endTop=topAt((int)Math.round(bx),(int)Math.round(bz));
            if(endTop==Integer.MIN_VALUE)endTop=263;
            int startTop=Math.max(GROUND,topAt((int)Math.round(sx),(int)Math.round(sz)));
            double len2=Math.hypot(ex-sx,ez-sz),ux=(ex-sx)/len2,uz=(ez-sz)/len2;
            Map<Long,Integer> deck=new HashMap<>();
            for(double s=-1;s<=len2+1;s+=.2){
                double t=Math.clamp(s/len2,0,1);
                double surface=(startTop+1)*(1-t)+(endTop+1)*t-2.4*4*t*(1-t);
                int k=(int)Math.round(surface*2);
                for(double w=-1.2;w<=1.21;w+=.4){
                    int x=(int)Math.round(sx+ux*s-uz*w),z=(int)Math.round(sz+uz*s+ux*w);
                    deck.merge(key(x,z),k,Math::max);
                }
            }
            for(var e:deck.entrySet()){
                int x=(int)(e.getKey()>>32),z=(int)(long)e.getKey(),k=e.getValue();
                int y=k%2==0?k/2-1:k/2;String slab=k%2==0?"birch_slab[type=top]":"birch_slab[type=bottom]";
                if(solid(x,y,z)&&!get(x,y,z).startsWith("birch"))continue;
                set(x,y,z,slab);for(int c=1;c<=3;c++)if(!solid(x,y+c,z))clear(x,y+c,z);
                reserve(x,z);
            }
            // Posts with lanterns along both sides.
            for(double s=0;s<=len2;s+=3){
                double t=s/len2;double surface=(startTop+1)*(1-t)+(endTop+1)*t-2.4*4*t*(1-t);
                for(int side:new int[]{-1,1}){
                    int x=(int)Math.round(sx+ux*s-uz*2.2*side),z=(int)Math.round(sz+uz*s+ux*2.2*side);
                    int y=(int)Math.floor(surface);
                    if(solid(x,y,z)&&!get(x,y,z).startsWith("birch"))continue;
                    set(x,y,z,"birch_fence");
                    if(((int)s)%9==0)set(x,y+1,z,"lantern[hanging=false]");
                }
            }
        }

        // ---- the heart of the sky -----------------------------------------------------------------------------
        private void heart(){
            int cx=TEMPLE_X,cz=TEMPLE_Z,cy=308;
            for(int dy=-8;dy<=8;dy++){
                double r=4.4*(1-Math.abs(dy)/8.6);
                for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++){
                    double d=Math.hypot(dx,dz);if(d>r)continue;
                    String m=d>r-1.1?"light_blue_stained_glass":(d<1.3&&Math.abs(dy)<=3)?"sea_lantern":"white_stained_glass";
                    set(cx+dx,cy+dy,cz+dz,m);
                }
            }
            for(int y=cy+9;y<=cy+10;y++)set(cx,y,cz,"end_rod[facing=up]");
            // Two tilted orbit rings and drifting shards.
            for(double a=0;a<360;a+=1.2){
                double rad=Math.toRadians(a);
                int x1=cx+(int)Math.round(9*Math.cos(rad)),z1=cz+(int)Math.round(9*Math.sin(rad)),y1=cy+(int)Math.round(9*Math.sin(rad)*.38);
                fill(x1,y1,z1,((int)a)%45<3?"sea_lantern":"light_blue_stained_glass");
                int x2=cx+(int)Math.round(11*Math.cos(rad)),z2=cz+(int)Math.round(11*Math.sin(rad)),y2=cy-1+(int)Math.round(11*Math.cos(rad)*.30);
                fill(x2,y2,z2,((int)a)%30<3?"amethyst_block":"white_stained_glass");
            }
            Random random=new Random(0x4845415254L);
            for(int i=0;i<9;i++){
                double a=i*40+random.nextInt(20);int r=14+random.nextInt(4);
                int x=cx+(int)Math.round(r*Math.cos(Math.toRadians(a))),z=cz+(int)Math.round(r*Math.sin(Math.toRadians(a))),y=cy-8+random.nextInt(10);
                int length=2+random.nextInt(3);
                for(int t=0;t<length;t++)set(x,y+t,z,t==0||t==length-1?"purple_stained_glass":"amethyst_block");
                set(x,y+length,z,"amethyst_cluster[facing=up]");set(x,y-1,z,"amethyst_cluster[facing=down]");
            }
        }

        // ---- trees --------------------------------------------------------------------------------------------
        private void trees(){
            int[][] cherries={{-28,14,8},{29,19,8},{-31,-15,7},{17,35,6}};
            for(int[] c:cherries)cherry(c[0],c[1],c[2],c[0]*31+c[1]);
            willow(-15,4,8,11);willow(14,-11,7,12);
            for(int[] b:new int[][]{{-12,-31},{7,-30},{-20,-22},{14,-24},{-36,20},{36,6},{20,-34}})
                bush(b[0],b[1],1.6+hash(b[0],0,b[1],91)*1.4,hash(b[0],1,b[1],92)<.5?"flowering_azalea_leaves[persistent=true]":"azalea_leaves[persistent=true]");
        }
        private boolean plantable(int x,int z){
            int t=topAt(x,z);if(t==Integer.MIN_VALUE||reservedAt(x,z)||levelAt(x,z)!=Integer.MIN_VALUE)return false;
            String s=get(x,t,z);return s!=null&&(s.equals("grass_block")||s.equals("moss_block"));
        }
        private void leafBall(double cx,double cy,double cz,double rx,double ry,double rz,String leaf,String alt,double altRate){
            for(int x=(int)Math.floor(cx-rx);x<=Math.ceil(cx+rx);x++)for(int y=(int)Math.floor(cy-ry);y<=Math.ceil(cy+ry);y++)for(int z=(int)Math.floor(cz-rz);z<=Math.ceil(cz+rz);z++){
                double q=sq((x-cx)/rx)+sq((y-cy)/ry)+sq((z-cz)/rz)+.25*n3.noise(x/2.5,y/2.5,z/2.5);
                if(q>1||!inside(x,y,z)||!empty(x,y,z))continue;
                set(x,y,z,alt!=null&&hash(x,y,z,93)<altRate?alt:leaf);
            }
        }
        private void branch(double ax,double ay,double az,double bx,double by,double bz,double radius,String wood){
            int steps=(int)Math.ceil(Math.sqrt(sq(bx-ax)+sq(by-ay)+sq(bz-az))*3);
            for(int i=0;i<=steps;i++){
                double t=i/(double)steps;double x=ax+(bx-ax)*t,y=ay+(by-ay)*t,z=az+(bz-az)*t;
                for(int dx=(int)Math.floor(-radius);dx<=Math.ceil(radius);dx++)for(int dy=(int)Math.floor(-radius);dy<=Math.ceil(radius);dy++)for(int dz=(int)Math.floor(-radius);dz<=Math.ceil(radius);dz++){
                    if(dx*dx+dy*dy+dz*dz>radius*radius+.01)continue;
                    int px=(int)Math.round(x)+dx,py=(int)Math.round(y)+dy,pz=(int)Math.round(z)+dz;
                    if(inside(px,py,pz)&&!solid(px,py,pz)||inside(px,py,pz)&&get(px,py,pz).contains("leaves"))set(px,py,pz,wood);
                }
            }
        }
        private void cherry(int x,int z,int height,int salt){
            if(!plantable(x,z))return;
            int y0=topAt(x,z)+1;
            branch(x,y0,z,x,y0+height,z,.75,"cherry_wood[axis=y]");
            int arms=4+(int)(hash(x,salt,z,94)*2);List<double[]> ends=new ArrayList<>();
            for(int i=0;i<arms;i++){
                double a=i*Math.PI*2/arms+hash(x,i,z,95)*1.1,reach=4.5+hash(x,i,z,96)*2.5;
                double ex=x+Math.cos(a)*reach,ez=z+Math.sin(a)*reach,ey=y0+height+1.5+hash(x,i,z,97)*2.5;
                branch(x,y0+height-2-i%2,z,ex,ey,ez,.55,"cherry_wood[axis=y]");ends.add(new double[]{ex,ey,ez});
            }
            for(double[] e:ends)leafBall(e[0],e[1]+1,e[2],3.9,2.3,3.9,"cherry_leaves[persistent=true]",null,0);
            leafBall(x,y0+height+3.2,z,4.4,2.5,4.4,"cherry_leaves[persistent=true]",null,0);
            set(x,y0+height+2,z,"shroomlight");
            // Blossom drips under the canopy and petals on the grass around it.
            for(int dx=-8;dx<=8;dx++)for(int dz=-8;dz<=8;dz++){
                int px=x+dx,pz=z+dz;if(dx*dx+dz*dz>64)continue;
                for(int y=y0+height+6;y>=y0+height-2;y--){
                    if(!"cherry_leaves[persistent=true]".equals(get(px,y,pz))||!empty(px,y-1,pz))continue;
                    if(hash(px,y,pz,98)<.22){int drop=1+(int)(hash(px,y,pz,99)*3);for(int d=1;d<=drop;d++)fill(px,y-d,pz,"cherry_leaves[persistent=true]");}
                    break;
                }
                if(plantable(px,pz)&&empty(px,topAt(px,pz)+1,pz)&&hash(px,0,pz,100)<.45)
                    set(px,topAt(px,pz)+1,pz,"pink_petals[facing="+(new String[]{"north","east","south","west"}[(int)(hash(px,1,pz,101)*4)])+",flower_amount="+(1+(int)(hash(px,2,pz,102)*4))+"]");
            }
        }
        private void willow(int x,int z,int height,int salt){
            if(!plantable(x,z))return;
            int y0=topAt(x,z)+1;
            double lean=hash(x,salt,z,111)*Math.PI*2;
            double tx=x+Math.cos(lean)*1.5,tz=z+Math.sin(lean)*1.5;
            branch(x,y0,z,tx,y0+height,tz,.8,"birch_wood[axis=y]");
            for(int i=0;i<5;i++){
                double a=i*Math.PI*2/5+hash(x,i,z,112),reach=3.5+hash(x,i,z,113)*2;
                branch(tx,y0+height-1,tz,tx+Math.cos(a)*reach,y0+height+1.5,tz+Math.sin(a)*reach,.5,"birch_wood[axis=y]");
            }
            leafBall(tx,y0+height+1.5,tz,6.5,3.2,6.5,"azalea_leaves[persistent=true]","flowering_azalea_leaves[persistent=true]",.38);
            set((int)Math.round(tx),y0+height+1,(int)Math.round(tz),"shroomlight");
            // Weeping streamers from the canopy rim, a few ending in glow berries.
            for(int dx=-8;dx<=8;dx++)for(int dz=-8;dz<=8;dz++){
                double dist=Math.hypot(dx,dz);if(dist<3.2||dist>7.5)continue;
                int px=(int)Math.round(tx)+dx,pz=(int)Math.round(tz)+dz;
                for(int y=y0+height+5;y>=y0+height-3;y--){
                    String s=get(px,y,pz);if(s==null||!s.contains("azalea_leaves"))continue;
                    if(!empty(px,y-1,pz)||hash(px,y,pz,114)>.5)break;
                    int length=2+(int)(hash(px,y,pz,115)*7);int g=topAt(px,pz);
                    for(int d=1;d<=length;d++){
                        if(!empty(px,y-d,pz)||(g!=Integer.MIN_VALUE&&y-d<=g+1))break;
                        set(px,y-d,pz,d==length&&hash(px,y,pz,116)<.4?"cave_vines[age=25,berries=true]":hash(px,y-d,pz,117)<.35?"flowering_azalea_leaves[persistent=true]":"azalea_leaves[persistent=true]");
                    }
                    break;
                }
            }
        }
        /** Azalea spilling over the crown's rim, so its edge reads as an overgrown garden rather than a cut. */
        private void rimGarden(){
            for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++){
                int i=x+RADIUS,j=z+RADIUS;if(!crown[i][j]||rim[i][j]>1.5)continue;
                if(reservedAt(x,z)||levelAt(x,z)!=Integer.MIN_VALUE||hash(x,0,z,191)>.16)continue;
                int t=top[i][j];
                double a=Math.atan2(z,x);double ox=x+Math.cos(a)*1.2,oz=z+Math.sin(a)*1.2;
                String leaf=hash(x,1,z,192)<.45?"flowering_azalea_leaves[persistent=true]":"azalea_leaves[persistent=true]";
                leafBall(ox,t-.5,oz,1.8+hash(x,2,z,193),1.6,1.8+hash(x,3,z,194),leaf,"azalea_leaves[persistent=true]",.3);
            }
        }
        private void bush(int x,int z,double r,String leaf){
            if(!plantable(x,z))return;
            int y=topAt(x,z)+1;
            leafBall(x,y+r*.4,z,r,r*.8,r,leaf,null,0);
        }

        // ---- ground cover and edges ---------------------------------------------------------------------------
        private void flora(){
            String[] flowers={"allium","azure_bluet","oxeye_daisy","cornflower","lily_of_the_valley","white_tulip","pink_tulip","blue_orchid"};
            String[] faces={"north","east","south","west"};
            for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++){
                int t=topAt(x,z);if(t==Integer.MIN_VALUE||t>=MAX_Y-2)continue;
                String g=get(x,t,z);if(g==null||!empty(x,t+1,z))continue;
                double h=hash(x,t,z,121);
                if(g.equals("moss_block")){if(h<.16)set(x,t+1,z,"moss_carpet");continue;}
                if(!g.equals("grass_block"))continue;
                if(reservedAt(x,z)&&h>.12)continue;
                String face=faces[(int)(hash(x,t,z,122)*4)];
                if(h<.2)set(x,t+1,z,"short_grass");
                else if(h<.23)set(x,t+1,z,"fern");
                else if(h<.255&&empty(x,t+2,z)){set(x,t+1,z,"tall_grass[half=lower]");set(x,t+2,z,"tall_grass[half=upper]");}
                else if(h<.335){int band=Math.floorMod((int)Math.floor(n3.noise(x/14.0,z/14.0)*6)+(hash(x,t,z,123)<.25?1:0),flowers.length);set(x,t+1,z,flowers[band]);}
                else if(h<.36)set(x,t+1,z,"pink_petals[facing="+face+",flower_amount="+(2+(int)(hash(x,t,z,124)*3))+"]");
                else if(h<.38)set(x,t+1,z,"wildflowers[facing="+face+",flower_amount="+(2+(int)(hash(x,t,z,125)*3))+"]");
                else if(h<.39)set(x,t+1,z,"bush");
                else if(h<.396)set(x,t+1,z,"firefly_bush");
                else if(h<.404&&empty(x,t+2,z)){String tall=new String[]{"lilac","peony","rose_bush"}[(int)(hash(x,t,z,126)*3)];set(x,t+1,z,tall+"[half=lower]");set(x,t+2,z,tall+"[half=upper]");}
                else if(h<.41)set(x,t+1,z,hash(x,t,z,127)<.5?"azalea":"flowering_azalea");
            }
        }
        /** Vines down the rims, glow lichen on cliffs, and roots, dripstone, glow berries and blossoms beneath. */
        private void dressing(){
            String[] dirs={"east","west","south","north"};int[][] steps={{1,0},{-1,0},{0,1},{0,-1}};
            List<Runnable> later=new ArrayList<>();
            for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++)for(int y=MIN_Y+1;y<=MAX_Y-1;y++){
                String s=get(x,y,z);if(!earth(s))continue;
                int fx=x,fy=y,fz=z;
                // Underside.
                if(empty(x,y-1,z)){
                    double h=hash(x,y,z,131);
                    if(h<.05){int length=1+(int)(hash(x,y,z,132)*3);later.add(()->dripstone(fx,fy-1,fz,length));}
                    else if(h<.09&&y>238)later.add(()->fill(fx,fy-1,fz,"hanging_roots"));
                    else if(h<.13&&y>214){int length=3+(int)(hash(x,y,z,133)*12);later.add(()->hangVines(fx,fy-1,fz,length));}
                    else if(h<.136)later.add(()->fill(fx,fy-1,fz,"spore_blossom"));
                }
                // Sides: moss runs down the upper cliffs in streaks.
                int column=topAt(x,z);
                if(column!=Integer.MIN_VALUE&&y<column-1&&y>column-14&&n3.noise(x/3.5,y/5.0,z/3.5)>.18
                        &&(empty(x+1,y,z)||empty(x-1,y,z)||empty(x,y,z+1)||empty(x,y,z-1)))later.add(()->set(fx,fy,fz,"moss_block"));
                for(int d=0;d<4;d++){
                    int ax=x+steps[d][0],az=z+steps[d][1];
                    if(!empty(ax,y,az))continue;
                    String toward=dirs[d^1];
                    double h=hash(x,y,z,140+d);
                    int t=topAt(x,z);
                    if(t!=Integer.MIN_VALUE&&y==t&&(s.equals("grass_block")||s.equals("moss_block"))&&h<.32){
                        int length=2+(int)(hash(x,y,z,150+d)*9);
                        later.add(()->{for(int k=1;k<=length;k++){if(!empty(ax,fy-k,az)||!solid(fx,fy-k,fz))break;set(ax,fy-k,az,"vine["+toward+"=true]");}});
                    }else if(y<(t==Integer.MIN_VALUE?MAX_Y:t-3)&&h<.03)later.add(()->fill(ax,fy,az,"glow_lichen["+toward+"=true]"));
                }
            }
            later.forEach(Runnable::run);
        }
        private void dripstone(int x,int y,int z,int length){
            String[] order=switch(length){case 1->new String[]{"tip"};case 2->new String[]{"frustum","tip"};default->new String[]{"base","frustum","tip"};};
            for(int i=0;i<order.length;i++){if(!empty(x,y-i,z))return;}
            for(int i=0;i<order.length;i++)set(x,y-i,z,"pointed_dripstone[thickness="+order[i]+",vertical_direction=down]");
        }
        private void hangVines(int x,int y,int z,int length){
            int made=0;
            for(int k=0;k<length;k++){if(!empty(x,y-k,z)||!empty(x,y-k-1,z))break;made++;}
            for(int k=0;k<made;k++)set(x,y-k,z,k==made-1?"cave_vines[age=25,berries="+(hash(x,y,z,161)<.6)+"]":"cave_vines_plant[berries="+(hash(x,y-k,z,162)<.4)+"]");
        }

        // ---- clouds and drifting rocks ------------------------------------------------------------------------
        private boolean nearShaft(int x,int z,int margin){
            for(int[] s:shafts)if(Math.abs(s[0]-x)<=margin&&Math.abs(s[1]-z)<=margin)return true;
            return Math.abs(x-LIFT_X)<=margin+1&&Math.abs(z-LIFT_Z)<=margin+1||Math.abs(x)<=margin+3&&z>=50&&z<=56+margin;
        }
        private void clouds(){
            int[][] clouds={{74,232,-34,10},{-76,226,36,11},{30,204,64,9},{-46,248,-66,9},{-86,262,-6,7},{58,290,-72,7}};
            for(int[] c:clouds){
                Random random=new Random(c[0]*31L+c[2]);
                List<double[]> puffs=new ArrayList<>();
                puffs.add(new double[]{c[0],c[1],c[2],c[3],c[3]*.36,c[3]*.66});
                for(int i=0;i<6;i++){
                    double a=random.nextDouble()*Math.PI*2,d=random.nextDouble()*c[3]*.7;
                    puffs.add(new double[]{c[0]+Math.cos(a)*d,c[1]+1+random.nextDouble()*2.5,c[2]+Math.sin(a)*d*.7,c[3]*(.35+random.nextDouble()*.3),c[3]*(.3+random.nextDouble()*.2),c[3]*(.3+random.nextDouble()*.25)});
                }
                List<int[]> cloud=new ArrayList<>();
                for(double[] p:puffs)for(int x=(int)(p[0]-p[3]);x<=p[0]+p[3];x++)for(int y=(int)(p[1]-p[4]);y<=p[1]+p[4];y++)for(int z=(int)(p[2]-p[5]);z<=p[2]+p[5];z++){
                    if(!inside(x,y,z)||!empty(x,y,z)||nearShaft(x,z,3))continue;
                    double q=sq((x-p[0])/p[3])+sq((y-p[1])/p[4])+sq((z-p[2])/p[5])+.3*n2.noise(x/3.0,y/3.0,z/3.0);
                    if(q<=1){set(x,y,z,"white_wool");cloud.add(new int[]{x,y,z});}
                }
                for(int[] p:cloud){
                    int x=p[0],y=p[1],z=p[2];
                    if(empty(x,y-1,z))set(x,y,z,hash(x,y,z,171)<.7?"light_gray_wool":"white_wool");
                    else if(empty(x,y+1,z)&&hash(x,y,z,172)<.35)set(x,y,z,"white_concrete");
                    else if(hash(x,y,z,173)<.06&&(empty(x+1,y,z)||empty(x-1,y,z)||empty(x,y,z+1)||empty(x,y,z-1)))set(x,y,z,"white_stained_glass");
                }
            }
        }
        private void floatingRocks(){
            Random random=new Random(0x524f434b53L);
            int made=0;
            for(int attempt=0;attempt<200&&made<16;attempt++){
                double a=random.nextDouble()*Math.PI*2,d=56+random.nextDouble()*38;
                int x=(int)Math.round(Math.cos(a)*d),z=(int)Math.round(Math.sin(a)*d),y=200+random.nextInt(95);
                double r=1.6+random.nextDouble()*2.2;int ext=(int)Math.ceil(r)+3;
                if(Math.abs(x)+ext>RADIUS||Math.abs(z)+ext>RADIUS||y-ext*2<MIN_Y||y+ext>MAX_Y-2||nearShaft(x,z,(int)r+3))continue;
                boolean free=true;
                for(int dx=-ext;dx<=ext&&free;dx++)for(int dy=-ext*2;dy<=ext;dy++)for(int dz=-ext;dz<=ext;dz++)if(!empty(x+dx,y+dy,z+dz)){free=false;break;}
                if(!free)continue;
                for(int dx=-ext;dx<=ext;dx++)for(int dy=-ext*2;dy<=1;dy++)for(int dz=-ext;dz<=ext;dz++){
                    double q=sq(dx/r)+sq(dz/r)+(dy>0?sq(dy/(r*.5)):sq(dy/(r*1.6)))+.35*n1.noise((x+dx)/2.0,(y+dy)/2.0,(z+dz)/2.0);
                    if(q>1)continue;
                    set(x+dx,y+dy,z+dz,dy==1||(dy==0&&q>.6)?"moss_block":rockAt(x+dx,y+dy,z+dz,dy<-r,-dy/(r*2.2)));
                }
                int t=y+1;while(t<MAX_Y&&!empty(x,t+1,z))t++;
                if(empty(x,t+1,z)&&hash(x,t,z,181)<.7)set(x,t+1,z,hash(x,t,z,182)<.5?"flowering_azalea":"pink_tulip");
                made++;
            }
        }

        // ---- water: falls and sealing -------------------------------------------------------------------------
        private void falls(){
            for(int[] s:shafts){
                int x=s[0],z=s[1],from=s[2],to=s[3];
                if(to<MIN_Y){for(int y=from;y>=MIN_Y;y--)clear(x,y,z);continue;}
                // The lip flows from the source beside it, then falls; nothing else stays in the shaft.
                set(x,from,z,"water[level=1]");
                for(int y=from-1;y>to;y--)set(x,y,z,"water[level=8]");
                for(int y=from+1;y<=from+2;y++)if(!water(get(x,y,z)))clear(x,y,z);
            }
        }
        /** Any source water beside open air or a plant gets a stone wall; any source over air gets a bed. */
        private void seal(){
            int[][] steps={{1,0},{-1,0},{0,1},{0,-1}};
            for(int round=0;round<2;round++)
            for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++)for(int y=MIN_Y;y<=MAX_Y;y++){
                String s=get(x,y,z);if(!source(s))continue;
                if(x==LIFT_X&&z==LIFT_Z&&s.startsWith("bubble_column"))continue;
                for(int[] st:steps){
                    int ax=x+st[0],az=z+st[1];String n=get(ax,y,az);
                    if(n==null||plant(n)&&!water(n)){if(inside(ax,y,az))set(ax,y,az,y>=GROUND-1?"mossy_stone_bricks":"mossy_cobblestone");}
                }
                String below=get(x,y-1,z);
                if(below==null||plant(below)&&!water(below))if(inside(x,y-1,z))set(x,y-1,z,"clay");
            }
        }

        private CelestialCascade freeze(){
            Map<Long,List<Integer>> lists=new HashMap<>();
            int count=0,lowest=MAX_Y,highest=MIN_Y;
            for(int x=-RADIUS;x<=RADIUS;x++)for(int z=-RADIUS;z<=RADIUS;z++)for(int y=MIN_Y;y<=MAX_Y;y++){
                short v=cells[index(x,y,z)];if(v==0)continue;
                int rx=Math.floorDiv(x+8,16),rz=Math.floorDiv(z+8,16),lx=Math.floorMod(x+8,16),lz=Math.floorMod(z+8,16);
                lists.computeIfAbsent(key(rx,rz),k->new ArrayList<>()).add((lx<<28)|(lz<<24)|((y-MIN_Y)<<16)|(v&0xffff));
                count++;lowest=Math.min(lowest,y);highest=Math.max(highest,y);
            }
            Map<Long,int[]> chunks=new HashMap<>();
            for(var e:lists.entrySet())chunks.put(e.getKey(),e.getValue().stream().mapToInt(Integer::intValue).toArray());
            return new CelestialCascade(palette.toArray(String[]::new),Map.copyOf(chunks),count,lowest,highest);
        }
    }
}
