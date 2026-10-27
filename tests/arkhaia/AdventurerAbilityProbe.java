import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.item.AdventurerWeaponService;
import com.example.voidscape.item.AdventurerWeaponService.AdventurerWeapon;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.server.level.ServerPlayer;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.entity.CraftEntity;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.nio.file.*;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Rare Adventurer weapon powers on a real Paper server, driven by a native ServerPlayer's attacks. */
public final class AdventurerAbilityProbe extends JavaPlugin implements Listener {
    record Hit(int tick, Entity target, double raw, double fin, EntityDamageEvent.DamageCause cause, boolean cancelled, Entity damager) {}
    interface Step {void run(Runnable next) throws Exception;}

    final List<Hit> hits = new ArrayList<>();
    VoidscapePlugin garden; AdventurerWeaponService weapons; World world; ServerPlayer actor; Player player; Location base;
    int checks; boolean done; java.lang.reflect.Field strengthTicker;

    void check(boolean ok, String label) {if (!ok) throw new AssertionError(label); checks++; getLogger().info("PASS: " + label);}

    @Override public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, () -> {try {setup(); chain(steps(), 0);} catch (Throwable error) {finish(error);}}, 200);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void record(EntityDamageEvent event) {
        Entity damager = event instanceof EntityDamageByEntityEvent by ? by.getDamager() : null;
        hits.add(new Hit(Bukkit.getCurrentTick(), event.getEntity(), event.getDamage(), event.getFinalDamage(), event.getCause(), event.isCancelled(), damager));
    }

    // -- harness ---------------------------------------------------------------

    void setup() throws Exception {
        garden = (VoidscapePlugin) Bukkit.getPluginManager().getPlugin("Evergarden");
        check(garden != null && garden.isEnabled(), "Evergarden boots");
        weapons = garden.adventurerWeapons();
        world = Bukkit.getWorlds().get(0);
        world.setTime(18000); world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false); world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setDifficulty(Difficulty.HARD);
        base = new Location(world, 0.5, 150, 0.5);
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) world.addPluginChunkTicket(x, z, this);
        for (int x = -16; x <= 16; x++) for (int z = -16; z <= 16; z++) world.getBlockAt(x, 149, z).setType(Material.SMOOTH_STONE, false);
        var server = net.minecraft.server.MinecraftServer.getServer();
        var level = ((org.bukkit.craftbukkit.CraftWorld) world).getHandle();
        var profile = new com.mojang.authlib.GameProfile(UUID.randomUUID(), "AbilityTest");
        actor = new ServerPlayer(server, level, profile, net.minecraft.server.level.ClientInformation.createDefault());
        var connection = new Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
        connection.channel = new EmbeddedChannel(); connection.address = new java.net.InetSocketAddress("127.0.0.1", 1);
        actor.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server, connection, actor,
                net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false)) {
            @Override public boolean hasClientLoaded() {return true;}
        };
        actor.setPos(base.getX(), base.getY(), base.getZ());
        server.getPlayerList().getPlayers().add(actor); server.getPlayerList().getPlayersByUUID().put(profile.id(), actor); level.addNewPlayer(actor);
        player = actor.getBukkitEntity(); player.setGameMode(GameMode.SURVIVAL); player.setInvulnerable(true);
        for (Class<?> type = actor.getClass(); type != null && strengthTicker == null; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) if (field.getName().equals("attackStrengthTicker")) {strengthTicker = field; field.setAccessible(true);}
        }
        // A connected client ticks its player; the fake one is ticked here so equipment and effects apply.
        Bukkit.getScheduler().runTaskTimer(this, () -> {if (actor != null && !done) actor.doTick();}, 1, 1);
    }

    void chain(List<Step> steps, int index) {
        if (done) return;
        if (index == steps.size()) {finish(null); return;}
        try {steps.get(index).run(() -> after(1, () -> chain(steps, index + 1)));} catch (Throwable error) {finish(error);}
    }

    void after(int ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(this, () -> {if (done) return; try {task.run();} catch (Throwable error) {finish(error);}}, ticks);
    }

    /** Repeats {@code act} every {@code gap} ticks until {@code seen}, failing after {@code tries}. */
    void until(String label, int tries, int gap, Runnable act, BooleanSupplier seen, Runnable next) {
        act.run();
        after(gap, () -> {
            if (seen.getAsBoolean()) {check(true, label); next.run();}
            else if (tries <= 1) check(false, label);
            else until(label, tries - 1, gap, act, seen, next);
        });
    }

    void hold(AdventurerWeapon weapon) {player.getInventory().setItemInMainHand(weapons.create(weapon)); actor.doTick();}

    void swing(LivingEntity target) {
        target.setNoDamageTicks(0);
        try {if (strengthTicker != null) strengthTicker.setInt(actor, 1000);} catch (IllegalAccessException error) {throw new RuntimeException(error);}
        actor.fallDistance = 0;
        actor.attack(((CraftEntity) target).getHandle());
    }

    void smash(LivingEntity target) {
        target.setNoDamageTicks(0);
        try {if (strengthTicker != null) strengthTicker.setInt(actor, 1000);} catch (IllegalAccessException error) {throw new RuntimeException(error);}
        actor.fallDistance = 4.0;
        actor.attack(((CraftEntity) target).getHandle());
        actor.fallDistance = 0;
    }

    /** The spear's right-click kinetic hit, delivered while the spear is raised as it is in play. */
    void charge(LivingEntity target) {
        target.setNoDamageTicks(0);
        actor.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND);
        check(player.isHandRaised(), "spear is raised for its charge");
        actor.stabAttack(net.minecraft.world.entity.EquipmentSlot.MAINHAND, ((CraftEntity) target).getHandle(), 8f, true, false, false);
        actor.stopUsingItem();
    }

    <T extends LivingEntity> T spawn(Class<T> type, double dx, double dz) {
        T mob = world.spawn(base.clone().add(dx, 0, dz), type, entity -> {
            entity.getAttribute(Attribute.MAX_HEALTH).setBaseValue(400);
            entity.setHealth(400);
            if (entity instanceof Mob m) {m.setAI(false); m.setPersistent(true); m.setRemoveWhenFarAway(false);}
            entity.getEquipment().clear();
        });
        mob.setFireTicks(0);
        return mob;
    }

    List<Hit> on(Entity target, int since) {return hits.stream().filter(h -> h.target() == target && h.tick() >= since && !h.cancelled()).toList();}
    Hit last(Entity target) {List<Hit> list = on(target, 0); return list.isEmpty() ? null : list.get(list.size() - 1);}
    double rawSum(Entity target, int since) {return on(target, since).stream().mapToDouble(Hit::raw).sum();}
    static boolean near(double a, double b) {return Math.abs(a - b) <= 0.05 * Math.max(1, Math.abs(b));}
    int now() {return Bukkit.getCurrentTick();}
    void clean() {for (Entity e : world.getEntities()) if (!(e instanceof Player)) e.remove(); player.getActivePotionEffects().forEach(e -> player.removePotionEffect(e.getType())); player.setAbsorptionAmount(0); player.setFireTicks(0);
        // No natural regeneration, so heals are the weapons' own.
        player.setFoodLevel(10); player.setSaturation(0); player.setHealth(player.getAttribute(Attribute.MAX_HEALTH).getValue());}

    // -- the checks --------------------------------------------------------------

    List<Step> steps() {
        List<Step> steps = new ArrayList<>();
        steps.add(next -> {
            for (AdventurerWeapon weapon : AdventurerWeapon.values()) {
                String lore = String.join("\n", weapons.create(weapon).getItemMeta().lore().stream()
                        .map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize).toList());
                check(lore.contains("ความสามารถพิเศษ") == (weapon != AdventurerWeapon.BRAMBLEPIKE), weapon.id() + ": ability lore " + (weapon == AdventurerWeapon.BRAMBLEPIKE ? "absent (unchanged weapon)" : "shown"));
            }
            var bow = weapons.create(AdventurerWeapon.THORNWHISPER).getItemMeta();
            check(bow.hasEnchant(Enchantment.INFINITY) && bow.hasEnchant(Enchantment.MENDING), "Thornwhisper has Infinity and Mending together");
            ItemStack legacy = weapons.create(AdventurerWeapon.EMBERFANG);
            var meta = legacy.getItemMeta();
            meta.getPersistentDataContainer().remove(garden.key("adventurer_revision"));
            var lines = new ArrayList<>(meta.lore().subList(0, 4));
            lines.add(net.kyori.adventure.text.Component.text("EVERGARDEN · SANCTUM ARMOURY"));
            lines.add(net.kyori.adventure.text.Component.text("Limit Break: Sharpness VII"));
            meta.lore(lines); legacy.setItemMeta(meta);
            var inventory = Bukkit.createInventory(null, 9); inventory.setItem(0, legacy);
            weapons.migrateInventory(inventory);
            String migrated = String.join("\n", inventory.getItem(0).getItemMeta().lore().stream()
                    .map(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()::serialize).toList());
            check(migrated.contains("ความสามารถพิเศษ") && migrated.endsWith("Limit Break: Sharpness VII"), "an older copy gains its ability lore and keeps appended enchant lines");
            next.run();
        });
        // Emberfang
        steps.add(next -> {
            clean(); hold(AdventurerWeapon.EMBERFANG);
            Vindicator target = spawn(Vindicator.class, 0, 2);
            swing(target);
            Hit first = last(target);
            check(first != null && target.getFireTicks() >= 90, "Emberfang ignites its target (fire " + target.getFireTicks() + ")");
            after(15, () -> {
                swing(target);
                Hit second = last(target);
                check(second != first && near(second.raw(), first.raw() * 2), "Emberfang hits a burning target twice as hard (" + first.raw() + " -> " + second.raw() + ")");
                next.run();
            });
        });
        // Stormpiercer
        steps.add(next -> {
            clean(); hold(AdventurerWeapon.STORMPIERCER);
            Vindicator target = spawn(Vindicator.class, 0, 2);
            charge(target);
            PotionEffect strength = player.getPotionEffect(PotionEffectType.STRENGTH);
            check(strength != null && strength.getAmplifier() == 0 && strength.getDuration() > 190, "Stormpiercer charge hit grants Strength I for 10 s");
            int start = now();
            until("Stormpiercer calls lightning that deals 6 bonus damage", 40, 25, () -> swing(target),
                    () -> on(target, start).stream().anyMatch(h -> near(h.raw(), 6.0)), next);
        });
        // Ironbloom
        steps.add(next -> {
            clean(); hold(AdventurerWeapon.IRONBLOOM);
            Vindicator target = spawn(Vindicator.class, 0, 2);
            smash(target);
            check(player.getAbsorptionAmount() == 6.0 && player.hasPotionEffect(PotionEffectType.ABSORPTION), "Ironbloom smash: 3 golden hearts (" + player.getAbsorptionAmount() + ")");
            after(25, () -> {
                smash(target);
                check(player.getAbsorptionAmount() == 6.0, "Ironbloom golden hearts do not stack (" + player.getAbsorptionAmount() + ")");
                swing(target);
                check(player.getAbsorptionAmount() == 6.0, "a grounded hit is not a smash");
                next.run();
            });
        });
        // Frostveil
        steps.add(next -> {
            clean(); hold(AdventurerWeapon.FROSTVEIL);
            Vindicator target = spawn(Vindicator.class, 0, 2);
            swing(target);
            PotionEffect slow = target.getPotionEffect(PotionEffectType.SLOWNESS);
            check(slow != null && slow.getAmplifier() == 1, "Frostveil slows on every hit");
            until("Frostveil frostbite: frozen and mining fatigue", 40, 14, () -> swing(target),
                    () -> target.isFreezeTickingLocked() && target.getFreezeTicks() >= target.getMaxFreezeTicks() && target.hasPotionEffect(PotionEffectType.MINING_FATIGUE), next);
        });
        // Sunflare
        steps.add(next -> {
            clean(); hold(AdventurerWeapon.SUNFLARE);
            Vindicator target = spawn(Vindicator.class, 0, 2);
            after(8, () -> {
                check(player.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE), "Sunflare held: fire resistance");
                smash(target);
                check(target.getFireTicks() >= 90 && target.hasPotionEffect(PotionEffectType.SLOWNESS), "Sunflare smash ignites and slows");
                next.run();
            });
        });
        // Moonrift
        steps.add(next -> {
            clean(); hold(AdventurerWeapon.MOONRIFT);
            Vindicator target = spawn(Vindicator.class, 0, 2);
            int[] mark = {now()};
            double[] raw = {0};
            until("Moonrift strikes again 50% harder, once", 40, 14, () -> {mark[0] = now(); swing(target); raw[0] = last(target).raw();},
                    () -> {
                        List<Hit> list = on(target, mark[0]);
                        return list.size() == 2 && near(list.get(1).raw(), raw[0] * 1.5);
                    }, next);
        });
        // Tidehook
        steps.add(next -> {
            clean(); player.getInventory().setItemInMainHand(null); after(8, () -> {
                hold(AdventurerWeapon.TIDEHOOK);
                after(8, () -> {
                    PotionEffect speed = player.getPotionEffect(PotionEffectType.SPEED);
                    check(speed != null && speed.getAmplifier() == 3, "Tidehook drawn: Speed IV");
                    check(player.hasPotionEffect(PotionEffectType.DOLPHINS_GRACE), "Tidehook held: fast swimming");
                    player.removePotionEffect(PotionEffectType.SPEED);
                    player.getInventory().setItemInMainHand(null);
                    after(8, () -> {
                        hold(AdventurerWeapon.TIDEHOOK);
                        after(8, () -> {
                            check(!player.hasPotionEffect(PotionEffectType.SPEED), "Tidehook re-draw within 30 s gives no new Speed");
                            next.run();
                        });
                    });
                });
            });
        });
        // Ashcleaver
        steps.add(next -> {
            clean(); hold(AdventurerWeapon.ASHCLEAVER);
            Vindicator target = spawn(Vindicator.class, 0, 2);
            after(8, () -> {
                int[] n = {0};
                Runnable[] hit = new Runnable[1];
                hit[0] = () -> {
                    swing(target);
                    if (++n[0] < 3) {after(14, hit[0]); return;}
                    check(target.getFireTicks() <= 0, "Ashcleaver: three hits do not ignite yet");
                    after(14, () -> {
                        swing(target);
                        check(target.getFireTicks() >= 90, "Ashcleaver: the fourth hit ignites");
                        after(14, () -> {
                            double before = player.getAbsorptionAmount();
                            swing(target);
                            check(player.getAbsorptionAmount() == before + 1.0, "Ashcleaver: hitting a burning foe gives half a golden heart");
                            player.getInventory().setItemInMainHand(null);
                            after(8, () -> {
                                check(player.getAbsorptionAmount() == before, "Ashcleaver: its hearts leave with the weapon");
                                next.run();
                            });
                        });
                    });
                };
                hit[0].run();
            });
        });
        // Glimmermaul
        steps.add(next -> {
            clean(); hold(AdventurerWeapon.GLIMMERMAUL);
            Vindicator living = spawn(Vindicator.class, 0, 2);
            Zombie undead = spawn(Zombie.class, 6, 2);
            Vindicator bystander = spawn(Vindicator.class, 1.5, 3);
            swing(living);
            double livingRaw = last(living).raw();
            check(near(livingRaw, 11.0 + 6.0), "Glimmermaul ground smash adds half a 3-block mace smash (" + livingRaw + ")");
            check(bystander.getVelocity().lengthSquared() > 0.05, "Glimmermaul ground smash knocks nearby foes back");
            after(25, () -> {
                swing(undead);
                double undeadRaw = last(undead).raw();
                check(undeadRaw - 11.0 > 11.0, "Glimmermaul smashes undead at full mace strength (" + undeadRaw + ")");
                next.run();
            });
        });
        // Thornwhisper
        steps.add(next -> {
            clean(); player.getInventory().setItemInMainHand(weapons.create(AdventurerWeapon.THORNWHISPER));
            Vindicator target = spawn(Vindicator.class, 0, 4);
            int start = now();
            shoot("thornwhisper", target, 6);
            check(near(last(target).raw(), 6 * 1.25), "Thornwhisper arrow keeps the +25% ranged bonus");
            after(90, () -> {
                List<Hit> venom = on(target, start + 1);
                double total = venom.stream().mapToDouble(Hit::raw).sum();
                check(venom.size() == 4 && near(total, 10.0), "Thornwhisper venom deals exactly 10 damage over 4 s (" + venom.size() + " ticks, " + total + ")");
                int again = now();
                shoot("thornwhisper", target, 6);
                after(90, () -> {
                    check(on(target, again + 1).isEmpty(), "Thornwhisper venom is on a 30 s cooldown");
                    next.run();
                });
            });
        });
        // Runelock
        steps.add(next -> {
            clean();
            Vindicator target = spawn(Vindicator.class, 0, 4);
            shoot("runelock", target, 6);
            check(near(last(target).raw(), 6 * 1.25 + 100), "Runelock bolt adds 25% of max HP (" + last(target).raw() + ")");
            Vindicator boss = spawn(Vindicator.class, 3, 4);
            boss.addScoreboardTag("boss");
            shoot("runelock", boss, 6);
            check(near(last(boss).raw(), 6 * 1.25 + 40), "Runelock: a boss takes 10% on the first bolt (" + last(boss).raw() + ")");
            boss.setNoDamageTicks(0);
            shoot("runelock", boss, 6);
            check(near(last(boss).raw(), 6 * 1.25), "Runelock: later bolts on that boss get no share");
            Zombie armored = spawn(Zombie.class, -3, 4);
            armored.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            Zombie plain = spawn(Zombie.class, -6, 4);
            plain.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            double[] reduction = new double[2];
            reduction[0] = armorTaken(armored, "runelock");
            reduction[1] = armorTaken(plain, null);
            check(reduction[0] < reduction[1] * 0.5, "Runelock breaches armor (" + reduction[0] + " vs " + reduction[1] + " absorbed)");
            next.run();
        });
        // Mistcaller
        steps.add(next -> {
            clean(); hold(AdventurerWeapon.MISTCALLER);
            Vindicator target = spawn(Vindicator.class, 0, 3);
            int start = now();
            charge(target);
            after(40, () -> {
                check(on(target, start + 2).stream().anyMatch(h -> near(h.raw(), 6.0)), "Mistcaller charge locks a guardian beam that lands 6 damage");
                player.setHealth(10);
                swing(target);
                double dealt = last(target).fin();
                after(3, () -> {
                    check(near(player.getHealth(), 10 + dealt * 0.25), "Mistcaller normal hit heals 25% of damage (" + player.getHealth() + ")");
                    next.run();
                });
            });
        });
        // Silvershiv
        steps.add(next -> {
            clean(); hold(AdventurerWeapon.SILVERSHIV);
            Vindicator living = spawn(Vindicator.class, 0, 2);
            Zombie undead = spawn(Zombie.class, 5, 2);
            after(8, () -> {
                check(player.hasPotionEffect(PotionEffectType.SPEED), "Silvershiv held: Speed I");
                face(living, true); swing(living); double front = last(living).raw();
                face(undead, true); swing(undead); double vsUndead = last(undead).raw();
                check(near(vsUndead, front * 1.3), "Silvershiv hits undead 30% harder (" + front + " -> " + vsUndead + ")");
                after(15, () -> {
                    face(living, false); swing(living); double back = last(living).raw();
                    check(near(back, front * 2), "Silvershiv backstab doubles damage (" + back + ")");
                    next.run();
                });
            });
        });
        // Stoneheart
        steps.add(next -> {
            clean(); hold(AdventurerWeapon.STONEHEART);
            Vindicator target = spawn(Vindicator.class, 0, 2);
            player.setInvulnerable(false); player.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200); player.setHealth(200);
            smash(target);
            int start = now();
            player.damage(4, DamageSource.builder(DamageType.GENERIC).build());
            check(on(player, start).isEmpty() && hits.stream().filter(h -> h.target() == player && h.tick() >= start).allMatch(Hit::cancelled), "Stoneheart smash: immune for 1 s");
            after(30, () -> {
                int mark = now();
                player.setNoDamageTicks(0);
                player.damage(4, DamageSource.builder(DamageType.GENERIC).build());
                check(on(player, mark).stream().anyMatch(h -> near(h.raw(), 8)), "Stoneheart: then double damage taken");
                after(110, () -> {
                    int later = now();
                    player.setNoDamageTicks(0);
                    player.damage(4, DamageSource.builder(DamageType.GENERIC).build());
                    check(on(player, later).stream().anyMatch(h -> near(h.raw(), 4)), "Stoneheart: normal damage after the 5 s window");
                    player.setInvulnerable(true);
                    next.run();
                });
            });
        });
        // Nightbloom
        steps.add(next -> {
            clean(); hold(AdventurerWeapon.NIGHTBLOOM);
            Vindicator target = spawn(Vindicator.class, 0, 2);
            player.setHealth(4);
            charge(target);
            double dealt = last(target).fin();
            after(3, () -> {
                check(near(player.getHealth(), Math.min(player.getAttribute(Attribute.MAX_HEALTH).getValue(), 4 + dealt)), "Nightbloom charge steals life equal to damage (" + player.getHealth() + ")");
                until("Nightbloom withers (10% chance)", 80, 14, () -> swing(target), () -> target.hasPotionEffect(PotionEffectType.WITHER), next);
            });
        });
        // Ironwave
        steps.add(next -> {
            clean(); hold(AdventurerWeapon.IRONWAVE);
            Vindicator target = spawn(Vindicator.class, 0, 2);
            Vindicator side = spawn(Vindicator.class, -2.5, -1);
            charge(target);
            check(player.hasPotionEffect(PotionEffectType.REGENERATION), "Ironwave charge: Regeneration I");
            player.removePotionEffect(PotionEffectType.REGENERATION);
            target.setNoDamageTicks(0);
            charge(target);
            check(!player.hasPotionEffect(PotionEffectType.REGENERATION), "Ironwave regeneration is on a 40 s cooldown");
            int start = now();
            until("Ironwave wave hits foes around for 50% attack (4.5)", 40, 14, () -> swing(target),
                    () -> on(side, start).stream().anyMatch(h -> near(h.raw(), 4.5)), next);
        });
        // Starfang
        steps.add(next -> {
            clean(); hold(AdventurerWeapon.STARFANG);
            Vindicator target = spawn(Vindicator.class, 0, 2);
            int[] mark = {0};
            double[] raw = {0};
            until("Starfang repeats the hit three more times", 30, 20, () -> {mark[0] = now(); swing(target); raw[0] = last(target).raw();},
                    () -> {List<Hit> list = on(target, mark[0]); return list.size() == 4 && list.stream().allMatch(h -> near(h.raw(), raw[0]));},
                    () -> {
                        Zombie stunTarget = spawn(Zombie.class, 5, 2);
                        stunTarget.setAI(true);
                        until("Starfang stuns (10% chance)", 80, 14, () -> {stunTarget.setHealth(400); swing(stunTarget);}, () -> !stunTarget.isAware(), next);
                    });
        });
        // Rootspire
        steps.add(next -> {
            clean(); hold(AdventurerWeapon.ROOTSPIRE);
            Villager friend = spawn(Villager.class, 2, 2);
            Vindicator foe = spawn(Vindicator.class, -3, 2);
            after(10, () -> {
                PotionEffect regen = player.getPotionEffect(PotionEffectType.REGENERATION);
                check(regen != null && regen.getAmplifier() == 1, "Rootspire held: Regeneration II");
                check(friend.hasPotionEffect(PotionEffectType.REGENERATION) && friend.hasPotionEffect(PotionEffectType.ABSORPTION), "Rootspire: nearby villager gets Regeneration and Absorption");
                check(!foe.hasPotionEffect(PotionEffectType.REGENERATION), "Rootspire does not help enemies");
                next.run();
            });
        });
        // Voidreel
        steps.add(next -> {
            clean(); hold(AdventurerWeapon.VOIDREEL);
            Vindicator target = spawn(Vindicator.class, 0, 2);
            swing(target);
            check(near(last(target).raw(), 8.6 * 1.5), "Voidreel: every hit is critical (" + last(target).raw() + ")");
            int[] mark = {0};
            until("Voidreel chains extra hits", 30, 40, () -> {mark[0] = now(); swing(target);}, () -> on(target, mark[0]).size() >= 2, next);
        });
        return steps;
    }

    void face(LivingEntity target, boolean towardPlayer) {
        Location at = target.getLocation();
        double dx = player.getX() - at.getX(), dz = player.getZ() - at.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        target.setRotation(towardPlayer ? yaw : yaw + 180, 0);
        target.setNoDamageTicks(0);
    }

    /** An adventurer projectile hit, as the service tags the shots of the bow / crossbow. */
    void shoot(String weapon, LivingEntity target, double damage) {
        Arrow arrow = world.spawn(target.getLocation().add(0, 3, 0), Arrow.class, a -> {a.setShooter(player); a.setGravity(false);});
        arrow.getPersistentDataContainer().set(garden.key("adventurer_projectile"), PersistentDataType.STRING, weapon);
        target.setNoDamageTicks(0);
        target.damage(damage, DamageSource.builder(DamageType.ARROW).withDirectEntity(arrow).withCausingEntity(player).build());
        arrow.remove();
    }

    double armorTaken(LivingEntity target, String weapon) {
        double[] armor = {0};
        Listener probe = new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void armor(EntityDamageEvent event) {
                if (event.getEntity() == target && event.isApplicable(EntityDamageEvent.DamageModifier.ARMOR)) armor[0] = -event.getDamage(EntityDamageEvent.DamageModifier.ARMOR);
            }
        };
        getServer().getPluginManager().registerEvents(probe, this);
        if (weapon != null) shoot(weapon, target, 6);
        else {Arrow arrow = world.spawn(target.getLocation().add(0, 3, 0), Arrow.class); target.setNoDamageTicks(0);
            target.damage(6 * 1.25 + 0.25 * 400, DamageSource.builder(DamageType.ARROW).withDirectEntity(arrow).withCausingEntity(player).build()); arrow.remove();}
        org.bukkit.event.HandlerList.unregisterAll(probe);
        return armor[0];
    }

    void finish(Throwable error) {
        if (done) return;
        done = true;
        try {Files.writeString(Path.of("arkhaia-result.txt"), error == null ? "PASS " + checks + " Paper checks" : "FAIL " + error);} catch (Exception ignored) {}
        if (error != null) getLogger().log(java.util.logging.Level.SEVERE, "Adventurer ability probe failed", error);
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 5);
    }
}
