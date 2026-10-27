import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.item.*;
import com.example.advancemagic.item.ArmorSetService.*;
import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.damage.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import java.net.InetSocketAddress;
import java.util.*;

/** Real Paper items, damage, persistence, custom upgrades and Sanctuary receipts. */
public final class ArmorSetsProbe extends BossArmorWearProbe {
    AdvanceMagicPlugin magic;ArmorSetService sets;ArmorCombatService combat;
    boolean denyHit,denyHeal;LivingEntity deniedTarget;
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{run();}catch(Throwable error){finish(error);}},20);}
    @EventHandler(priority=EventPriority.HIGHEST) public void denied(EntityDamageEvent event){if(denyHit&&event.getEntity()==deniedTarget)event.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void deniedHeal(EntityRegainHealthEvent event){if(denyHeal&&event.getEntity()==player)event.setCancelled(true);}
    void equip(ArmorSet set){player.getInventory().setArmorContents(new ItemStack[4]);for(Piece p:Piece.values())player.getInventory().setItem(p.slot,sets.create(set,p));actor.doTick();combat.refresh(player);}
    void healthy(){player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());player.setAbsorptionAmount(0);player.setNoDamageTicks(0);player.setFireTicks(0);player.getActivePotionEffects().forEach(p->player.removePotionEffect(p.getType()));}
    void lethal(){player.setNoDamageTicks(0);player.damage(10000,DamageSource.builder(DamageType.GENERIC).build());}
    NamespacedKey key(String name){return new NamespacedKey(magic,name);}
    double outgoing(LivingEntity target,double amount,DamageType type){
        double before=target.getHealth();target.setNoDamageTicks(0);target.damage(amount,DamageSource.builder(type).withCausingEntity(player).withDirectEntity(player).build());return before-target.getHealth();
    }
    @Override void run()throws Exception{
        garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");sets=new ArmorSetService(magic);combat=magic.armorCombat();
        check(garden.isEnabled()&&magic.isEnabled()&&combat!=null,"both release plugins and set powers boot");
        world=Bukkit.getWorlds().getFirst();world.setDifficulty(Difficulty.NORMAL);world.setTime(18000);world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION,false);
        Location at=world.getSpawnLocation().clone();at.setY(200);world.getChunkAt(at).load();
        for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)world.getBlockAt(at.getBlockX()+x,199,at.getBlockZ()+z).setType(Material.STONE,false);
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();var profile=new GameProfile(UUID.randomUUID(),"ArmorSetsChecks");
        actor=new ServerPlayer(server,level,profile,ClientInformation.createDefault());var connection=new Connection(PacketFlow.SERVERBOUND);connection.channel=new EmbeddedChannel();connection.address=new InetSocketAddress("127.0.0.1",1);
        actor.connection=new ServerGamePacketListenerImpl(server,connection,actor,CommonListenerCookie.createInitial(profile,false)){@Override public boolean hasClientLoaded(){return true;}};
        actor.setPos(at.getX(),200,at.getZ());server.getPlayerList().getPlayers().add(actor);server.getPlayerList().getPlayersByUUID().put(profile.id(),actor);level.addNewPlayer(actor);
        player=actor.getBukkitEntity();player.setGravity(false);player.setInvulnerable(false);actor.getAbilities().invulnerable=false;player.setGameMode(GameMode.SURVIVAL);
        Bukkit.getPluginManager().registerEvents(this,this);
        var plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText();
        for(ArmorSet set:ArmorSet.values())for(Piece piece:Piece.values()){
            ItemStack item=sets.create(set,piece);var meta=item.getItemMeta();
            check(meta.lore().size()==1&&plain.serialize(meta.lore().getFirst()).equals("Combat"),set+" "+piece+" only Combat base lore");
            check(!meta.isUnbreakable()&&meta.getEquippable().isDamageOnHurt(),set+" "+piece+" native durability enabled");
            check(meta.getItemModel().equals(set.model(piece))&&Objects.equals(meta.getEquippable().getModel(),piece==Piece.HELM?null:set.asset()),set+" "+piece+" model and wearable asset preserved");
            for(Attribute attribute:List.of(Attribute.ARMOR,Attribute.ARMOR_TOUGHNESS)){
                double expected=piece.material.getDefaultAttributeModifiers(piece.slot).get(attribute).stream().mapToDouble(a->a.getAmount()).sum()*1.5;
                check(Math.abs(meta.getAttributeModifiers(attribute).stream().mapToDouble(a->a.getAmount()).sum()-expected)<1e-8,set+" "+piece+" "+attribute.getKey()+" at 150% netherite");
            }
            double nativeKnockback=piece.material.getDefaultAttributeModifiers(piece.slot).get(Attribute.KNOCKBACK_RESISTANCE).stream().mapToDouble(a->a.getAmount()).sum();
            check(Math.abs(meta.getAttributeModifiers(Attribute.KNOCKBACK_RESISTANCE).stream().mapToDouble(a->a.getAmount()).sum()-nativeKnockback)<1e-9,set+" "+piece+" native knockback resistance retained");
            check(!sets.migrate(item),"current "+set+" "+piece+" migration is idempotent");
            check(ItemStack.deserializeBytes(item.serializeAsBytes()).isSimilar(item),set+" "+piece+" item codec preserves full metadata");
        }
        ItemStack old=sets.create(ArmorSet.REVENANT,Piece.CHESTPLATE);var meta=old.getItemMeta();
        meta.getPersistentDataContainer().remove(key("armor_combat_version"));meta.setAttributeModifiers(null);meta.setUnbreakable(true);
        var equipment=meta.getEquippable();equipment.setDamageOnHurt(false);meta.setEquippable(equipment);((Damageable)meta).setDamage(77);
        meta.displayName(net.kyori.adventure.text.Component.text("My named chest"));
        meta.lore(List.of(net.kyori.adventure.text.Component.text("เกราะแล็กเกอร์ดำ แกนผลึกโลหิต"),net.kyori.adventure.text.Component.text("ชุดเรเวแนนต์ · 1 / 4 ชิ้น"),net.kyori.adventure.text.Component.text("Keep my upgrade")));
        garden.relics().applyLimitBreakMeta(old.getType(),meta,LimitBreakType.PROTECTION,7);old.setItemMeta(meta);
        check(sets.migrate(old),"legacy armor migrates");meta=old.getItemMeta();
        check(plain.serialize(meta.displayName()).equals("My named chest")&&((Damageable)meta).getDamage()==77,"migration keeps rename and durability damage");
        check(garden.relics().getLimitBreakLevel(old,LimitBreakType.PROTECTION)==7&&meta.getEnchantLevel(Enchantment.PROTECTION)==4,"migration keeps custom plus-seven protection and native enchant");
        check(meta.lore().stream().map(plain::serialize).noneMatch(t->t.contains("1 / 4")||t.contains("แล็กเกอร์"))&&meta.lore().stream().map(plain::serialize).anyMatch(t->t.equals("Keep my upgrade")),"migration strips owned Thai lore and retains upgrade lore");

        equip(ArmorSet.REVENANT);healthy();
        check(Math.abs(player.getAttribute(Attribute.ARMOR).getValue()-30)<1e-8&&Math.abs(player.getAttribute(Attribute.ARMOR_TOUGHNESS).getValue()-18)<1e-8,"worn full set has native armor 30 and toughness 18");
        player.damage(10,DamageSource.builder(DamageType.MOB_ATTACK).build());
        check(Arrays.stream(player.getInventory().getArmorContents()).allMatch(i->((Damageable)i.getItemMeta()).getDamage()>0),"native damage actually wears all four custom pieces");
        equip(ArmorSet.SERAPH);healthy();
        var boots=player.getInventory().getBoots();var bootMeta=(Damageable)boots.getItemMeta();bootMeta.setDamage(boots.getType().getMaxDurability()-1);boots.setItemMeta(bootMeta);
        player.damage(8,DamageSource.builder(DamageType.MOB_ATTACK).build());
        check(player.getInventory().getBoots()==null||player.getInventory().getBoots().isEmpty(),"custom armor actually breaks and leaves its equipped slot");
        check(!combat.fullSeraph(player),"breaking one piece disables the full-set bonus immediately");
        equip(ArmorSet.REVENANT);healthy();
        for(ItemStack item:player.getInventory().getArmorContents()){
            var itemMeta=item.getItemMeta();garden.relics().applyEternityMeta(itemMeta);item.setItemMeta(itemMeta);
        }
        player.damage(8,DamageSource.builder(DamageType.MOB_ATTACK).build());
        check(Arrays.stream(player.getInventory().getArmorContents()).allMatch(i->((Damageable)i.getItemMeta()).getDamage()==0),"custom armor honors existing Eternity durability protection");
        equip(ArmorSet.REVENANT);healthy();
        for(ItemStack item:player.getInventory().getArmorContents()){
            item.addUnsafeEnchantment(Enchantment.PROTECTION,10);var itemMeta=item.getItemMeta();garden.bossEnchants().markGenerated(itemMeta);item.setItemMeta(itemMeta);
        }
        actor.doTick();player.damage(200,DamageSource.builder(DamageType.GENERIC).build());
        check(!player.isDead()&&player.getHealth()>1&&!player.getPersistentDataContainer().has(key("revenant_revives")),"overflow Protection X settles before fatal guard, avoiding a wasted charge");
        // Every individual slot independently grants one charge, retained through replacing its item.
        for(Piece piece:Piece.values()){
            player.getInventory().setArmorContents(new ItemStack[4]);player.getInventory().setItem(piece.slot,sets.create(ArmorSet.REVENANT,piece));healthy();lethal();
            check(!player.isDead()&&player.getHealth()==1,"single "+piece+" denies death without a full set");
            long[] cds=player.getPersistentDataContainer().get(key("revenant_revives"),PersistentDataType.LONG_ARRAY);
            check(cds[piece.ordinal()]>System.currentTimeMillis()+590000,"single "+piece+" receives ten-minute slot cooldown");
            player.getInventory().setItem(piece.slot,sets.create(ArmorSet.REVENANT,piece));
            var event=new EntityDamageEvent(player,EntityDamageEvent.DamageCause.CUSTOM,10000);
            combat.fatal(event);check(!event.isCancelled(),"replacing "+piece+" cannot reset cooldown");
        }
        equip(ArmorSet.REVENANT);player.getPersistentDataContainer().remove(key("revenant_revives"));
        for(int i=0;i<4;i++){healthy();lethal();check(!player.isDead()&&player.getHealth()==1,"full Revenant charge "+(i+1)+" independently denies death");}
        var fifth=new EntityDamageEvent(player,EntityDamageEvent.DamageCause.CUSTOM,10000);combat.fatal(fifth);check(!fifth.isCancelled(),"all four spent charges cannot deny a fifth fatal hit");
        var cds=player.getPersistentDataContainer().get(key("revenant_revives"),PersistentDataType.LONG_ARRAY);cds[2]=System.currentTimeMillis()-1;player.getPersistentDataContainer().set(key("revenant_revives"),PersistentDataType.LONG_ARRAY,cds);
        healthy();lethal();check(player.getHealth()==1,"expired slot charge replenishes independently");
        // Native Totem is available once all armor charges are cooling down.
        player.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));healthy();lethal();
        check(!player.isDead()&&player.getInventory().getItemInOffHand().isEmpty(),"native Totem still works after armor charges are exhausted");healthy();

        equip(ArmorSet.REVENANT);healthy();
        for(String boss:List.of("acheron","phlegethon","cocytus","banshee","malefeasance","styx")){
            check(combat.sanctuaryVictory(player,boss),"first distinct "+boss+" soul awards +2 HP");
            check(!combat.sanctuaryVictory(player,boss),"repeat "+boss+" cannot stack HP");
        }
        check(combat.souls(player)==6&&player.getAttribute(Attribute.MAX_HEALTH).getValue()==32,"six distinct souls cap at +12 HP");
        check(!combat.sanctuaryVictory(player,"seraphon")&&!combat.sanctuaryVictory(player,"zombie"),"other bosses and ordinary mobs cannot grant a seventh soul");
        player.getInventory().setHelmet(null);combat.refresh(player);check(player.getAttribute(Attribute.MAX_HEALTH).getValue()==20&&combat.souls(player)==6,"removing a piece disables HP bonus while retaining progress");
        equip(ArmorSet.REVENANT);combat.refresh(player);combat.refresh(player);check(player.getAttribute(Attribute.MAX_HEALTH).getValue()==32,"reequip restores one HP modifier without stacking");
        player.setHealth(4);combat.confirmedHit(player,10);check(player.getHealth()==9,"virtual boss confirmed 10 damage heals exactly five");
        combat.confirmedHit(player,0);check(player.getHealth()==9,"zero or rejected boss damage cannot heal");denyHeal=true;combat.confirmedHit(player,10);check(player.getHealth()==9,"lifesteal respects cancelled healing");denyHeal=false;
        player.getInventory().setBoots(null);combat.confirmedHit(player,10);check(player.getHealth()==9,"partial Revenant set has no lifesteal");

        equip(ArmorSet.SERAPH);healthy();
        Zombie undead=world.spawn(at.clone().add(3,0,0),Zombie.class,m->{m.setAI(false);m.setSilent(true);m.setGravity(false);m.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);m.setHealth(200);m.getAttribute(Attribute.ARMOR).setBaseValue(0);});
        Cow other=world.spawn(at.clone().add(-3,0,0),Cow.class,m->{m.setAI(false);m.setSilent(true);m.setGravity(false);m.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);m.setHealth(200);});
        check(outgoing(undead,3,DamageType.GENERIC)==12,"Seraph undead damage is exactly x4, never x8");
        check(outgoing(other,3,DamageType.GENERIC)==6,"Seraph other target damage is exactly x2");
        check(outgoing(undead,3,DamageType.INDIRECT_MAGIC)==12&&outgoing(other,3,DamageType.ON_FIRE)==6,"attributed magic and elemental damage receive the same separate multipliers");
        var targeting=new EntityTargetLivingEntityEvent(undead,player,EntityTargetEvent.TargetReason.CLOSEST_PLAYER);Bukkit.getPluginManager().callEvent(targeting);check(targeting.isCancelled(),"undead cannot acquire full Seraph wearer");
        player.getInventory().setHelmet(null);targeting=new EntityTargetLivingEntityEvent(undead,player,EntityTargetEvent.TargetReason.CLOSEST_PLAYER);Bukkit.getPluginManager().callEvent(targeting);check(!targeting.isCancelled(),"partial Seraph has no undead repulsion");
        check(outgoing(undead,3,DamageType.GENERIC)==3&&outgoing(other,3,DamageType.GENERIC)==3,"partial Seraph has no damage bonuses");

        // Existing crafting/upgrade path, not just material-category matching.
        for(ArmorSet set:ArmorSet.values())for(Piece piece:Piece.values()){
            ItemStack item=sets.create(set,piece);
            item.addUnsafeEnchantment(Enchantment.PROTECTION,4);
            ItemStack scroll=garden.relics().createScrollLimitBreak(LimitBreakType.PROTECTION);
            ItemStack upgraded=garden.relics().evaluateScrollCraft(scroll,item);
            check(upgraded!=null&&garden.relics().getLimitBreakLevel(upgraded,LimitBreakType.PROTECTION)==5,set+" "+piece+" accepts real protection IV to V upgrade scroll");
            check(upgraded.getItemMeta().getItemModel().equals(item.getItemMeta().getItemModel())&&sets.set(upgraded)==set,set+" "+piece+" upgrade retains model and set identity");
            item.addUnsafeEnchantment(Enchantment.MENDING,1);item.addUnsafeEnchantment(Enchantment.UNBREAKING,3);
            var nativeItem=org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(item);
            check(net.minecraft.world.item.enchantment.EnchantmentHelper.modifyDurabilityToRepairFromXp(level,nativeItem,5)==10,set+" "+piece+" native Mending converts five XP to ten repair");
            int worn=0;for(int trial=0;trial<4000;trial++)worn+=net.minecraft.world.item.enchantment.EnchantmentHelper.processDurabilityChange(level,nativeItem,1);
            check(worn>2500&&worn<3100,set+" "+piece+" native armor Unbreaking III reduces wear");
        }

        // Real normal damage settles after Paper has applied HP loss; overkill and cancellation cannot mint healing.
        equip(ArmorSet.REVENANT);player.setHealth(4);other.setHealth(8);other.setNoDamageTicks(0);outgoing(other,1000,DamageType.GENERIC);
        deniedTarget=undead;denyHit=true;outgoing(undead,20,DamageType.GENERIC);
        Bukkit.getScheduler().runTask(this,()->{try{
            check(player.getHealth()==8,"normal lethal overkill heals half actual eight HP, cancelled target hit heals zero");
            denyHit=false;check(other.isDead(),"real native target death completed");finish(null);
        }catch(Throwable error){finish(error);}});
    }
}
