package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** A disposable, admin-owned Witch anchor with a unique winged custom skin. */
final class SeraphPreview implements AutoCloseable {
    private static final int LIFETIME_TICKS=20*600;
    private record Preview(UUID admin,Witch witch,ItemDisplay java,ArmorStand bedrock,
                           long created,Map<UUID,String> viewers) {}
    private final VoidscapePlugin plugin;
    private final String modelPrefix,displayName;
    private final double sideOffset;
    private final Map<UUID,Preview> active=new HashMap<>();
    private long clock;

    SeraphPreview(VoidscapePlugin plugin){this(plugin,"judge_seraph_","Seraph",0);}
    SeraphPreview(VoidscapePlugin plugin,String modelPrefix,String displayName,double sideOffset){
        this.plugin=plugin;this.modelPrefix=modelPrefix;this.displayName=displayName;this.sideOffset=sideOffset;
    }
    boolean owns(Entity e){return active.values().stream().anyMatch(p->p.witch()==e||p.java()==e||p.bedrock()==e);}
    private boolean admin(Player p){return p!=null&&p.isOnline()&&(p.isOp()||p.hasPermission("evergarden.admin")||p.hasPermission("voidscape.admin"));}
    private void tag(Entity e){
        e.setPersistent(false);e.setVisibleByDefault(false);e.setGravity(false);
        e.getPersistentDataContainer().set(plugin.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);
        e.getPersistentDataContainer().set(plugin.key(modelPrefix.equals("judge_seraph_")?"seraph_preview":modelPrefix+"preview"),PersistentDataType.BYTE,(byte)1);
    }
    boolean spawn(Player p){
        if(!admin(p))return false;
        if(!JudgePackService.bedrock(p)&&!plugin.judgePack().applied(p)){
            plugin.judgePack().offer(p);
            plugin.message(p,"โหลดแพ็ก World Boss ก่อน แล้วกดเสกโมเดลอีกครั้ง · /evergarden boss pack");
            return false;
        }
        var forward=p.getLocation().getDirection().setY(0);
        if(forward.lengthSquared()<1e-6)forward=new org.bukkit.util.Vector(0,0,1);
        forward.normalize();
        Location at=p.getLocation().clone().add(forward.clone().multiply(sideOffset==0?4:6))
            .add(new org.bukkit.util.Vector(-forward.getZ(),0,forward.getX()).multiply(sideOffset));
        at.setPitch(0);
        if(!at.getBlock().isPassable()||!at.clone().add(0,1,0).getBlock().isPassable()){
            plugin.message(p,"ต้องมีพื้นที่ว่างด้านหน้าอย่างน้อย 2 บล็อกเพื่อวางโมเดล");return false;
        }
        at.setDirection(p.getLocation().toVector().subtract(at.toVector()).setY(0));
        remove(p);
        Witch witch=null;ItemDisplay java=null;ArmorStand bedrock=null;
        try{
            witch=at.getWorld().spawn(at,Witch.class,e->{
                tag(e);e.setAI(false);e.setSilent(true);e.setCollidable(false);e.setInvulnerable(true);
                e.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,LIFETIME_TICKS+200,0,true,false));
            });
            java=at.getWorld().spawn(at,ItemDisplay.class,e->{
                tag(e);e.setItemStack(JudgmentBody.model(modelPrefix+"idle"));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
                e.setInterpolationDuration(4);e.setTeleportDuration(4);e.setViewRange(3);
                e.setDisplayWidth(7);e.setDisplayHeight(7);e.setShadowRadius(0);
                e.setBrightness(new Display.Brightness(14,15));
                e.setTransformation(new Transformation(new Vector3f(0,2,0),new Quaternionf(),new Vector3f(1.6f),new Quaternionf()));
            });
            bedrock=at.getWorld().spawn(at.clone().add(0,.5,0),ArmorStand.class,e->{
                tag(e);e.setVisible(false);e.setMarker(false);e.setSilent(true);e.setBasePlate(false);e.setArms(false);
                e.setCollidable(false);e.setInvulnerable(true);
                e.getEquipment().setHelmet(JudgmentBody.model(modelPrefix+"idle"));
                for(EquipmentSlot slot:List.of(EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,
                    EquipmentSlot.FEET,EquipmentSlot.HAND,EquipmentSlot.OFF_HAND))
                    e.addEquipmentLock(slot,ArmorStand.LockType.ADDING_OR_CHANGING);
            });
            Preview preview=new Preview(p.getUniqueId(),witch,java,bedrock,clock,new HashMap<>());
            active.put(p.getUniqueId(),preview);
            update(preview);
            p.getWorld().playSound(at,Sound.BLOCK_AMETHYST_BLOCK_CHIME,1,1.2f);
            plugin.message(p,"เสกโมเดล "+displayName+" แล้ว · สลับท่ายืน/ร่ายเวท · อยู่ได้ 10 นาที · ลบได้ใน World Boss Test");
            return true;
        }catch(Throwable error){
            if(witch!=null)witch.remove();if(java!=null)java.remove();if(bedrock!=null)bedrock.remove();
            plugin.getLogger().warning("Cannot spawn "+displayName+" preview: "+error);
            plugin.message(p,"เสกโมเดลไม่สำเร็จ · ตรวจ server log");return false;
        }
    }
    boolean remove(Player p){
        if(!admin(p))return false;
        Preview preview=active.remove(p.getUniqueId());
        if(preview==null)return false;
        destroy(preview);return true;
    }
    private void destroy(Preview preview){
        preview.java().remove();preview.bedrock().remove();preview.witch().remove();
        preview.viewers().clear();
    }
    void quit(Player p){Preview preview=active.remove(p.getUniqueId());if(preview!=null)destroy(preview);}
    void tick(){
        clock+=2;
        for(Preview preview:new ArrayList<>(active.values())){
            if(clock-preview.created()>=LIFETIME_TICKS||!preview.witch().isValid()||!preview.java().isValid()||!preview.bedrock().isValid()){
                active.remove(preview.admin());destroy(preview);continue;
            }
            update(preview);
        }
    }
    private void update(Preview preview){
        Location anchor=preview.witch().getLocation();
        Player owner=Bukkit.getPlayer(preview.admin());
        if(owner!=null&&owner.getWorld()==anchor.getWorld()&&owner.getLocation().distanceSquared(anchor)<18*18){
            Location target=anchor.clone().setDirection(owner.getLocation().toVector().subtract(anchor.toVector()).setY(0));
            anchor.setYaw(target.getYaw());preview.witch().setRotation(anchor.getYaw(),0);
        }
        long age=clock-preview.created();
        boolean casting=age%100>=35&&age%100<68;
        String pose=modelPrefix+(casting?"cast":"idle");
        if(!pose.equals(preview.java().getItemStack().getItemMeta().getItemModel().getKey())){
            preview.java().setItemStack(JudgmentBody.model(pose));
            preview.bedrock().getEquipment().setHelmet(JudgmentBody.model(pose));
        }
        double bob=Math.sin(age*.045)*.12;
        Location javaAt=anchor.clone().add(0,bob,0);
        preview.java().teleport(javaAt);
        preview.java().setRotation(anchor.getYaw(),0);
        Location bedrockAt=anchor.clone().add(0,.5+bob,0);
        preview.bedrock().teleport(bedrockAt);
        preview.bedrock().setRotation(anchor.getYaw(),0);
        if(casting&&age%8==0){
            Location spell=anchor.clone().add(0,2.3+bob,0).add(anchor.getDirection().multiply(.9));
            anchor.getWorld().spawnParticle(Particle.END_ROD,spell,5,.35,.45,.35,.01);
        }
        Set<UUID> present=new HashSet<>();
        for(Player viewer:anchor.getWorld().getPlayers()){
            present.add(viewer.getUniqueId());
            boolean bedrock=JudgePackService.bedrock(viewer),accepted=plugin.judgePack().applied(viewer);
            String kind=bedrock?"bedrock":accepted?"java":"none";
            if(kind.equals(preview.viewers().put(viewer.getUniqueId(),kind)))continue;
            if(accepted&&!bedrock)viewer.showEntity(plugin,preview.java());else viewer.hideEntity(plugin,preview.java());
            if(bedrock)viewer.showEntity(plugin,preview.bedrock());else viewer.hideEntity(plugin,preview.bedrock());
        }
        preview.viewers().keySet().retainAll(present);
    }
    @Override public void close(){for(Preview p:active.values())destroy(p);active.clear();}
}
