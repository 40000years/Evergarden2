package com.example.advancemagic.item;

import com.example.advancemagic.spell.Spell;
import com.example.advancemagic.elemental.StaffGemService;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import java.util.*;

public final class WandService implements Listener {
    public static final Material BASE=Material.CARROT_ON_A_STICK;
    public static final Material CORE_BASE=Material.HEART_OF_THE_SEA;
    private final NamespacedKey wandKey;
    private final NamespacedKey castsKey;
    private final NamespacedKey coreKey;
    private final NamespacedKey durabilityKey;
    private final NamespacedKey durabilityLevelKey;
    private final NamespacedKey damageLevelKey;
    private final NamespacedKey cooldownLevelKey;
    private final NamespacedKey flyingStaffKey;
    private final NamespacedKey upgradeItemKey=new NamespacedKey("advance_magic","wand_upgrade");
    public static final int BASE_USES=30, MAX_UPGRADE_LEVEL=10, USES_PER_REPAIR=5;
    private final Map<NamespacedKey,Spell> recipes=new HashMap<>();
    private final Plugin plugin;
    public WandService(Plugin plugin) {
        this.plugin=plugin;
        wandKey=new NamespacedKey(plugin,"wand");
        castsKey=new NamespacedKey(plugin,"casts");
        coreKey=new NamespacedKey(plugin,"core");
        durabilityKey=new NamespacedKey(plugin,"durability");
        durabilityLevelKey=new NamespacedKey(plugin,"durability_level");
        damageLevelKey=new NamespacedKey(plugin,"damage_level");
        cooldownLevelKey=new NamespacedKey(plugin,"cooldown_level");
        flyingStaffKey=new NamespacedKey(plugin,"flying_staff");
    }
    public static String coreTitle(Spell spell) {
        return switch(spell) {
            case LIGHTNING_STRIKE -> "Lightning";
            case FROST_NOVA -> "Frost";
            case SHADOW_STEP -> "Shadows";
            case NATURES_BLOOM -> "Nature";
            case EARTH_WALL -> "Earth";
            case DRAGONS_BREATH -> "Dragon";
            case VOID_PULL -> "the Void";
            case SONIC_BOOM -> "the Warden";
            case BLAZE_BARRAGE -> "the Blaze";
            case WITHER_RAY -> "Wither";
            case SHULKER_LEVITATION -> "Levitation";
            case METEOR_STRIKE -> "Meteor";
            case IRON_ARMOR -> "Iron";
            case VEX_LEGION -> "Evocation";
            case GUARDIAN_BEAM -> "the Guardian";
            case SOLAR_APOCALYPSE -> "the Sun";
            case CHRONOS_FINAL_HOUR -> "Time";
            case HEAVENS_JUDGMENT -> "Judgment";
            case VERDANT_SOVEREIGN -> "the Verdant Crown";
            case LUNAR_REQUIEM -> "the Requiem Moon";
            case CRIMSON_OBLIVION -> "Oblivion";
        };
    }
    private static String coreDisplayName(Spell spell) {
        return ChatColor.LIGHT_PURPLE+"✦ "+ChatColor.GOLD+"Core of "+coreTitle(spell)
                +ChatColor.DARK_GRAY+" [LEGACY]";
    }
    private static List<String> coreLore(Spell spell) {
        return List.of(ChatColor.GRAY+"แกนคราฟต์ระบบเก่า · เลิกใช้คราฟต์คทาแล้ว",
                ChatColor.DARK_AQUA+"เวทเดิม: "+spell.title,
                ChatColor.YELLOW+"ยังใช้เป็นเชื้อเพลิงที่แท่นฟื้นฟูได้");
    }
    private static boolean retiredCoreSelector(String selector) {
        if(selector==null)return false;
        for(Spell spell:Spell.values())if(selector.equals("advance_magic:core_"+spell.id()))return true;
        return false;
    }
    private static String wandDisplayName(Spell spell) {
        return ChatColor.LIGHT_PURPLE+"✦ "+(spell.isMythic()?ChatColor.GOLD:ChatColor.LIGHT_PURPLE)
                +spell.title+" Wand"+(spell.isMythic()?ChatColor.RED+" [MYTHIC]":"");
    }
    public ItemStack createCore(Spell spell) {
        ItemStack item=new ItemStack(CORE_BASE);
        var meta=item.getItemMeta();
        meta.setDisplayName(coreDisplayName(spell));
        meta.setLore(coreLore(spell));
        meta.getPersistentDataContainer().set(coreKey,PersistentDataType.STRING,spell.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("advance_magic","core"),PersistentDataType.STRING,spell.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("voidscape","magic_core"),PersistentDataType.STRING,spell.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("evergarden","magic_core"),PersistentDataType.STRING,spell.id());
        item.setItemMeta(meta);
        return item;
    }
    public Spell coreSpell(ItemStack item) {
        if(item==null||item.getType()!=CORE_BASE||!item.hasItemMeta())return null;
        var pdc=item.getItemMeta().getPersistentDataContainer();
        Spell result=null;
        for(NamespacedKey key:List.of(coreKey,new NamespacedKey("advance_magic","core"),
                new NamespacedKey("voidscape","magic_core"),new NamespacedKey("evergarden","magic_core"))) {
            if(!pdc.has(key))continue;
            String id=pdc.get(key,PersistentDataType.STRING);
            Spell candidate=id==null?null:Spell.parse(id);
            if(candidate==null||(result!=null&&result!=candidate))return null;
            result=candidate;
        }
        return result;
    }
    public ItemStack create(Spell spell) {
        ItemStack item=new ItemStack(BASE);
        var meta=item.getItemMeta();
        meta.setDisplayName(wandDisplayName(spell));
        if(spell==Spell.SHULKER_LEVITATION) {
            meta.setLore(List.of(
                ChatColor.GOLD+"[ระดับตำนานสูงสุด · MYTHIC 0.5%]",
                ChatColor.DARK_PURPLE+"§k||§r "+ChatColor.LIGHT_PURPLE+"Ancient Dragon Singularity "+ChatColor.DARK_PURPLE+"§k||",
                ChatColor.GRAY+"คลิกขวาเพื่อปลดปล่อยหายนะมิติบรรพกาล",
                ChatColor.AQUA+"Mana: "+spell.mana+" / Cooldown: "+spell.cooldown+"s",
                ChatColor.YELLOW+"⚡ พายุฟ้าผ่า · มังกรจุติ · มหาหลุมดำกลืนมิติ · ดินแดน Sculk Wither III"
            ));
        } else {
            meta.setLore(List.of(ChatColor.GRAY+"คลิกขวาเพื่อร่ายเวทมนตร์",ChatColor.AQUA+"Mana: "+spell.mana+" / Cooldown: "+spell.cooldown+"s"));
        }
        var lore=new ArrayList<>(meta.getLore());
        lore.add(ChatColor.GREEN+"เอฟเฟกต์ต่อเนื่องอัตโนมัติ · ไม่เสียมานาเพิ่ม");
        meta.setLore(lore);
        meta.setUnbreakable(false);
        // The vanilla model is a safe fallback when a client has no resource pack.
        var modelData=meta.getCustomModelDataComponent();
        modelData.setStrings(List.of("advance_magic:"+spell.id()));meta.setCustomModelDataComponent(modelData);
        meta.setItemModel(new NamespacedKey("advance_magic",spell.id()));
        meta.getPersistentDataContainer().set(wandKey,PersistentDataType.STRING,spell.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("advance_magic","wand"),PersistentDataType.STRING,spell.id());
        meta.getPersistentDataContainer().set(castsKey,PersistentDataType.INTEGER,0);
        meta.getPersistentDataContainer().set(durabilityKey,PersistentDataType.INTEGER,BASE_USES);
        meta.getPersistentDataContainer().set(durabilityLevelKey,PersistentDataType.INTEGER,0);
        meta.getPersistentDataContainer().set(damageLevelKey,PersistentDataType.INTEGER,0);
        meta.getPersistentDataContainer().set(cooldownLevelKey,PersistentDataType.INTEGER,0);
        item.setItemMeta(meta);refreshLore(item);staffs().migrate(item);return item;
    }
    public Spell spell(ItemStack item) {
        if(item==null||item.getType()!=BASE||!item.hasItemMeta())return null;
        var pdc=item.getItemMeta().getPersistentDataContainer();
        Spell result=null;
        for(NamespacedKey key:List.of(wandKey,new NamespacedKey("advance_magic","wand"))) {
            if(!pdc.has(key))continue;
            String id=pdc.get(key,PersistentDataType.STRING);
            Spell candidate=id==null?null:Spell.parse(id);
            if(candidate==null||(result!=null&&result!=candidate))return null;
            result=candidate;
        }
        return result;
    }
    public int casts(ItemStack item) {
        if(item==null||!item.hasItemMeta())return 0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(castsKey,PersistentDataType.INTEGER,0);
    }
    public enum Upgrade { DURABILITY("wand_repair"), DAMAGE("wand_damage"), COOLDOWN("wand_cooldown"); final String id; Upgrade(String id){this.id=id;} }
    public Upgrade upgrade(ItemStack item) {
        if(item==null||!item.hasItemMeta())return null;
        if(StaffGemService.identify(item)==StaffGemService.Gem.MAX)return Upgrade.DURABILITY;
        String id=item.getItemMeta().getPersistentDataContainer().get(upgradeItemKey,PersistentDataType.STRING);
        for(Upgrade upgrade:Upgrade.values())if(upgrade.id.equals(id))return upgrade;
        return null;
    }
    private int level(ItemStack item,NamespacedKey key) {
        if(item==null||!item.hasItemMeta())return 0;
        return Math.clamp(item.getItemMeta().getPersistentDataContainer().getOrDefault(key,PersistentDataType.INTEGER,0),0,MAX_UPGRADE_LEVEL);
    }
    public int durabilityLevel(ItemStack item){return level(item,durabilityLevelKey);}
    private boolean isFlyingStaff(ItemStack item){
        return item!=null&&item.getType()==Material.BLAZE_ROD&&item.hasItemMeta()
                &&item.getItemMeta().getPersistentDataContainer().has(flyingStaffKey,PersistentDataType.BYTE);
    }
    public boolean isDurable(ItemStack item){return spell(item)!=null||staffs().isStaff(item)||isFlyingStaff(item);}
    public int damageLevel(ItemStack item){return level(item,damageLevelKey);}
    public int cooldownLevel(ItemStack item){return level(item,cooldownLevelKey);}
    public int maxUses(ItemStack item){return baseUses(item)+(staffs().isStaff(item)?StaffGemService.capacityBonus(item):durabilityLevel(item)*usesPerUpgrade(item));}
    // Elemental spells recast every few seconds, so staffs need far more uses than the old 30-cast wands.
    private int baseUses(ItemStack item){
        return staffs().isStaff(item)?Math.clamp(plugin.getConfig().getInt("elemental-system.staff-durability.base-uses",300),1,100000):BASE_USES;
    }
    private int usesPerUpgrade(ItemStack item){
        return staffs().isStaff(item)?Math.clamp(plugin.getConfig().getInt("elemental-system.staff-durability.uses-per-repair-core",50),0,100000):USES_PER_REPAIR;
    }
    public double cooldownScale(ItemStack item){return staffs().isStaff(item)?1-StaffGemService.cooldownPercent(item)/100.0:1.0-cooldownLevel(item)*.03;}
    public int usesLeft(ItemStack item){
        if(item==null||!item.hasItemMeta())return 0;
        int left=Math.max(0,item.getItemMeta().getPersistentDataContainer().getOrDefault(durabilityKey,PersistentDataType.INTEGER,maxUses(item)));
        return staffs().isStaff(item)?Math.min(left,maxUses(item)):left;
    }
    public double damageMultiplier(ItemStack item){return staffs().isStaff(item)?1+StaffGemService.damagePercent(item)/100.0:1.0+damageLevel(item)*.03;}
    /** Restores plugin uses only; preserves type, upgrades, mastery and all other metadata. */
    public boolean restore(ItemStack item){
        if(!isDurable(item)||item.getAmount()!=1||usesLeft(item)>=maxUses(item))return false;
        var meta=item.getItemMeta();
        meta.getPersistentDataContainer().set(durabilityKey,PersistentDataType.INTEGER,maxUses(item));
        item.setItemMeta(meta);refreshLore(item);return true;
    }
    public boolean consumeUse(ItemStack item){
        if(!isDurable(item)||usesLeft(item)<=0||(isFlyingStaff(item)&&item.getAmount()!=1))return false;
        var meta=item.getItemMeta();meta.getPersistentDataContainer().set(durabilityKey,PersistentDataType.INTEGER,usesLeft(item)-1);item.setItemMeta(meta);refreshLore(item);return true;
    }
    private boolean ensureWandState(ItemStack item) {
        if(!isDurable(item)||!item.hasItemMeta())return false;
        var meta=item.getItemMeta();var data=meta.getPersistentDataContainer();boolean changed=false;
        if(!data.has(durabilityKey,PersistentDataType.INTEGER)){data.set(durabilityKey,PersistentDataType.INTEGER,baseUses(item));changed=true;}
        if(staffs().isStaff(item)){
            for(NamespacedKey old:List.of(durabilityLevelKey,damageLevelKey,cooldownLevelKey))if(data.has(old)){data.remove(old);changed=true;}
        }else if(!data.has(durabilityLevelKey,PersistentDataType.INTEGER)){data.set(durabilityLevelKey,PersistentDataType.INTEGER,0);changed=true;}
        if(isFlyingStaff(item)){
            if(!meta.hasMaxStackSize()||meta.getMaxStackSize()!=1){meta.setMaxStackSize(1);changed=true;}
            if(data.has(damageLevelKey)){data.remove(damageLevelKey);changed=true;}
            if(data.has(cooldownLevelKey)){data.remove(cooldownLevelKey);changed=true;}
        }else if(!staffs().isStaff(item)){
            if(!data.has(damageLevelKey,PersistentDataType.INTEGER)){data.set(damageLevelKey,PersistentDataType.INTEGER,0);changed=true;}
            if(!data.has(cooldownLevelKey,PersistentDataType.INTEGER)){data.set(cooldownLevelKey,PersistentDataType.INTEGER,0);changed=true;}
        }
        if(meta.hasEnchant(org.bukkit.enchantments.Enchantment.MENDING)){meta.removeEnchant(org.bukkit.enchantments.Enchantment.MENDING);changed=true;}
        if(changed){item.setItemMeta(meta);refreshLore(item);} return changed;
    }
    private ElementalStaffService staffs(){return ((com.example.advancemagic.AdvanceMagicPlugin)plugin).staffs();}
    private void refreshLore(ItemStack item) {
        if(staffs().isStaff(item)){
            // Staff lore is owned by the loadout writer, which also renders durability and cores.
            var spells=((com.example.advancemagic.AdvanceMagicPlugin)plugin).staffSpells();
            if(spells!=null)spells.refresh(item);
            return;
        }
        if(isFlyingStaff(item)){
            var meta=item.getItemMeta();
            boolean tandem=meta.getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin,"flying_staff_tier"),PersistentDataType.INTEGER,1)==2;
            var lore=new ArrayList<String>();
            if(tandem){
                lore.add(ChatColor.GOLD+"ระดับ 2 · ผู้ขับ 1 คน + ผู้ซ้อน 1 คน");
                lore.add(ChatColor.GRAY+"ความเร็ว 50% · ใช้มานา 150% ของรุ่นปกติ");
                lore.add(ChatColor.GRAY+"คลิกไม้เท้าเพื่อขึ้นซ้อนหลังผู้ขับขึ้นขี่");
            }
            lore.add(ChatColor.AQUA+"Durability: "+usesLeft(item)+" / "+maxUses(item)+" uses");
            lore.add(ChatColor.LIGHT_PURPLE+"Upgrade: Durability "+durabilityLevel(item)+"/10");
            meta.setLore(lore);
            item.setItemMeta(meta);return;
        }
        Spell spell=spell(item);if(spell==null||!item.hasItemMeta())return;
        var meta=item.getItemMeta();
        meta.setLore(wandLore(item,spell));
        item.setItemMeta(meta);
    }
    private List<String> wandLore(ItemStack item,Spell spell) {
        int durability=durabilityLevel(item),damage=damageLevel(item),cooldown=cooldownLevel(item);
        var lore=new ArrayList<>(List.of(ChatColor.GRAY+"Use to cast (no sneaking required)",
                ChatColor.AQUA+"Mana: "+spell.mana+" / Cooldown: "+String.format(Locale.ROOT,"%.1f",getEffectiveCooldown(item,spell))+"s",
                ChatColor.AQUA+"Durability: "+usesLeft(item)+" / "+maxUses(item)+" uses",ChatColor.RED+"Damage: +"+(damage*3)+"%  "+ChatColor.YELLOW+"Cooldown: -"+(cooldown*3)+"%",
                ChatColor.LIGHT_PURPLE+"Upgrade: Durability "+durability+"/10 · Damage "+damage+"/10 · Cooldown "+cooldown+"/10"));
        if(spell.isLegendary())lore.add(0,ChatColor.GOLD+"[MYTHIC]");
        if(casts(item)>0)lore.add(ChatColor.LIGHT_PURPLE+"Mastery: "+casts(item)+" main casts");
        return lore;
    }
    public double getEffectiveCooldown(ItemStack item,Spell spell) {
        if(spell==null)return 1.0;
        int count=casts(item);
        double reduction=(count/5)*5.0;
        double minCooldown=Math.max(1.0,spell.cooldown*0.2);
        return Math.max(minCooldown,(double)spell.cooldown-reduction)*(1.0-cooldownLevel(item)*.03);
    }
    public int recordCast(ItemStack item,Spell spell) {
        if(item==null||!item.hasItemMeta()||spell==null)return 0;
        int count=casts(item)+1;
        var meta=item.getItemMeta();
        meta.getPersistentDataContainer().set(castsKey,PersistentDataType.INTEGER,count);
        item.setItemMeta(meta);
        refreshLore(item);
        return count;
    }
    /** Remove every historical wand recipe, including already-discovered book entries. */
    private Set<NamespacedKey> retiredRecipeKeys(){
        Set<NamespacedKey> keys=new HashSet<>();
        for(String suffix:List.of("ni_c","ni_b","ns_c","ns_b"))keys.add(new NamespacedKey(plugin,"wand_gen_"+suffix));
        for(Spell spell:Spell.values())for(String suffix:List.of("","_bottom","_ni_c","_ni_b","_ns_c","_ns_b"))keys.add(new NamespacedKey(plugin,spell.id()+suffix));
        return keys;
    }
    public void register(){recipes.clear();retiredRecipeKeys().forEach(Bukkit::removeRecipe);}
    public void discover(Player p){p.undiscoverRecipes(retiredRecipeKeys());}
    public boolean migrate(ItemStack item) {
        if(isFlyingStaff(item)){
            var before=item.getItemMeta();boolean changed=ensureWandState(item);refreshLore(item);
            return changed||!Objects.equals(before,item.getItemMeta());
        }
        if(staffs().isStaff(item))return false;
        if(staffs().migrate(item))return true;
        Spell core=coreSpell(item);
        if(core!=null) {
            var meta=item.getItemMeta();boolean changed=false;
            String displayName=coreDisplayName(core);
            if(!Objects.equals(meta.getDisplayName(),displayName)){meta.setDisplayName(displayName);changed=true;}
            List<String> lore=coreLore(core);
            if(!Objects.equals(meta.getLore(),lore)){meta.setLore(lore);changed=true;}
            if(meta.getItemModel()!=null&&retiredCoreSelector(meta.getItemModel().toString())){
                meta.setItemModel(null);changed=true;
            }
            var data=meta.getCustomModelDataComponent();
            List<String> selectors=data.getStrings().stream().filter(selector->!retiredCoreSelector(selector)).toList();
            if(!selectors.equals(data.getStrings())){
                data.setStrings(selectors);
                meta.setCustomModelDataComponent(selectors.isEmpty()&&data.getFloats().isEmpty()
                        &&data.getFlags().isEmpty()&&data.getColors().isEmpty()?null:data);
                changed=true;
            }
            if(changed)item.setItemMeta(meta);
            return changed;
        }
        Upgrade upgrade=upgrade(item);
        if(upgrade!=null){
            var meta=item.getItemMeta();var data=meta.getCustomModelDataComponent();
            String key="advance_magic:"+upgrade.id;
            if(new NamespacedKey("advance_magic",upgrade.id).equals(meta.getItemModel())&&data.getStrings().equals(List.of(key)))return false;
            meta.setItemModel(new NamespacedKey("advance_magic",upgrade.id));data.setStrings(List.of(key));meta.setCustomModelDataComponent(data);
            item.setItemMeta(meta);return true;
        }
        return false;
    }

    public void migrate(Inventory inventory) {
        for(int slot=0;slot<inventory.getSize();slot++) {
            ItemStack item=inventory.getItem(slot);if(migrate(item))inventory.setItem(slot,item);
        }
    }
    private ItemStack upgraded(ItemStack wand,ItemStack catalyst) {
        if(!isDurable(wand)||staffs().isStaff(wand))return null;
        Upgrade type=upgrade(catalyst);if(type==null)return null;
        if(isFlyingStaff(wand)&&(type!=Upgrade.DURABILITY||wand.getAmount()!=1))return null;
        NamespacedKey key=switch(type){case DURABILITY->durabilityLevelKey;case DAMAGE->damageLevelKey;case COOLDOWN->cooldownLevelKey;};
        int current=level(wand,key);if(current>=MAX_UPGRADE_LEVEL)return null;
        ItemStack result=wand.clone();var meta=result.getItemMeta();meta.getPersistentDataContainer().set(key,PersistentDataType.INTEGER,current+1);
        if(type==Upgrade.DURABILITY)meta.getPersistentDataContainer().set(durabilityKey,PersistentDataType.INTEGER,
                Math.min(baseUses(wand)+(current+1)*usesPerUpgrade(wand),usesLeft(wand)+usesPerUpgrade(wand)));
        result.setItemMeta(meta);refreshLore(result);return result;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void anvil(PrepareAnvilEvent event) {
        if(isDurable(event.getInventory().getFirstItem())
                || isDurable(event.getInventory().getSecondItem()))event.setResult(null);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void applyByClick(InventoryClickEvent event) {
        if(event.getClick()!=ClickType.LEFT&&event.getClick()!=ClickType.RIGHT)return;
        if(!(event.getWhoClicked() instanceof Player player)||!(event.getClickedInventory() instanceof org.bukkit.inventory.PlayerInventory))return;
        ItemStack cursor=event.getCursor(),slot=event.getCurrentItem();
        boolean coreOnCursor=upgrade(cursor)!=null&&isDurable(slot);
        boolean wandOnCursor=isDurable(cursor)&&upgrade(slot)!=null;
        if(!coreOnCursor&&!wandOnCursor)return;
        ItemStack target=coreOnCursor?slot:cursor,catalyst=coreOnCursor?cursor:slot;
        if(staffs().isStaff(target)){
            event.setCancelled(true);player.sendMessage(ChatColor.YELLOW+"คทาใช้ Gem 2 ช่องแล้ว · ใส่หรือถอดที่ /spell");return;
        }
        if(isFlyingStaff(target)&&(upgrade(catalyst)!=Upgrade.DURABILITY||target.getAmount()!=1)){
            event.setCancelled(true);
            player.sendMessage(ChatColor.RED+"ไม้เท้าบินใช้ได้เฉพาะ Wand Repair Core และต้องแยกไม้เท้าทีละอัน");return;
        }
        ItemStack result=coreOnCursor?upgraded(slot,cursor):upgraded(cursor,slot);
        event.setCancelled(true);
        if(result==null){player.sendMessage(ChatColor.RED+"This wand upgrade is already at level 10.");return;}
        if(coreOnCursor){
            event.setCurrentItem(result);
            if(cursor.getAmount()==1)event.setCursor(new ItemStack(Material.AIR));
            else{ItemStack remaining=cursor.clone();remaining.setAmount(cursor.getAmount()-1);event.setCursor(remaining);}
        }else{
            event.setCursor(result);
            if(slot.getAmount()==1)event.setCurrentItem(new ItemStack(Material.AIR));
            else{ItemStack remaining=slot.clone();remaining.setAmount(slot.getAmount()-1);event.setCurrentItem(remaining);}
        }
        player.sendMessage(ChatColor.GREEN+"Wand upgraded.");
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void enchant(PrepareItemEnchantEvent event){if(isDurable(event.getItem()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void enchantResult(EnchantItemEvent event){if(isDurable(event.getItem()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void mend(PlayerItemMendEvent event){if(isDurable(event.getItem()))event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST)
    public void smith(PrepareSmithingEvent event){
        if(isDurable(event.getInventory().getItem(0))||isDurable(event.getInventory().getItem(1))||isDurable(event.getInventory().getItem(2)))event.setResult(null);
    }
    public void migrateEntity(org.bukkit.entity.Entity entity) {
        if(entity instanceof org.bukkit.entity.Item dropped) {ItemStack item=dropped.getItemStack();if(migrate(item))dropped.setItemStack(item);}
        if(entity instanceof org.bukkit.entity.ItemFrame frame) {ItemStack item=frame.getItem();if(migrate(item))frame.setItem(item);}
    }
    @EventHandler(priority=EventPriority.LOWEST) public void open(InventoryOpenEvent event){migrate(event.getInventory());migrate(event.getPlayer().getInventory());}
    @EventHandler(priority=EventPriority.LOWEST) public void pickup(EntityPickupItemEvent event){migrateEntity(event.getItem());}
    @EventHandler(priority=EventPriority.LOWEST) public void spawn(ItemSpawnEvent event){migrateEntity(event.getEntity());}
    @EventHandler public void load(EntitiesLoadEvent event){event.getEntities().forEach(this::migrateEntity);}
    private boolean ours(Recipe recipe) { return recipe instanceof Keyed k&&recipes.containsKey(k.getKey()); }

    public boolean containsCore(ItemStack[] matrix) {
        if(matrix==null) return false;
        for(ItemStack it : matrix) {
            if(it!=null && coreSpell(it)!=null) return true;
        }
        return false;
    }

    /** Retained API: crafting is deferred until elemental skill/loadout design is complete. */
    public boolean canCraft(HumanEntity player){return false;}
    public Spell craftingSpell(ItemStack[] matrix){return null;}
    private boolean retiredCraftIngredients(ItemStack[] matrix){
        if(containsCore(matrix))return true;
        if(matrix!=null)for(ItemStack item:matrix)if(staffs().isRetiredOrStaff(item))return true;
        return false;
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent e){
        if(retiredCraftIngredients(e.getInventory().getMatrix())||ours(e.getRecipe()))e.getInventory().setResult(null);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void craft(CraftItemEvent e){
        if(retiredCraftIngredients(e.getInventory().getMatrix())||ours(e.getRecipe()))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onResultClick(InventoryClickEvent e){
        if(e.getInventory() instanceof CraftingInventory inv&&e.getSlotType()==InventoryType.SlotType.RESULT
                &&(retiredCraftIngredients(inv.getMatrix())||ours(inv.getRecipe())))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void automatic(org.bukkit.event.block.CrafterCraftEvent e){
        if(e.getBlock().getState() instanceof org.bukkit.block.Crafter crafter
                &&(retiredCraftIngredients(crafter.getInventory().getContents())||ours(e.getRecipe())))e.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
    public void onWandDamage(org.bukkit.event.player.PlayerItemDamageEvent event) {
        if(isDurable(event.getItem())) {
            event.setCancelled(true);
        }
    }

    public void close() {
        recipes.keySet().forEach(Bukkit::removeRecipe);
        recipes.clear();
    }
}
