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
    void hit(double amount,DamageType type){player.setNoDamageTicks(0);player.damage(amount,DamageSource.builder(type).build());}
    LivingEntity dummy(Class<? extends LivingEntity> type,Location at,double health,boolean ai){
        return world.spawn(at,type,m->{m.setAI(ai);m.setSilent(true);m.setGravity(false);m.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);m.setHealth(health);
            if(m.getAttribute(Attribute.ARMOR)!=null)m.getAttribute(Attribute.ARMOR).setBaseValue(0);});
    }
    void dragonPowers(DragonSetPowers powers,Location at)throws Exception{
        // Over the caps: a 10 damage mob hit is 2.0 after vanilla armor and 1.58 with the 21% extra reduction.
        equip(ArmorSet.INFERNAL_DRAKE);healthy();powers.testRolls(1.0,null);
        hit(10,DamageType.MOB_ATTACK);
        check(Math.abs(20-player.getHealth()-1.58)<0.02,"full dragon set takes 1.58 of a 10 damage mob hit (vanilla cap alone: 2.0) — took "+(20-player.getHealth()));
        healthy();hit(10,DamageType.GENERIC);
        check(Math.abs(20-player.getHealth()-10)<1e-6,"armor-bypassing damage gets no over-cap reduction");
        player.getInventory().setArmorContents(new ItemStack[]{null,null,null,sets.create(ArmorSet.INFERNAL_DRAKE,Piece.HELM)});actor.doTick();
        check(powers.overcap(player)==0&&powers.fullSet(player)==null,"a lone dragon helm stays under the caps and grants no set power");

        // Infernal Drake.
        equip(ArmorSet.INFERNAL_DRAKE);healthy();
        hit(6,DamageType.LAVA);hit(6,DamageType.ON_FIRE);hit(6,DamageType.IN_FIRE);
        check(player.getHealth()==20,"Infernal Drake: lava and fire deal nothing");
        player.setFireTicks(200);for(int i=0;i<5;i++)powers.tick();
        check(player.getFireTicks()<=0,"Infernal Drake: burning is put out");
        var combust=new EntityCombustEvent(player,8f);Bukkit.getPluginManager().callEvent(combust);
        check(combust.isCancelled(),"Infernal Drake: cannot catch fire");
        LivingEntity cow=dummy(Cow.class,at.clone().add(-3,0,0),500,false);
        check(Math.abs(outgoing(cow,10,DamageType.GENERIC)-10)<1e-6,"Infernal Drake at full HP: no rage bonus");
        player.setHealth(10);check(Math.abs(outgoing(cow,10,DamageType.GENERIC)-13.75)<1e-6,"Infernal Drake at half HP: x1.375");
        player.setHealth(2);check(Math.abs(outgoing(cow,10,DamageType.GENERIC)-16.75)<1e-6,"Infernal Drake at 10% HP: x1.675");
        healthy();powers.testRolls(0.0,null);
        double before=cow.getHealth();outgoing(cow,20,DamageType.GENERIC);
        check(powers.flying()==1,"Infernal Drake: a hit launches a blaze fireball");
        check(!world.getEntitiesByClass(SmallFireball.class).isEmpty(),"the fireball is a real blaze fireball both clients draw");
        for(int i=0;i<40&&powers.flying()>0;i++)powers.tick();
        double fire=before-20-cow.getHealth();
        check(powers.flying()==0&&Math.abs(fire-7)<1e-6,"the fireball lands for 35% of the hit (7 of 20): "+fire);
        check(world.getEntitiesByClass(SmallFireball.class).isEmpty(),"the landed fireball is removed");
        check(cow.getFireTicks()>0,"the fireball sets the target alight");
        outgoing(cow,20,DamageType.GENERIC);
        check(powers.flying()==0,"fireballs have a one second cooldown");
        powers.advanceClock(1100);
        LivingEntity blaze=dummy(Blaze.class,at.clone().add(0,0,-3),500,false);
        before=blaze.getHealth();outgoing(blaze,20,DamageType.GENERIC);
        for(int i=0;i<40&&powers.flying()>0;i++)powers.tick();
        check(before-20-blaze.getHealth()>3,"a fire-immune blaze still takes the fireball blast ("+(before-20-blaze.getHealth())+")");
        blaze.remove();powers.testRolls(1.0,null);
        healthy();lethal();
        check(!player.isDead()&&Math.abs(player.getHealth()-6)<1e-6,"Infernal Drake denies a lethal hit and leaves 30% HP");
        long ready=player.getPersistentDataContainer().getOrDefault(key("dragon_last_stand"),PersistentDataType.LONG,0L);
        check(ready>System.currentTimeMillis()+38000,"last stand cools down for 40 seconds");
        var second=new EntityDamageEvent(player,EntityDamageEvent.DamageCause.CUSTOM,10000);powers.fatal(second);
        check(!second.isCancelled(),"a second lethal hit inside the cooldown is not denied");
        var command=new EntityDamageEvent(player,EntityDamageEvent.DamageCause.KILL,Float.MAX_VALUE);powers.advanceClock(41000);powers.fatal(command);
        check(!command.isCancelled(),"/kill is never denied");
        healthy();lethal();check(!player.isDead()&&player.getHealth()>1,"after 40 seconds the last stand is ready again");

        // Frostwyrm.
        equip(ArmorSet.FROSTWYRM);healthy();
        hit(6,DamageType.FREEZE);check(player.getHealth()==20,"Frostwyrm: freezing deals nothing");
        player.setFreezeTicks(120);for(int i=0;i<5;i++)powers.tick();
        check(player.getFreezeTicks()==0,"Frostwyrm: frost build-up melts away");
        powers.advanceClock(20000);
        check(powers.frostStacks(player)==0,"Frostwyrm starts without stacks");
        for(int i=0;i<12;i++){hit(2,DamageType.MOB_ATTACK);powers.advanceClock(500);}
        check(powers.frostStacks(player)==10,"Frostwyrm: each hit taken adds a stack, up to ten ("+powers.frostStacks(player)+")");
        healthy();hit(10,DamageType.GENERIC);
        check(Math.abs(20-player.getHealth()-7)<1e-6,"ten stacks cut damage by 30% (10 -> "+(20-player.getHealth())+")");
        int held=powers.frostStacks(player);powers.advanceClock(5000);
        check(powers.frostStacks(player)==held,"stacks hold for six seconds after the last hit");
        powers.advanceClock(1000+2500);
        check(powers.frostStacks(player)==held-3,"then fade one per second ("+powers.frostStacks(player)+")");
        healthy();powers.testRolls(0.0,null);
        LivingEntity husk=dummy(Husk.class,at.clone().add(0,0,3),500,true);
        outgoing(husk,1,DamageType.GENERIC);
        check(!((Mob)husk).isAware()&&powers.stunned(husk)&&husk.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS)
            &&husk.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS).getAmplifier()>=6,"Frostwyrm: a hit can stun the target (frozen AI, cannot move)");
        check(husk.getPotionEffect(org.bukkit.potion.PotionEffectType.SLOWNESS).getDuration()==60,"the stun lasts three seconds");
        powers.advanceClock(3100);for(int i=0;i<5;i++)powers.tick();
        check(((Mob)husk).isAware()&&!powers.stunned(husk),"the stunned mob wakes up after three seconds");
        outgoing(husk,1,DamageType.GENERIC);
        check(((Mob)husk).isAware(),"a just-freed target has three seconds of stun immunity");
        LivingEntity guardian=dummy(Husk.class,at.clone().add(2,0,3),500,true);
        guardian.getPersistentDataContainer().set(new NamespacedKey(garden,"world_boss_entity"),PersistentDataType.BYTE,(byte)1);
        outgoing(guardian,1,DamageType.GENERIC);
        check(((Mob)guardian).isAware()&&!powers.stunned(guardian),"bosses are never stunned");
        guardian.remove();
        powers.testRolls(1.0,null);
        LivingEntity calm=dummy(Husk.class,at.clone().add(-2,0,3),500,true);outgoing(calm,1,DamageType.GENERIC);
        check(((Mob)calm).isAware(),"without the 15% roll there is no stun");calm.remove();
        // Powder snow firms up under the wearer and returns once they leave.
        Location snowAt=at.clone().add(3,0,3);
        for(int x=1;x<=3;x++)for(int z=1;z<=3;z++)world.getBlockAt(at.getBlockX()+x,199,at.getBlockZ()+z).setType(Material.POWDER_SNOW,false);
        player.teleport(snowAt);for(int i=0;i<5;i++)powers.tick();
        powers.harden(player,player.getLocation());
        check(world.getBlockAt(at.getBlockX()+3,199,at.getBlockZ()+3).getType()==Material.SNOW_BLOCK&&powers.hardenedBlocks()==9,"Frostwyrm: powder snow underfoot firms up ("+powers.hardenedBlocks()+")");
        for(int i=0;i<5;i++)powers.tick();
        var speed=player.getAttribute(Attribute.MOVEMENT_SPEED).getModifier(new NamespacedKey("advance_magic","frostwyrm_snow_speed"));
        check(speed!=null&&Math.abs(speed.getAmount()-.4)<1e-9,"Frostwyrm: +40% speed on snow");
        player.teleport(at.clone().add(-3,0,-3));powers.advanceClock(2000);for(int i=0;i<5;i++)powers.tick();
        check(world.getBlockAt(at.getBlockX()+3,199,at.getBlockZ()+3).getType()==Material.POWDER_SNOW&&powers.hardenedBlocks()==0,"the powder snow returns once the wearer leaves");
        check(player.getAttribute(Attribute.MOVEMENT_SPEED).getModifier(new NamespacedKey("advance_magic","frostwyrm_snow_speed"))==null,"the snow speed ends off snow");
        for(int x=1;x<=3;x++)for(int z=1;z<=3;z++)world.getBlockAt(at.getBlockX()+x,199,at.getBlockZ()+z).setType(Material.STONE,false);
        player.teleport(at);
        powers.advanceClock(41000);
        healthy();lethal();check(!player.isDead()&&Math.abs(player.getHealth()-6)<1e-6,"Frostwyrm denies a lethal hit too");
        powers.advanceClock(41000);

        // Tempest Drake.
        equip(ArmorSet.TEMPEST_DRAKE);healthy();powers.testRolls(1.0,null);
        hit(6,DamageType.LIGHTNING_BOLT);
        check(player.getHealth()==20,"Tempest Drake: lightning deals nothing");
        check(powers.stormActive(player)&&Math.abs(player.getAbsorptionAmount()-10)<1e-6,"a lightning strike charges the wearer and grants five golden hearts");
        check(Math.abs(outgoing(cow,10,DamageType.GENERIC)-40)<1e-6,"charged hits deal +300% (x4)");
        check(!powers.charge(player)&&Math.abs(player.getAbsorptionAmount()-10)<1e-6,"a second strike inside the cooldown neither stacks nor refreshes");
        powers.advanceClock(4100);
        check(!powers.stormActive(player)&&Math.abs(outgoing(cow,10,DamageType.GENERIC)-10)<1e-6,"the charge lasts four seconds");
        powers.advanceClock(16000);for(int i=0;i<5;i++)powers.tick();
        check(player.getAbsorptionAmount()==0,"the golden hearts fade with the cooldown");
        check(powers.charge(player),"after 20 seconds lightning charges again");
        powers.advanceClock(STORM_WAIT);for(int i=0;i<5;i++)powers.tick();
        powers.testRolls(0.0,false);
        LivingEntity weak=dummy(Zombie.class,at.clone().add(3,0,-2),60,false);
        outgoing(weak,1,DamageType.GENERIC);powers.settle();
        check(weak.isDead(),"Tempest Drake: lightning on a monster below 100 HP slays it outright");
        powers.advanceClock(1600);
        LivingEntity sturdy=dummy(Cow.class,at.clone().add(3,0,2),300,false);
        outgoing(sturdy,10,DamageType.GENERIC);powers.settle();
        check(!sturdy.isDead()&&Math.abs(300-sturdy.getHealth()-10-10)<1e-6,"above 100 HP the bolt deals 6 + 40% of the hit ("+(300-sturdy.getHealth()-10)+")");
        powers.advanceClock(1600);
        LivingEntity marked=dummy(Zombie.class,at.clone().add(-3,0,2),60,false);
        marked.getPersistentDataContainer().set(new NamespacedKey(garden,"world_boss_entity"),PersistentDataType.BYTE,(byte)1);
        outgoing(marked,1,DamageType.GENERIC);powers.settle();
        check(!marked.isDead()&&marked.getHealth()<59,"bosses are never slain outright, only struck");
        marked.remove();sturdy.remove();
        powers.advanceClock(1600);powers.testRolls(0.0,true);
        outgoing(cow,1,DamageType.GENERIC);powers.settle();
        check(powers.stormActive(player),"a bolt that strikes the wearer charges them");
        powers.testRolls(null,null);powers.advanceClock(30000);for(int i=0;i<5;i++)powers.tick();
        cow.remove();husk.remove();player.setAbsorptionAmount(0);
    }
    static final long STORM_WAIT=20100;
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
            if(set.dragon()){
                List<String> expected=new ArrayList<>(List.of(ArmorSet.DRAGON_TAG+set.setName,set.description(piece)));
                for(String power:set.powers())expected.add(ArmorSet.POWER_MARK+power);
                expected.add(ArmorSet.POWER_MARK+ArmorSet.OVERCAP_LINE);
                check(meta.lore().stream().map(plain::serialize).toList().equals(expected)&&set.powers().size()>=4,set+" "+piece+" dragon set lore lists its powers");
            }
            else check(meta.lore().size()==1&&plain.serialize(meta.lore().getFirst()).equals("Combat"),set+" "+piece+" only Combat base lore");
            check(!meta.isUnbreakable()&&meta.getEquippable().isDamageOnHurt(),set+" "+piece+" native durability enabled");
            check(meta.getItemModel().equals(set.model(piece))&&Objects.equals(meta.getEquippable().getModel(),piece==Piece.HELM?null:set.asset()),set+" "+piece+" model and wearable asset preserved");
            for(Attribute attribute:List.of(Attribute.ARMOR,Attribute.ARMOR_TOUGHNESS)){
                double expected=set.dragon()?(attribute==Attribute.ARMOR?ArmorSet.DRAGON_ARMOR:ArmorSet.DRAGON_TOUGHNESS)[piece.ordinal()]
                    :piece.material.getDefaultAttributeModifiers(piece.slot).get(attribute).stream().mapToDouble(a->a.getAmount()).sum()*set.scale;
                check(meta.getAttributeModifiers(attribute).size()==1&&Math.abs(meta.getAttributeModifiers(attribute).stream().mapToDouble(a->a.getAmount()).sum()-expected)<1e-8,
                    set+" "+piece+" "+attribute.getKey().getKey()+" "+expected+(set.dragon()?"":" at "+Math.round(set.scale*100)+"% netherite"));
            }
            double nativeKnockback=piece.material.getDefaultAttributeModifiers(piece.slot).get(Attribute.KNOCKBACK_RESISTANCE).stream().mapToDouble(a->a.getAmount()).sum();
            check(Math.abs(meta.getAttributeModifiers(Attribute.KNOCKBACK_RESISTANCE).stream().mapToDouble(a->a.getAmount()).sum()-(set.dragon()?ArmorSet.DRAGON_KNOCKBACK[piece.ordinal()]:nativeKnockback))<1e-9,
                set+" "+piece+(set.dragon()?" knockback resistance 10%":" native knockback resistance retained"));
            int maxDamage=((org.bukkit.inventory.meta.Damageable)meta).hasMaxDamage()?((org.bukkit.inventory.meta.Damageable)meta).getMaxDamage():piece.material.getMaxDurability();
            check(maxDamage==(set.dragon()?ArmorSet.DRAGON_DURABILITY[piece.ordinal()]:piece.material.getMaxDurability()),set+" "+piece+" durability "+maxDamage);
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

        // A 1.39 dragon piece (2x netherite, placeholder power line) moves to the 1.40 stats; a reforged affix survives.
        ItemStack legacy=sets.create(ArmorSet.FROSTWYRM,Piece.CHESTPLATE);var legacyMeta=legacy.getItemMeta();
        legacyMeta.getPersistentDataContainer().set(key("armor_combat_version"),PersistentDataType.INTEGER,1);
        legacyMeta.removeAttributeModifier(Attribute.ARMOR);
        legacyMeta.addAttributeModifier(Attribute.ARMOR,new org.bukkit.attribute.AttributeModifier(new NamespacedKey("advance_magic","frostwyrm_chestplate_armor"),16,
            org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.CHEST));
        NamespacedKey affix=new NamespacedKey("evergarden_forge","affix_stalwart_chest");
        legacyMeta.addAttributeModifier(Attribute.ARMOR,new org.bukkit.attribute.AttributeModifier(affix,2.5,org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,EquipmentSlotGroup.CHEST));
        ((Damageable)legacyMeta).setMaxDamage(1184);
        legacyMeta.lore(List.of(net.kyori.adventure.text.Component.text(ArmorSet.DRAGON_TAG+ArmorSet.FROSTWYRM.setName),
            net.kyori.adventure.text.Component.text(ArmorSet.FROSTWYRM.description(Piece.CHESTPLATE)),
            net.kyori.adventure.text.Component.text(ArmorSet.DRAGON_POWER),net.kyori.adventure.text.Component.text("Forge line")));
        legacy.setItemMeta(legacyMeta);
        check(sets.migrate(legacy),"a 1.39 dragon piece migrates");legacyMeta=legacy.getItemMeta();
        var legacyArmor=legacyMeta.getAttributeModifiers(Attribute.ARMOR);
        check(legacyArmor.size()==2&&legacyArmor.stream().anyMatch(m->m.getKey().equals(affix)&&m.getAmount()==2.5)
            &&legacyArmor.stream().anyMatch(m->m.getKey().getNamespace().equals("advance_magic")&&m.getAmount()==19),"migration sets chest armor 19 and keeps the Forge affix ("+legacyArmor.size()+")");
        check(((Damageable)legacyMeta).getMaxDamage()==1200,"migrated chestplate durability 1200");
        List<String> legacyLore=legacyMeta.lore().stream().map(plain::serialize).toList();
        check(!legacyLore.contains(ArmorSet.DRAGON_POWER)&&legacyLore.contains("Forge line")&&legacyLore.contains(ArmorSet.POWER_MARK+ArmorSet.FROSTWYRM.powers().get(1)),"migration swaps the placeholder for the power lines and keeps foreign lore");
        check(!sets.migrate(legacy),"migrated dragon piece is stable");

        DragonSetPowers powers=magic.dragonPowers();
        check(powers!=null,"dragon set powers boot");
        for(ArmorSet dragon:ArmorSet.dragons()){
            equip(dragon);
            check(sets.full(player,dragon)&&!combat.fullRevenant(player)&&!combat.fullSeraph(player)&&powers.fullSet(player)==dragon,dragon+" full set is worn and grants no Seraph/Revenant powers");
            check(Math.abs(player.getAttribute(Attribute.ARMOR).getValue()-30)<1e-9&&Math.abs(player.getAttribute(Attribute.ARMOR_TOUGHNESS).getValue()-20)<1e-9,
                dragon+" full set reaches the vanilla caps (armor 30, toughness 20)");
            check(Math.abs(powers.overcap(player)-.21)<1e-9,dragon+" armor 57 / toughness 32 above the caps turns into 21% extra reduction ("+powers.overcap(player)+")");
        }
        dragonPowers(powers,at);
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
