package com.example.advancemagic.item;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.spell.Spell;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Eight elemental appearances and a neutral wooden starter, with strict item identities. */
public final class ElementalStaffService implements Listener {
    public enum Element {
        FIRE("Fire",ChatColor.RED,0xff6a18), WIND("Wind",ChatColor.GREEN,0x9fffe2),
        EARTH("Earth",ChatColor.DARK_GREEN,0xb9df42), ICE("Ice",ChatColor.AQUA,0xa6ecff),
        WATER("Water",ChatColor.BLUE,0x25aaff), LIGHTNING("Lightning",ChatColor.YELLOW,0xd7b8ff),
        LIGHT("Light",ChatColor.GOLD,0xffe5a1), DARK("Dark",ChatColor.DARK_PURPLE,0xb94cff);
        public final String title;
        public final ChatColor color;
        public final int rgb;
        Element(String title,ChatColor color,int rgb){this.title=title;this.color=color;this.rgb=rgb;}
        public String id(){return name().toLowerCase(Locale.ROOT);}
        public NamespacedKey model(){return new NamespacedKey("advance_magic","staff_"+id());}
        public boolean special(){return ordinal()>=LIGHTNING.ordinal();}
        public Element next(){return switch(this){case FIRE->WIND;case WIND->EARTH;case EARTH->ICE;case ICE->WATER;case WATER->FIRE;case LIGHTNING->LIGHT;case LIGHT->DARK;case DARK->LIGHTNING;};}
        public static Element parse(String value){if(value==null)return null;for(Element e:values())if(e.id().equalsIgnoreCase(value))return e;return null;}
    }
    private final AdvanceMagicPlugin plugin;
    private static final NamespacedKey ELEMENT=new NamespacedKey("advance_magic","staff_element");
    private static final NamespacedKey PHASE=new NamespacedKey("advance_magic","staff_model_version");
    private static final NamespacedKey KIND=new NamespacedKey("advance_magic","staff_kind");
    public static final NamespacedKey WOOD_MODEL=new NamespacedKey("advance_magic","staff_wood");
    private static final NamespacedKey OLD_NAME=new NamespacedKey("advance_magic","retired_wand_name");
    private static final NamespacedKey OLD_LORE=new NamespacedKey("advance_magic","retired_wand_lore");
    private int frame;
    public ElementalStaffService(AdvanceMagicPlugin plugin){this.plugin=plugin;}
    public ItemStack create(Element element){
        Objects.requireNonNull(element,"element");
        ItemStack item=new ItemStack(WandService.BASE);
        apply(item,element,false);
        return item;
    }
    public ItemStack createWooden(){
        ItemStack item=new ItemStack(WandService.BASE);var meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD+"Amethystwood Staff");
        meta.setLore(List.of(ChatColor.GRAY+"คทาไม้เริ่มต้น · ไม่มีธาตุ",ChatColor.GRAY+"แกนไม้พันทองเหลือง โอบผลึกอาเมธิสต์"));
        var model=meta.getCustomModelDataComponent();model.setStrings(List.of(WOOD_MODEL.toString()));
        meta.setCustomModelDataComponent(model);meta.setItemModel(WOOD_MODEL);meta.setMaxStackSize(1);
        meta.getPersistentDataContainer().set(KIND,PersistentDataType.STRING,"wood");
        meta.getPersistentDataContainer().set(PHASE,PersistentDataType.INTEGER,1);
        item.setItemMeta(meta);return item;
    }
    public boolean isWooden(ItemStack item){
        if(item==null||item.getType()!=WandService.BASE||!item.hasItemMeta())return false;
        var meta=item.getItemMeta();var data=meta.getPersistentDataContainer();
        return "wood".equals(data.get(KIND,PersistentDataType.STRING))&&!data.has(ELEMENT)
                &&plugin.wands().spell(item)==null&&WOOD_MODEL.equals(meta.getItemModel())
                &&meta.getCustomModelDataComponent().getStrings().equals(List.of(WOOD_MODEL.toString()));
    }
    /** Reject contradictory identity instead of trusting a decorative model component alone. */
    public Element element(ItemStack item){
        if(item==null||item.getType()!=WandService.BASE||!item.hasItemMeta())return null;
        var meta=item.getItemMeta();var data=meta.getPersistentDataContainer();
        if(data.has(KIND))return null;
        Element value=Element.parse(data.get(ELEMENT,PersistentDataType.STRING));
        if(value==null||!value.model().equals(meta.getItemModel()))return null;
        if(!meta.getCustomModelDataComponent().getStrings().equals(List.of(value.model().toString())))return null;
        Spell legacy=plugin.wands().spell(item);
        return legacy!=null&&fromLegacy(legacy)!=value?null:value;
    }
    public boolean isStaff(ItemStack item){return element(item)!=null||isWooden(item);}
    /** A tagged legacy wand remains recognisable to other plugins, but never casts from player input. */
    public boolean isRetiredOrStaff(ItemStack item){return isStaff(item)||plugin.wands().spell(item)!=null;}
    public static Element fromLegacy(Spell spell){
        return switch(spell){
            case LIGHTNING_STRIKE -> Element.LIGHTNING;
            case FROST_NOVA,CHRONOS_FINAL_HOUR -> Element.ICE;
            case SHADOW_STEP,VOID_PULL,WITHER_RAY,VEX_LEGION,LUNAR_REQUIEM,CRIMSON_OBLIVION -> Element.DARK;
            case NATURES_BLOOM,EARTH_WALL,IRON_ARMOR,VERDANT_SOVEREIGN -> Element.EARTH;
            case DRAGONS_BREATH,BLAZE_BARRAGE,METEOR_STRIKE -> Element.FIRE;
            case SONIC_BOOM,SHULKER_LEVITATION -> Element.WIND;
            case GUARDIAN_BEAM -> Element.WATER;
            case SOLAR_APOCALYPSE,HEAVENS_JUDGMENT -> Element.LIGHT;
        };
    }
    private void apply(ItemStack item,Element element,boolean archive){
        var meta=item.getItemMeta();var pdc=meta.getPersistentDataContainer();
        String defaultName=element.color+element.title+" Staff";
        if(archive){
            if(meta.hasDisplayName()&&!pdc.has(OLD_NAME))pdc.set(OLD_NAME,PersistentDataType.STRING,meta.getDisplayName());
            if(meta.hasLore()&&!pdc.has(OLD_LORE))pdc.set(OLD_LORE,PersistentDataType.STRING,String.join("\n",meta.getLore()));
        }
        // Keep player-assigned names; archive the old generated name and lore for recovery.
        String name=meta.hasDisplayName()?ChatColor.stripColor(meta.getDisplayName()):"";
        Spell legacy=plugin.wands().spell(item);
        boolean generated=legacy!=null&&(name.equals(legacy.title+" Wand")||name.equals("✦ "+legacy.title+" Wand")
                ||name.equals(legacy.title+" Wand [MYTHIC]")||name.equals("✦ "+legacy.title+" Wand [MYTHIC]"));
        if(name.isEmpty()||legacy==null||generated)meta.setDisplayName(defaultName);
        var lore=new ArrayList<>(List.of(ChatColor.GRAY+"คทาธาตุ"+(element.special()?"พิเศษ":""),
                ChatColor.DARK_GRAY+"โมเดลตัวอย่าง · ระบบสกิลกำลังปรับปรุง",
                ChatColor.GRAY+"วงจรธาตุ: "+element.title+" → "+element.next().title));
        if(archive&&meta.hasLore())for(String line:meta.getLore())if(!generatedLegacyLore(line))lore.add(line);
        meta.setLore(lore);
        var selectors=meta.getCustomModelDataComponent();selectors.setStrings(List.of(element.model().toString()));
        meta.setCustomModelDataComponent(selectors);meta.setItemModel(element.model());meta.setMaxStackSize(1);
        pdc.set(ELEMENT,PersistentDataType.STRING,element.id());pdc.set(PHASE,PersistentDataType.INTEGER,1);
        item.setItemMeta(meta);
    }
    private boolean generatedLegacyLore(String line){
        String plain=ChatColor.stripColor(line).trim();
        return plain.equals("[MYTHIC]")||plain.startsWith("Use to cast")||plain.startsWith("Mana:")
                ||plain.startsWith("Durability:")||plain.startsWith("Damage:")||plain.startsWith("Upgrade:")
                ||plain.startsWith("Mastery:")||plain.startsWith("คลิกขวาเพื่อร่ายเวทมนตร์")
                ||plain.startsWith("เอฟเฟกต์ต่อเนื่องอัตโนมัติ");
    }
    public boolean migrate(ItemStack item){
        if(item==null||item.getType()!=WandService.BASE||!item.hasItemMeta())return false;
        if(isStaff(item))return false;
        var pdc=item.getItemMeta().getPersistentDataContainer();
        // A conflicting or unknown staff tag is not a migration candidate.
        if(pdc.has(ELEMENT)||pdc.has(KIND))return false;
        Spell legacy=plugin.wands().spell(item);if(legacy==null)return false;
        apply(item,fromLegacy(legacy),true);return true;
    }
    public void migrate(Inventory inventory){for(int i=0;i<inventory.getSize();i++){var item=inventory.getItem(i);if(migrate(item))inventory.setItem(i,item);}}
    @EventHandler(priority=EventPriority.LOWEST) public void held(PlayerItemHeldEvent e){migrate(e.getPlayer().getInventory());}
    @EventHandler(priority=EventPriority.LOWEST) public void swap(PlayerSwapHandItemsEvent e){migrate(e.getMainHandItem());migrate(e.getOffHandItem());}
    /** Java keeps its two crown motes; Bedrock uses the staff's native model glints. */
    public void tick(){
        frame++;
        if(!plugin.getConfig().getBoolean("elemental-staffs.ambient-effects",true))return;
        var online=Bukkit.getOnlinePlayers();
        Map<World,List<Player>> javaViewers=new HashMap<>();
        for(Player viewer:online)if(!plugin.packs().isBedrock(viewer))
            javaViewers.computeIfAbsent(viewer.getWorld(),world->new ArrayList<>()).add(viewer);
        for(Player p:online){
            if(p.isDead()||p.getGameMode()==GameMode.SPECTATOR||p.isInvisible())continue;
            Element element=element(p.getInventory().getItemInMainHand());if(element==null)continue;
            List<Player> viewers=javaViewers.get(p.getWorld());if(viewers==null||viewers.isEmpty())continue;
            var forward=p.getLocation().getDirection().setY(0);if(forward.lengthSquared()<.01)forward.setZ(1);forward.normalize();
            var right=new org.bukkit.util.Vector(-forward.getZ(),0,forward.getX());
            Location crown=p.getLocation().add(0,1.9,0).add(forward.multiply(.45))
                    .add(right.multiply(p.getMainHand()==MainHand.LEFT?-.35:.35));
            double a=frame*.28;
            var dust=new Particle.DustOptions(Color.fromRGB(element.rgb),.55f);
            Location first=crown.clone().add(Math.cos(a)*.11,Math.sin(a*.7)*.10,Math.sin(a)*.11);
            Location second=crown.clone().add(-Math.cos(a)*.09,-.12,-Math.sin(a)*.09);
            // The receiver-list overload defaults to force=true; the original
            // Location overload used false. Keep the original limiter/range.
            p.getWorld().spawnParticle(Particle.DUST,viewers,null,first.getX(),first.getY(),first.getZ(),1,0,0,0,0,dust,false);
            p.getWorld().spawnParticle(Particle.DUST,viewers,null,second.getX(),second.getY(),second.getZ(),1,0,0,0,0,dust,false);
        }
    }
}
