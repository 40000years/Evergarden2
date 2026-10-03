package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** One authored sculpture, with Java models, Bedrock attachables and solid vanilla fallback. */
final class JudgmentBody implements AutoCloseable {
    private record Piece(String name,JudgmentFight.Part part,int shard,double centerY,Vector offset,
                         ItemDisplay java,ArmorStand bedrock,List<BlockDisplay> fallback) {}
    private final VoidscapePlugin plugin;
    private final List<Piece> pieces=new ArrayList<>();
    private final Set<UUID> entities=new HashSet<>();
    private final List<FallingBlock> sanctuaries=new ArrayList<>();
    private final Map<UUID,String> visibility=new HashMap<>();
    private final Location center;
    private final YamlConfiguration shapes;
    private boolean fallbackReady;
    private int phase=1;
    private boolean leftAlive=true,rightAlive=true,exposed;
    private long clock;
    private final Map<JudgmentFight.Part,Long> flashes=new EnumMap<>(JudgmentFight.Part.class);
    private double charge;
    private Location lastLeft,lastRight;
    private final JudgeAether aether;
    private final JudgeSigils sigils;
    private record Impact(Location at,double height,double radius,boolean castingCircle) {}
    private record Lance(Location from,Location to) {}
    private final List<Impact> impacts=new ArrayList<>();
    private final Map<Integer,Lance> lances=new HashMap<>();
    private long impactUntil;
    JudgmentBody(VoidscapePlugin plugin,Location center){
        this.plugin=plugin;this.center=center.clone();
        this.aether=new JudgeAether(plugin);
        this.sigils=new JudgeSigils(plugin,aether);
        try(InputStream source=plugin.getResource("judge-shape.yml")){
            if(source==null)throw new IOException("Judge geometry missing");
            shapes=YamlConfiguration.loadConfiguration(new InputStreamReader(source,StandardCharsets.UTF_8));
        }catch(IOException error){throw new IllegalStateException(error);}
        try{
            for(String name:shapes.getKeys(false)){
                JudgmentFight.Part part=name.startsWith("judge_left")?JudgmentFight.Part.LEFT:
                    name.startsWith("judge_right")?JudgmentFight.Part.RIGHT:JudgmentFight.Part.CORE;
                if(name.equals("judge_shard"))for(int i=0;i<12;i++)add(name,part,i);
                else add(name,part,-1);
            }
            update(new JudgmentFight(1,1,.65,240,16),0,null,null);
        }catch(Throwable error){close();throw error;}
    }
    static ItemStack model(String name){
        ItemStack item=new ItemStack(Material.IRON_HELMET);var meta=item.getItemMeta();
        meta.setItemModel(new NamespacedKey("voidscape",name));item.setItemMeta(meta);return item;
    }
    private void tag(Entity e){
        e.setPersistent(false);e.setGravity(false);e.setInvulnerable(true);e.setVisibleByDefault(false);
        e.getPersistentDataContainer().set(plugin.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);
        entities.add(e.getUniqueId());
    }
    private void add(String name,JudgmentFight.Part part,int shard){
        float scale=(float)shapes.getDouble(name+".scale");double cy=shapes.getDoubleList(name+".center").get(1);
        float width=(float)shapes.getDouble(name+".bounds.java-width",36),height=(float)shapes.getDouble(name+".bounds.java-height",36);
        ItemDisplay display=center.getWorld().spawn(center,ItemDisplay.class,e->{
            tag(e);e.setItemStack(model(name));e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            e.setRotation(0,0);e.setInterpolationDuration(4);e.setTeleportDuration(name.equals("judge_heart")?0:4);e.setViewRange(3);
            e.setDisplayWidth(width);e.setDisplayHeight(height);e.setShadowRadius(0);
            e.setBrightness(new Display.Brightness(name.endsWith("_light")||name.equals("judge_heart")?15:11,15));
            // Display culling starts at entity Y. Keep its root at the model's lower anchor,
            // rather than centering the entity above the palm/face and clipping its lower half.
            e.setTransformation(new Transformation(new Vector3f(0,(float)cy,0),new Quaternionf(),new Vector3f(scale),new Quaternionf()));
        });
        try{
            ArmorStand carrier=center.getWorld().spawn(center,ArmorStand.class,e->{
                tag(e);e.setVisible(false);e.setMarker(false);e.setSilent(true);e.setBasePlate(false);e.setArms(false);e.setCollidable(false);
                e.getEquipment().setHelmet(model(name));e.setRotation(0,0);
                for(org.bukkit.inventory.EquipmentSlot slot:List.of(org.bukkit.inventory.EquipmentSlot.HEAD,org.bukkit.inventory.EquipmentSlot.CHEST,
                    org.bukkit.inventory.EquipmentSlot.LEGS,org.bukkit.inventory.EquipmentSlot.FEET,org.bukkit.inventory.EquipmentSlot.HAND,org.bukkit.inventory.EquipmentSlot.OFF_HAND))
                    e.addEquipmentLock(slot,ArmorStand.LockType.ADDING_OR_CHANGING);
            });
            List<Double> origin=shapes.getDoubleList(name+".offset");
            Vector offset=origin.size()==3?new Vector(origin.get(0),origin.get(1),origin.get(2)):new Vector();
            pieces.add(new Piece(name,part,shard,cy,offset,display,carrier,new ArrayList<>()));
        }catch(Throwable error){display.remove();throw error;}
    }
    private void buildFallback(){
        for(Piece piece:pieces){
            float width=(float)shapes.getDouble(piece.name+".bounds.java-width",36),height=(float)shapes.getDouble(piece.name+".bounds.java-height",36);
            for(Map<?,?> cube:shapes.getMapList(piece.name+".cubes")){
                if(Boolean.TRUE.equals(cube.get("detail")))continue;
                List<?> from=(List<?>)cube.get("from"),size=(List<?>)cube.get("size");
                int tile=((Number)cube.get("tile")).intValue();
                Material material=Material.valueOf(new String[]{"SMOOTH_QUARTZ","POLISHED_TUFF","RAW_GOLD_BLOCK","WAXED_COPPER_BLOCK",
                    "CRYING_OBSIDIAN","OCHRE_FROGLIGHT","REDSTONE_BLOCK","CHISELED_QUARTZ_BLOCK"}[tile]);
                BlockDisplay block=center.getWorld().spawn(anchor(piece,clock,lastLeft,lastRight),BlockDisplay.class,e->{
                    tag(e);e.setBlock(material.createBlockData());e.setRotation(0,0);e.setTeleportDuration(4);e.setViewRange(3);
                    e.setDisplayWidth(width);e.setDisplayHeight(height);e.setShadowRadius(0);
                    e.setBrightness(new Display.Brightness(tile==5||tile==6?15:11,15));
                    e.setTransformation(new Transformation(new Vector3f(number(from,0),number(from,1),number(from,2)),new Quaternionf(),
                        new Vector3f(number(size,0),number(size,1),number(size,2)),new Quaternionf()));
                });
                piece.fallback.add(block);
            }
        }
        fallbackReady=true;visibility.clear();
    }
    private static float number(List<?> list,int index){return ((Number)list.get(index)).floatValue();}
    private void viewers(){
        boolean needsFallback=center.getWorld().getPlayers().stream().anyMatch(p->!JudgePackService.bedrock(p)&&!plugin.judgePack().applied(p));
        if(needsFallback&&!fallbackReady)buildFallback();
        Set<UUID> present=new HashSet<>();
        for(Player p:center.getWorld().getPlayers()){
            present.add(p.getUniqueId());boolean bedrock=JudgePackService.bedrock(p),custom=plugin.judgePack().applied(p);
            String key=(bedrock?"B":custom?"J":"V")+phase+":"+leftAlive+":"+rightAlive+":"+exposed;
            if(key.equals(visibility.put(p.getUniqueId(),key)))continue;
            for(Piece piece:pieces){
                boolean active=active(piece);
                visible(p,piece.java,active&&!bedrock&&custom);
                visible(p,piece.bedrock,active&&bedrock);
                for(BlockDisplay e:piece.fallback)visible(p,e,active&&!bedrock&&!custom);
            }
        }
        visibility.keySet().retainAll(present);
    }
    private void visible(Player p,Entity e,boolean show){if(show)p.showEntity(plugin,e);else p.hideEntity(plugin,e);}
    private boolean active(Piece piece){
        if(piece.part==JudgmentFight.Part.LEFT)return leftAlive&&phase<3;
        if(piece.part==JudgmentFight.Part.RIGHT)return rightAlive&&phase<3;
        if(piece.shard>=0)return phase==3;
        if(piece.name.equals("judge_heart"))return exposed||phase==3;
        // The final phase retains the recognizable face; shards frame it instead of replacing it.
        return true;
    }
    private Location anchor(Piece piece,long tick,Location left,Location right){
        if(piece.part!=JudgmentFight.Part.CORE){
            Location hand=piece.part==JudgmentFight.Part.LEFT?left:right;
            return hand==null?center.clone().add(piece.part==JudgmentFight.Part.LEFT?-21:21,1,-2):hand.clone();
        }
        if(piece.shard>=0){
            double a=piece.shard*Math.PI/6+tick*.003;
            return center.clone().add(Math.cos(a)*18,27+Math.sin(a)*13,4);
        }
        // The mask stays overhead. Its vulnerable gemstone descends separately,
        // aligned to the four-block core hitbox, without visual/collision lag.
        return center.clone().add(0,piece.name.equals("judge_heart")?0:17,0).add(piece.offset);
    }
    void update(JudgmentFight fight,long tick,Location left,Location right){
        boolean nextLeft=fight.hand(JudgmentFight.Part.LEFT)>0,nextRight=fight.hand(JudgmentFight.Part.RIGHT)>0;
        if(leftAlive&&!nextLeft)shatter(left==null?center.clone().add(-21,4,-2):left.clone().add(0,4,0));
        if(rightAlive&&!nextRight)shatter(right==null?center.clone().add(21,4,-2):right.clone().add(0,4,0));
        if(phase!=fight.phase())shatter(center.clone().add(0,26,0));
        phase=fight.phase();leftAlive=nextLeft;rightAlive=nextRight;exposed=fight.exposed(tick);clock=tick;
        aether.frame(center,128);
        lastLeft=left;lastRight=right;
        // Move first, then reveal: pack acceptance and phase changes cannot flash a model at spawn.
        for(Piece piece:pieces){
            if(!active(piece))continue;
            if(piece.name.equals("judge_heart")){
                piece.java.setBrightness(new Display.Brightness(exposed?15:5,exposed?15:5));
                piece.java.setGlowing(exposed);piece.bedrock.setGlowing(exposed);
            }
            Location root=anchor(piece,tick,left,right),at=root.clone().add(0,piece.centerY,0);
            if(piece.java.getLocation().distanceSquared(root)>.001)piece.java.teleport(root);
            Location head=at.clone().subtract(0,1.5,0);
            if(piece.bedrock.getLocation().distanceSquared(head)>.001)piece.bedrock.teleport(head);
            for(BlockDisplay block:piece.fallback)if(block.getLocation().distanceSquared(root)>.001)block.teleport(root);
        }
        viewers();
        // Keep the sculpted face unobstructed. Spell circles belong to live attacks, not the head.
        if(phase==3&&!exposed)sigils.circle("heart:shield","judge_orbit",center.clone().add(0,.14,0),3.2,0,false);
        else if(exposed)sigils.circle("heart:shield","judge_sanctuary",center.clone().add(0,.14,0),3.2,0,false);
        else sigils.remove("heart:shield");
        if(tick<impactUntil){
            for(int i=0;i<impacts.size();i++){
                Impact impact=impacts.get(i);Location at=impact.at();
                sigils.ray("fx:ray:"+i,at.clone().add(0,impact.height(),0),at,4);
                sigils.circle("fx:seal:"+i,"judge_seal",at,impact.radius(),0,false);
                if(impact.castingCircle())sigils.circle("fx:cast:"+i,"judge_seal",at.clone().add(0,impact.height(),0),9,tick*.014,false);
            }
            for(var entry:lances.entrySet())sigils.ray("fx:lance:"+entry.getKey(),entry.getValue().from(),entry.getValue().to(),5);
        }else if(!impacts.isEmpty()||!lances.isEmpty()){impacts.clear();lances.clear();sigils.clearEffects();}
        for(FallingBlock e:sanctuaries)if(e.isValid()){e.setTicksLived(1);e.setVelocity(new Vector());}
        if(tick%8==0)ambient(tick,left,right);
    }
    private void ambient(long tick,Location left,Location right){
        Color color=charge>0?Color.fromRGB(255,134,114):Color.fromRGB(238,62,94);
        // Faceted constellation behind the mask: straight filaments, no borrowed circles.
        if(phase<3){
            double shimmer=Math.sin(tick*.055);
            for(int side:new int[]{-1,1}){
                Location eye=center.clone().add(side*3.8,29,-1.8);
                center.getWorld().spawnParticle(Particle.END_ROD,eye,2,.5,.06,.04,.005);
                if(tick%16==0){
                    Location tip=center.clone().add(side*13.5,39,2);
                    filament(tip,center.clone().add(side*16,25+shimmer,2),color,.65f);
                    filament(tip,center.clone().add(side*9,42,2),color,.5f);
                }
            }
        }
        if(exposed){
            Location heart=center.clone().add(0,2.1,0);
            center.getWorld().spawnParticle(Particle.END_ROD,heart,5,1.4,1.5,1.4,.015);
            if(phase<3)for(int i=0;i<7;i++)dust(center.clone().add(Math.sin(i+tick*.04)*.35,5+i*1.5,0),color,.65f);
        }
        if(phase<3){
            if(leftAlive)handLight(left==null?center.clone().add(-21,1,-2):left);
            if(rightAlive)handLight(right==null?center.clone().add(21,1,-2):right);
        }
        if(charge>0)for(int i=0;i<8;i++){
            double a=i*Math.PI/4+tick*.018;double r=12*(1-charge)+3;
            dust(center.clone().add(Math.cos(a)*r,29+Math.sin(a)*r,-3),color,1.1f);
        }
        for(var flash:flashes.entrySet())if(tick<flash.getValue()){
            Location at=flash.getKey()==JudgmentFight.Part.CORE?center.clone().add(0,exposed?2.1:29,-2):
                (flash.getKey()==JudgmentFight.Part.LEFT?lastLeft:lastRight);
            if(at!=null)dust(at.clone().add(0,flash.getKey()==JudgmentFight.Part.CORE?0:3.3,-2.6),Color.fromRGB(255,235,199),1.4f);
        }
    }
    private void handLight(Location hand){center.getWorld().spawnParticle(Particle.END_ROD,hand.clone().add(0,3.3,-2.6),2,.5,1,.08,.003);}
    void charge(double progress){charge=Math.clamp(progress,0,1);}
    JudgeSigils sigils(){return sigils;}
    void impact(Location at,long tick){if(impacts.size()<10)impacts.add(new Impact(at.clone(),24,8,false));impactUntil=tick+12;}
    void impact(Location at,long tick,double height,double radius){if(impacts.size()<10)impacts.add(new Impact(at.clone(),height,radius,true));impactUntil=tick+12;}
    void lance(int index,Location from,Location to,long tick){lances.put(index,new Lance(from.clone(),to.clone()));impactUntil=tick+12;}
    void flash(JudgmentFight.Part part){flashes.put(part,clock+10);}
    private void filament(Location a,Location b,Color color,float size){
        Vector vector=b.toVector().subtract(a.toVector());int steps=(int)Math.ceil(vector.length());
        for(int i=0;i<=steps;i++)dust(a.clone().add(vector.clone().multiply(i/(double)Math.max(1,steps))),color,size);
    }
    private void dust(Location at,Color color,float size){aether.dust(at,color,size);}
    private void shatter(Location at){
        center.getWorld().spawnParticle(Particle.BLOCK,at,38,2,2,1,.08,Material.SMOOTH_QUARTZ.createBlockData());
        center.getWorld().spawnParticle(Particle.END_ROD,at,24,2,2,1,.08);
    }
    boolean owns(Entity e){return entities.contains(e.getUniqueId())||sanctuaries.contains(e)||sigils.owns(e);}
    int count(){return entities.size();}
    void sanctuaries(double x,double z,double radius){
        clearSanctuaries();
        try{
            for(int quarter=0;quarter<4;quarter++){
                double a=quarter*Math.PI/2;
                Location at=center.clone().add(x*Math.cos(a)-z*Math.sin(a),.2,x*Math.sin(a)+z*Math.cos(a));
                for(int y=0;y<5;y++)marker(at.clone().add(0,y,0));
                for(int i=0;i<8;i++){double angle=i*Math.PI/4;marker(at.clone().add(Math.cos(angle)*radius,0,Math.sin(angle)*radius));}
            }
        }catch(Throwable error){clearSanctuaries();throw error;}
    }
    private void marker(Location at){
        FallingBlock e=at.getWorld().spawnFallingBlock(at,Material.EMERALD_BLOCK.createBlockData());
        e.setGravity(false);e.setDropItem(false);e.setCancelDrop(true);e.setHurtEntities(false);e.setInvulnerable(true);e.setPersistent(false);
        e.getPersistentDataContainer().set(plugin.key("world_boss_entity"),PersistentDataType.BYTE,(byte)1);sanctuaries.add(e);
    }
    void clearSanctuaries(){for(FallingBlock e:sanctuaries)e.remove();sanctuaries.clear();charge=0;sigils.clearAttack();}
    @Override public void close(){
        clearSanctuaries();sigils.close();for(UUID id:entities){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
        pieces.clear();entities.clear();visibility.clear();
    }
}
