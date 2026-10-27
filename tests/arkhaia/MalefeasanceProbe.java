import com.example.voidscape.boss.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.potion.*;
import java.util.*;

/** Real Paper: Advance Magic immunity, actual loss-based drain, safe blinks, sky gates and rebirth. */
public class MalefeasanceProbe extends AcheronSplitProbe {
    @Override void run()throws Exception{SanctuaryProbeFixture.open(this,com.example.voidscape.world.OverworldSanctuaries.Kind.MALEFEASANCE);mechanics();garden.sanctuaryEncounters().close();finish(null);}
    @Override Object minions(){return controller;}
    @Override void wound(Mob victim,double amount){SanctuaryProbeFixture.wound(this,victim,amount);}
    Object dimensions,rift;
    void freshMalef()throws Exception{
        summon(ArkhaiaWaves.Kind.MALEFEASANCE);resetPlayer(pad.clone().add(0,0,14));
        dimensions=get(get(controller,"trials"),"dimensions");
        rift=call(dimensions,"state",new Class<?>[]{unit.getClass(),long.class},unit,now);
        put(rift,"nextBatch",Long.MAX_VALUE);put(rift,"nextBlink",Long.MAX_VALUE);
    }
    void strike(boolean red)throws Exception{player.setNoDamageTicks(0);call(dimensions,"strike",new Class<?>[]{rift.getClass(),Player.class,boolean.class,long.class},rift,player,red,now);}
    double health()throws Exception{return (double)get(unit,"health");}
    @SuppressWarnings("unchecked") List<Object> gates()throws Exception{return (List<Object>)get(rift,"gates");}
    void emit(boolean red)throws Exception{
        put(rift,"crimson",red);call(dimensions,"emit",new Class<?>[]{rift.getClass(),List.class,long.class},rift,List.of(player),now);
        // Isolate the committed player gate; the spreading sky gates have separate coverage checks.
        List<Object> gates=gates();Object first=gates.getFirst();gates.clear();gates.add(first);
    }
    void beamBounds(Object gate,double bottom)throws Exception{
        Map<?,?> planes=(Map<?,?>)get(get(controller,"sigils"),"planes");ItemDisplay beam=(ItemDisplay)planes.get(get(gate,"key")+":beam:x");
        check(beam!=null,"active sky gate owns a visible beam display");
        var transform=beam.getTransformation();double middle=beam.getY()+transform.getTranslation().y,length=transform.getScale().y;
        check(Math.abs(middle+length*.5-((Location)get(gate,"sky")).getY())<1e-4&&Math.abs(middle-length*.5-bottom)<1e-4,"visible beam boundaries match the sky and the block-clipped damage interval");
    }
    @Override void mechanics()throws Exception{
        world.setDifficulty(Difficulty.NORMAL);controller=minions();invoke(controller,"demo",new Class<?>[]{UUID.class,boolean.class},player.getUniqueId(),false);
        pad=((Location)get(controller,"center")).clone();now=(long)get(controller,"clock");
        freshMalef();
        check(health()==600&&mob.getHealth()==600&&mob.getAttribute(Attribute.MAX_HEALTH).getValue()==600,"Malefeasance has 600 native and encounter HP");
        var magic=((com.example.advancemagic.AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic")).context();
        for(DamageType type:List.of(DamageType.INDIRECT_MAGIC,DamageType.FIREBALL,DamageType.LIGHTNING_BOLT)){
            double lost=magic.damage(player,mob,1000,type);
            check(lost==0&&health()==600&&mob.getHealth()==600,"Advance Magic damage is fully rejected for "+type.getKey());
        }
        check(!magic.affect(player,mob,null),"Advance Magic affect event rejects control/status spells on Malefeasance");
        wound(mob,20);check(health()==580&&mob.getHealth()==580,"ordinary projectile damage still works immediately after rejected magic");
        check(((Map<?,?>)get(unit,"hits")).size()==1,"rejected magic does not consume the ordinary damage cooldown");

        freshMalef();emit(false);Location committed=((Location)get(gates().getFirst(),"ground")).clone();riverTicks(29);
        check(player.getHealth()==200&&!player.hasPotionEffect(PotionEffectType.WITHER),"sky gate keeps its full warning and visible six-tick descent before contact");riverTicks(1);
        check(player.getHealth()==180&&player.getPotionEffect(PotionEffectType.WITHER).getAmplifier()==1,"violet beam hits once for 20 and applies Wither II");riverTicks(6);
        check(player.getHealth()==180,"one lingering beam cannot repeatedly damage the same player");riverTicks(2);
        check(((Map<?,?>)get(get(controller,"sigils"),"planes")).keySet().stream().noneMatch(k->k.toString().contains(":beam:")),"visible beam closes when its damaging contact window ends");
        freshMalef();emit(true);riverTicks(30);
        check(player.getHealth()==176&&player.getPotionEffect(PotionEffectType.WEAKNESS).getAmplifier()==0,"crimson beam applies Weakness I with its first 24-damage strike");
        freshMalef();emit(false);player.teleport(player.getLocation().add(4,0,0));riverTicks(35);
        check(player.getHealth()==200,"committed sky circle can be escaped before the beam falls");
        freshMalef();emit(false);Location at=player.getLocation();
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)world.getBlockAt(at.getBlockX()+x,pad.getBlockY()+4,at.getBlockZ()+z).setType(Material.STONE,false);
        riverTicks(35);check(player.getHealth()==200,"solid overhead terrain stops a descending beam before the player");
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)world.getBlockAt(at.getBlockX()+x,pad.getBlockY()+4,at.getBlockZ()+z).setType(Material.AIR,false);

        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)for(int y=15;y<=74;y++)world.getBlockAt(pad.getBlockX()+x,pad.getBlockY()+y,pad.getBlockZ()+14+z).setType(Material.AIR,false);
        freshMalef();Location aerial=pad.clone().add(0,40,14);resetPlayer(aerial);
        check((boolean)call(controller,"accepts",new Class<?>[]{Player.class},player),"high Malefeasance fixture uses active sanctuary target eligibility");
        emit(false);Object highGate=gates().getFirst();Location highSky=((Location)get(highGate,"sky")).clone();
        check(Math.abs(highSky.getY()-aerial.getY()-12)<1e-8,"sky gate snapshots a launch seal above its committed aerial target");
        player.teleport(aerial.clone().add(0,20,0));riverTicks(30);
        check(player.getHealth()==200&&((Set<?>)get(highGate,"victims")).isEmpty()&&((Location)get(highGate,"sky")).distanceSquared(highSky)<1e-8,"boosting entirely above the committed seal avoids invisible beam damage without moving the seal");
        beamBounds(highGate,((Location)get(highGate,"ground")).getY());player.teleport(aerial);riverTicks(1);
        check(player.getHealth()==180,"an aerial player inside the visible beam is struck during its existing contact window");riverTicks(7);
        check(((Map<?,?>)get(get(controller,"sigils"),"planes")).keySet().stream().noneMatch(k->k.toString().contains(":beam:")),"raised beam visuals close with the same contact deadline as grounded beams");

        freshMalef();resetPlayer(aerial);check((boolean)call(controller,"accepts",new Class<?>[]{Player.class},player),"high roof fixture remains an eligible sanctuary participant");emit(false);Object roofGate=gates().getFirst();
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)world.getBlockAt(aerial.getBlockX()+x,pad.getBlockY()+20,aerial.getBlockZ()+z).setType(Material.STONE,false);
        player.teleport(pad.clone().add(0,0,14));riverTicks(30);
        check(player.getHealth()==200&&((Set<?>)get(roofGate,"victims")).isEmpty(),"an elevated beam cannot hit below the solid surface that stops its visible descent");
        beamBounds(roofGate,pad.getBlockY()+21);player.teleport(pad.clone().add(0,25,14));riverTicks(1);
        check(player.getHealth()==180,"a player above the blocking roof can still contact the visible aerial beam");
        for(int x=-4;x<=4;x++)for(int z=-4;z<=4;z++)world.getBlockAt(aerial.getBlockX()+x,pad.getBlockY()+20,aerial.getBlockZ()+z).setType(Material.AIR,false);

        freshMalef();wound(mob,200);strike(true);
        check(health()==400&&player.getHealth()==176,"first crimson hit marks the player without immediate free healing");
        riverTicks(11);strike(false);
        check(Math.abs(health()-412)<1e-8&&mob.getHealth()==health(),"subsequent violet hit drains sixty percent of actual HP loss and synchronizes native health");
        riverTicks(11);strike(true);check(Math.abs(health()-426.4)<1e-8,"subsequent crimson hit can also drain the marked player");
        player.removePotionEffect(PotionEffectType.WEAKNESS);double before=health();riverTicks(11);strike(false);
        check(health()==before,"cleansing Weakness stops blood debt healing");
        freshMalef();wound(mob,200);strike(true);riverTicks(11);player.getAttribute(Attribute.MAX_ABSORPTION).setBaseValue(80);player.setAbsorptionAmount(80);before=health();double hp=player.getHealth();strike(false);
        check(player.getHealth()==hp&&health()==before,"absorption-only damage cannot produce free boss healing");
        freshMalef();wound(mob,200);player.setInvulnerable(true);actor.getAbilities().invulnerable=true;strike(true);
        check(health()==400&&!player.hasPotionEffect(PotionEffectType.WEAKNESS)&&((Map<?,?>)get(rift,"bloodMarks")).isEmpty(),"rejected damage cannot apply the drain mark or heal the boss");
        player.setInvulnerable(false);actor.getAbilities().invulnerable=false;
        freshMalef();player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,400,1));strike(true);invoke(controller,"clear",new Class<?>[]{});
        check(player.getPotionEffect(PotionEffectType.WEAKNESS).getAmplifier()==1,"cleanup preserves another source's stronger Weakness effect");

        freshMalef();put(rift,"nextBlink",now);Location from=mob.getLocation();riverTicks(1);Location landing=((Location)get(rift,"landing")).clone();
        check((long)get(rift,"blinkAt")==now+16&&mob.getLocation().distanceSquared(from)<1e-8,"blink marks its landing for sixteen ticks before instant travel");
        riverTicks(15);check(mob.getLocation().distanceSquared(from)<1e-8,"boss does not move before the portal warning completes");riverTicks(1);
        check(mob.getLocation().distanceSquared(landing)<1e-8&&mob.getLocation().distanceSquared(from)>=49,"blink travels instantly to the committed safe location");
        check(Math.hypot(mob.getX()-pad.getX(),mob.getZ()-pad.getZ())<=29&&mob.getLocation().subtract(0,1,0).getBlock().getType().isSolid(),"blink preserves the arena boundary and solid footing");
        check(Math.hypot(mob.getX()-player.getX(),mob.getZ()-player.getZ())>=3.5,"blink never lands inside a player");
        riverTicks(140);check((int)get(rift,"hops")==1,"no second blink within seven seconds of landing");
        riverTicks(30);check((int)get(rift,"hops")>=2,"the next telegraphed blink follows about 8.3 seconds after the first");

        freshMalef();put(rift,"nextBatch",now);player.setInvulnerable(true);actor.getAbilities().invulnerable=true;riverTicks(110);
        check(gates().size()>=8&&gates().size()<=48,"frequent batches keep many bounded sky circles visible together");
        check(gates().stream().anyMatch(g->{try{return (boolean)get(g,"crimson");}catch(Exception e){throw new RuntimeException(e);}})&&gates().stream().anyMatch(g->{try{return !(boolean)get(g,"crimson");}catch(Exception e){throw new RuntimeException(e);}}),"purple and crimson gates overlap visually when the mode changes");
        player.setInvulnerable(false);actor.getAbilities().invulnerable=false;

        freshMalef();emit(true);strike(true);wound(mob,10000);
        check((boolean)get(unit,"resurrected")&&health()==300&&mob.getHealth()==300&&mob.isValid(),"first lethal hit revives exactly once at fifty percent: 300 HP");
        check(((Map<?,?>)get(controller,"spirits")).size()==1&&gates().isEmpty(),"rebirth retains the encounter and cancels old attacks without final death");
        check(!player.hasPotionEffect(PotionEffectType.WEAKNESS),"rebirth removes the old owned blood debt");
        wound(mob,10000);check(health()==300,"rebirth ritual cannot be skipped by another lethal hit");riverTicks(49);
        check(health()==300&&get(unit,"pose").equals("rebirth"),"rebirth animation owns the full protected ritual");riverTicks(2);wound(mob,10000);
        check(((Map<?,?>)get(controller,"spirits")).isEmpty()&&!mob.isValid(),"second lethal hit defeats the boss without a second revival");
        freshMalef();check(!(boolean)get(unit,"resurrected")&&health()==600,"a fresh encounter restores the one-time rebirth charge");strike(true);strike(false);emit(false);
        invoke(controller,"clear",new Class<?>[]{});
        check(((Map<?,?>)get(dimensions,"states")).isEmpty()&&!player.hasPotionEffect(PotionEffectType.WITHER)&&!player.hasPotionEffect(PotionEffectType.WEAKNESS),"reset removes all dimensional states, marks and owned potion effects");
        check(((Map<?,?>)get(get(controller,"sigils"),"planes")).keySet().stream().noneMatch(k->k.toString().startsWith("malef:")),"reset leaves no sky, beam, portal, drain or revival displays");
        List<String> clips=ArkhaiaBedrockSupport.riverClips("arkhaia_malefeasance");
        check(clips.size()==17&&clips.get(10).equals("sky_windup")&&clips.get(16).equals("rebirth"),"seventeen model animation indexes match the Java/Bedrock protocol");
    }
}
