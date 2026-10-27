import com.example.voidscape.boss.ArkhaiaWaves;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.damage.*;
import org.bukkit.entity.*;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.*;
import java.util.*;

/** Real events and native item consumption for the soul-veil encounter. */
public class BansheeProbe extends AcheronSplitProbe {
    boolean reduceSoul,cancelSoul;
    @org.bukkit.event.EventHandler(priority=org.bukkit.event.EventPriority.HIGH)
    public void alteredSoul(EntityDamageEvent event){
        if(event.getEntity()==player&&event.getDamageSource().getDamageType().equals(DamageType.GENERIC_KILL)){
            if(reduceSoul)event.setDamage(event.getDamage()*.2);if(cancelSoul)event.setCancelled(true);
        }
    }
    void ghost()throws Exception{summon(ArkhaiaWaves.Kind.BANSHEE_WAIL);}
    Object state()throws Exception{return get(unit,"soul");}
    Object souls()throws Exception{return get(controller,"souls");}
    List<?> anchors()throws Exception{return (List<?>)get(state(),"anchors");}
    double ghostHealth()throws Exception{return (double)get(unit,"health");}
    boolean soulHit()throws Exception{return (boolean)call(controller,"soulHit",new Class<?>[]{Player.class,Entity.class},player,mob);}
    void curse()throws Exception{put(unit,"mark",mob.getLocation());impact();riverTicks(1);}
    void consume(Material type){org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(new ItemStack(type)).finishUsingItem(actor.level(),actor);}
    @Override void mechanics()throws Exception{
        Bukkit.getPluginManager().registerEvents(this,this);
        world.setDifficulty(Difficulty.NORMAL);controller=minions();invoke(controller,"demo",new Class<?>[]{UUID.class,boolean.class},player.getUniqueId(),false);
        pad=new Location(world,site.x()+6.5,109,site.z()+.5);now=(long)get(controller,"clock");
        for(int x=-20;x<=20;x++)for(int z=-20;z<=25;z++){
            world.getBlockAt(pad.getBlockX()+x,108,pad.getBlockZ()+z).setType(Material.STONE,false);
            for(int y=109;y<=122;y++)world.getBlockAt(pad.getBlockX()+x,y,pad.getBlockZ()+z).setType(Material.AIR,false);
        }
        ghost();check(ghostHealth()==500&&mob.getAttribute(Attribute.MAX_HEALTH).getValue()==500,"Banshee has exact 500 virtual and native HP");
        check(anchors().size()==3,"shrouded Banshee creates three breakable soul anchors");
        resetPlayer(pad.clone().add(.95,0,2.5));face(pad.clone().add(.95,1,.55));mob.damage(20,player);
        check(ghostHealth()==500,"sword cannot hurt shrouded Banshee");wound(mob,20);check(ghostHealth()==500,"arrows cannot hurt shrouded Banshee");
        Object first=anchors().get(0);Mob receiver=(Mob)get(first,"receiver");
        check(receiver.isValid()&&receiver.isInvisible()&&((BlockDisplay)get(first,"crystal")).getBrightness().getBlockLight()==15,"anchor survives world spawn filters with invisible receiver and full-bright crystal");
        resetPlayer(receiver.getLocation().clone().add(0,-.3,2.5));face(receiver.getLocation().clone().add(0,.5,0));receiver.damage(10,player);
        check((double)get(first,"health")==20,"sword damages actual visible anchor receiver");receiver.damage(10,player);
        check((double)get(first,"health")==20,"same-tick extra hit cannot double-charge anchor damage");riverTicks(11);
        Arrow arrow=world.spawnArrow(receiver.getLocation().clone().add(0,.5,3),new org.bukkit.util.Vector(0,0,-1),1.5f,0);arrow.setShooter(player);arrow.setGravity(false);
        for(int i=0;i<5&&arrow.isValid();i++)((org.bukkit.craftbukkit.entity.CraftArrow)arrow).getHandle().tick();arrow.remove();
        check((double)get(first,"health")<20,"real travelling arrow collides with soul-anchor receiver");riverTicks(11);wound(receiver,100);
        check(anchors().size()==2&&!receiver.isValid()&&!((Entity)get(first,"crystal")).isValid(),"destroyed anchor removes receiver, crystal and its share of the shield");
        wound(mob,20);check(ghostHealth()==500,"two remaining anchors still protect Banshee");
        for(Object anchor:new ArrayList<>(anchors()))wound((Mob)get(anchor,"receiver"),100);
        check(anchors().isEmpty()&&(long)get(state(),"exposedUntil")==now+240,"last anchor opens exactly twelve seconds of exposure");
        riverTicks(1);check(get(unit,"pose").equals("reveal"),"broken veil selects dedicated reveal animation");wound(mob,20);
        check(ghostHealth()==480,"revealed true form accepts arrow damage");riverTicks(21);
        resetPlayer(pad.clone().add(.95,0,2.5));face(pad.clone().add(.95,1,.55));mob.damage(10,player);
        check(ghostHealth()<480,"revealed true form accepts sword damage");double health=ghostHealth();
        long until=(long)get(state(),"exposedUntil");riverTicks((int)(until-now-1));check(anchors().isEmpty(),"shield cannot return before full exposure window ends");riverTicks(1);
        check(anchors().size()==3&&get(unit,"pose").equals("reform"),"exposure expiry reforms three anchors with dedicated animation");wound(mob,50);
        check(ghostHealth()==health,"reformation restores protection without healing boss HP");

        ghost();resetPlayer(pad.clone().add(0,0,4));check(soulHit()&&player.getHealth()==195,"unarmored soul strike deals exactly five HP");
        for(Material material:new Material[]{Material.DIAMOND_CHESTPLATE,Material.NETHERITE_CHESTPLATE}){
            resetPlayer(pad.clone().add(0,0,4));
            var armor=new ItemStack[]{new ItemStack(Material.DIAMOND_BOOTS),new ItemStack(Material.DIAMOND_LEGGINGS),new ItemStack(material),new ItemStack(Material.DIAMOND_HELMET)};
            for(var item:armor)item.addEnchantment(org.bukkit.enchantments.Enchantment.PROTECTION,4);player.getInventory().setArmorContents(armor);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE,200,4));
            check(soulHit()&&player.getHealth()==195,"Protection IV, armor and Resistance V cannot reduce five-HP soul strike "+material);
            check(Arrays.stream(player.getInventory().getArmorContents()).allMatch(i->((org.bukkit.inventory.meta.Damageable)i.getItemMeta()).getDamage()==1),"soul contact wears all four armor pieces once");
        }
        resetPlayer(pad.clone().add(0,0,4));player.getAttribute(Attribute.MAX_ABSORPTION).setBaseValue(40);player.setAbsorptionAmount(4);
        check(soulHit()&&Math.abs(player.getHealth()+player.getAbsorptionAmount()-199)<.001,"existing absorption still loses exactly five total HP to soul strike");
        resetPlayer(pad.clone().add(0,0,4));player.setGameMode(GameMode.CREATIVE);check(!soulHit()&&player.getHealth()==200,"soul attack preserves creative immunity");
        resetPlayer(pad.clone().add(0,0,4));face(mob.getEyeLocation());player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD));actor.startUsingItem(net.minecraft.world.InteractionHand.OFF_HAND);
        for(int i=0;i<6;i++)actor.doTick();check(player.isBlocking(),"shield fixture reaches native blocking state");player.setNoDamageTicks(0);
        check(soulHit()&&player.getHealth()==195,"native raised shield cannot block fixed soul damage");actor.stopUsingItem();
        resetPlayer(pad.clone().add(0,0,4));reduceSoul=true;check(soulHit()&&player.getHealth()==195,"virtual armor modifiers cannot reduce fixed five-HP soul attack");reduceSoul=false;
        resetPlayer(pad.clone().add(0,0,4));cancelSoul=true;check(!soulHit()&&player.getHealth()==200,"explicitly cancelled damage still protects player");cancelSoul=false;
        resetPlayer(pad.clone().add(0,0,4));player.setHealth(100);curse();
        check(player.getHealth()==95&&player.hasPotionEffect(PotionEffectType.HUNGER),"curse contact deals five HP and applies Hunger II");
        check(player.getPotionEffect(PotionEffectType.HUNGER).getDuration()==200,"curse hunger lasts exactly ten seconds");
        for(var reason:new EntityRegainHealthEvent.RegainReason[]{EntityRegainHealthEvent.RegainReason.MAGIC,EntityRegainHealthEvent.RegainReason.MAGIC_REGEN,EntityRegainHealthEvent.RegainReason.SATIATED,EntityRegainHealthEvent.RegainReason.EATING,EntityRegainHealthEvent.RegainReason.REGEN,EntityRegainHealthEvent.RegainReason.CUSTOM}){
            actor.heal(10,reason);check(player.getHealth()==95,"native healing blocked for "+reason);
        }
        ItemStack potion=new ItemStack(Material.POTION);PotionMeta meta=(PotionMeta)potion.getItemMeta();meta.setBasePotionType(PotionType.HEALING);potion.setItemMeta(meta);
        org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(potion).finishUsingItem(actor.level(),actor);
        check(player.getHealth()==95,"consuming a real healing potion cannot restore HP while cursed");
        consume(Material.GOLDEN_APPLE);check(player.getAbsorptionAmount()==0&&!player.hasPotionEffect(PotionEffectType.ABSORPTION),"real golden apple cannot add absorption while cursed");
        consume(Material.ENCHANTED_GOLDEN_APPLE);check(player.getAbsorptionAmount()==0,"enchanted golden apple cannot bypass healing seal");
        player.setHealth(120);player.setAbsorptionAmount(6);riverTicks(1);
        check(player.getHealth()==95&&player.getAbsorptionAmount()==0,"direct plugin HP and absorption increases are capped during seal");
        var seals=(Map<?,?>)get(souls(),"seals");Object seal=seals.values().iterator().next();long ends=(long)get(seal,"until");riverTicks((int)(ends-now-1));actor.heal(10,EntityRegainHealthEvent.RegainReason.CUSTOM);
        check(player.getHealth()==95,"heal seal still blocks at its final active tick");riverTicks(1);actor.heal(10,EntityRegainHealthEvent.RegainReason.CUSTOM);
        check(player.getHealth()==105&&seals.isEmpty()&&!player.hasPotionEffect(PotionEffectType.HUNGER),"healing and normal hunger resume exactly at ten seconds");
        consume(Material.GOLDEN_APPLE);check(player.getAbsorptionAmount()>0,"golden-apple absorption works again after seal expires");
        ghost();resetPlayer(pad.clone().add(0,0,4));player.setHealth(100);curse();call(controller,"departed",new Class<?>[]{Player.class},player);
        actor.heal(10,EntityRegainHealthEvent.RegainReason.CUSTOM);check(player.getHealth()==105&&!player.hasPotionEffect(PotionEffectType.HUNGER),"departure clears heal seal and owned Hunger immediately");
        ghost();resetPlayer(pad.clone().add(0,0,4));player.setHealth(100);curse();List<Entity> owned=new ArrayList<>();for(Object a:anchors()){owned.add((Entity)get(a,"receiver"));owned.add((Entity)get(a,"crystal"));owned.add((Entity)get(a,"label"));}
        call(controller,"clear",new Class<?>[]{});actor.heal(10,EntityRegainHealthEvent.RegainReason.CUSTOM);
        check(owned.stream().noneMatch(Entity::isValid)&&player.getHealth()==105&&!player.hasPotionEffect(PotionEffectType.HUNGER),"encounter clear removes anchors and releases all healing restrictions");

        ghost();resetPlayer(pad.clone().add(0,0,4));player.setHealth(4);player.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));player.getInventory().setItemInMainHand(new ItemStack(Material.TOTEM_OF_UNDYING));
        player.getInventory().setArmorContents(new ItemStack[]{new ItemStack(Material.DIAMOND_BOOTS),new ItemStack(Material.DIAMOND_LEGGINGS),new ItemStack(Material.DIAMOND_CHESTPLATE),new ItemStack(Material.DIAMOND_HELMET)});
        world.setGameRule(org.bukkit.GameRules.KEEP_INVENTORY,false);Set<UUID> oldItems=new HashSet<>();for(Item item:world.getEntitiesByClass(Item.class))oldItems.add(item.getUniqueId());
        check(soulHit()&&player.isDead(),"lethal soul strike bypasses Totems in both hands and kills player");
        var drops=world.getEntitiesByClass(Item.class).stream().filter(i->!oldItems.contains(i.getUniqueId())).map(Item::getItemStack).toList();
        check(drops.stream().filter(i->i.getType()==Material.TOTEM_OF_UNDYING).mapToInt(ItemStack::getAmount).sum()==2,"both held Totems remain unconsumed in death drops");
        check(drops.stream().filter(i->i.getType().name().startsWith("DIAMOND_")).count()==4&&drops.stream().filter(i->i.getType().name().startsWith("DIAMOND_")).allMatch(i->((org.bukkit.inventory.meta.Damageable)i.getItemMeta()).getDamage()==1),"lethal soul hit wears armor before native death drops copy equipment");
    }
}
