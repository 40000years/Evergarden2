import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.dungeon.DungeonManager;
import com.example.voidscape.item.RelicService;
import com.example.voidscape.item.VaultLootTable;
import com.example.voidscape.world.DungeonLayout;
import com.example.voidscape.world.DungeonLayout.Site;
import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.spell.Spell;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.server.network.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Real players, inventories, events and files on disposable Paper; never bundled in the release. */
public final class VaultSecurityProbe extends JavaPlugin {
    VoidscapePlugin garden;
    AdvanceMagicPlugin magic;
    DungeonManager dungeons;
    YamlConfiguration ledger;
    final List<ServerPlayer> actors = new ArrayList<>();
    int checks;

    void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
        checks++;
        getLogger().info("PASS Vault: " + label);
    }
    Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target);
    }
    void setField(Object target, String name, Object value) throws Exception {
        Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); f.set(target, value);
    }
    String sitePath(Site site) { return "sites." + site.id(); }
    String claimPath(Site site, Player player) { return sitePath(site) + ".opened." + player.getUniqueId(); }
    ItemStack[] inventory(Player player) {
        return Arrays.stream(player.getInventory().getContents()).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
    }
    void keys(Player player, int count, boolean offhand) {
        ItemStack item = garden.relics().createVoidKey(); item.setAmount(count);
        if (offhand) player.getInventory().setItemInOffHand(item);
        else player.getInventory().setItemInMainHand(item);
    }
    int keyCount(Player player) {
        return Arrays.stream(player.getInventory().getContents()).filter(garden.relics()::isVoidKey).mapToInt(ItemStack::getAmount).sum();
    }
    PlayerInteractEvent click(Player player, Site site, Action action, EquipmentSlot hand) {
        Block vault = garden.world().getBlockAt(site.x(), 97, site.z() - 16);
        if (vault.getType() != Material.VAULT) throw new AssertionError("Generated vault is missing at " + vault.getLocation());
        PlayerInteractEvent e = new PlayerInteractEvent(player, action,
            hand == EquipmentSlot.HAND ? player.getInventory().getItemInMainHand() : player.getInventory().getItemInOffHand(),
            vault, BlockFace.UP, hand);
        Bukkit.getPluginManager().callEvent(e);
        return e;
    }
    void saveLedger() throws Exception {
        Method save = DungeonManager.class.getDeclaredMethod("save"); save.setAccessible(true);
        check(Boolean.TRUE.equals(save.invoke(dungeons)), "claim fixture saved through the actual atomic ledger writer");
    }

    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            boolean baseline = Boolean.getBoolean("vault.probe.baseline");
            try {
                run(baseline);
                Files.writeString(Path.of("vault-security-result.txt"), (baseline ? "REPRODUCED " : "PASS ") + checks + " real Paper checks");
            } catch (Throwable error) {
                getLogger().log(java.util.logging.Level.SEVERE, "VAULT CHECK FAILED", error);
                try { Files.writeString(Path.of("vault-security-result.txt"), "FAIL " + error); } catch (Exception ignored) {}
            } finally {
                cleanup(); Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 5);
            }
        }, 40);
    }

    void run(boolean baseline) throws Exception {
        garden = (VoidscapePlugin) Bukkit.getPluginManager().getPlugin("Evergarden");
        magic = (AdvanceMagicPlugin) Bukkit.getPluginManager().getPlugin("advance-magic");
        check(garden != null && garden.isEnabled() && magic != null && magic.isEnabled(), "both release plugins boot");
        dungeons = garden.dungeons(); ledger = (YamlConfiguration) field(dungeons, "ledger");
        Site site = DungeonLayout.STARTER_SITES.getFirst();
        if (baseline) { reproduce(site); return; }
        Path checkpoint = Path.of("vault-checkpoint.txt");
        if (Files.exists(checkpoint)) { restart(site, Files.readAllLines(checkpoint)); return; }
        oddsAndNames();
        Player p = actor(site, "VaultClaimant", UUID.randomUUID());
        keys(p, 32, false);
        ItemStack[] before = inventory(p);
        click(p, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.OFF_HAND);
        check(Arrays.equals(before, inventory(p)) && !ledger.getBoolean(claimPath(site, p)), "duplicate offhand event cannot roll or consume a key");
        check(click(p, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND).isCancelled(), "custom Vault intercepts the vanilla block event");
        check(keyCount(p) == 31 && ledger.getBoolean(claimPath(site, p)), "first main-hand use consumes exactly one key and commits one claim");
        YamlConfiguration disk = YamlConfiguration.loadConfiguration((java.io.File) field(dungeons, "file"));
        check(disk.getBoolean(claimPath(site, p)), "first reward entitlement is persisted before delivery");
        String receipt = sitePath(site) + ".vault-claims." + p.getUniqueId();
        check(p.getName().equals(disk.getString(receipt + ".player")) && disk.getLong(receipt + ".at") > 0
            && disk.getString(receipt + ".reward") != null, "persisted receipt identifies player, UUID, time and actual reward");
        ItemStack[] claimed = inventory(p);
        for (int i = 0; i < 100; i++) click(p, site, i % 2 == 0 ? Action.RIGHT_CLICK_BLOCK : Action.LEFT_CLICK_BLOCK, EquipmentSlot.HAND);
        check(Arrays.equals(claimed, inventory(p)), "100 repeated left/right clicks cannot reroll or spend additional keys");
        p.setSneaking(true); p.setGameMode(GameMode.CREATIVE);
        click(p, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        check(Arrays.equals(claimed, inventory(p)), "sneaking Creative player cannot reroll a claimed production Vault");
        p.setGameMode(GameMode.SURVIVAL); p.setOp(true);
        click(p, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        check(Arrays.equals(claimed, inventory(p)), "sneaking operator cannot reroll a claimed production Vault");
        p.setOp(false);
        var permission = p.addAttachment(this, "evergarden.admin", true);
        click(p, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        check(Arrays.equals(claimed, inventory(p)), "sneaking delegated admin cannot reroll a claimed production Vault");
        p.removeAttachment(permission); p.setSneaking(false);
        ledger.set(sitePath(site) + ".next-open", System.currentTimeMillis() + 600000);
        ledger.set(sitePath(DungeonLayout.STARTER_SITES.get(1)) + ".next-open", System.currentTimeMillis() + 600000);
        ledger.set(sitePath(site) + ".test-metadata", "preserved"); saveLedger();
        dungeons.resetAllCooldowns();
        check(!ledger.contains(sitePath(site) + ".next-open") && !ledger.contains(sitePath(DungeonLayout.STARTER_SITES.get(1)) + ".next-open"), "reset clears only temple cooldowns across multiple sites");
        check(ledger.getBoolean(claimPath(site, p)) && "preserved".equals(ledger.getString(sitePath(site) + ".test-metadata"))
            && ledger.contains(receipt), "reset preserves legacy claims, receipts and all other per-site metadata");
        click(p, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        check(Arrays.equals(claimed, inventory(p)), "cooldown reset cannot give the same player another reward");
        Player offhand = actor(site, "VaultOffhand", UUID.randomUUID()); keys(offhand, 2, true);
        click(offhand, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        check(keyCount(offhand) == 1 && ledger.getBoolean(claimPath(site, offhand)), "main-hand interaction can consume a genuine offhand key exactly once");
        Player otherSite = actor(DungeonLayout.STARTER_SITES.get(1), "VaultTraveler", UUID.randomUUID()); keys(otherSite, 4, false);
        click(otherSite, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        click(otherSite, DungeonLayout.STARTER_SITES.get(1), Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        check(keyCount(otherSite) == 2 && ledger.getBoolean(claimPath(site, otherSite))
            && ledger.getBoolean(claimPath(DungeonLayout.STARTER_SITES.get(1), otherSite)), "a different genuine temple still gives its own legitimate reward");
        failureChecks(site);
        Files.writeString(checkpoint, p.getUniqueId() + "\n" + ledger.getString(receipt + ".reward"));
        garden.world().save();
    }

    void reproduce(Site site) throws Exception {
        Player p = actor(site, "OldVaultClaimant", UUID.randomUUID()); keys(p, 8, false);
        click(p, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        check(ledger.getBoolean(claimPath(site, p)) && keyCount(p) == 7, "baseline first claim succeeds");
        p.setSneaking(true); p.setGameMode(GameMode.CREATIVE);
        click(p, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        check(keyCount(p) == 6, "VULNERABILITY: sneaking Creative repeats the same Vault roll");
        p.setSneaking(false); p.setGameMode(GameMode.SURVIVAL);
        ledger.set(sitePath(site) + ".next-open", System.currentTimeMillis() + 600000);
        dungeons.resetAllCooldowns();
        check(!ledger.getBoolean(claimPath(site, p)), "VULNERABILITY: cooldown reset deletes the existing player claim");
        click(p, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        check(keyCount(p) == 5, "VULNERABILITY: ordinary survival player rerolls after admin cooldown reset");
        Player failed = actor(site, "OldVaultDiskFull", UUID.randomUUID()); keys(failed, 2, false);
        Path tmp = ((java.io.File) field(dungeons, "file")).toPath().resolveSibling("dungeons.yml.tmp");
        Files.createDirectory(tmp);
        try {
            ItemStack[] before = inventory(failed);
            click(failed, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
            check(!Arrays.equals(before, inventory(failed)) && keyCount(failed) == 1 && !(boolean) field(dungeons, "storageHealthy"),
                "VULNERABILITY: rewards and key consumption happen even when the ledger cannot save");
            var disk = YamlConfiguration.loadConfiguration((java.io.File) field(dungeons, "file"));
            check(!disk.getBoolean(claimPath(site, failed)), "VULNERABILITY: rewarded claim is absent on disk and eligible again after restart");
        } finally { Files.delete(tmp); }
    }

    void failureChecks(Site site) throws Exception {
        Player p = actor(site, "VaultDiskFull", UUID.randomUUID()); keys(p, 2, false);
        Path tmp = ((java.io.File) field(dungeons, "file")).toPath().resolveSibling("dungeons.yml.tmp");
        Files.createDirectory(tmp);
        try {
            ItemStack[] before = inventory(p);
            click(p, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
            check(Arrays.equals(before, inventory(p)) && !ledger.getBoolean(claimPath(site, p)), "failed save gives no reward, consumes no key and rolls back the claim");
            check(!ledger.contains(sitePath(site) + ".vault-claims." + p.getUniqueId()), "failed save leaves no successful audit receipt");
            check(!(boolean) field(dungeons, "storageHealthy"), "failed writer marks storage unavailable");
            Files.delete(tmp);
            click(p, DungeonLayout.STARTER_SITES.get(1), Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
            check(Arrays.equals(before, inventory(p)), "unhealthy storage blocks other Vaults too even if a later write might succeed");
            String cooldown = sitePath(site) + ".next-open";
            ledger.set(cooldown, 123456789L);
            dungeons.resetAllCooldowns();
            check(ledger.getLong(cooldown) == 123456789L, "cooldown reset refuses to mutate unhealthy storage");
        } finally { if (Files.exists(tmp)) Files.delete(tmp); }
        setField(dungeons, "storageHealthy", true); // Recover only this disposable test fixture.
        String cooldown = sitePath(site) + ".next-open";
        saveLedger(); Files.createDirectory(tmp);
        try {
            dungeons.resetAllCooldowns();
            check(ledger.getLong(cooldown) == 123456789L && !(boolean) field(dungeons, "storageHealthy"), "failed cooldown save rolls back its removals and disables further rewards");
        } finally { Files.delete(tmp); }
        setField(dungeons, "storageHealthy", true); saveLedger();
    }

    void restart(Site site, List<String> pending) throws Exception {
        UUID uuid = UUID.fromString(pending.getFirst());
        Player renamed = actor(site, "NewNameSameUUID", uuid); keys(renamed, 4, false);
        String receipt = sitePath(site) + ".vault-claims." + uuid;
        check(ledger.getBoolean(claimPath(site, renamed)), "claimed UUID survives a complete Paper restart and player name change");
        check(pending.get(1).equals(ledger.getString(receipt + ".reward")) && "VaultClaimant".equals(ledger.getString(receipt + ".player")), "the original chosen reward and claimant name stay in the audit receipt");
        ItemStack[] before = inventory(renamed);
        click(renamed, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        dungeons.resetAllCooldowns();
        click(renamed, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        check(Arrays.equals(before, inventory(renamed)), "renaming and another cooldown reset after restart cannot reopen the same Vault");
        Player independent = actor(site, "OtherRealAccount", UUID.randomUUID()); keys(independent, 2, false);
        click(independent, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        check(keyCount(independent) == 1 && ledger.getBoolean(claimPath(site, independent)), "a different UUID retains an independent legitimate claim");
        Player legacy = actor(site, "LegacyVaultClaim", UUID.randomUUID()); keys(legacy, 2, false);
        ledger.set(claimPath(site, legacy), true); saveLedger();
        before = inventory(legacy);
        click(legacy, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        check(Arrays.equals(before, inventory(legacy)) && !ledger.contains(sitePath(site) + ".vault-claims." + legacy.getUniqueId()), "legacy boolean-only claims are honored without requiring a new receipt");
    }

    void oddsAndNames() {
        int[] counts = new int[VaultLootTable.Reward.values().length];
        for (int ticket = 0; ticket < 10000; ticket++) counts[VaultLootTable.reward(ticket).ordinal()]++;
        check(Arrays.equals(counts, new int[]{1500,1200,700,500,5850,50,50,150}), "all 10000 tickets preserve the exact category weights");
        Set<Spell> mythics = EnumSet.of(Spell.SHULKER_LEVITATION, Spell.SOLAR_APOCALYPSE, Spell.CHRONOS_FINAL_HOUR, Spell.HEAVENS_JUDGMENT);
        Map<Spell,Integer> mythicCounts = new EnumMap<>(Spell.class);
        for (int ticket = 9900; ticket < 9950; ticket++) for (int pick = 0; pick < 4; pick++) {
            Spell spell = magic.wands().coreSpell(garden.relics().rollVaultReward(new TicketRandom(ticket, pick)));
            check(mythics.contains(spell), "mythic factory yields an authenticated mythic core");
            mythicCounts.merge(spell, 1, Integer::sum);
        }
        check(mythicCounts.size() == 4 && mythicCounts.values().stream().allMatch(n -> n == 50), "Heaven receives exactly 50 of 40000 equally weighted combined draws = 0.125 percent");
        for (int pick = 0; pick < 14; pick++) {
            Spell spell = magic.wands().coreSpell(garden.relics().rollVaultReward(new TicketRandom(2700, pick)));
            check(spell != null && !mythics.contains(spell), "ordinary core pool cannot leak a mythic core");
        }
        ItemStack fakeKey = new ItemStack(Material.TRIAL_KEY); var meta = fakeKey.getItemMeta();
        meta.displayName(garden.relics().createVoidKey().getItemMeta().displayName()); fakeKey.setItemMeta(meta);
        check(!garden.relics().isVoidKey(fakeKey), "renamed vanilla Trial Key cannot impersonate an Evergarden Key");
        ItemStack heaven = garden.relics().createMagicCore("heavens_judgment");
        ItemStack fake = new ItemStack(Material.HEART_OF_THE_SEA); meta = fake.getItemMeta(); meta.displayName(heaven.getItemMeta().displayName());
        meta.lore(heaven.getItemMeta().lore()); fake.setItemMeta(meta);
        check(magic.wands().coreSpell(fake) == null, "copied Heaven core name and lore cannot authenticate a vanilla item");
        ItemStack ordinary = garden.relics().createMagicCore("lightning_strike"); meta = ordinary.getItemMeta();
        meta.displayName(heaven.getItemMeta().displayName()); meta.lore(heaven.getItemMeta().lore()); ordinary.setItemMeta(meta);
        check(magic.wands().coreSpell(ordinary) == Spell.LIGHTNING_STRIKE, "renaming a genuine ordinary core to Heaven cannot upgrade its recipe");
        meta.getPersistentDataContainer().set(new NamespacedKey("evergarden", "magic_core"), PersistentDataType.STRING, "heavens_judgment");
        ordinary.setItemMeta(meta);
        check(magic.wands().coreSpell(ordinary) == null, "conflicting core tags fail authentication instead of selecting the rarest name");
        Site site = DungeonLayout.STARTER_SITES.getFirst(); Player p = actor(site, "VaultFakeKey", UUID.randomUUID());
        p.getInventory().setItemInMainHand(fakeKey); ItemStack[] before = inventory(p);
        click(p, site, Action.RIGHT_CLICK_BLOCK, EquipmentSlot.HAND);
        check(Arrays.equals(before, inventory(p)) && !ledger.getBoolean(claimPath(site, p)), "real Vault rejects a renamed vanilla key without a reward or claim");
    }
    static final class TicketRandom implements java.util.random.RandomGenerator {
        final int ticket, pick; int calls;
        TicketRandom(int ticket, int pick) { this.ticket = ticket; this.pick = pick; }
        @Override public long nextLong() { throw new AssertionError("Unexpected RNG method"); }
        @Override public int nextInt(int bound) {
            int value = calls++ == 0 ? ticket : pick;
            if (value < 0 || value >= bound || calls > 2) throw new AssertionError("Unexpected reroll or RNG bound");
            return value;
        }
    }

    Player actor(Site site, String name, UUID uuid) {
        var server = MinecraftServer.getServer(); var level = ((CraftWorld) garden.world()).getHandle(); var profile = new GameProfile(uuid, name);
        ServerPlayer handle = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
        var connection = new Connection(PacketFlow.SERVERBOUND); connection.channel = new EmbeddedChannel();
        connection.address = new InetSocketAddress("127.0.0.1", 1);
        handle.connection = new ServerGamePacketListenerImpl(server, connection, handle, CommonListenerCookie.createInitial(profile, false)) {
            @Override public boolean hasClientLoaded() { return true; }
        };
        handle.setPos(site.x() + .5, 98, site.z() - 14.5);
        server.getPlayerList().getPlayers().add(handle); server.getPlayerList().getPlayersByUUID().put(uuid, handle); level.addNewPlayer(handle);
        actors.add(handle); Player p = handle.getBukkitEntity(); p.setGravity(false); p.setInvulnerable(true); p.setGameMode(GameMode.SURVIVAL); return p;
    }
    void cleanup() {
        for (ServerPlayer handle : actors) {
            Player p = handle.getBukkitEntity(); if (p.isOp()) p.setOp(false);
            var server = MinecraftServer.getServer(); server.getPlayerList().getPlayers().remove(handle);
            server.getPlayerList().getPlayersByUUID().remove(handle.getUUID()); handle.discard();
        }
    }
}
