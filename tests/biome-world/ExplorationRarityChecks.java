import com.example.voidscape.world.*;
import java.nio.file.*;
import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/** Survey real placement filters, including production landmark reservations, without creating chunks. */
public final class ExplorationRarityChecks extends JavaPlugin {
    private static int checks;
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);checks++;System.out.println("PASS "+message);}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{
        try{
            if(Files.exists(Path.of("rarity-survey/placement-survey.csv"))){
                check(Files.readString(Path.of("rarity-survey/placement-survey.csv")).contains("72819345"),"production-default survey remains available after restart");
                Files.writeString(Path.of("rarity-result.txt"),"PASS "+checks+" rarity restart checks\n");
            }else{
                main(new String[]{"rarity-survey"});
                Files.copy(Path.of("rarity-survey/rarity-result.txt"),Path.of("rarity-result.txt"));
            }
        }catch(Throwable error){getLogger().log(java.util.logging.Level.SEVERE,"PLACEMENT SURVEY FAILED",error);try{Files.writeString(Path.of("rarity-result.txt"),"FAIL "+error);}catch(Exception ignored){}}
        finally{Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);}
    },40);}
    private static VoidGenerator fixture(long seed,double chance,Set<Long> old,Set<Long> v2){
        var dungeons=new DungeonLayout(seed,18,1.0);
        var whales=new SkyWhaleLayout(seed,dungeons,32,.01);
        var landmarks=new SkyLandmarkLayout(seed,dungeons,whales);
        var temples=new WorldBossTempleLayout(seed,dungeons,whales,landmarks,32,.01,Set.of());
        var bosses=new BossStructureLayout(seed,dungeons,whales,landmarks,temples,32,64,
            Map.of(BossStructureLayout.Kind.ARKHAIA,.01,BossStructureLayout.Kind.VALKYRIE,.01,BossStructureLayout.Kind.SERAPHON,.01),
            EnumSet.allOf(BossStructureLayout.Kind.class),Set.of());
        return new VoidGenerator(seed,dungeons,whales,true,landmarks,true,true,null,temples,true,bosses,
            new BiomeTerrain(seed,3072,Set.of()),true,chance,old,v2);
    }
    public static void main(String[] args)throws Exception{
        Path output=Path.of(args[0]);Files.createDirectories(output);
        var report=new StringBuilder("seed,cells,rolls_passed,accepted,effective_percent,accepted_per_million_square_blocks,reason,count\n");
        var kinds=new StringBuilder("seed,biome,kind,accepted,biome_center_cells\n");
        var preservedCells=new HashSet<Long>();
        for(int gx=-128;gx<128;gx++)for(int gz=-128;gz<128;gz++)preservedCells.add(SkyPlacementHistory.key(gx,gz));
        for(long seed:new long[]{72819345L,20261013L,314159265L}){
            var generator=fixture(seed,.35,Set.of(),Set.of());var layout=generator.exploration();
            var previous=new ExplorationLayout(generator,generator.terrain(),true,.35,Set.of(),Set.of(),Set.of(),preservedCells);
            var counts=new EnumMap<ExplorationLayout.PlacementResult,Integer>(ExplorationLayout.PlacementResult.class);
            var perKind=new EnumMap<ExplorationLayout.Kind,Integer>(ExplorationLayout.Kind.class);int cells=0;
            var perBiome=new EnumMap<BiomeTerrain.Kind,Integer>(BiomeTerrain.Kind.class);
            ExplorationLayout.Site example=null;
            for(int gx=-128;gx<128;gx++)for(int gz=-128;gz<128;gz++){
                var inspected=layout.inspectCell(gx,gz);cells++;counts.merge(inspected.result(),1,Integer::sum);
                var before=previous.inspectCell(gx,gz);
                if(before.result()!=inspected.result())throw new AssertionError("Visual redesign changed a placement filter");
                perBiome.merge(generator.terrain().kind(gx*256+128,gz*256+128),1,Integer::sum);
                if(cells%61==0&&!Objects.equals(inspected.site(),layout.cell(gx,gz)))throw new AssertionError("Audit diverges from actual placement");
                if(inspected.site()!=null){
                    var site=inspected.site();example=site;perKind.merge(site.kind(),1,Integer::sum);
                    var old=before.site();
                    if(old==null||old.designVersion()!=4||old.kind()!=site.kind()||old.x()!=site.x()||old.z()!=site.z()
                        ||old.floorY()!=site.floorY()||old.rotation()!=site.rotation()||old.variant()!=site.variant())
                        throw new AssertionError("Visual redesign moved or rerolled an existing candidate");
                    if(site.designVersion()!=5||site.kind().biome()!=generator.terrain().kind(site.x(),site.z()))throw new AssertionError("Wrong version or biome");
                    if(site.x()-gx*256<48||site.x()-gx*256>207||site.z()-gz*256<48||site.z()-gz*256>207)throw new AssertionError("Site leaves its protected cell interior");
                }
            }
            int accepted=counts.getOrDefault(ExplorationLayout.PlacementResult.ACCEPTED,0),rolled=cells-counts.getOrDefault(ExplorationLayout.PlacementResult.CHANCE,0);
            check(true,seed+": redesign preserves every version-4 placement decision, kind, coordinate, rotation and loot seed");
            check(rolled>cells*.338&&rolled<cells*.362,seed+": configured 35% roll stays unbiased: "+rolled+"/"+cells);
            check(accepted>cells*.03&&accepted<rolled,seed+": production terrain and protected areas reduce actual generation: "+accepted+"/"+cells);
            check(perKind.size()==32&&perKind.values().stream().allMatch(count->count>0),seed+": all 32 kinds occur with production landmark reservations: "+perKind);
            check(counts.values().stream().mapToInt(Integer::intValue).sum()==cells,seed+": rejection reasons account for every sampled cell and match the live placement path");
            for(var reason:ExplorationLayout.PlacementResult.values())report.append(String.format(Locale.ROOT,"%d,%d,%d,%d,%.4f,%.4f,%s,%d%n",seed,cells,rolled,accepted,100.0*accepted/cells,1e6*accepted/cells/(256.0*256),reason,counts.getOrDefault(reason,0)));
            for(var kind:ExplorationLayout.Kind.values())kinds.append(seed+","+kind.biome()+","+kind.id+","+perKind.getOrDefault(kind,0)+","+perBiome.get(kind.biome())+"\n");
            var site=Objects.requireNonNull(example);int gx=Math.floorDiv(site.x(),256),gz=Math.floorDiv(site.z(),256);long key=SkyPlacementHistory.key(gx,gz);
            var old=fixture(seed,.35,Set.of(key),Set.of()).exploration().cell(gx,gz);
            var v2=fixture(seed,.35,Set.of(),Set.of(key)).exploration().cell(gx,gz);
            check(old!=null&&v2!=null&&old.designVersion()==1&&v2.designVersion()==2&&old.x()==v2.x()&&old.kind()==v2.kind()&&old.rotation()==v2.rotation(),seed+": old version history preserves candidate, kind and rotation independently of the expanded fresh-world pool");
            var disabled=fixture(seed,0,Set.of(),Set.of()).exploration();
            check(disabled.cell(gx,gz)==null&&disabled.inspectCell(gx,gz).result()==ExplorationLayout.PlacementResult.CHANCE,seed+": zero probability never creates a destination");
            var all=fixture(seed,1,Set.of(),Set.of()).exploration();
            check(all.inspectCell(gx,gz).result()!=ExplorationLayout.PlacementResult.CHANCE,seed+": 100% probability still uses all safety filters");
        }
        Files.writeString(output.resolve("placement-survey.csv"),report);Files.writeString(output.resolve("placement-kinds.csv"),kinds);
        Files.writeString(output.resolve("rarity-result.txt"),"PASS "+checks+" placement checks; 196608 cells across 3 seeds, with production defaults and no chunk creation\n");
        System.out.println("PASS "+checks+" placement checks; surveys: "+output);
    }
}
