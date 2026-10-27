package com.example.voidscape.world;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Upgrade only matching generated interior blocks, leaving exterior, edits and inventories intact. */
public final class BossInteriorService implements Listener {
    public record Change(int x,int y,int z,Material before,BlockFace beforeFace,Material after,BlockFace afterFace){}
    private static final Map<BossStructureLayout.Kind,Map<Long,List<Change>>> CHANGES=new ConcurrentHashMap<>();
    private final VoidscapePlugin plugin;
    public BossInteriorService(VoidscapePlugin plugin){this.plugin=plugin;}
    private static long key(int x,int z){return ((long)x<<32)|(z&0xffffffffL);}
    public static Map<Long,List<Change>> changes(BossStructureLayout.Kind kind){
        return CHANGES.computeIfAbsent(kind,BossInteriorService::buildChanges);
    }
    private static Map<Long,List<Change>> buildChanges(BossStructureLayout.Kind kind){
        var before=BossStructure.architecture(kind);var after=kind.blueprint();
        var touched=new HashSet<Long>();
        for(var b:after.interiorBoxes())for(int x=Math.floorDiv(b.x1(),16);x<=Math.floorDiv(b.x2(),16);x++)
            for(int z=Math.floorDiv(b.z1(),16);z<=Math.floorDiv(b.z2(),16);z++)touched.add(key(x,z));
        var result=new HashMap<Long,List<Change>>();int height=kind.maxY-kind.floorY+1;
        var materials=Material.values();var faces=BlockFace.values();
        for(long chunk:touched){
            int cx=(int)(chunk>>32),cz=(int)chunk;
            int[] old=new int[16*16*height],now=new int[old.length];
            fill(before.chunkBoxes(cx,cz),old,cx,cz,kind.floorY,height);
            fill(after.chunkBoxes(cx,cz),now,cx,cz,kind.floorY,height);
            var list=new ArrayList<Change>();
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int dy=0;dy<height;dy++){
                int i=(x*16+z)*height+dy;if(old[i]==now[i])continue;
                int bx=cx*16+x,bz=cz*16+z,y=kind.floorY+dy;
                if(!BossInteriorDesign.inside(kind,bx,y,bz))throw new IllegalStateException("Exterior changed by furnishings: "+kind+" "+bx+","+y+","+bz);
                list.add(new Change(bx,y,bz,old[i]==0?Material.AIR:materials[(old[i]>>6)-1],(old[i]&63)==0?null:faces[(old[i]&63)-1],
                    now[i]==0?Material.AIR:materials[(now[i]>>6)-1],(now[i]&63)==0?null:faces[(now[i]&63)-1]));
            }
            if(!list.isEmpty())result.put(chunk,List.copyOf(list));
        }
        return Map.copyOf(result);
    }
    private static void fill(List<BossStructure.Box> boxes,int[] cells,int cx,int cz,int minY,int height){
        for(var b:boxes){
            int x1=Math.max(0,b.x1()-cx*16),x2=Math.min(15,b.x2()-cx*16),z1=Math.max(0,b.z1()-cz*16),z2=Math.min(15,b.z2()-cz*16);
            int y1=Math.max(minY,b.y1()),y2=Math.min(minY+height-1,b.y2());if(y1>y2)continue;
            int value=b.material()==Material.AIR?0:((b.material().ordinal()+1)<<6)|(b.facing()==null?0:b.facing().ordinal()+1);
            for(int x=x1;x<=x2;x++)for(int z=z1;z<=z2;z++){
                int start=(x*16+z)*height;Arrays.fill(cells,start+y1-minY,start+y2-minY+1,value);
            }
        }
    }
    public void populate(Chunk chunk,BossStructureLayout.Site site){
        if(site.legacyDesign()||!plugin.bossStructures().enabled(site.kind()))return;
        int cx=chunk.getX()-Math.floorDiv(site.x(),16),cz=chunk.getZ()-Math.floorDiv(site.z(),16);
        var list=changes(site.kind()).getOrDefault(key(cx,cz),List.of());
        if(list.isEmpty())return;
        var marker=plugin.key(site.kind().id+"_interior_v3");
        if(!chunk.getPersistentDataContainer().has(marker,PersistentDataType.BYTE)){
            // A chunk of an edited/deleted structure must not receive floating furniture.
            int anchors=0;var old=BossStructure.architecture(site.kind());var current=site.blueprint();
            for(int x=0;x<16&&anchors<4;x++)for(int z=0;z<16&&anchors<4;z++){
                int bx=cx*16+x,bz=cz*16+z;var expected=old.at(bx,site.kind().floorY,bz);
                var actual=chunk.getBlock(x,site.kind().floorY,z).getType();
                if(expected.isSolid()&&(actual==expected||actual==current.at(bx,site.kind().floorY,bz)))anchors++;
            }
            if(anchors<4)return;
            for(var change:list){
                var block=chunk.getBlock(Math.floorMod(change.x(),16),change.y(),Math.floorMod(change.z(),16));
                // Never replace an inventory, or an administrator's independently edited block.
                if(block.getState() instanceof org.bukkit.block.Container)continue;
                if(change.after()==Material.CHEST&&change.y()==site.kind().floorY+1&&change.z()==site.kind().chestZ
                    &&(change.x()==4||change.x()==6)
                    &&chunk.getPersistentDataContainer().has(plugin.key(site.kind().id+"_treasure_v1"),PersistentDataType.BYTE))continue;
                if(block.getType()!=change.before())continue;
                if(!change.before().isAir()&&!old.state(change.before(),change.beforeFace()).matches(block.getBlockData()))continue;
                block.setBlockData(current.state(change.after(),change.afterFace()),false);
            }
            chunk.getPersistentDataContainer().set(marker,PersistentDataType.BYTE,(byte)1);
        }
        displays(chunk,site);
    }
    private void displays(Chunk chunk,BossStructureLayout.Site site){
        var idKey=plugin.key("boss_interior_display");var existing=new HashSet<String>();
        for(var entity:chunk.getEntities()){
            String id=entity.getPersistentDataContainer().get(idKey,PersistentDataType.STRING);if(id!=null)existing.add(id);
        }
        for(var display:site.blueprint().displays()){
            var p=display.point();int x=site.x()+p.x(),z=site.z()+p.z();
            if(Math.floorDiv(x,16)!=chunk.getX()||Math.floorDiv(z,16)!=chunk.getZ())continue;
            String id=site.kind().id+":"+site.x()+":"+site.z()+":"+p.x()+":"+p.y()+":"+p.z();
            if(existing.contains(id)||!chunk.getBlock(Math.floorMod(x,16),p.y()-1,Math.floorMod(z,16)).getType().isSolid())continue;
            var location=new Location(chunk.getWorld(),x+.5,p.y(),z+.5);
            location.setYaw(switch(display.facing()){case NORTH->180;case SOUTH->0;case EAST->-90;default->90;});
            chunk.getWorld().spawn(location,ArmorStand.class,stand->{
                stand.setGravity(false);stand.setInvulnerable(true);stand.setMarker(true);stand.setBasePlate(false);stand.setArms(true);
                stand.setPersistent(true);stand.setRemoveWhenFarAway(false);
                stand.getPersistentDataContainer().set(idKey,PersistentDataType.STRING,id);
                var equipment=stand.getEquipment();
                equipment.setHelmet(new ItemStack(display.diamond()?Material.DIAMOND_HELMET:Material.IRON_HELMET));
                equipment.setChestplate(new ItemStack(display.diamond()?Material.DIAMOND_CHESTPLATE:Material.IRON_CHESTPLATE));
                equipment.setLeggings(new ItemStack(display.diamond()?Material.DIAMOND_LEGGINGS:Material.IRON_LEGGINGS));
                equipment.setBoots(new ItemStack(display.diamond()?Material.DIAMOND_BOOTS:Material.IRON_BOOTS));
                equipment.setItemInMainHand(new ItemStack(display.weapon()));equipment.setItemInOffHand(new ItemStack(Material.SHIELD));
            });existing.add(id);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void armor(PlayerArmorStandManipulateEvent event){
        if(event.getRightClicked().getPersistentDataContainer().has(plugin.key("boss_interior_display"),PersistentDataType.STRING)
            &&!plugin.dungeons().canBypassProtection(event.getPlayer()))event.setCancelled(true);
    }
}
