import com.example.voidscape.boss.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.*;
import java.util.*;

/** Real Paper true-heart hits, raised shields, native wear, timed drains/conversion and fatal execution. */
public class StyxProbe extends RiverTrialsProbe implements Listener {
    boolean cancelHit;int shieldBreaks;
    @EventHandler(priority=EventPriority.HIGHEST) public void cancel(EntityDamageEvent event){if(cancelHit&&event.getEntity()==player)event.setCancelled(true);}
    @EventHandler public void broke(org.bukkit.event.player.PlayerItemBreakEvent event){if(event.getPlayer()==player&&event.getBrokenItem().getType()==Material.SHIELD)shieldBreaks++;}
    void freshStyx()throws Exception{
        freshTrial(ArkhaiaWaves.Kind.STYX);put(state,"nextSiphon",Long.MAX_VALUE);put(state,"nextBerserk",Long.MAX_VALUE);
        resetPlayer(pad.clone().add(0,0,14));actor.stopUsingItem();
    }
    double styxHp()throws Exception{return (double)get(unit,"health");}
    void styxHp(double health)throws Exception{put(unit,"health",health);mob.setHealth(health);}
    boolean strike(double amount)throws Exception{player.setNoDamageTicks(0);return (boolean)call(trials,"styxStrike",new Class<?>[]{state.getClass(),Player.class,double.class,long.class},state,player,amount,now);}
    void armor(){player.getInventory().setArmorContents(new ItemStack[]{new ItemStack(Material.DIAMOND_BOOTS),new ItemStack(Material.DIAMOND_LEGGINGS),new ItemStack(Material.DIAMOND_CHESTPLATE),new ItemStack(Material.DIAMOND_HELMET)});}
    int[] wear(){return Arrays.stream(player.getInventory().getArmorContents()).mapToInt(i->((Damageable)i.getItemMeta()).getDamage()).toArray();}
    void shield(boolean off)throws Exception{
        ItemStack item=new ItemStack(Material.SHIELD);item.addUnsafeEnchantment(Enchantment.UNBREAKING,10);
        if(off)player.getInventory().setItemInOffHand(item);else player.getInventory().setItemInMainHand(item);
        actor.startUsingItem(off?net.minecraft.world.InteractionHand.OFF_HAND:net.minecraft.world.InteractionHand.MAIN_HAND);
        for(int i=0;i<6;i++)actor.doTick();check(player.isBlocking(),"native "+(off?"offhand":"main-hand")+" shield is raised");
    }
    @Override void mechanics()throws Exception{
        Bukkit.getPluginManager().registerEvents(this,this);world.setDifficulty(Difficulty.NORMAL);
        controller=minions();invoke(controller,"demo",new Class<?>[]{UUID.class,boolean.class},player.getUniqueId(),false);
        pad=((Location)get(controller,"center")).clone();now=(long)get(controller,"clock");
        for(int x=-36;x<=36;x++)for(int z=-36;z<=36;z++){
            world.getBlockAt(pad.getBlockX()+x,pad.getBlockY()-1,pad.getBlockZ()+z).setType(Material.STONE,false);
            for(int y=0;y<=14;y++)world.getBlockAt(pad.getBlockX()+x,pad.getBlockY()+y,pad.getBlockZ()+z).setType(Material.AIR,false);
        }
        freshStyx();check(styxHp()==600&&mob.getHealth()==600&&mob.getAttribute(Attribute.MAX_HEALTH).getValue()==900,"Styx starts at 600 native/encounter HP with a 900 cap");
        for(Material weapon:List.of(Material.AIR,Material.NETHERITE_SWORD,Material.NETHERITE_AXE,Material.NETHERITE_PICKAXE,Material.TRIDENT)){
            player.getInventory().setItemInMainHand(new ItemStack(weapon));mob.damage(80,player);
            check(styxHp()==600&&((Map<?,?>)get(unit,"hits")).isEmpty(),"melee immunity rejects "+weapon+" without consuming ranged cooldown");
        }
        var magic=((com.example.advancemagic.AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic")).context();
        magic.damage(player,mob,50,DamageType.INDIRECT_MAGIC);check(styxHp()==550&&mob.getHealth()==550,"Advance Magic remains an accepted counter to melee immunity: hp="+styxHp()+" native="+mob.getHealth());
        riverTicks(11);wound(mob,30);check(styxHp()==520,"ordinary travelling projectile damage also remains accepted");

        freshStyx();armor();for(ItemStack item:player.getInventory().getArmorContents())item.addUnsafeEnchantment(Enchantment.PROTECTION,20);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,300,4));player.getAttribute(Attribute.MAX_ABSORPTION).setBaseValue(100);player.setAbsorptionAmount(80);
        check(strike(32)&&Math.abs(player.getHealth()-168)<1e-8&&player.getAbsorptionAmount()==80,"normal Styx hit deals exactly 32 heart HP through armor, Protection, Resistance and absorption");
        check(Arrays.stream(wear()).allMatch(v->v==64),"accepted normal hit wears every armor piece by 64: eight times the original impact rate");
        freshStyx();armor();shield(true);int breaks=shieldBreaks;check(strike(32)&&player.getHealth()==168,"raised shield cannot reduce true-heart damage");
        check(player.getInventory().getItemInOffHand().isEmpty()&&shieldBreaks==breaks+1&&!player.isHandRaised(),"offhand shield breaks immediately despite Unbreaking X");
        freshStyx();shield(false);check(strike(32)&&player.getInventory().getItemInMainHand().isEmpty(),"raised main-hand shield also breaks immediately");
        freshStyx();player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));strike(32);
        check(player.getInventory().getItemInOffHand().getType()==Material.SHIELD,"shield merely carried in inventory is not destroyed");
        freshStyx();armor();shield(true);put(unit,"siphonUntil",now+200);cancelHit=true;boolean accepted=strike(32);cancelHit=false;
        check(!accepted&&player.getHealth()==200&&styxHp()==600&&Arrays.stream(wear()).allMatch(v->v==0)&&player.getInventory().getItemInOffHand().getType()==Material.SHIELD,"cancelled hit cannot damage hearts, drain HP, wear armor or break a shield");actor.stopUsingItem();
        freshStyx();player.setHealth(10);player.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));
        check(strike(32)&&!player.isDead()&&player.getHealth()==1,"ordinary lethal true-heart attack still permits a Totem rescue");

        freshStyx();strike(32);check(styxHp()==600,"Styx does not drain outside its ten-second buff");
        freshStyx();put(unit,"siphonUntil",now+200);strike(32);
        check(styxHp()==616&&mob.getHealth()==616,"blood buff heals half of actual heart HP lost");
        double before=styxHp();player.setNoDamageTicks(0);player.damage(5);check(styxHp()==before,"other damage sources do not heal Styx");
        styxHp(895);strike(32);check(styxHp()==900&&mob.getHealth()==900,"drain cannot exceed the 900 native/encounter maximum");
        riverTicks(200);before=styxHp();strike(32);check(styxHp()==before,"drain stops at the full ten-second expiration");

        freshStyx();begin("SIPHON");riverTicks(23);check((long)get(unit,"siphonUntil")==0&&get(unit,"pose").equals("blood_windup"),"blood ritual has a visible 24-tick preparation");riverTicks(1);
        check((long)get(unit,"siphonUntil")==now+200,"blood ritual grants exactly ten seconds of drain");
        freshStyx();begin("BERSERK");riverTicks(24);check((long)get(unit,"berserkUntil")==now+100,"berserker ritual grants exactly five seconds");
        put(state,"move",null);put(state,"next",Long.MAX_VALUE);wound(mob,100);
        check(styxHp()==700&&mob.getHealth()==700,"berserker converts accepted 100 damage into 100 HP gain");
        riverTicks(11);wound(mob,100);check(styxHp()==800,"repeated hits continue increasing berserker HP");
        riverTicks(11);wound(mob,10000);check(styxHp()==900&&mob.isValid(),"even lethal incoming damage is converted and capped during berserker");
        styxHp(700);mob.damage(50,player);check(styxHp()==700,"melee remains rejected rather than feeding berserker");
        riverTicks(79);wound(mob,50);check(styxHp()==650,"after five seconds ranged hits damage Styx normally again");

        freshStyx();wound(mob,150);check(styxHp()==450&&!(boolean)get(unit,"enraged"),"exactly half of maximum HP does not trigger enrage");riverTicks(11);wound(mob,1);
        check(styxHp()==449&&(boolean)get(unit,"enraged"),"crossing below 450 HP latches the thirty-percent enrage");
        strike(32);check(Math.abs(player.getHealth()-158.4)<1e-4,"enrage raises normal true-heart damage by exactly thirty percent; native hp="+player.getHealth());
        put(unit,"siphonUntil",now+200);strike(32);check(styxHp()>450&&(boolean)get(unit,"enraged"),"healing above half HP does not stack or erase the triggered enrage");
        freshStyx();begin("EXECUTION");player.teleport(pad.clone().add(5,0,10));riverTicks(45);check(player.getHealth()==200,"sideways dodge escapes the committed fatal chop");
        freshStyx();put(unit,"siphonUntil",now+200);put(unit,"berserkUntil",now+100);invoke(controller,"clear",new Class<?>[]{});
        check((long)get(unit,"siphonUntil")==0&&(long)get(unit,"berserkUntil")==0&&((Map<?,?>)get(trials,"states")).isEmpty(),"encounter reset clears timed buffs and combat states");
        check(ArkhaiaBedrockSupport.riverClips("arkhaia_styx").size()==28,"all twenty-eight combat and buff clip indexes are available");

        freshStyx();styxHp(500);put(unit,"siphonUntil",now+200);resetPlayer(pad.clone().add(0,0,10));
        player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(1000);player.setHealth(1000);player.getAttribute(Attribute.MAX_ABSORPTION).setBaseValue(500);player.setAbsorptionAmount(500);
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,400,4));armor();for(ItemStack item:player.getInventory().getArmorContents())item.addUnsafeEnchantment(Enchantment.PROTECTION,20);
        player.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));begin("EXECUTION");riverTicks(43);
        check(player.getHealth()==1000,"fatal execution retains the entire forty-four-tick warning");riverTicks(1);
        check(player.isDead()&&player.getHealth()==0,"direct execution guarantees death through extreme HP, absorption, resistance, armor and Totem");
        check(styxHp()==516,"execution drains only sixteen HP: half of one ordinary 32-damage hit");
    }
}
