import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.elemental.ElementalStats;
import com.example.advancemagic.elemental.StaffSpellService;
import com.example.advancemagic.item.ArmorSetService;
import com.example.advancemagic.item.ElementalStaffService.Element;
import com.example.advancemagic.item.LegendaryWeaponService.Weapon;
import com.example.forge.AffixData;
import com.example.forge.AffixService;
import com.example.forge.AffixType;
import com.example.forge.ForgeApi;
import com.example.forge.ForgePlugin;
import com.example.forge.GemService.Gem;
import com.example.forge.GemType;
import com.example.forge.LootCategory;
import com.example.forge.Rarity;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** With Advance Magic: real legendary weapons, armor sets and staffs go through the forge and keep working. */
final class ForgeMagicGearChecks {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    static void run(ForgeProbe probe, ForgePlugin forge) {
        var magic = (AdvanceMagicPlugin) Bukkit.getPluginManager().getPlugin("advance-magic");
        probe.check(magic != null && magic.isEnabled(), "Advance Magic enabled beside the forge");
        var registration = Bukkit.getServicesManager().getRegistration(ElementalStats.Provider.class);
        probe.check(registration != null && registration.getPlugin() == forge, "the forge provides Advance Magic's cast stats");
        AffixService affixes = forge.affixes();

        // Armor sets: identity, AM's own armor modifiers and the forge's all survive; AM's upkeep has nothing to fix.
        var sets = new ArmorSetService(magic);
        for (var set : ArmorSetService.ArmorSet.values()) {
            for (var piece : ArmorSetService.Piece.values()) {
                ItemStack item = sets.create(set, piece);
                String name = PLAIN.serialize(item.getItemMeta().customName());
                probe.check(ForgeApi.affix(item, "MYTHIC", new Random(piece.ordinal() + 11)), set + " " + piece + " takes mythic affixes");
                var modifiers = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS).modifiers();
                probe.check(sets.set(item) == set && !sets.migrate(item) && name.equals(PLAIN.serialize(item.getItemMeta().customName()))
                        && modifiers.stream().anyMatch(e -> e.modifier().getKey().getNamespace().equals("advance_magic"))
                        && !affixes.ensure(item) && !affixes.salvageable(item),
                    set + " " + piece + " keeps its set identity, name and 1.5x armor, stays intact and cannot be salvaged");
            }
        }

        // Legendary weapons: the held-weapon upkeep (every 4 ticks) keeps the forge's lore and modifiers.
        for (Weapon weapon : Weapon.values()) {
            ItemStack item = magic.legendaryWeapons().create(weapon);
            probe.check(ForgeApi.affix(item, "MYTHIC", new Random(weapon.ordinal() + 21)), weapon + " takes mythic affixes");
            boolean stable = true;
            for (int i = 0; i < 3; i++) stable &= !magic.legendaryWeapons().migrate(item);
            List<String> lore = item.getItemMeta().lore().stream().map(PLAIN::serialize).toList();
            var modifiers = item.getData(DataComponentTypes.ATTRIBUTE_MODIFIERS).modifiers();
            probe.check(stable && magic.legendaryWeapons().identify(item) == weapon && !affixes.ensure(item)
                    && lore.getFirst().startsWith("✦ เกรด") && lore.getLast().equals("ย่อแล้วตีเพื่อใช้สกิล")
                    && modifiers.stream().anyMatch(e -> e.modifier().getKey().getKey().equals("legendary_damage"))
                    && modifiers.stream().anyMatch(e -> e.modifier().getKey().getNamespace().equals("evergardenforge"))
                    && !affixes.salvageable(item),
                weapon + " stays a working legendary with forge lore and bonuses through repeated upkeep: " + lore);
        }

        // Staffs: AM's lore rebuild keeps the forge block; the affixes and gems reach the real cast stats.
        StaffSpellService spells = magic.staffSpells();
        ItemStack staff = spells.createStaff(Element.FIRE, StaffSpellService.Tier.MYTHIC);
        ItemStack wooden = spells.createWoodenStaff();
        probe.check(affixes.category(staff) == LootCategory.STAFF && affixes.category(wooden) == LootCategory.STAFF, "real fire and wooden staffs are staffs");
        Map<AffixType, Double> levels = new LinkedHashMap<>();
        levels.put(AffixType.ARCANE, 1.0);
        levels.put(AffixType.WELLSPRING, 1.0);
        levels.put(AffixType.FRUGAL, 1.0);
        levels.put(AffixType.FLOW, 1.0);
        affixes.apply(staff, new AffixData(Rarity.MYTHIC, levels, 2));
        affixes.setGem(staff, 0, new Gem(GemType.FIRE, 5));
        affixes.setGem(staff, 1, new Gem(GemType.LIFE, 5));
        spells.refresh(staff);
        probe.check(magic.staffs().element(staff) == Element.FIRE && !affixes.ensure(staff) && !affixes.salvageable(staff),
            "the staff keeps its element, AM's lore rebuild keeps the forge block, and it cannot be salvaged");

        Player player = fake();
        try {
            player.getInventory().setItemInMainHand(spells.createStaff(Element.FIRE, StaffSpellService.Tier.MYTHIC));
            ElementalStats.Snapshot plain = magic.elementalStats().snapshot(player);
            player.getInventory().setItemInMainHand(staff);
            ElementalStats.Snapshot forged = magic.elementalStats().snapshot(player);
            double life = 0.05 * 3.6, fire = 0.05 * 3.6;
            probe.check(Math.abs(forged.spellPower() - plain.spellPower() * 1.25) < 1e-6
                    && Math.abs(forged.healingPower() - plain.healingPower() * (1 + life)) < 1e-6
                    && Math.abs(forged.maxMana() - Math.min(300, plain.maxMana() + 25)) < 1e-6
                    && Math.abs(forged.manaReduction() - Math.min(ElementalStats.MAX_REDUCTION, plain.manaReduction() + 0.08)) < 1e-6
                    && Math.abs(forged.manaRegenBonus() - Math.min(1.5, plain.manaRegenBonus() + 0.25)) < 1e-6
                    && Math.abs(forged.elementBonus(Element.FIRE) - Math.min(1, plain.elementBonus(Element.FIRE) + fire)) < 1e-6
                    && forged.elementBonus(Element.WATER) == plain.elementBonus(Element.WATER),
                "held forged staff: spell power x1.25, healing +18%, +25 max mana, -8% mana cost, +25% regen, fire +18%: "
                    + plain + " -> " + forged);
            player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
            ElementalStats.Snapshot sword = magic.elementalStats().snapshot(player);
            probe.check(sword.spellPower() == plain.spellPower() && sword.maxMana() == plain.maxMana(), "putting the staff away removes the bonuses");
        } finally {
            player.getInventory().clear();
            player.setGameMode(GameMode.SPECTATOR);
        }
    }

    private static Player fake() {
        World world = Bukkit.getWorlds().getFirst();
        var server = MinecraftServer.getServer();
        var level = ((CraftWorld) world).getHandle();
        var profile = new GameProfile(UUID.randomUUID(), "ForgeMage");
        var actor = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        connection.channel = new EmbeddedChannel();
        connection.address = new InetSocketAddress("127.0.0.1", 1);
        actor.connection = new ServerGamePacketListenerImpl(server, connection, actor, CommonListenerCookie.createInitial(profile, false)) {
            @Override public boolean hasClientLoaded() { return true; }
        };
        actor.setPos(.5, world.getHighestBlockYAt(0, 0) + 2, .5);
        server.getPlayerList().getPlayers().add(actor);
        server.getPlayerList().getPlayersByUUID().put(profile.id(), actor);
        level.addNewPlayer(actor);
        Player player = actor.getBukkitEntity();
        player.setGravity(false);
        player.setInvulnerable(true);
        player.setGameMode(GameMode.SURVIVAL);
        return player;
    }
}
