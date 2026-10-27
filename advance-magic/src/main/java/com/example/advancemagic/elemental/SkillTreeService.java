package com.example.advancemagic.elemental;

import com.example.advancemagic.AdvanceMagicPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Permanent specialization. Points come from Arcane levels (structure treasure XP, kept apart from
 * vanilla experience, every level costlier than the last) and directly from bosses. */
public final class SkillTreeService implements Listener,TabExecutor {
    /** Twenty ranks in four colour tiers of five; each tier adds less per rank than the one before. */
    public enum Node {
        POWER("พลังเวท",Material.BLAZE_POWDER,"เพิ่มพลังเวททุกธาตุ","พลังเวท",4,3,2,1),
        MANA("ขยายมานา",Material.AMETHYST_SHARD,"เพิ่มมานาสูงสุด","มานาสูงสุด",4,4,3,2),
        REGEN("ฟื้นมานา",Material.GHAST_TEAR,"มานาฟื้นเร็วขึ้น","ฟื้นมานา",10,10,5,5),
        EFFICIENCY("ประหยัดมานา",Material.LAPIS_LAZULI,"ร่ายเวทใช้มานาน้อยลง","ค่ามานา",3,3,1,1),
        HEALING("เยียวยา",Material.GLISTERING_MELON_SLICE,"เวทฮีลแรงขึ้น","พลังฮีล",5,4,3,2),
        FIRE("เชี่ยวชาญไฟ",Material.MAGMA_CREAM,"เวทธาตุไฟแรงขึ้น","ดาเมจไฟ",4,3,2,1),
        WIND("เชี่ยวชาญลม",Material.FEATHER,"เวทธาตุลมแรงขึ้น","ดาเมจลม",4,3,2,1),
        EARTH("เชี่ยวชาญดิน",Material.CLAY_BALL,"เวทธาตุดินแรงขึ้น","ดาเมจดิน",4,3,2,1),
        ICE("เชี่ยวชาญน้ำแข็ง",Material.SNOWBALL,"เวทธาตุน้ำแข็งแรงขึ้น","ดาเมจน้ำแข็ง",4,3,2,1),
        WATER("เชี่ยวชาญน้ำ",Material.PRISMARINE_SHARD,"เวทธาตุน้ำแรงขึ้น","ดาเมจน้ำ",4,3,2,1),
        LIGHTNING("เชี่ยวชาญสายฟ้า",Material.COPPER_INGOT,"เวทธาตุสายฟ้าแรงขึ้น","ดาเมจสายฟ้า",4,3,2,1),
        LIGHT("เชี่ยวชาญแสง",Material.GLOWSTONE_DUST,"เวทธาตุแสงแรงขึ้น","ดาเมจแสง",4,3,2,1),
        DARK("เชี่ยวชาญมืด",Material.ENDER_PEARL,"เวทธาตุมืดแรงขึ้น","ดาเมจมืด",4,3,2,1);
        public static final int TIERS=4,PER_TIER=5;
        public final String title,description,stat;public final Material icon;public final int max=TIERS*PER_TIER;
        private final double[] steps;
        Node(String title,Material icon,String description,String stat,double... steps){
            this.title=title;this.icon=icon;this.description=description;this.stat=stat;this.steps=steps;
        }
        /** Bonus one rank adds in a tier (0-based): percent, or flat mana for MANA. */
        public double step(int tier){return steps[Math.clamp(tier,0,TIERS-1)];}
        /** Total bonus at a rank. The first tier (and the mana branch's first two) keep the old per-rank values. */
        public double value(int rank){double v=0;for(int r=1;r<=Math.min(rank,max);r++)v+=steps[(r-1)/PER_TIER];return v;}
        public static Node parse(String id){try{return valueOf(id.toUpperCase(Locale.ROOT));}catch(Exception e){return null;}}
    }
    /** Menu tabs. Each skill is one volume-style row: icon, −, five meter cells, +, rank badge. */
    private enum Page {
        CORE("พื้นฐาน","core",Node.POWER,Node.HEALING,Node.MANA,Node.REGEN,Node.EFFICIENCY),
        ELEMENTS_A("ธาตุ I · ไฟ ลม ดิน น้ำแข็ง","elements_a",Node.FIRE,Node.WIND,Node.EARTH,Node.ICE),
        ELEMENTS_B("ธาตุ II · น้ำ สายฟ้า แสง มืด","elements_b",Node.WATER,Node.LIGHTNING,Node.LIGHT,Node.DARK);
        final String title,texture;final Node[] rows;
        Page(String title,String texture,Node... rows){this.title=title;this.texture=texture;this.rows=rows;}
    }
    private static final int LEVEL=3,ORB=4,EXP=5,HELP=6,RESET=7,CLOSE=8,HINT=49,MINUS=1,PLUS=7,BADGE=8;
    private static final String CONFIG="elemental-system.skill-tree.";
    private static final int PROGRESSION=2;
    private final AdvanceMagicPlugin plugin;
    private final class Menu implements InventoryHolder {
        final Inventory inventory;Page page=Page.CORE;long resetArmed;
        /** Ranks bought since this menu opened: the only ones − may refund. */
        final Map<Node,Integer> added=new EnumMap<>(Node.class);
        Menu(){inventory=Bukkit.createInventory(this,54,Component.text("✦ Skill Tree",TextColor.color(0x4b2a7a))
            .append(Component.text(" · กด + เพื่ออัปสกิล",TextColor.color(0x5c5470))));}
        public Inventory getInventory(){return inventory;}
    }
    public SkillTreeService(AdvanceMagicPlugin plugin){this.plugin=plugin;}
    private NamespacedKey key(String value){return new NamespacedKey(plugin,"tree_"+value);}
    // Arcane XP ---------------------------------------------------------------------------
    public int maxLevel(){return Math.clamp(plugin.getConfig().getInt(CONFIG+"max-level",100),1,1000);}
    public int structureChestXp(){return Math.max(0,plugin.getConfig().getInt(CONFIG+"structure-chest-xp",60));}
    /** Arcane XP from level L to L+1 = base·growth^(L−1): every level costs more than the last. */
    public long cost(int level){
        double base=Math.max(1,plugin.getConfig().getDouble(CONFIG+"xp-base",100)),growth=Math.clamp(plugin.getConfig().getDouble(CONFIG+"xp-growth",1.15),1,3);
        return (long)Math.min(1e15,Math.round(base*Math.pow(growth,Math.max(0,level-1))));
    }
    /** Arcane XP that reaches a level. */
    public long totalXp(int level){long sum=0;for(int l=1;l<Math.min(level,maxLevel());l++)sum=Math.min(Long.MAX_VALUE/4,sum+cost(l));return sum;}
    public long xp(Player p){return Math.max(0,p.getPersistentDataContainer().getOrDefault(key("xp"),PersistentDataType.LONG,0L));}
    public int level(Player p){
        long xp=xp(p),need=0;int level=1,max=maxLevel();
        while(level<max&&xp>=(need=Math.min(Long.MAX_VALUE/4,need+cost(level))))level++;
        return level;
    }
    /** XP gained inside the current level and the XP that level needs; {0,0} at the cap. */
    public long[] progress(Player p){int level=level(p);return level>=maxLevel()?new long[]{0,0}:new long[]{xp(p)-totalXp(level),cost(level)};}
    public int rank(Player p,Node n){return Math.clamp(p.getPersistentDataContainer().getOrDefault(key(n.name().toLowerCase(Locale.ROOT)),PersistentDataType.INTEGER,0),0,n.max);}
    public double value(Player p,Node n){return n.value(rank(p,n));}
    public int earnedPoints(Player p){return Math.max(0,p.getPersistentDataContainer().getOrDefault(key("earned_points"),PersistentDataType.INTEGER,0));}
    /** One point per Arcane level, two to start, plus direct boss rewards. */
    public int points(Player p){int spent=0;for(Node n:Node.values())spent+=rank(p,n);return (int)Math.max(0,(long)level(p)+2+earnedPoints(p)-spent);}
    /** Direct points for bosses; never touches Arcane or vanilla XP. */
    public boolean grantPoints(Player p,int amount,String source){
        if(p==null||!p.isOnline()||amount<=0)return false;
        int before=earnedPoints(p),after=(int)Math.min(1000000L,(long)before+amount);
        if(after==before)return false;
        p.getPersistentDataContainer().set(key("earned_points"),PersistentDataType.INTEGER,after);
        p.sendMessage(ChatColor.GOLD+"Skill Tree +"+(after-before)+" · "+source+" · แต้มคงเหลือ "+points(p));
        p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,.5f,1.4f);return true;
    }
    /** Arcane XP never touches vanilla experience; every level reached is one more skill point. */
    public void addXp(Player p,long amount){addXp(p,amount,null);}
    public void addXp(Player p,long amount,String source){
        if(p==null||amount<=0||!p.isOnline())return;
        int before=level(p);
        p.getPersistentDataContainer().set(key("xp"),PersistentDataType.LONG,Math.min(totalXp(maxLevel()),xp(p)+Math.min(amount,1_000_000_000L)));
        int after=level(p);
        if(after>before){
            p.sendMessage(ChatColor.LIGHT_PURPLE+"✦ เลเวลเวท "+after+" · ได้ "+(after-before)+" แต้ม Skill Tree · แต้มคงเหลือ "+points(p)+" · /skilltree");
            p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,.5f,1.4f);
        }else if(source!=null){
            long[] at=progress(p);
            p.sendActionBar(text("+"+amount+" EXP เวท · "+source+(at[1]>0?" · เลเวล "+after+" ("+at[0]+"/"+at[1]+")":""),0xc77dff));
            p.playSound(p.getLocation(),Sound.ENTITY_EXPERIENCE_ORB_PICKUP,.5f,.7f);
        }
    }
    /** One-time: legacy quadratic Arcane XP keeps its exact level and progress on the exponential curve. */
    private void migrate(Player p){
        var data=p.getPersistentDataContainer();var flag=key("progression");
        if(data.getOrDefault(flag,PersistentDataType.INTEGER,0)>=PROGRESSION)return;
        long xp=xp(p);
        if(xp>0){
            int old=1;while(old<40&&xp>=50L*old*(old+1))old++;
            long from=50L*(old-1)*old,span=50L*old*(old+1)-from;
            double done=old>=40?0:(double)(xp-from)/span;
            long converted=Math.min(totalXp(maxLevel()),totalXp(old)+(long)(done*cost(old)));
            data.set(key("xp"),PersistentDataType.LONG,converted);
            plugin.getLogger().info("[SkillTree] progression player="+p.getUniqueId()+" level="+old+" xp="+xp+"->"+converted);
        }
        data.set(flag,PersistentDataType.INTEGER,PROGRESSION);
    }
    /** Coordinate markers survive breaking/replacing a chest and do not travel with copied block NBT. */
    private static NamespacedKey chestKey(org.bukkit.block.Container c){return new NamespacedKey("advance-magic","tree_chest_"+c.getX()+"_"+c.getY()+"_"+c.getZ());}
    public static void markStructureChest(org.bukkit.block.Container c){
        var data=c.getChunk().getPersistentDataContainer();var key=chestKey(c);
        if(!data.has(key))data.set(key,PersistentDataType.BYTE,(byte)1);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void structureLoot(org.bukkit.event.world.LootGenerateEvent e){
        if(!e.isPlugin()&&e.getLootTable().getKey().getKey().startsWith("chests/")&&e.getInventoryHolder() instanceof org.bukkit.block.Container c)markStructureChest(c);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void treasure(InventoryOpenEvent e){
        if(!(e.getPlayer() instanceof Player p)||(p.getGameMode()!=GameMode.SURVIVAL&&p.getGameMode()!=GameMode.ADVENTURE))return;
        List<org.bukkit.block.Container> containers=new ArrayList<>();var holder=e.getInventory().getHolder();
        if(holder instanceof org.bukkit.block.DoubleChest d){
            if(d.getLeftSide() instanceof org.bukkit.block.Container c)containers.add(c);
            if(d.getRightSide() instanceof org.bukkit.block.Container c)containers.add(c);
        }else if(holder instanceof org.bukkit.block.Container c)containers.add(c);
        boolean eligible=false,claimed=false;
        for(var c:containers){
            var data=c.getChunk().getPersistentDataContainer();String position=c.getX()+"_"+c.getY()+"_"+c.getZ();
            // Existing authored loot already has durable chunk provenance from the scroll/staff rollout.
            if(data.has(new NamespacedKey("evergarden","elemental_scroll_"+position),PersistentDataType.BYTE)
                ||data.has(new NamespacedKey("evergarden","elemental_staff_"+position),PersistentDataType.BYTE))markStructureChest(c);
            byte state=data.getOrDefault(chestKey(c),PersistentDataType.BYTE,(byte)0);eligible|=state==1;claimed|=state==2;
        }
        if(!eligible)return;
        for(var c:containers)c.getChunk().getPersistentDataContainer().set(chestKey(c),PersistentDataType.BYTE,(byte)2);
        if(!claimed)addXp(p,structureChestXp(),"หีบโครงสร้าง");
    }
    public boolean learn(Player p,Node n){
        if(n==null||points(p)<=0||rank(p,n)>=n.max)return false;
        if((n==Node.REGEN&&rank(p,Node.MANA)<1)||(n==Node.EFFICIENCY&&rank(p,Node.MANA)<2)
            ||(n.ordinal()>=Node.FIRE.ordinal()&&rank(p,Node.POWER)<2))return false;
        p.getPersistentDataContainer().set(key(n.name().toLowerCase(Locale.ROOT)),PersistentDataType.INTEGER,rank(p,n)+1);
        plugin.getLogger().info("[SkillTree] player="+p.getUniqueId()+" node="+n+" rank="+rank(p,n));return true;
    }
    public boolean reset(Player p){
        int spent=0;for(Node n:Node.values())spent+=rank(p,n);
        if(spent==0||p.getLevel()<5||!p.getInventory().containsAtLeast(new ItemStack(Material.AMETHYST_SHARD),16))return false;
        var remaining=p.getInventory().removeItem(new ItemStack(Material.AMETHYST_SHARD,16));if(!remaining.isEmpty())return false;
        p.setLevel(p.getLevel()-5);for(Node n:Node.values())p.getPersistentDataContainer().remove(key(n.name().toLowerCase(Locale.ROOT)));
        plugin.getLogger().info("[SkillTree] reset player="+p.getUniqueId());return true;
    }
    public void discover(Player p){
        migrate(p);
        plugin.staffSpells().discover(p);
        // Codex records survive immediate imprinting, selling, or using the last scroll.
        for(ElementalSpell spell:plugin.staffSpells().discovered(p)){
            var marker=key("found_"+spell.id());var data=p.getPersistentDataContainer();
            if(!data.has(marker))data.set(marker,PersistentDataType.BYTE,(byte)1);
        }
    }
    // Presentation ------------------------------------------------------------------------
    private static final String[] TIER_NAMES={"เขียว","ฟ้า","ม่วง","ส้ม"};
    private static final int[] TIER_COLORS={0x5ee87a,0x4fb4ff,0xb47cff,0xff9a3c};
    private static final int GOLD=0xffd166;
    private static Node parent(Node n){return n==Node.REGEN||n==Node.EFFICIENCY?Node.MANA:n.ordinal()>=Node.FIRE.ordinal()?Node.POWER:null;}
    private static int needed(Node n){return n==Node.REGEN?1:n==Node.EFFICIENCY||n.ordinal()>=Node.FIRE.ordinal()?2:0;}
    private boolean unlocked(Player p,Node n){Node parent=parent(n);return parent==null||rank(p,parent)>=needed(n);}
    private static String amount(double v){return v==Math.rint(v)?String.valueOf((long)v):String.valueOf(v);}
    private static String bonus(Node n,double v){return (n==Node.EFFICIENCY?"-":"+")+amount(v)+(n==Node.MANA?" ":"% ")+n.stat;}
    private static String effect(Node n,int rank){return bonus(n,n.value(rank));}
    private static int tier(int rank){return Math.clamp((rank-1)/Node.PER_TIER,0,Node.TIERS-1);}
    private static int rankColor(Node n,int rank){return rank<=0?0x8c8299:rank>=n.max?GOLD:TIER_COLORS[tier(rank)];}
    private static String tierName(Node n,int rank){return rank>=n.max?"ทอง":TIER_NAMES[tier(rank)];}
    private static int tint(Node n){
        try{return com.example.advancemagic.item.ElementalStaffService.Element.valueOf(n.name()).rgb;}catch(IllegalArgumentException notElement){}
        return switch(n){case POWER->0xff9f43;case MANA->0xb388ff;case REGEN->0x7fdbff;case EFFICIENCY->0x5c7cfa;default->0xff7eb6;};
    }
    private static Component text(String value,int rgb){return Component.text(value,TextColor.color(rgb)).decoration(TextDecoration.ITALIC,false);}
    private static String id(Node n){return n.name().toLowerCase(Locale.ROOT);}
    /** Paper drawn by the pack model advance_magic:skilltree_<texture>; Geyser maps the same ids for Bedrock. */
    private static ItemStack gui(String texture,Component name,List<Component> lore){
        var item=new ItemStack(Material.PAPER);var meta=item.getItemMeta();
        meta.setItemModel(new NamespacedKey("advance_magic","skilltree_"+texture));
        meta.displayName(name==null?Component.empty():name);
        if(name==null)meta.setHideTooltip(true);else meta.lore(lore);
        meta.addItemFlags(ItemFlag.values());item.setItemMeta(meta);return item;
    }
    /** Times a meter cell has filled: rank 7 → cells 1-2 on tier 2 (blue), cells 3-5 still tier 1 (green). */
    private static int fills(int rank,int pos){return rank<pos?0:(rank-pos)/Node.PER_TIER+1;}
    /** Meter cell texture; "_new" outlines a cell that filled since the menu opened. */
    private static String cell(Node n,int rank,int added,boolean open,int pos){
        int fills=fills(rank,pos);
        if(!open)return "cell"+pos+"_locked";
        if(fills==0)return "cell"+pos+"_empty";
        return "cell"+pos+"_"+(rank>=n.max?"gold":"t"+fills)+(fills>fills(rank-added,pos)?"_new":"");
    }
    private static List<Component> tiers(Node n,int rank,int added){
        List<Component> lore=new ArrayList<>();
        for(int t=0;t<Node.TIERS;t++){
            int done=Math.clamp(rank-t*Node.PER_TIER,0,Node.PER_TIER);
            lore.add(text((done>0?"■ ":"□ ")+TIER_NAMES[t]+" ขั้น "+(t*Node.PER_TIER+1)+"-"+(t+1)*Node.PER_TIER+"  ",done>0?TIER_COLORS[t]:0x6e6878)
                .append(text(bonus(n,n.step(t))+" ต่อขั้น"+(done==Node.PER_TIER?"  ✔":done>0?"  ("+done+"/"+Node.PER_TIER+")":""),done>0?0xe8e0f2:0x8c8299)));
        }
        lore.add(text((rank>=n.max?"■ ":"□ ")+"ทอง = ครบ "+n.max+" ขั้น",rank>=n.max?GOLD:0x6e6878));
        lore.add(Component.empty());
        lore.add(text("รวมตอนนี้  ",0x8c8299).append(text(rank==0?"ยังไม่มีโบนัส":effect(n,rank),0xe8e0f2)));
        if(added>0)lore.add(text("เพิ่งใส่ "+added+" ขั้น (ขอบขาว) · กด − คืนได้ก่อนปิดเมนู",0xffffff));
        return lore;
    }
    private void row(Player p,Menu m,int row,Node n,int points){
        Inventory inv=m.inventory;int base=row*9,rank=rank(p,n),added=Math.min(rank,m.added.getOrDefault(n,0));
        boolean open=unlocked(p,n),maxed=rank>=n.max;Node parent=parent(n);int color=open?tint(n):0x8c8299;
        String now=rank==0?"ยังไม่มีโบนัส":effect(n,rank),need=parent==null?"":"ต้องมี "+parent.title+" ขั้น "+needed(n);
        List<Component> info=new ArrayList<>();
        info.add(text(n.description,0xb7aec4));
        info.add(Component.empty());
        info.add(text("ระดับ  ",0x8c8299).append(text(rank+"/"+n.max+(rank>0?" · ขั้น"+tierName(n,rank):""),rankColor(n,rank))));
        info.add(text("ตอนนี้  ",0x8c8299).append(text(now,0xe8e0f2)));
        if(!maxed)info.add(text("ขั้นถัดไป  ",0x8c8299).append(text(effect(n,rank+1),0x9ef01a)));
        if(parent!=null)info.add(text((open?"✔ ":"✖ ")+need,open?0x7bd389:0xff6b6b));
        info.add(Component.empty());
        info.add(text(maxed?"อัปเต็มแล้ว":!open?"ยังล็อก · "+need+" ก่อน":"กด + (ปุ่มเขียว) เพื่อเพิ่มระดับ",maxed?GOLD:!open?0xff6b6b:0x9ef01a));
        inv.setItem(base,gui("icon_"+id(n),text(n.title,color).decoration(TextDecoration.BOLD,true),info));
        inv.setItem(base+MINUS,added>0
            ?gui("minus",text("− ลด "+n.title,0xff9466),List.of(text("คืน 1 แต้ม · ขั้น "+rank+" → "+(rank-1),0xe8e0f2),
                text("ลดได้อีก "+added+" ขั้น (เฉพาะที่เพิ่งใส่ ก่อนปิดเมนู)",0x8c8299),text("Shift-คลิก = คืนทั้ง "+added+" แต้ม",0x8c8299)))
            :gui("minus_off",text("− ลด",0x8c8299),List.of(text(rank>0?"ขั้นที่ใส่ไว้ก่อนเปิดเมนูนี้ลดไม่ได้":"ยังไม่ได้ใส่แต้ม",0x8c8299),
                text(rank>0?"อยากจัดใหม่ทั้งหมด ใช้ปุ่มรีเซ็ต (มุมขวาบน)":"กด + เพื่อใส่แต้ม",0x8c8299))));
        Component meter=text(n.title+"  "+rank+"/"+n.max+(rank>0?" · ขั้น"+tierName(n,rank):""),rank>0?rankColor(n,rank):color).decoration(TextDecoration.BOLD,true);
        List<Component> ladder=tiers(n,rank,added);
        for(int pos=1;pos<=5;pos++)inv.setItem(base+1+pos,gui(cell(n,rank,added,open,pos),meter,ladder));
        inv.setItem(base+PLUS,open&&!maxed&&points>0
            ?gui("plus",text("+ เพิ่ม "+n.title,0x6dffb0),List.of(text("ใช้ 1 แต้ม · ขั้น "+rank+" → "+(rank+1)+" ("+tierName(n,rank+1)+")",0xe8e0f2),
                text(now+" → ",0x8c8299).append(text(effect(n,rank+1),0x9ef01a)),
                text("แต้มคงเหลือ "+points,0x8c8299),text("Shift-คลิก = ใส่จนเต็มหรือจนแต้มหมด",0x8c8299)))
            :gui("plus_off",text("+ เพิ่ม",0x8c8299),List.of(text(maxed?"เต็มแล้ว ("+rank+"/"+n.max+")":!open?need+" ก่อน":"ไม่มีแต้มเหลือ",maxed?GOLD:0xff6b6b),
                text(maxed?"สกิลนี้อัปสูงสุดแล้ว":!open?"สกิลนี้ยังล็อกอยู่":"ได้แต้มจากเลเวลเวท (EXP จากหีบโครงสร้าง) และการปราบบอส",0x8c8299))));
        inv.setItem(base+BADGE,open
            ?gui("rank_"+rank,text("ระดับ "+rank+"/"+n.max+(rank>0?" · ขั้น"+tierName(n,rank):""),rankColor(n,rank)),List.of(text(now,0xb7aec4)))
            :gui("lock",text("ล็อก",0xff6b6b),List.of(text(need+" ก่อน",0xe8e0f2))));
    }
    private void draw(Player p,Menu m){
        Inventory inv=m.inventory;int points=points(p),spent=0,level=level(p);long[] at=progress(p);boolean capped=at[1]==0;
        for(Node n:Node.values())spent+=rank(p,n);
        for(int i=0;i<54;i++)inv.setItem(i,gui("panel",null,null));
        Page[] pages=Page.values();
        for(int i=0;i<pages.length;i++){
            Page page=pages[i];boolean on=page==m.page;
            List<Component> lore=new ArrayList<>();
            for(Node n:page.rows)lore.add(text("• "+n.title+"  ",unlocked(p,n)?tint(n):0x8c8299).append(text(rank(p,n)+"/"+n.max,rankColor(n,rank(p,n)))));
            if(page!=Page.CORE&&rank(p,Node.POWER)<2)lore.add(text("✖ ต้องมี พลังเวท ขั้น 2 (หน้า พื้นฐาน)",0xff6b6b));
            lore.add(Component.empty());
            lore.add(text(on?"เปิดหน้านี้อยู่":"คลิกเพื่อเปิดหน้านี้",on?GOLD:0x9ef01a));
            inv.setItem(i,gui("tab_"+page.texture+(on?"_on":""),text("หน้า "+page.title,on?GOLD:0xe8e0f2).decoration(TextDecoration.BOLD,on),lore));
        }
        List<Component> arcane=new ArrayList<>();
        arcane.add(text(capped?"เลเวลสูงสุดแล้ว":"EXP "+at[0]+" / "+at[1]+" · อีก "+(at[1]-at[0])+" ถึงเลเวล "+(level+1),0xe8e0f2));
        arcane.add(text("ทุกเลเวลได้ 1 แต้ม · เลเวลยิ่งสูงยิ่งใช้ EXP มาก",0xb7aec4));
        arcane.add(text("EXP ได้จากการเปิดหีบโครงสร้างครั้งแรก (+"+structureChestXp()+")",0xb7aec4));
        arcane.add(text("แยกจากเลเวล Minecraft ปกติ",0x8c8299));
        inv.setItem(LEVEL,gui(level>99?"level_more":"level_"+level,text("เลเวลเวท "+level,0xc9a7ff).decoration(TextDecoration.BOLD,true),arcane));
        int tenths=capped?10:(int)Math.min(9,10*at[0]/Math.max(1,at[1]));
        inv.setItem(EXP,gui("xp_"+tenths,text(capped?"EXP เต็มแล้ว":"EXP "+at[0]+" / "+at[1],0xc9a7ff).decoration(TextDecoration.BOLD,true),arcane));
        List<Component> header=new ArrayList<>();
        header.add(text("ใช้ไปแล้ว "+spent+" แต้ม",0x8c8299));
        header.add(text("แต้มมาจาก: เลเวลเวท (เลเวลละ 1) และการปราบบอส",0xb7aec4));
        header.add(Component.empty());
        header.add(text("โบนัสตอนนี้",0x7fdbff));
        for(Node n:Node.values())if(rank(p,n)>0)header.add(text("• ",rankColor(n,rank(p,n))).append(text(effect(n,rank(p,n)),0xe8e0f2)));
        if(spent==0)header.add(text("ยังไม่ได้อัปสกิลใด",0x8c8299));
        inv.setItem(ORB,gui(points>99?"orb_more":"orb_"+points,text("แต้มคงเหลือ "+points+" แต้ม",points>0?0x9ef01a:0xffb703).decoration(TextDecoration.BOLD,true),header));
        inv.setItem(HELP,gui("help",text("วิธีใช้",0x7fc8ff).decoration(TextDecoration.BOLD,true),List.of(
            text("1. ลูกแก้วม่วง = แต้มที่เหลือ · ซ้าย = เลเวลเวท · ขวา = EXP",0xe8e0f2),
            text("2. กด + (เขียว) เพื่อใส่แต้ม แถบจะสูงขึ้นเหมือนเร่งเสียง",0xe8e0f2),
            text("3. ครบ 5 ช่องแถบเปลี่ยนสี: เขียว → ฟ้า → ม่วง → ส้ม → ทอง",0xe8e0f2),
            text("   สกิลละ 20 ขั้น · ขั้นสีหลังๆ ได้โบนัสต่อขั้นน้อยลง",0xb7aec4),
            text("4. กด − (ส้ม) คืนแต้มได้เฉพาะที่เพิ่งใส่ก่อนปิดเมนู (ขอบขาว)",0xe8e0f2),
            text("5. ตัวเลขขวาสุด = ระดับ/20 · รูปกุญแจ = ต้องอัปสกิลก่อนหน้า",0xe8e0f2),
            Component.empty(),
            text("แต้มได้จากเลเวลเวท (EXP จากหีบโครงสร้าง) และปราบบอส",0xb7aec4),
            text("เปลี่ยนหน้าที่ 3 ปุ่มซ้ายบน: พื้นฐาน / ธาตุ I / ธาตุ II",0xb7aec4),
            text("Shift-คลิก + = ใส่จนเต็ม · Shift-คลิก − = คืนทั้งหมด",0x8c8299),
            text("ใส่แล้วมีผลทันที ไม่ต้องกดบันทึก",0x8c8299))));
        inv.setItem(RESET,System.currentTimeMillis()<m.resetArmed
            ?gui("reset_armed",text("ยืนยันรีเซ็ต?",0xff6b6b).decoration(TextDecoration.BOLD,true),List.of(
                text("คลิกอีกครั้งภายใน 5 วินาทีเพื่อคืนแต้มทั้งหมด",0xe8e0f2),text("ใช้ Amethyst Shard 16 + เลเวล Minecraft 5",0xffb703)))
            :gui("reset",text("รีเซ็ตแต้มทั้งหมด",0xffb703).decoration(TextDecoration.BOLD,true),List.of(
                text("คืนแต้มทุกสกิลเพื่อจัดใหม่ (เลเวลเวทและ EXP ไม่หาย)",0xe8e0f2),text("ค่าใช้จ่าย: Amethyst Shard 16 + เลเวล Minecraft 5",0xb7aec4),text("คลิก 2 ครั้งเพื่อยืนยัน",0x8c8299))));
        inv.setItem(CLOSE,gui("close",text("ปิด",0xff6b6b),List.of(text("ระดับที่ใส่มีผลทันทีที่กด +",0x8c8299))));
        for(int i=0;i<m.page.rows.length;i++)row(p,m,i+1,m.page.rows[i],points);
        if(m.page!=Page.CORE&&rank(p,Node.POWER)<2)inv.setItem(HINT,gui("lock",text("ธาตุยังล็อกอยู่",0xff6b6b).decoration(TextDecoration.BOLD,true),
            List.of(text("อัป พลังเวท ให้ถึงขั้น 2 ที่หน้า พื้นฐาน",0xe8e0f2),text("คลิกเพื่อไปหน้า พื้นฐาน",0x9ef01a))));
    }
    public void open(Player p){
        discover(p);var menu=new Menu();draw(p,menu);p.openInventory(menu.inventory);
        int points=points(p);
        if(points>0)p.sendActionBar(text("มี "+points+" แต้ม · กด + (ปุ่มเขียว) ข้างสกิลเพื่ออัป",0x9ef01a));
    }
    private void add(Player p,Menu m,Node n,boolean all){
        int before=rank(p,n);
        while(learn(p,n)){m.added.merge(n,1,Integer::sum);if(!all)break;}
        int rank=rank(p,n);
        if(rank>before){
            boolean maxed=rank>=n.max,tierUp=!maxed&&rank%Node.PER_TIER==0;
            p.playSound(p.getLocation(),maxed?Sound.UI_TOAST_CHALLENGE_COMPLETE:tierUp?Sound.ENTITY_PLAYER_LEVELUP:Sound.BLOCK_AMETHYST_BLOCK_RESONATE,
                maxed?.6f:tierUp?.6f:1f,maxed?1f:tierUp?1.2f:.8f+.08f*((rank-1)%Node.PER_TIER+1));
            p.sendActionBar(text(maxed?n.title+" ครบ "+n.max+" ขั้น · ขั้นทอง! · "+effect(n,rank)
                :tierUp?n.title+" ครบขั้น"+TIER_NAMES[rank/Node.PER_TIER-1]+" → ต่อไปขั้น"+TIER_NAMES[rank/Node.PER_TIER]+" · "+effect(n,rank)
                :n.title+" "+rank+"/"+n.max+" · "+effect(n,rank)+" · แต้มเหลือ "+points(p),rankColor(n,rank)));
        }else{
            p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.7f,.6f);
            p.sendActionBar(text(rank>=n.max?"ขั้นเต็มแล้ว":!unlocked(p,n)?"ต้องอัป "+parent(n).title+" ขั้น "+needed(n)+" ก่อน":"แต้มไม่พอ · เก็บ EXP เวทจากหีบโครงสร้าง หรือปราบบอส",0xff6b6b));
        }
    }
    /** A skill learned on top of n that would lose its prerequisite if n dropped one rank. */
    private Node dependent(Player p,Node n){
        for(Node c:Node.values())if(parent(c)==n&&rank(p,c)>0&&rank(p,n)-1<needed(c))return c;
        return null;
    }
    /** Refunds only ranks bought in this menu session; a full respec stays behind the paid reset. */
    private void remove(Player p,Menu m,Node n,boolean all){
        int before=rank(p,n);Node blocker=null;
        while(m.added.getOrDefault(n,0)>0&&rank(p,n)>0&&(blocker=dependent(p,n))==null){
            p.getPersistentDataContainer().set(key(id(n)),PersistentDataType.INTEGER,rank(p,n)-1);
            m.added.merge(n,-1,Integer::sum);
            if(!all)break;
        }
        int rank=rank(p,n);
        if(rank<before){
            plugin.getLogger().info("[SkillTree] refund player="+p.getUniqueId()+" node="+n+" rank="+rank);
            p.playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_HIT,1f,.7f);
            p.sendActionBar(text(n.title+" "+rank+"/"+n.max+" · คืน "+(before-rank)+" แต้ม · แต้มเหลือ "+points(p),0xff9466));
        }else{
            p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.7f,.6f);
            p.sendActionBar(text(blocker!=null?"ลด "+blocker.title+" ก่อน (ต้องใช้ "+n.title+" ขั้น "+needed(blocker)+")"
                :rank>0?"ขั้นที่ใส่ไว้ก่อนเปิดเมนูลดไม่ได้ · ใช้ปุ่มรีเซ็ต":"ยังไม่ได้ใส่แต้ม",0xff6b6b));
        }
    }
    private String resetBlocker(Player p){
        int spent=0;for(Node n:Node.values())spent+=rank(p,n);
        return spent==0?"ยังไม่ได้ใช้แต้ม ไม่ต้องรีเซ็ต":p.getLevel()<5?"ต้องมีเลเวล Minecraft อย่างน้อย 5"
            :!p.getInventory().containsAtLeast(new ItemStack(Material.AMETHYST_SHARD),16)?"ต้องมี Amethyst Shard 16 ชิ้นในกระเป๋า":null;
    }
    private void resetClick(Player p,Menu m){
        String blocked=resetBlocker(p);
        if(blocked!=null){m.resetArmed=0;p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.7f,.6f);p.sendActionBar(text(blocked,0xff6b6b));return;}
        if(System.currentTimeMillis()>=m.resetArmed){
            m.resetArmed=System.currentTimeMillis()+5000;
            p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_PLING,.6f,.8f);
            p.sendActionBar(text("คลิกรีเซ็ตอีกครั้งเพื่อยืนยัน · Amethyst Shard 16 + เลเวล 5",0xffb703));
            // Disarm visibly once the window passes.
            plugin.getServer().getScheduler().runTaskLater(plugin,()->{
                if(m.resetArmed!=0&&System.currentTimeMillis()>=m.resetArmed&&p.getOpenInventory().getTopInventory().getHolder()==m){m.resetArmed=0;draw(p,m);}
            },101);
            return;
        }
        m.resetArmed=0;
        if(reset(p)){
            m.added.clear();p.playSound(p.getLocation(),Sound.BLOCK_AMETHYST_CLUSTER_BREAK,1f,.8f);
            p.sendMessage(ChatColor.GREEN+"Skill Tree: คืนแต้มทั้งหมดแล้ว · แต้มคงเหลือ "+points(p));
        }else{p.playSound(p.getLocation(),Sound.BLOCK_NOTE_BLOCK_BASS,.7f,.6f);p.sendActionBar(text("รีเซ็ตไม่สำเร็จ",0xff6b6b));}
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent e){
        if(!(e.getView().getTopInventory().getHolder() instanceof Menu m)||!(e.getWhoClicked() instanceof Player p))return;
        e.setCancelled(true);if(e.getClickedInventory()!=e.getView().getTopInventory())return;
        ClickType click=e.getClick();
        if(click!=ClickType.LEFT&&click!=ClickType.RIGHT&&click!=ClickType.SHIFT_LEFT&&click!=ClickType.SHIFT_RIGHT)return;
        int slot=e.getRawSlot(),row=slot/9,col=slot%9;
        if(slot!=RESET)m.resetArmed=0;
        if(slot==CLOSE){p.closeInventory();return;}
        if(slot>=0&&slot<Page.values().length){m.page=Page.values()[slot];p.playSound(p.getLocation(),Sound.ITEM_BOOK_PAGE_TURN,.8f,1.1f);}
        else if(slot==RESET)resetClick(p,m);
        else if(slot==HINT&&m.page!=Page.CORE&&rank(p,Node.POWER)<2){m.page=Page.CORE;p.playSound(p.getLocation(),Sound.ITEM_BOOK_PAGE_TURN,.8f,1.1f);}
        else if(row>=1&&row<=m.page.rows.length){
            Node n=m.page.rows[row-1];
            if(col==PLUS)add(p,m,n,click.isShiftClick());
            else if(col==MINUS)remove(p,m,n,click.isShiftClick());
            else p.sendActionBar(text("กด + (ปุ่มเขียว) เพื่อเพิ่ม · กด − เพื่อคืนแต้มที่เพิ่งใส่",0xb7aec4));
        }
        // Redraw in place: no reopen flicker and the cursor stays put.
        draw(p,m);
    }
    @EventHandler public void drag(InventoryDragEvent e){if(e.getView().getTopInventory().getHolder() instanceof Menu)e.setCancelled(true);}
    @EventHandler public void join(PlayerJoinEvent e){discover(e.getPlayer());}
    @EventHandler(priority=EventPriority.MONITOR) public void inventory(InventoryCloseEvent e){
        if(!(e.getPlayer() instanceof Player p))return;
        if(e.getInventory().getHolder() instanceof Menu m){
            List<String> changes=new ArrayList<>();
            m.added.forEach((n,count)->{if(count>0)changes.add(n.title+" +"+count);});
            if(!changes.isEmpty())p.sendMessage(ChatColor.GREEN+"Skill Tree บันทึกแล้ว · "+String.join(", ",changes)+" · แต้มเหลือ "+points(p));
        }
        discover(p);
    }
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args){
        if(args.length>0&&args[0].equalsIgnoreCase("xp")){
            if(!sender.hasPermission("advance-magic.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}
            Player target=args.length==3?Bukkit.getPlayerExact(args[1]):null;long amount=0;
            try{if(args.length==3)amount=Long.parseLong(args[2]);}catch(NumberFormatException invalid){}
            if(target==null||amount<=0){sender.sendMessage(ChatColor.YELLOW+"/skilltree xp <player> <amount>");return true;}
            addXp(target,amount,"แอดมิน");
            sender.sendMessage(ChatColor.GREEN+target.getName()+" · เลเวลเวท "+level(target)+" · EXP "+xp(target)+" · แต้มคงเหลือ "+points(target));return true;
        }
        if(!(sender instanceof Player p)){sender.sendMessage("Use /skilltree in game.");return true;}
        if(args.length==0){open(p);return true;}
        if(args[0].equalsIgnoreCase("reset")){
            if(args.length==2&&args[1].equalsIgnoreCase("confirm")){p.sendMessage(reset(p)?ChatColor.GREEN+"คืนแต้มแล้ว":ChatColor.RED+"ต้องมีแต้มที่ใช้ไป, Amethyst Shard 16 และเลเวล 5");open(p);}else p.sendMessage("/skilltree reset confirm · 16 Amethyst Shards + 5 Minecraft levels");return true;
        }
        if(args[0].equalsIgnoreCase("learn")&&args.length==2){p.sendMessage(learn(p,Node.parse(args[1]))?"พัฒนาสกิลแล้ว":"ไม่ผ่านเงื่อนไข");open(p);return true;}
        p.sendMessage("/skilltree [learn <node>|reset confirm]");return true;
    }
    public List<String> onTabComplete(CommandSender s,Command c,String l,String[] a){
        if(a.length==1)return (s.hasPermission("advance-magic.admin")?List.of("learn","reset","xp"):List.of("learn","reset")).stream().filter(v->v.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        if(a.length==2&&a[0].equalsIgnoreCase("learn"))return Arrays.stream(Node.values()).map(n->n.name().toLowerCase(Locale.ROOT)).filter(n->n.startsWith(a[1].toLowerCase(Locale.ROOT))).toList();
        if(a.length==2&&a[0].equalsIgnoreCase("xp")&&s.hasPermission("advance-magic.admin"))return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n->n.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))).toList();
        return List.of();
    }
}
