package com.example.advancemagic.item;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.spell.LegendaryWeaponVisuals;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.util.*;

/** Cosmetic prototype items. VFX is native pack animation; no following entities. */
public final class LegendaryWeaponService implements Listener, AutoCloseable {
    public enum Weapon {
        EXCALIBUR(Material.NETHERITE_SWORD,"Excalibur",NamedTextColor.GOLD,0x82C9FF,"ดาบศักดิ์สิทธิ์แห่งราชัน"),
        LONGINUS(Material.NETHERITE_SWORD,"Longinus",NamedTextColor.GOLD,0xFFB84E,"หอกศักดิ์สิทธิ์ผูกผ้าแดง"),
        TYRFING(Material.NETHERITE_SWORD,"Tyrfing",NamedTextColor.RED,0xFF2753,"ดาบต้องสาป อักขระกลืนวิญญาณ"),
        MJOLNIR(Material.MACE,"Mjolnir",NamedTextColor.AQUA,0x37C9FF,"ค้อนอัสนีแห่งทวยเทพ"),
        VAJRA(Material.NETHERITE_SWORD,"Vajra",NamedTextColor.AQUA,0x2CF4F2,"วัชระ อัญมณีสายฟ้าศักดิ์สิทธิ์");
        public final Material material;
        public final String title,description;
        public final NamedTextColor color;
        public final int rgb;
        Weapon(Material material,String title,NamedTextColor color,int rgb,String description){
            this.material=material;this.title=title;this.color=color;this.rgb=rgb;this.description=description;
        }
        public String id(){return name().toLowerCase(Locale.ROOT);}
        public NamespacedKey model(){return new NamespacedKey("advance_magic","legendary_"+id());}
        public static Weapon parse(String id){
            if(id==null)return null;
            if(id.equalsIgnoreCase("mjonir"))return MJOLNIR;
            for(Weapon weapon:values())if(weapon.id().equalsIgnoreCase(id))return weapon;
            return null;
        }
    }
    private record Held(Weapon main,Weapon off) {}
    private final AdvanceMagicPlugin plugin;
    private final NamespacedKey key;
    private final Map<UUID,Held> held=new HashMap<>();
    private final Map<UUID,Integer> lastEquip=new HashMap<>();
    private final LonginusProjectileService projectiles;
    private final LegendaryCombatService combat;
    public LegendaryCombatService combat(){return combat;}
    private int ticks;
    public LegendaryWeaponService(AdvanceMagicPlugin plugin){this.plugin=plugin;key=new NamespacedKey(plugin,"legendary_weapon");
        projectiles=null; // Longinus is now strictly a melee weapon; no projectile bridge is started.
        combat=new LegendaryCombatService(plugin,this);
        Bukkit.getPluginManager().registerEvents(combat,plugin);
        Bukkit.getPluginManager().registerEvents(new LegendaryEnchanting(this),plugin);
        Bukkit.getScheduler().runTaskTimer(plugin,combat::tick,1,1);
        for(Player p:Bukkit.getOnlinePlayers()){migrate(p.getInventory());migrate(p.getEnderChest());}
        for(World w:Bukkit.getWorlds())for(var e:w.getEntities())migrateEntity(e);
    }
    public LonginusProjectileService projectiles(){return projectiles;}

    public ItemStack create(Weapon weapon){
        ItemStack item=new ItemStack(weapon.material);
        var meta=item.getItemMeta();
        meta.displayName(Component.text(weapon.title,weapon.color).decoration(TextDecoration.ITALIC,false));
        meta.setItemModel(weapon.model());meta.setUnbreakable(true);meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.getPersistentDataContainer().set(key,PersistentDataType.STRING,weapon.id());
        item.setItemMeta(meta);
        configureCombat(item,weapon);
        return item;
    }

    public Weapon identify(ItemStack item){
        if(item==null||!item.hasItemMeta())return null;
        var meta=item.getItemMeta();
        Weapon weapon=Weapon.parse(meta.getPersistentDataContainer().get(key,PersistentDataType.STRING));
        return weapon!=null&&item.getType()==weapon.material&&weapon.model().equals(meta.getItemModel())?weapon:null;
    }
    private List<Component> tooltip(){return List.of(Component.text("ย่อแล้วตีเพื่อใช้สกิล",NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false));}
    private void configureCombat(ItemStack item,Weapon weapon){
        var meta=item.getItemMeta();
        meta.lore(tooltip());meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        meta.setAttributeModifiers((weapon==Weapon.LONGINUS?Material.TRIDENT:weapon.material).getDefaultAttributeModifiers(EquipmentSlot.HAND));
        meta.removeAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        meta.addAttributeModifier(org.bukkit.attribute.Attribute.ATTACK_DAMAGE,new org.bukkit.attribute.AttributeModifier(
            new NamespacedKey(plugin,"legendary_damage"),combat.base(weapon)-1,org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.MAINHAND));
        if(weapon==Weapon.LONGINUS)meta.addAttributeModifier(org.bukkit.attribute.Attribute.ENTITY_INTERACTION_RANGE,
            new org.bukkit.attribute.AttributeModifier(new NamespacedKey(plugin,"longinus_reach"),4,
                org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.MAINHAND));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin,"legendary_combat_version"),PersistentDataType.INTEGER,1);
        item.setItemMeta(meta);
    }
    /** Upgrade only authenticated legacy weapons, retaining names, enchants and PDC. */
    public boolean migrate(ItemStack item){
        if(item==null||!item.hasItemMeta())return false;
        var meta=item.getItemMeta();
        Weapon weapon=Weapon.parse(meta.getPersistentDataContainer().get(key,PersistentDataType.STRING));
        if(weapon==null||!weapon.model().equals(meta.getItemModel()))return false;
        boolean legacy=(weapon==Weapon.MJOLNIR&&item.getType()==Material.NETHERITE_AXE)||
            (weapon==Weapon.LONGINUS&&item.getType()==Material.TRIDENT)||(weapon==Weapon.VAJRA&&item.getType()==Material.BLAZE_ROD);
        if(item.getType()!=weapon.material&&!legacy)return false;
        var damage=meta.getAttributeModifiers(org.bukkit.attribute.Attribute.ATTACK_DAMAGE);
        boolean outdated=damage==null||damage.stream().noneMatch(m->m.getKey().equals(new NamespacedKey(plugin,"legendary_damage"))&&Math.abs(m.getAmount()-(combat.base(weapon)-1))<.0001);
        if(legacy||outdated||meta.getPersistentDataContainer().getOrDefault(new NamespacedKey(plugin,"legendary_combat_version"),PersistentDataType.INTEGER,0)!=1){
            item.setType(weapon.material);configureCombat(item,weapon);return true;
        }
        if(!tooltip().equals(meta.lore())||!meta.hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)){
            meta.lore(tooltip());meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);item.setItemMeta(meta);return true;
        }
        return false;
    }
    /** Bedrock does not target distant entities with a Java reach attribute.
     * Resolve a distant swing on the server, then use native attack/events/cooldown. */
    public boolean extendedStrike(Player player){
        if(player.isDead()||player.getGameMode()==GameMode.SPECTATOR||
            identify(player.getInventory().getItemInMainHand())!=Weapon.LONGINUS||player.getAttackCooldown()<.9f)return false;
        var eye=player.getEyeLocation();
        var hit=player.getWorld().rayTrace(eye,eye.getDirection(),7,FluidCollisionMode.NEVER,true,0,
            entity->entity!=player&&entity instanceof org.bukkit.entity.LivingEntity&&entity.isValid()&&!entity.isDead());
        if(hit==null||hit.getHitEntity()==null||hit.getHitPosition().distanceSquared(eye.toVector())<=9)return false;
        player.attack(hit.getHitEntity());return true;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void swing(org.bukkit.event.player.PlayerAnimationEvent event){
        if(event.getAnimationType()!=org.bukkit.event.player.PlayerAnimationType.ARM_SWING)return;
        var weapon=identify(event.getPlayer().getInventory().getItemInMainHand());if(weapon==null)return;
        if(combat.charging(event.getPlayer()))return;
        if(event.getPlayer().isSneaking()&&combat.activate(event.getPlayer(),weapon))return;
        if(weapon!=Weapon.LONGINUS)return;
        // Geyser sends air hits as Punch on 26.3. Paper resets attack strength
        // immediately after this event. Deferring by one tick always observes an
        // empty cooldown, so distant Bedrock hits must resolve inside the event.
        extendedStrike(event.getPlayer());
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void reachLimit(org.bukkit.event.entity.EntityDamageByEntityEvent event){
        if(event.getCause()!=org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK&&event.getCause()!=org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)return;
        if(!(event.getDamager() instanceof Player player)||
            identify(player.getInventory().getItemInMainHand())!=Weapon.LONGINUS)return;
        var point=player.getEyeLocation().toVector();var box=event.getEntity().getBoundingBox();
        var nearest=new org.bukkit.util.Vector(Math.clamp(point.getX(),box.getMinX(),box.getMaxX()),
            Math.clamp(point.getY(),box.getMinY(),box.getMaxY()),Math.clamp(point.getZ(),box.getMinZ(),box.getMaxZ()));
        if(point.distanceSquared(nearest)>49.000001)event.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void noThrow(org.bukkit.event.entity.ProjectileLaunchEvent event){
        if(event.getEntity() instanceof org.bukkit.entity.Trident spear){
            var item=spear.getItemStack();migrate(item);
            if(identify(item)==Weapon.LONGINUS)event.setCancelled(true);
        }
    }
    public void migrate(Inventory inventory){
        for(int i=0;i<inventory.getSize();i++){var item=inventory.getItem(i);if(migrate(item))inventory.setItem(i,item);}
    }
    private void migrateEntity(org.bukkit.entity.Entity entity){
        if(entity instanceof org.bukkit.entity.Item drop){var item=drop.getItemStack();if(migrate(item))drop.setItemStack(item);}
    }
    @EventHandler public void join(org.bukkit.event.player.PlayerJoinEvent e){migrate(e.getPlayer().getInventory());migrate(e.getPlayer().getEnderChest());}
    @EventHandler public void opened(InventoryOpenEvent e){migrate(e.getInventory());migrate(e.getPlayer().getInventory());}
    @EventHandler public void selected(org.bukkit.event.player.PlayerItemHeldEvent e){migrate(e.getPlayer().getInventory());}
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void pickup(org.bukkit.event.entity.EntityPickupItemEvent e){migrateEntity(e.getItem());}
    @EventHandler public void loaded(org.bukkit.event.world.EntitiesLoadEvent e){for(var entity:e.getEntities())migrateEntity(entity);}
    public boolean holding(Player player,Weapon weapon){
        return identify(player.getInventory().getItemInMainHand())==weapon||identify(player.getInventory().getItemInOffHand())==weapon;
    }

    /** Called every four server ticks. Client textures keep animating independently. */
    public void tick(){
        ticks+=4;
        if(!plugin.getConfig().getBoolean("legendary-weapons.enabled",true)){held.clear();lastEquip.clear();return;}
        for(Player player:Bukkit.getOnlinePlayers()){
            UUID id=player.getUniqueId();
            if(player.isDead()||player.getGameMode()==GameMode.SPECTATOR){held.remove(id);continue;}
            var inventory=player.getInventory();
            var main=inventory.getItemInMainHand();if(migrate(main))inventory.setItemInMainHand(main);
            var off=inventory.getItemInOffHand();if(migrate(off))inventory.setItemInOffHand(off);
            Held now=new Held(identify(player.getInventory().getItemInMainHand()),identify(player.getInventory().getItemInOffHand()));
            Held old=held.put(id,now);
            if(now.main==null&&now.off==null){held.remove(id);continue;}
            Weapon primary=now.main!=null?now.main:now.off;
            if(!now.equals(old)&&ticks-lastEquip.getOrDefault(id,-40)>=40){
                lastEquip.put(id,ticks);sound(player,primary,false);
                if(plugin.getConfig().getBoolean("legendary-weapons.equip-sigil",true))
                    LegendaryWeaponVisuals.equip(plugin,player,primary,()->holding(player,primary));
            }
            if(ticks%240==0)sound(player,primary,true);
        }
    }
    private void sound(Player p,Weapon w,boolean ambient){
        if(!plugin.getConfig().getBoolean("legendary-weapons.sounds",true))return;
        float volume=(float)Math.max(0,Math.min(1,plugin.getConfig().getDouble("legendary-weapons.volume",.45)));
        if(volume==0)return;
        Sound tone=switch(w){
            case EXCALIBUR -> Sound.BLOCK_BEACON_ACTIVATE;
            case LONGINUS -> Sound.BLOCK_AMETHYST_BLOCK_RESONATE;
            case TYRFING -> Sound.BLOCK_RESPAWN_ANCHOR_CHARGE;
            case MJOLNIR -> Sound.ITEM_TRIDENT_THUNDER;
            case VAJRA -> Sound.BLOCK_BEACON_POWER_SELECT;
        };
        // Holder-only resonance; it does not broadcast thunder across the world.
        p.playSound(p.getLocation(),ambient?Sound.BLOCK_AMETHYST_BLOCK_CHIME:tone,SoundCategory.PLAYERS,
            volume*(ambient?.18f:1f),w==Weapon.TYRFING?.65f:w==Weapon.MJOLNIR?.8f:1.15f);
        if(!ambient)p.playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,SoundCategory.PLAYERS,volume*.5f,1.5f);
    }

    private static final class Menu implements InventoryHolder {
        final Inventory inventory=Bukkit.createInventory(this,9,"Legendary Weapons");
        public Inventory getInventory(){return inventory;}
    }
    public void open(Player player){
        if(!player.hasPermission("advance-magic.admin"))return;
        Menu menu=new Menu();
        for(Weapon weapon:Weapon.values())menu.inventory.setItem(2+weapon.ordinal(),create(weapon));
        player.openInventory(menu.inventory);
    }
    private boolean give(Player player,Weapon weapon){
        if(player.getInventory().firstEmpty()<0){player.sendMessage(ChatColor.RED+"กระเป๋าเต็ม กรุณาเว้นช่องว่าง");return false;}
        player.getInventory().addItem(create(weapon));return true;
    }
    public boolean command(CommandSender sender,String[] args){
        if(!sender.hasPermission("advance-magic.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}
        if(args.length==1){
            if(sender instanceof Player player)open(player);
            else sender.sendMessage("/magic weapon give <excalibur|longinus|tyrfing|mjolnir|vajra|all> <player>");
            return true;
        }
        if(args.length<3||args.length>4||!args[1].equalsIgnoreCase("give")){
            sender.sendMessage("/magic weapon give <excalibur|longinus|tyrfing|mjolnir|vajra|all> [player]");return true;
        }
        Player target=args.length==4&&!args[3].equalsIgnoreCase("@s")?onlinePlayer(args[3]):sender instanceof Player p?p:null;
        Weapon weapon=Weapon.parse(args[2]);boolean all=args[2].equalsIgnoreCase("all");
        if(target==null||(!all&&weapon==null)){sender.sendMessage(ChatColor.RED+"Unknown weapon or online player.");return true;}
        int count=0;
        for(Weapon entry:all?Weapon.values():new Weapon[]{weapon})if(give(target,entry))count++;
        sender.sendMessage(ChatColor.GREEN+"มอบอาวุธตำนาน "+count+" ชิ้นให้ "+target.getName());return true;
    }
    private Player onlinePlayer(String name){
        Player exact=Bukkit.getPlayerExact(name);
        if(exact!=null)return exact;
        for(Player player:Bukkit.getOnlinePlayers())if(player.getName().equalsIgnoreCase(name)||
                player.getName().equalsIgnoreCase("."+name))return player;
        return null;
    }
    public List<String> complete(CommandSender sender,String[] args){
        if(!sender.hasPermission("advance-magic.admin"))return List.of();
        List<String> values=new ArrayList<>();
        if(args.length==2)values.add("give");
        if(args.length==3){values.add("all");for(Weapon w:Weapon.values())values.add(w.id());}
        if(args.length==4){values.add("@s");for(Player p:Bukkit.getOnlinePlayers())values.add(p.getName());}
        String prefix=args[args.length-1].toLowerCase(Locale.ROOT);
        return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void click(InventoryClickEvent event){
        if(!(event.getView().getTopInventory().getHolder() instanceof Menu))return;
        event.setCancelled(true);
        if(!(event.getWhoClicked() instanceof Player p)||!p.hasPermission("advance-magic.admin")||!event.isLeftClick()||event.isShiftClick())return;
        int index=event.getRawSlot()-2;
        if(index>=0&&index<Weapon.values().length)give(p,Weapon.values()[index]);
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void drag(InventoryDragEvent event){
        if(event.getView().getTopInventory().getHolder() instanceof Menu)event.setCancelled(true);
    }
    @EventHandler public void quit(PlayerQuitEvent event){UUID id=event.getPlayer().getUniqueId();held.remove(id);lastEquip.remove(id);}
    @Override public void close(){held.clear();lastEquip.clear();combat.close();if(projectiles!=null)projectiles.close();}
}
