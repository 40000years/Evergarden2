import com.example.advancemagic.pack.delivery.ResourcePackManager;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Logger;

/** Exercises the real delivery code and Adventure requests with controlled ticks. */
@SuppressWarnings("unchecked")
public final class ResourcePackDeliveryChecks {
    static final UUID MAGIC = UUID.randomUUID(), GARDEN = UUID.randomUUID(), JUDGE = UUID.randomUUID();
    static final String HASH = "1234567890123456789012345678901234567890";
    static final String URL = "https://packs.example.org/";
    static final SimpleServicesManager services = new SimpleServicesManager();
    static final Map<UUID, Player> players = new HashMap<>();
    static final List<Scheduled> scheduled = new ArrayList<>();
    static final List<ResourcePackRequest> requests = new ArrayList<>();
    static final List<Scheduled> repeating = new ArrayList<>();
    static boolean online = true, failSend;
    static int checks, kicks;

    static final class Scheduled {
        final Plugin owner;
        final Runnable action;
        boolean cancelled;
        Scheduled(Plugin owner, Runnable action) { this.owner = owner; this.action = action; }
    }

    public static void main(String[] args) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        PluginManager plugins = proxy(PluginManager.class, (p, m, a) -> defaultValue(m.getReturnType()));
        BukkitScheduler scheduler = proxy(BukkitScheduler.class, (p, m, a) -> {
            if (m.getName().equals("runTask")||m.getName().equals("runTaskTimer")) {
                Scheduled task = new Scheduled((Plugin) a[0], (Runnable) a[1]);
                if(m.getName().equals("runTaskTimer"))repeating.add(task);else scheduled.add(task);
                return proxy(BukkitTask.class, (t, method, values) -> switch (method.getName()) {
                    case "cancel" -> { task.cancelled = true; yield null; }
                    case "isCancelled" -> task.cancelled;
                    case "getOwner" -> task.owner;
                    default -> defaultValue(method.getReturnType());
                });
            }
            return defaultValue(m.getReturnType());
        });
        Server server = proxy(Server.class, (p, m, a) -> switch (m.getName()) {
            case "getServicesManager" -> services;
            case "getPluginManager" -> plugins;
            case "getScheduler" -> scheduler;
            case "isPrimaryThread" -> true;
            case "getPlayer" -> players.get(a[0]);
            case "getLogger" -> Logger.getLogger("resource-pack-checks");
            case "getName", "getVersion", "getBukkitVersion", "getMinecraftVersion" -> "PackChecks";
            default -> defaultValue(m.getReturnType());
        });
        Bukkit.setServer(server);
        Plugin magic = plugin("magic"), garden = plugin("garden");
        Player player = player(UUID.randomUUID());
        var a = ResourcePackManager.connect(magic, MAGIC);
        var b = ResourcePackManager.connect(garden, GARDEN);
        var c = ResourcePackManager.connect(garden, JUDGE);
        check(services.getKnownServices().size() == 1, "one coordinator shared across owners");
        check(c.offer(player, URL + "judge.zip", HASH, "Judge", false, false), "queue judge");
        check(b.offer(player, URL + "garden.zip", HASH, "Garden", false, false), "queue garden");
        check(a.offer(player, URL + "magic.zip", HASH, "Magic", false, false), "queue magic");
        check(requests.isEmpty() && scheduled.size() == 1, "one task, no immediate sends");
        check(!a.offer(player, URL + "magic.zip", HASH, "Magic", false, true), "resend does not duplicate pending work");
        tick();
        check(requests.size() == 1, "three packs delivered in one batch");
        check(ids(requests.getLast()).equals(List.of(MAGIC, GARDEN, JUDGE)), "registration order keeps pack priority");
        check(!requests.getLast().replace() && !requests.getLast().required(), "preserve other plugins' packs and optional policy");
        check(!a.offer(player, URL + "magic.zip", HASH, "Magic", false, false), "suppress duplicate while downloading");
        check(!a.offer(player, URL + "magic.zip", HASH, "Magic", false, true), "suppress immediate repeated manual resend");
        reply(player, MAGIC, PlayerResourcePackStatusEvent.Status.DOWNLOADED);
        reply(player, MAGIC, PlayerResourcePackStatusEvent.Status.ACCEPTED);
        check(a.status(player).equals("DOWNLOADED"), "late acceptance does not regress progress");
        reply(player, MAGIC, PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        check(!a.offer(player, URL + "magic.zip", HASH, "Magic", false, false), "loaded pack is not offered again");
        check(a.offer(player, URL + "magic.zip", HASH, "Magic", false, true), "explicit resend after success");
        tick();
        check(ids(requests.getLast()).equals(List.of(MAGIC)), "resend touches only the requested pack");
        reply(player, MAGIC, PlayerResourcePackStatusEvent.Status.FAILED_RELOAD);
        check(a.status(player).equals("FAILED_RELOAD"), "reload failure recorded");
        check(!a.offer(player, URL + "magic.zip", HASH, "Magic", false, false), "failure does not cause automatic retry loop");
        check(a.offer(player, URL + "magic.zip", HASH, "Magic", false, true), "explicit retry after failure");
        tick();
        reply(player, MAGIC, PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        reply(player, MAGIC, PlayerResourcePackStatusEvent.Status.FAILED_RELOAD);
        check(a.status(player).equals("FAILED_RELOAD"), "later reload failure invalidates success");
        check(b.offer(player, URL + "garden.zip", "abcdefabcdefabcdefabcdefabcdefabcdefabcd", "Garden", false, false), "changed hash bypasses deduplication");
        check(c.offer(player, URL + "new-judge.zip", HASH, "Judge", false, false), "changed URL bypasses deduplication");
        tick();
        check(ids(requests.getLast()).equals(List.of(GARDEN, JUDGE)), "changed packs share one batch");

        expectInvalid(() -> a.offer(player, URL, "bad", "Magic", false, false));
        expectInvalid(() -> a.offer(player, "file:///tmp/pack.zip", HASH, "Magic", false, false));
        expectInvalid(() -> a.offer(player, "https://user:password@example.org/pack.zip", HASH, "Magic", false, false));
        // A change of policy is part of the offer identity. Required/optional
        // groups stay distinct and retain their original relative priority.
        int before = requests.size();
        a.offer(player, URL + "required.zip", HASH, "Magic", true, false);
        b.offer(player, URL + "optional.zip", HASH, "Garden", false, false);
        c.offer(player, URL + "optional.zip", HASH, "Judge", false, false);
        tick();
        check(requests.size() == before + 2, "mixed required policy creates two batches");
        check(requests.get(before).required() && ids(requests.get(before)).equals(List.of(MAGIC)), "only configured pack is required");
        check(!requests.getLast().required() && ids(requests.getLast()).equals(List.of(GARDEN, JUDGE)), "judge remains optional");

        failSend = true;
        a.offer(player, URL + "failed.zip", HASH, "Magic", false, false);
        tick();
        check(a.status(player).equals("SEND_FAILED"), "transport exception does not report success");
        failSend = false;
        check(a.offer(player, URL + "failed.zip", HASH, "Magic", false, true), "transport exception remains retryable");
        tick();

        b.offer(player, URL + "handoff.zip", HASH, "Garden", false, false);
        a.close();
        check(scheduled.getLast().owner == garden, "remaining plugin takes over scheduled work");
        tick();
        check(ids(requests.getLast()).equals(List.of(GARDEN)), "pending work survives host shutdown");
        c.offer(player, URL + "offline.zip", HASH, "Judge", false, false);
        online = false;
        manager().quit(new PlayerQuitEvent(player, ""));
        before = requests.size();
        tick();
        check(requests.size() == before, "disconnect cancels queued player work");
        online = true;
        check(c.offer(player, URL + "offline.zip", HASH, "Judge", false, false), "new connection can load the same cached pack");
        tick();
        check(c.describe(player).contains("Batches: 1"), "connection statistics reset");
        b.close(); c.close();
        check(services.getKnownServices().isEmpty(), "last owner releases service and session data");

        // Same binary package, separate plugin classloaders. The reflective
        // bridge shares one provider rather than creating a second coordinator.
        try (var first = loader(Path.of(args[0])); var second = loader(Path.of(args[1]))) {
            Class<?> typeA = first.loadClass(ResourcePackManager.class.getName());
            Class<?> typeB = second.loadClass(ResourcePackManager.class.getName());
            check(typeA != typeB, "test uses genuinely isolated delivery classes");
            Object clientA = typeA.getMethod("connect", Plugin.class, UUID.class).invoke(null, magic, MAGIC);
            Object clientB = typeB.getMethod("connect", Plugin.class, UUID.class).invoke(null, garden, GARDEN);
            offer(clientA, player, "isolated-magic"); offer(clientB, player, "isolated-garden");
            before = requests.size();
            tick();
            check(requests.size() == before + 1 && ids(requests.getLast()).equals(List.of(MAGIC, GARDEN)), "isolated loaders still share one batch");
            clientB.getClass().getMethod("hold", Player.class, int.class).invoke(clientB, player, 30);
            offer(clientA, player, "isolated-held");
            before = requests.size();
            tick();
            check(requests.size() == before, "a hold from the other loader keeps the batch back");
            clientB.getClass().getMethod("offer", Player.class, List.class, List.class, String.class, boolean.class, boolean.class)
                    .invoke(clientB, player, List.of(URL + "isolated-copy.zip", URL + "isolated-own.zip"), List.of(HASH, HASH), "isolated", false, false);
            tick();
            check(requests.size() == before + 1 && ids(requests.getLast()).equals(List.of(MAGIC, GARDEN)), "several addresses cross plugin loaders");
            clientA.getClass().getMethod("close").invoke(clientA);
            offer(clientB, player, "after-isolated-handoff");
            tick();
            check(ids(requests.getLast()).equals(List.of(GARDEN)), "cross-loader handoff remains usable");
            clientB.getClass().getMethod("close").invoke(clientB);
        }
        check(services.getKnownServices().isEmpty(), "isolated providers clean up");
        var required=ResourcePackManager.connect(magic,MAGIC);
        required.offer(player,URL+"required-check.zip",HASH,"Required",true,false);
        tick();
        reply(player,MAGIC,PlayerResourcePackStatusEvent.Status.DECLINED);
        validate();
        check(kicks==1,"declining a required pack disconnects the player");
        reply(player,MAGIC,PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        validate();
        check(kicks==1,"success stops required-pack enforcement");
        check(repeating.stream().allMatch(task->task.cancelled),"watchdog stops once required packs finish");
        required.offer(player,URL+"timeout.zip",HASH,"Required",true,false);
        tick();
        var sessions=ResourcePackManager.class.getDeclaredField("sessions");sessions.setAccessible(true);
        Object session=((Map<?,?>)sessions.get(manager())).get(player.getUniqueId());
        var attempts=session.getClass().getDeclaredField("attempts");attempts.setAccessible(true);
        Object attempt=((Map<?,?>)attempts.get(session)).get(MAGIC);
        var sentAt=attempt.getClass().getDeclaredField("sentAt");sentAt.setAccessible(true);
        sentAt.setLong(attempt,System.nanoTime()-180_000_000_000L);
        validate();
        check(kicks==1,"a slow three-minute download keeps the player connected");
        sentAt.setLong(attempt,System.nanoTime()-601_000_000_000L);
        validate();
        check(kicks==2,"a stalled required load disconnects after the deadline");
        required.unavailable(player);
        check(required.status(player).equals("UNAVAILABLE"),"missing required pack is explicit");
        validate();
        check(kicks==3,"a required pack with no valid URL cannot be bypassed");
        check(required.offer(player,URL+"required-check.zip",HASH,"Required",true,false),"corrected pack configuration can be offered again");
        tick();
        reply(player,MAGIC,PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD);
        validate();
        check(kicks==4,"failed required download disconnects");
        required.close();
        check(services.getKnownServices().isEmpty()&&repeating.stream().allMatch(task->task.cancelled),"required watchdog releases with the last owner");
        recovery(magic, garden);
        System.out.println("PASS: " + checks + " resource pack delivery checks");
    }

    /** One batch for every pack; when it breaks, the packs return one at a time and a failed one moves to its next address. */
    static void recovery(Plugin magic, Plugin garden) throws Exception {
        var m = ResourcePackManager.connect(magic, MAGIC);
        var g = ResourcePackManager.connect(garden, GARDEN);
        var j = ResourcePackManager.connect(garden, JUDGE);
        String copy = "abcdefabcdefabcdefabcdefabcdefabcdefabcd";
        int kicked = kicks;
        Player late = player(UUID.randomUUID());
        j.hold(late, 30);
        m.offer(late, URL + "late-magic.zip", HASH, "Magic", false, false);
        int before = requests.size();
        tick();
        check(requests.size() == before, "a held batch waits");
        Object session = sessionOf(late);
        var holds = session.getClass().getDeclaredField("holds"); holds.setAccessible(true);
        ((Map<UUID, Long>) holds.get(session)).replaceAll((id, deadline) -> System.nanoTime() - 1);
        validate();
        tick();
        check(requests.size() == before + 1 && ids(requests.getLast()).equals(List.of(MAGIC)), "a pack that never arrives stops holding the batch at its deadline");

        Player player = player(UUID.randomUUID());
        j.hold(player, 30);
        m.offer(player, List.of(URL + "m.zip"), List.of(HASH), "Magic", true, false);
        g.offer(player, List.of(URL + "g.zip"), List.of(HASH), "Garden", true, false);
        before = requests.size();
        tick();
        check(requests.size() == before, "the batch waits for a pack that is still being prepared");
        j.offer(player, List.of("https://copy.example.net/j.zip", URL + "j.zip"), List.of(copy, HASH), "Judge", true, false);
        tick();
        var batch = requests.getLast();
        check(requests.size() == before + 1 && ids(batch).equals(List.of(MAGIC, GARDEN, JUDGE)), "then every pack goes out in one batch");
        check(batch.packs().get(2).uri().getHost().equals("copy.example.net") && batch.packs().get(2).hash().equals(copy), "the public copy goes first, with its own hash");
        reply(player, JUDGE, PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD);
        reply(player, MAGIC, PlayerResourcePackStatusEvent.Status.DISCARDED);
        reply(player, GARDEN, PlayerResourcePackStatusEvent.Status.DISCARDED);
        validate();
        check(kicks == kicked, "a broken batch is recovered instead of disconnecting");
        check(m.status(player).equals("QUEUED") && j.status(player).equals("QUEUED"), "discarded and failed packs are queued again");
        tick();
        check(requests.size() == before + 2 && ids(requests.getLast()).equals(List.of(MAGIC)), "recovery sends one pack at a time, in priority order");
        reply(player, MAGIC, PlayerResourcePackStatusEvent.Status.ACCEPTED);
        tick();
        check(requests.size() == before + 2, "the next pack waits while one is loading");
        reply(player, MAGIC, PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        tick();
        check(ids(requests.getLast()).equals(List.of(GARDEN)), "garden follows once magic has loaded");
        reply(player, GARDEN, PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        tick();
        var judge = requests.getLast().packs().getFirst();
        check(ids(requests.getLast()).equals(List.of(JUDGE)) && judge.uri().toString().equals(URL + "j.zip") && judge.hash().equals(HASH),
                "the failed pack is fetched from its next address with that address's hash");
        reply(player, JUDGE, PlayerResourcePackStatusEvent.Status.SUCCESSFULLY_LOADED);
        validate();
        check(kicks == kicked, "the recovered player stays connected");
        check(m.describe(player).contains("Re-sent: 3"), "recovery is counted");
        tick();
        check(ids(requests.getLast()).equals(List.of(JUDGE)), "nothing is sent again once every pack has loaded");
        check(j.offer(player, List.of(URL + "only.zip"), List.of(HASH), "Judge", true, true), "a new single-address Judge pack");
        tick();
        reply(player, JUDGE, PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD);
        validate();
        check(kicks == kicked + 1, "a required pack with no address left still disconnects");
        expectInvalid(() -> j.offer(player, List.of(URL + "x.zip"), List.of(), "Judge", true, true));
        m.close(); g.close(); j.close();
        check(services.getKnownServices().isEmpty(), "recovery state releases with the last owner");
    }
    static Object sessionOf(Player player) throws Exception {
        var sessions = ResourcePackManager.class.getDeclaredField("sessions"); sessions.setAccessible(true);
        return ((Map<?, ?>) sessions.get(manager())).get(player.getUniqueId());
    }

    static void offer(Object client, Player player, String name) throws Exception {
        client.getClass().getMethod("offer", Player.class, String.class, String.class, String.class, boolean.class, boolean.class)
                .invoke(client, player, URL + name + ".zip", HASH, name, false, false);
    }
    static URLClassLoader loader(Path classes) throws Exception {
        return new URLClassLoader(new URL[]{classes.toUri().toURL()}, ResourcePackDeliveryChecks.class.getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (!name.startsWith("com.example.advancemagic.pack.delivery.")) return super.loadClass(name, resolve);
                Class<?> type = findLoadedClass(name);
                if (type == null) type = findClass(name);
                if (resolve) resolveClass(type);
                return type;
            }
        };
    }
    static void tick() { var work = List.copyOf(scheduled); scheduled.clear(); for (var task : work) if (!task.cancelled) task.action.run(); }
    static ResourcePackManager manager() { return services.load(ResourcePackManager.class); }
    static void reply(Player player, UUID pack, PlayerResourcePackStatusEvent.Status status) { manager().status(new PlayerResourcePackStatusEvent(player, pack, status)); }
    static void validate() throws Exception {var method=ResourcePackManager.class.getDeclaredMethod("validateRequired");method.setAccessible(true);method.invoke(manager());}
    static List<UUID> ids(ResourcePackRequest request) { return request.packs().stream().map(pack -> pack.id()).toList(); }
    static Plugin plugin(String name) {
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        return proxy(Plugin.class, (p, m, a) -> switch (m.getName()) {
            case "getName" -> name;
            case "getLogger" -> Logger.getLogger(name);
            case "getConfig" -> config;
            case "isEnabled" -> true;
            default -> defaultValue(m.getReturnType());
        });
    }
    static Player player(UUID id) {
        Player player = proxy(Player.class, (p, m, a) -> switch (m.getName()) {
            case "getUniqueId" -> id;
            case "getName" -> "pack-test";
            case "isOnline" -> online;
            case "kick" -> {kicks++;yield null;}
            case "sendResourcePacks" -> {
                if (failSend) throw new IllegalStateException("simulated transport failure");
                requests.add((ResourcePackRequest) a[0]); yield null;
            }
            default -> defaultValue(m.getReturnType());
        });
        players.put(id, player); return player;
    }
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p, m, a) -> {
            if (m.getDeclaringClass() == Object.class) return switch (m.getName()) {
                case "equals" -> p == a[0]; case "hashCode" -> System.identityHashCode(p); default -> type.getSimpleName() + " proxy";
            };
            return handler.invoke(p, m, a);
        });
    }
    static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == long.class) return 0L;
        if (type == double.class) return 0D;
        if (type == float.class) return 0F;
        if (type == char.class) return '\0';
        return 0;
    }
    static void expectInvalid(Runnable operation) {
        try { operation.run(); throw new AssertionError("Invalid descriptor accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
}
