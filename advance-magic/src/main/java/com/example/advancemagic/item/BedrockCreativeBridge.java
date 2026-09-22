package com.example.advancemagic.item;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.spell.Spell;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Bridges Bedrock Edition creative inventory actions with Java server.
 * Intercepts Bedrock ItemStackRequestPacket to detect custom item selections,
 * and directly spawns the full custom ItemStack into the Bedrock player's
 * inventory with proper Geyser reverse sync.
 */
public final class BedrockCreativeBridge implements Listener {
    private final AdvanceMagicPlugin plugin;
    private final Map<String, Long> lastSpawnTime = new ConcurrentHashMap<>();
    private final Set<UUID> hookedPlayers = Collections.synchronizedSet(new HashSet<>());
    private boolean geyserPresent = false;

    public BedrockCreativeBridge(AdvanceMagicPlugin plugin) {
        this.plugin = plugin;
        try {
            Class.forName("org.geysermc.geyser.api.GeyserApi");
            geyserPresent = true;
            plugin.getLogger().info("[BedrockCreativeBridge] Geyser detected! Bedrock creative mode item spawning is enabled.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("[BedrockCreativeBridge] Geyser not found. Bedrock creative bridge is inactive.");
        }
    }

    public void init() {
        if (!geyserPresent) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            hookPlayer(p);
        }
    }

    private static Object invokeMethod(Object target, String methodName, Class<?>[] paramTypes, Object[] args) throws Exception {
        if (target == null) return null;
        Method m = null;
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                m = clazz.getDeclaredMethod(methodName, paramTypes == null ? new Class<?>[0] : paramTypes);
                break;
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            }
        }
        if (m == null) {
            m = target.getClass().getMethod(methodName, paramTypes == null ? new Class<?>[0] : paramTypes);
        }
        m.setAccessible(true);
        return m.invoke(target, args == null ? new Object[0] : args);
    }

    public void hookPlayer(Player player) {
        if (!geyserPresent || player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Method apiMethod = apiClass.getMethod("api");
            Object api = apiMethod.invoke(null);
            Method connMethod = apiClass.getMethod("connectionByUuid", UUID.class);
            Object conn = connMethod.invoke(api, uuid);
            if (conn == null) return; // Not a Bedrock player

            Object upstream = invokeMethod(conn, "getUpstream", null, null);
            if (upstream == null) return;

            Object bedrockSession = invokeMethod(upstream, "getSession", null, null);
            if (bedrockSession == null) return;

            Object currentHandler = invokeMethod(bedrockSession, "getPacketHandler", null, null);
            if (currentHandler == null) return;

            if (Proxy.isProxyClass(currentHandler.getClass())) {
                InvocationHandler existing = Proxy.getInvocationHandler(currentHandler);
                if (existing instanceof CreativePacketInterceptor) {
                    hookedPlayers.add(uuid);
                    return; // Already hooked
                }
            }

            Set<Class<?>> interfaces = new LinkedHashSet<>();
            for (Class<?> c = currentHandler.getClass(); c != null; c = c.getSuperclass()) {
                Collections.addAll(interfaces, c.getInterfaces());
            }
            try {
                interfaces.add(Class.forName("org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler"));
            } catch (ClassNotFoundException ignored) {}

            CreativePacketInterceptor interceptor = new CreativePacketInterceptor(player, conn, currentHandler);
            Object proxy = Proxy.newProxyInstance(
                currentHandler.getClass().getClassLoader(),
                interfaces.toArray(new Class<?>[0]),
                interceptor
            );

            Method setPacketHandler = bedrockSession.getClass().getMethod("setPacketHandler",
                Class.forName("org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler"));
            setPacketHandler.setAccessible(true);
            setPacketHandler.invoke(bedrockSession, proxy);
            hookedPlayers.add(uuid);
            plugin.getLogger().info("[BedrockCreativeBridge] Hooked creative packet listener for Bedrock player " + player.getName());
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[BedrockCreativeBridge] Could not hook player " + player.getName(), t);
        }
    }

    private final class CreativePacketInterceptor implements InvocationHandler {
        private final Player player;
        private final Object geyserSession;
        private final Object delegate;

        CreativePacketInterceptor(Player player, Object geyserSession, Object delegate) {
            this.player = player;
            this.geyserSession = geyserSession;
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (args != null && args.length == 1 && args[0] != null) {
                Object packet = args[0];
                String packetName = packet.getClass().getSimpleName();
                if ("ItemStackRequestPacket".equals(packetName)) {
                    try {
                        onItemStackRequest(player, geyserSession, packet);
                    } catch (Throwable t) {
                        plugin.getLogger().log(Level.WARNING, "[BedrockCreativeBridge] Error in onItemStackRequest", t);
                    }
                }
            }
            return method.invoke(delegate, args);
        }
    }

    private void onItemStackRequest(Player player, Object session, Object packet) {
        try {
            List<?> requests = (List<?>) invokeMethod(packet, "getRequests", null, null);
            if (requests == null) return;

            for (Object req : requests) {
                Object actionsObj = invokeMethod(req, "getActions", null, null);
                Object[] actions = actionsObj instanceof Object[] a ? a :
                                   actionsObj instanceof List<?> l ? l.toArray() : null;
                if (actions == null) continue;

                int creativeNetId = -1;
                for (Object action : actions) {
                    if (action == null) continue;
                    if (action.getClass().getSimpleName().contains("CraftCreative")) {
                        Object netIdObj = invokeMethod(action, "getCreativeItemNetworkId", null, null);
                        if (netIdObj instanceof Integer id) {
                            creativeNetId = id;
                        }
                        break;
                    }
                }

                if (creativeNetId > 0) {
                    Object itemMappings = invokeMethod(session, "getItemMappings", null, null);
                    List<?> creativeList = (List<?>) invokeMethod(itemMappings, "getCreativeItems", null, null);
                    int index = creativeNetId - 1;
                    if (creativeList != null && index >= 0 && index < creativeList.size()) {
                        Object creativeItem = creativeList.get(index);
                        Object itemData = invokeMethod(creativeItem, "getItem", null, null);
                        Object def = invokeMethod(itemData, "getDefinition", null, null);
                        String identifier = (String) invokeMethod(def, "getIdentifier", null, null);

                        if (identifier != null && (identifier.startsWith("advance_magic:") || identifier.startsWith("voidscape:") || identifier.startsWith("evergarden:"))) {
                            // Debounce duplicate clicks within 350ms
                            long now = System.currentTimeMillis();
                            String debounceKey = player.getUniqueId() + ":" + identifier;
                            Long last = lastSpawnTime.get(debounceKey);
                            if (last != null && now - last < 350L) {
                                continue;
                            }
                            lastSpawnTime.put(debounceKey, now);

                            plugin.getLogger().info("[BedrockCreativeBridge] Bedrock creative spawn triggered: " + identifier + " for " + player.getName());
                            Bukkit.getScheduler().runTask(plugin, () -> spawnItemDirectly(player, identifier));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[BedrockCreativeBridge] Error in onItemStackRequest", t);
        }
    }

    private void spawnItemDirectly(Player player, String identifier) {
        if (!player.isOnline() || player.getGameMode() != org.bukkit.GameMode.CREATIVE) return;
        ItemStack item = resolveCustomItem(identifier);
        if (item == null) return;

        int heldSlot = player.getInventory().getHeldItemSlot();
        ItemStack held = player.getInventory().getItem(heldSlot);
        if (held == null || held.getType().isAir()) {
            player.getInventory().setItem(heldSlot, item);
        } else {
            var left = player.getInventory().addItem(item);
            if (!left.isEmpty()) {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
        player.updateInventory();
        plugin.getLogger().info("[BedrockCreativeBridge] Successfully spawned " + identifier + " into " + player.getName() + "'s inventory!");
    }

    private ItemStack resolveCustomItem(String identifier) {
        if (identifier == null) return null;

        // 1. Advance Magic Wands & Cores
        if (identifier.startsWith("advance_magic:core_")) {
            String spellId = identifier.substring("advance_magic:core_".length());
            Spell spell = Spell.parse(spellId);
            if (spell != null) return plugin.wands().createCore(spell);
        } else if (identifier.startsWith("advance_magic:")) {
            String spellId = identifier.substring("advance_magic:".length());
            Spell spell = Spell.parse(spellId);
            if (spell != null) return plugin.wands().create(spell);
        }

        // 2. Evergarden / Voidscape Relics & Cores
        if (identifier.startsWith("voidscape:") || identifier.startsWith("evergarden:")) {
            String relicId = identifier.replace("voidscape:", "").replace("evergarden:", "").toLowerCase(Locale.ROOT);
            Plugin evergarden = Bukkit.getPluginManager().getPlugin("Evergarden");
            if (evergarden != null) {
                try {
                    Method relicsMethod = evergarden.getClass().getMethod("relics");
                    Object relicService = relicsMethod.invoke(evergarden);

                    // Check if it's a magic core
                    if (relicId.startsWith("core_")) {
                        String coreId = relicId.substring("core_".length());
                        Method createCoreMethod = relicService.getClass().getMethod("createMagicCore", String.class);
                        return (ItemStack) createCoreMethod.invoke(relicService, coreId);
                    }

                    // Check if it is an Evergarden crop or seed
                    if (relicId.startsWith("seed_") || relicId.startsWith("crop_")) {
                        boolean isSeed = relicId.startsWith("seed_");
                        String cropRawId = relicId.substring(isSeed ? "seed_".length() : "crop_".length());
                        Method cropsMethod = evergarden.getClass().getMethod("crops");
                        Object cropService = cropsMethod.invoke(evergarden);
                        Method factoryMethod = cropService.getClass().getMethod("factory");
                        Object cropFactory = factoryMethod.invoke(cropService);

                        Class<?> cropTypeClass = Class.forName("com.example.voidscape.crop.CropType");
                        Method fromIdMethod = cropTypeClass.getMethod("fromId", String.class);
                        Object cropType = fromIdMethod.invoke(null, cropRawId);
                        if (cropType != null) {
                            if (isSeed) {
                                Method createSeed = cropFactory.getClass().getMethod("createSeed", cropTypeClass, int.class);
                                return (ItemStack) createSeed.invoke(cropFactory, cropType, 16);
                            } else {
                                Method createFood = cropFactory.getClass().getMethod("createFood", cropTypeClass, int.class);
                                return (ItemStack) createFood.invoke(cropFactory, cropType, 16);
                            }
                        }
                    }

                    // Check if it's a relic
                    Class<?> relicEnum = Class.forName("com.example.voidscape.item.RelicService$Relic");
                    for (Object enumConstant : relicEnum.getEnumConstants()) {
                        String id = (String) invokeMethod(enumConstant, "id", null, null);
                        if (relicId.equalsIgnoreCase(id)) {
                            Method createMethod = relicService.getClass().getMethod("create", relicEnum, int.class);
                            return (ItemStack) createMethod.invoke(relicService, enumConstant, 1);
                        }
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("[BedrockCreativeBridge] Could not resolve Evergarden item " + identifier + ": " + t.getMessage());
                }
            }
        }

        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> hookPlayer(p), 10L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> hookPlayer(p), 30L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        hookedPlayers.remove(uuid);
        lastSpawnTime.keySet().removeIf(k -> k.startsWith(uuid.toString()));
    }

}
