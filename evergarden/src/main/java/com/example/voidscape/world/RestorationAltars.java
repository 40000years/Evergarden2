package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import java.util.*;

/** Inventory-owned rituals: no dropped rewards, escrow entities or crash-time refunds. */
public final class RestorationAltars implements Listener {
    private record Selection(RestorationLayout.Site site,int fuelSlot,ItemStack fuel,long expires) {}
    private record Ritual(Selection selection,int wandSlot,ItemStack wand,long started) {}
    private final VoidscapePlugin plugin;
    private final AdvanceMagicPlugin magic;
    private final RestorationVisuals visuals;
    private final Map<UUID,Selection> selections=new HashMap<>();
    private final Map<UUID,Ritual> rituals=new HashMap<>();
    private final Map<UUID,Long> hints=new HashMap<>();
    private long ticks;
    public RestorationAltars(VoidscapePlugin plugin,AdvanceMagicPlugin magic){
        this.plugin=plugin;this.magic=magic;
        visuals=new RestorationVisuals(plugin);Bukkit.getPluginManager().registerEvents(visuals,plugin);
        Bukkit.getScheduler().runTaskTimer(plugin,this::tick,5,5);
    }
    private Location center(RestorationLayout.Site s){return new Location(plugin.world(),s.x()+.5,s.y()+1.5,s.z()+.5);}
    private boolean intact(RestorationLayout.Site s){
        World w=plugin.world();if(!w.isChunkLoaded(Math.floorDiv(s.x(),16),Math.floorDiv(s.z(),16)))return false;
        return w.getBlockAt(s.x(),s.y()+1,s.z()).getType()==RestorationShrine.marker(s)
                &&w.getBlockAt(s.x(),s.y(),s.z()).getType()==(s.altar()?Material.CHISELED_QUARTZ_BLOCK:Material.SEA_LANTERN);
    }
    private RestorationLayout.Site marker(Block b){
        if(b==null||b.getWorld()!=plugin.world())return null;
        var s=plugin.restorationLayout().near(b.getX(),b.getY(),b.getZ(),0);
        return s!=null&&intact(s)?s:null;
    }
    private boolean validPlayer(Player p,RestorationLayout.Site s){return p!=null&&p.isOnline()&&!p.isDead()
            &&p.getGameMode()!=GameMode.SPECTATOR&&p.getWorld()==plugin.world()
            &&p.getLocation().distanceSquared(center(s))<=64&&magic.isEnabled()&&intact(s);}
    private void message(Player p,String s){p.sendMessage(ChatColor.AQUA+"✦ "+s);}
    @EventHandler(priority=EventPriority.LOW)
    public void interact(PlayerInteractEvent e){
        if(e.getHand()!=EquipmentSlot.HAND||e.getAction()==Action.PHYSICAL||e.useInteractedBlock()==Event.Result.DENY)return;
        var site=marker(e.getClickedBlock());if(site==null)return;
        e.setCancelled(true);use(e.getPlayer(),site);
    }
    @EventHandler(priority=EventPriority.LOW,ignoreCancelled=true)
    public void entity(PlayerInteractEntityEvent e){
        if(e.getHand()!=EquipmentSlot.HAND)return;
        var site=visuals.site(e.getRightClicked());if(site==null)return;
        e.setCancelled(true);use(e.getPlayer(),site);
    }
    @EventHandler(priority=EventPriority.LOW,ignoreCancelled=true)
    public void entityAt(PlayerInteractAtEntityEvent e){entity(e);}
    @EventHandler(priority=EventPriority.LOW)
    public void attack(EntityDamageByEntityEvent e){
        if(!(e.getDamager() instanceof Player p))return;
        var site=visuals.site(e.getEntity());if(site==null)return;
        e.setCancelled(true);if(e.getCause()==EntityDamageEvent.DamageCause.ENTITY_ATTACK)use(p,site);
    }
    private void use(Player p,RestorationLayout.Site site){
        if(p.isSneaking()&&p.getInventory().getItemInMainHand().getType().isAir()&&selections.containsKey(p.getUniqueId())){cancel(p.getUniqueId());return;}
        if(!site.altar()){hint(p,site);return;}
        if(!validPlayer(p,site)||!magic.casts().canCast(p))return;
        if(rituals.containsKey(p.getUniqueId()))return;
        ItemStack held=p.getInventory().getItemInMainHand();
        if(magic.restoration().isFuel(held)){
            clear(p.getUniqueId());
            selections.put(p.getUniqueId(),new Selection(site,p.getInventory().getHeldItemSlot(),held.clone(),ticks+600));
            magic.casts().repairing(p,true);
            message(p,"เลือก Core แล้ว — ถือคทาที่ต้องการซ่อม แล้วแตะแท่นอีกครั้ง หรือโยนคทาใกล้แท่น ภายใน 30 วินาที");
        }else if(selections.containsKey(p.getUniqueId())&&selections.get(p.getUniqueId()).site().equals(site))start(p);
        else message(p,"ถือ Core of Restoration หรือ Core เวทที่ยังไม่คราฟ แล้วคลิก / แตะแท่นก่อน");
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void drop(PlayerDropItemEvent e){
        Selection selected=selections.get(e.getPlayer().getUniqueId());
        if(selected==null||!magic.wands().isDurable(e.getItemDrop().getItemStack()))return;
        // Cancel the real drop before any world item can be collected or cleared.
        e.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin,()->{if(e.getPlayer().isOnline())start(e.getPlayer());});
    }
    private void start(Player p){
        UUID id=p.getUniqueId();Selection s=selections.get(id);
        if(s==null||rituals.containsKey(id))return;
        if(!validPlayer(p,s.site())||ticks>s.expires()||!s.fuel().equals(p.getInventory().getItem(s.fuelSlot()))){cancel(id);return;}
        if(rituals.values().stream().anyMatch(r->r.selection().site().equals(s.site()))){message(p,"แท่นกำลังทำพิธีให้ผู้เล่นอีกคน รอสักครู่");return;}
        int slot=p.getInventory().getHeldItemSlot();ItemStack wand=p.getInventory().getItem(slot);
        if(slot==s.fuelSlot()||wand==null||!magic.wands().restore(wand.clone())){
            message(p,"ถือคทาเวทหรือไม้เท้าบินที่ความทนทานยังไม่เต็ม — คทาฟื้นฟู 5 ครั้งซ่อมไม่ได้");return;
        }
        rituals.put(id,new Ritual(s,slot,wand.clone(),ticks));
        message(p,"เริ่มพิธี 10 วินาที อยู่ใกล้แท่นและเก็บ Core / คทาไว้ในช่องเดิม");
        p.getWorld().playSound(center(s.site()),Sound.BLOCK_BEACON_ACTIVATE,.8f,1.4f);
    }
    private void tick(){
        ticks+=5;
        for(UUID id:List.copyOf(selections.keySet())){
            Selection s=selections.get(id);Player p=Bukkit.getPlayer(id);Ritual r=rituals.get(id);
            if(!validPlayer(p,s.site())||(r==null&&ticks>s.expires())||!s.fuel().equals(p.getInventory().getItem(s.fuelSlot()))){cancel(id);continue;}
            if(r==null)continue;
            if(!r.wand().equals(p.getInventory().getItem(r.wandSlot()))){cancel(id);continue;}
            Location c=center(s.site());double phase=(ticks-r.started())*.09;
            for(int i=0;i<12;i++){
                double a=phase+i*Math.PI/6;
                p.getWorld().spawnParticle(Particle.END_ROD,c.clone().add(Math.cos(a)*2,1+Math.sin(phase*.4),Math.sin(a)*2),1,0,0,0,0);
            }
            if((ticks-r.started())%20==0){
                p.sendActionBar(net.kyori.adventure.text.Component.text("ฟื้นฟูคทา "+Math.min(100,(ticks-r.started())/2)+"%"));
                p.getWorld().playSound(c,Sound.BLOCK_AMETHYST_BLOCK_CHIME,.45f,.8f+(ticks-r.started())/200f);
            }
            if(ticks-r.started()>=200)complete(p,r);
        }
    }
    private void complete(Player p,Ritual r){
        Selection s=r.selection();
        // Validate exact stacks, then commit both changes to this player's inventory once.
        ItemStack[] contents=Arrays.stream(p.getInventory().getStorageContents()).map(i->i==null?null:i.clone()).toArray(ItemStack[]::new);
        if(!s.fuel().equals(contents[s.fuelSlot()])||!r.wand().equals(contents[r.wandSlot()])
                ||!magic.restoration().isFuel(contents[s.fuelSlot()])||!magic.wands().restore(contents[r.wandSlot()])){cancel(p.getUniqueId());return;}
        if(contents[s.fuelSlot()].getAmount()==1)contents[s.fuelSlot()]=null;
        else contents[s.fuelSlot()].setAmount(contents[s.fuelSlot()].getAmount()-1);
        p.getInventory().setStorageContents(contents);p.saveData();clear(p.getUniqueId());
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,center(s.site()),60,.7,1,.7,.15);
        p.getWorld().playSound(center(s.site()),Sound.BLOCK_BEACON_POWER_SELECT,1,1.5f);
        message(p,"ฟื้นความทนทานคทาจนเต็มแล้ว ใช้ Core 1 ชิ้น");
    }
    private void hint(Player p,RestorationLayout.Site site){
        if(ticks-hints.getOrDefault(p.getUniqueId(),-200L)<100)return;hints.put(p.getUniqueId(),ticks);
        var next=plugin.restorationLayout().nearestAltar(site.x(),site.z(),8);
        if(next==null){message(p,"ห้องพิธีนี้ไม่มีแท่นฟื้นฟู ลองสำรวจ structure แห่งถัดไป");return;}
        var border=plugin.world().getWorldBorder();
        if(!border.isInside(center(next))){message(p,"ห้องพิธีนี้ไม่มีแท่น ลองสำรวจ structure อื่นภายในขอบโลก");return;}
        message(p,"ร่องรอยแท่นฟื้นฟู "+next.theme()+" → X "+next.x()+" Y "+(next.y()+1)+" Z "+next.z()+" (อาจยังไม่ได้สำรวจ)");
    }
    private void cancel(UUID id){Player p=Bukkit.getPlayer(id);clear(id);if(p!=null)message(p,"พิธีหยุดแล้ว — ยังไม่ได้ใช้ Core หรือเปลี่ยนคทา");}
    private void clear(UUID id){selections.remove(id);rituals.remove(id);Player p=Bukkit.getPlayer(id);if(p!=null)magic.casts().repairing(p,false);}
    @EventHandler public void quit(PlayerQuitEvent e){clear(e.getPlayer().getUniqueId());hints.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void death(PlayerDeathEvent e){clear(e.getEntity().getUniqueId());}
    @EventHandler public void world(PlayerChangedWorldEvent e){if(selections.containsKey(e.getPlayer().getUniqueId()))cancel(e.getPlayer().getUniqueId());}
    // Only the generated pedestal and its foundation are protected, not player workstations.
    private boolean protectedBlock(Block b){
        if(b.getWorld()!=plugin.world())return false;
        var s=plugin.restorationLayout().near(b.getX(),b.getY(),b.getZ(),1);
        return s!=null&&s.x()==b.getX()&&s.z()==b.getZ()&&b.getY()>=s.y()&&b.getY()<=s.y()+1&&intact(s);
    }
    @EventHandler(ignoreCancelled=true) public void breakBlock(BlockBreakEvent e){if(protectedBlock(e.getBlock()))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void explosion(EntityExplodeEvent e){e.blockList().removeIf(this::protectedBlock);}
    @EventHandler(ignoreCancelled=true) public void explosion(BlockExplodeEvent e){e.blockList().removeIf(this::protectedBlock);}
    @EventHandler(ignoreCancelled=true) public void piston(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(this::protectedBlock))e.setCancelled(true);}
    @EventHandler(ignoreCancelled=true) public void piston(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(this::protectedBlock))e.setCancelled(true);}
    public void close(){visuals.close();for(UUID id:List.copyOf(selections.keySet()))clear(id);hints.clear();}
}
