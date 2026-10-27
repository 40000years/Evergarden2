package com.example.advancemagic.item;

import com.example.advancemagic.AdvanceMagicPlugin;
import static com.example.advancemagic.item.ArmorSetService.ArmorSet.*;
import com.example.advancemagic.item.ArmorSetService.Piece;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Set powers share native combat, durability, enchants and an explicit virtual-boss hit receipt. */
public final class ArmorCombatService implements Listener,AutoCloseable {
    public static final long REVIVE_COOLDOWN=600_000L;
    private static final List<String> BOSSES=List.of("acheron","phlegethon","cocytus","banshee","malefeasance","styx");
    private static final Set<String> BOSS_MARKERS=Set.of("world_boss_entity","sanctuary_boss","arkhaia_part",
        "arkhaia_minion","arkhaia_boss","seraphon_boss","boss_entity","boss","is_boss");
    private final AdvanceMagicPlugin plugin;
    private final ArmorSetService sets;
    private final NamespacedKey revives,souls,health;
    private record Credit(Player player,double damage){}
    private static final class Pending {
        final LivingEntity target;final double before;final List<Credit> credits=new ArrayList<>();
        Pending(LivingEntity target){this.target=target;before=target.getHealth();}
    }
    private final Map<UUID,Pending> pending=new HashMap<>();
    public ArmorCombatService(AdvanceMagicPlugin plugin,ArmorSetService sets){
        this.plugin=plugin;this.sets=sets;revives=new NamespacedKey(plugin,"revenant_revives");
        souls=new NamespacedKey(plugin,"revenant_sanctuary_souls");health=new NamespacedKey(plugin,"revenant_max_health");
    }
    public boolean fullRevenant(Player player){return sets.full(player,REVENANT);}
    public boolean fullSeraph(Player player){return sets.full(player,SERAPH);}
    /** Seraph deters ordinary undead; encounter bosses retain their combat targets. */
    public boolean repels(Player player,LivingEntity mob){return undead(mob)&&!encounterBoss(mob)&&fullSeraph(player);}
    private static boolean encounterBoss(LivingEntity entity){
        if(entity instanceof Boss)return true;
        for(NamespacedKey key:entity.getPersistentDataContainer().getKeys())
            if(BOSS_MARKERS.contains(key.getKey()))return true;
        for(String tag:entity.getScoreboardTags()){
            String marker=tag.toLowerCase(Locale.ROOT);
            if(BOSS_MARKERS.contains(marker)||marker.equals("custom-boss")||marker.endsWith(":boss")||marker.endsWith("_boss"))return true;
        }
        return false;
    }
    public static boolean undead(LivingEntity entity){return Tag.ENTITY_TYPES_UNDEAD.isTagged(entity.getType());}
    public int souls(Player player){return Integer.bitCount(player.getPersistentDataContainer().getOrDefault(souls,PersistentDataType.INTEGER,0)&63);}

    // Highest runs after ordinary weapon/enchant bonuses and before Evergarden's virtual HP handlers.
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void outgoing(EntityDamageEvent event){
        if(!(event.getEntity() instanceof LivingEntity target))return;
        Player player=attacker(event);
        if(player!=null&&player!=target&&fullSeraph(player)&&Double.isFinite(event.getDamage())&&event.getDamage()>0)
            event.setDamage(event.getDamage()*(undead(target)?4:2));
    }
    private Player attacker(EntityDamageEvent event){
        if(event.getDamageSource().getCausingEntity() instanceof Player player)return player;
        if(event instanceof EntityDamageByEntityEvent by){
            if(by.getDamager() instanceof Player player)return player;
            if(by.getDamager() instanceof Projectile projectile&&projectile.getShooter() instanceof Player player)return player;
        }
        return null;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void dealt(EntityDamageEvent event){
        if(!(event.getEntity() instanceof LivingEntity target)||target instanceof ArmorStand)return;
        Player player=attacker(event);double damage=event.getFinalDamage();
        if(player==null||player==target||!fullRevenant(player)||!Double.isFinite(damage)||damage<=0)return;
        Pending batch=pending.get(target.getUniqueId());
        if(batch==null){batch=new Pending(target);pending.put(target.getUniqueId(),batch);Pending receipt=batch;
            Bukkit.getScheduler().runTask(plugin,()->settle(receipt));}
        batch.credits.add(new Credit(player,Math.min(target.getHealth(),damage)));
    }
    private void settle(Pending batch){
        if(!pending.remove(batch.target.getUniqueId(),batch))return;
        double claimed=batch.credits.stream().mapToDouble(Credit::damage).sum();
        double lost=Math.max(0,batch.before-batch.target.getHealth());
        if(claimed<=0||lost<=0)return;
        double fraction=Math.min(1,lost/claimed);
        for(Credit credit:batch.credits)heal(credit.player,credit.damage*fraction*.5);
    }
    /** Call only after an encounter has accepted damage and reduced its virtual HP. */
    public void confirmedHit(Player player,double dealt){
        if(fullRevenant(player)&&Double.isFinite(dealt)&&dealt>0)heal(player,dealt*.5);
    }
    private void heal(Player player,double amount){
        if(!player.isOnline()||player.isDead()||amount<=0)return;
        double max=player.getAttribute(Attribute.MAX_HEALTH).getValue();
        amount=Math.min(amount,max-player.getHealth());if(amount<=0)return;
        var event=new EntityRegainHealthEvent(player,amount,EntityRegainHealthEvent.RegainReason.CUSTOM);
        Bukkit.getPluginManager().callEvent(event);
        if(!event.isCancelled()&&Double.isFinite(event.getAmount())&&event.getAmount()>0)
            player.setHealth(Math.min(player.getAttribute(Attribute.MAX_HEALTH).getValue(),player.getHealth()+event.getAmount()));
    }
    // Registered after all plugins finish enabling, so native/overflow protection has settled.
    public void fatal(EntityDamageEvent event){
        if(event.isCancelled())return;
        if(!(event.getEntity() instanceof Player player)||event.getFinalDamage()<player.getHealth())return;
        if(player.isDead()||player.getGameMode()!=GameMode.SURVIVAL&&player.getGameMode()!=GameMode.ADVENTURE||!Double.isFinite(event.getFinalDamage()))return;
        long[] ready=player.getPersistentDataContainer().get(revives,PersistentDataType.LONG_ARRAY);
        if(ready==null||ready.length!=4)ready=new long[4];
        long now=System.currentTimeMillis();
        for(Piece piece:Piece.values())if(sets.worn(player,REVENANT,piece)&&ready[piece.ordinal()]<=now){
            ready[piece.ordinal()]=now+REVIVE_COOLDOWN;
            player.getPersistentDataContainer().set(revives,PersistentDataType.LONG_ARRAY,ready);
            event.setCancelled(true);player.setHealth(1);player.setFireTicks(0);player.setNoDamageTicks(20);
            if(event.getCause()==EntityDamageEvent.DamageCause.VOID){
                Location safe=player.getRespawnLocation();if(safe==null)safe=player.getWorld().getSpawnLocation();
                player.teleport(safe);player.setFallDistance(0);player.setVelocity(new org.bukkit.util.Vector());
            }
            player.saveData();
            player.getWorld().spawnParticle(Particle.SOUL,player.getLocation().add(0,1,0),32,.45,.7,.45,.04);
            player.playSound(player.getLocation(),Sound.ITEM_TOTEM_USE,.8f,.65f);
            player.sendActionBar(Component.text("Revenant — Death denied",NamedTextColor.RED));
            return;
        }
    }
    public void registerSurvival(){
        Bukkit.getPluginManager().registerEvent(EntityDamageEvent.class,new Listener(){},EventPriority.HIGHEST,
            (listener,event)->fatal((EntityDamageEvent)event),plugin,true);
    }
    /** Sanctuary controller only: six distinct victories, never regular undead or river echoes. */
    public boolean sanctuaryVictory(Player player,String boss){
        int index=BOSSES.indexOf(boss);if(index<0||!fullRevenant(player))return false;
        int mask=player.getPersistentDataContainer().getOrDefault(souls,PersistentDataType.INTEGER,0)&63;
        if((mask&(1<<index))!=0)return false;
        player.getPersistentDataContainer().set(souls,PersistentDataType.INTEGER,mask|(1<<index));
        refresh(player);player.saveData();
        player.sendActionBar(Component.text("Revenant — Sanctuary soul "+souls(player)+"/6 · +2 Max HP",NamedTextColor.RED));
        return true;
    }
    public void refresh(Player player){
        var attribute=player.getAttribute(Attribute.MAX_HEALTH);if(attribute==null)return;
        double amount=fullRevenant(player)?souls(player)*2:0;
        var old=attribute.getModifier(health);
        if(old!=null&&Math.abs(old.getAmount()-amount)<1e-9)return;
        if(old!=null)attribute.removeModifier(old);
        if(amount>0)attribute.addTransientModifier(new AttributeModifier(health,amount,AttributeModifier.Operation.ADD_NUMBER));
        if(!player.isDead()&&player.getHealth()>attribute.getValue())player.setHealth(attribute.getValue());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void target(EntityTargetLivingEntityEvent event){
        if(event.getTarget() instanceof Player player&&event.getEntity() instanceof LivingEntity mob&&repels(player,mob))event.setCancelled(true);
    }
    public void tick(){
        for(Player player:Bukkit.getOnlinePlayers()){
            sets.migrate(player.getInventory());refresh(player);
            if(fullSeraph(player))for(Entity entity:player.getNearbyEntities(48,24,48))
                if(entity instanceof Mob mob&&mob.getTarget()==player&&repels(player,mob)){
                    mob.setTarget(null);mob.getPathfinder().stopPathfinding();
                }
        }
    }
    private void inventoryChanged(Player player){Bukkit.getScheduler().runTask(plugin,()->{if(player.isOnline()){sets.migrate(player.getInventory());refresh(player);}});}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void click(InventoryClickEvent event){if(event.getWhoClicked() instanceof Player player)inventoryChanged(player);}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void drag(InventoryDragEvent event){if(event.getWhoClicked() instanceof Player player)inventoryChanged(player);}
    @EventHandler public void join(PlayerJoinEvent event){sets.migrate(event.getPlayer().getInventory());sets.migrate(event.getPlayer().getEnderChest());inventoryChanged(event.getPlayer());}
    @EventHandler public void open(InventoryOpenEvent event){sets.migrate(event.getInventory());}
    @EventHandler public void breakItem(PlayerItemBreakEvent event){inventoryChanged(event.getPlayer());}
    @EventHandler public void respawn(PlayerRespawnEvent event){inventoryChanged(event.getPlayer());}
    @EventHandler public void quit(PlayerQuitEvent event){clear(event.getPlayer());}
    private void clear(Player player){
        var attribute=player.getAttribute(Attribute.MAX_HEALTH);if(attribute==null)return;attribute.removeModifier(health);
        if(!player.isDead()&&player.getHealth()>attribute.getValue())player.setHealth(attribute.getValue());
    }
    @Override public void close(){pending.clear();Bukkit.getOnlinePlayers().forEach(this::clear);}
}
