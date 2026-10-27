package com.example.advancemagic.pack.delivery;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.*;

/**
 * Shared main-thread delivery service. This package is compiled into both plugins
 * so either can run alone. Bukkit ServicesManager owns the single live instance;
 * Client also handles servers which isolate the two copies in separate loaders.
 *
 * Every pack a player needs goes out in one batch, so the client reloads once. When a
 * download in that batch fails, the client discards the rest; the packs are then sent
 * again one at a time, a failed one from its next address, and only a pack with no
 * address left counts as failed.
 */
public final class ResourcePackManager implements Listener {
    private final Map<UUID, Plugin> owners = new LinkedHashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private Plugin host;
    private BukkitTask flushTask;
    private BukkitTask validationTask;
    /** Slow connections need time for every required ZIP; each owner may raise it in its config. */
    private static final String TIMEOUT_KEY = "resource-pack.required-timeout-seconds";
    private static final int DEFAULT_TIMEOUT_SECONDS = 600;
    /** Re-sends one pack may get after broken batches, across all of its addresses. */
    private static final int MAX_RETRIES = 3;
    private static final Set<String> SENT = Set.of("OFFERED", "ACCEPTED", "DOWNLOADED");

    private record Source(URI uri, String sha1) {}
    private record Offer(UUID id, List<Source> sources, String prompt, boolean required) {
        ResourcePackInfo info(int index) {
            Source source = sources.get(index);
            return ResourcePackInfo.resourcePackInfo(id, source.uri(), source.sha1());
        }
    }
    private static final class Attempt {
        final Offer offer;
        String status = "QUEUED";
        int source, retries;
        long sentAt;
        long queuedAt = System.nanoTime();
        Attempt(Offer offer) { this.offer = offer; }
    }
    private static final class Session {
        final Map<UUID, Offer> pending = new LinkedHashMap<>();
        final Map<UUID, Attempt> attempts = new HashMap<>();
        final Set<UUID> unavailable = new HashSet<>();
        /** Packs this player's first batch waits for, with their deadlines. */
        final Map<UUID, Long> holds = new HashMap<>();
        boolean recovering;
        int batches, offers, skipped, retries;
    }

    private ResourcePackManager(Plugin host) { attach(host); }

    public static Client connect(Plugin owner, UUID packId) {
        requireMainThread();
        Object provider = null;
        var services = Bukkit.getServicesManager();
        for (Class<?> service : services.getKnownServices()) {
            if (!service.getName().equals(ResourcePackManager.class.getName())) continue;
            var registration = services.getRegistration(service);
            if (registration != null) { provider = registration.getProvider(); break; }
        }
        if (provider == null) provider = new ResourcePackManager(owner);
        Client client = new Client(provider, owner, packId);
        client.call("register", new Class<?>[]{Plugin.class, UUID.class}, owner, packId);
        return client;
    }

    private void attach(Plugin nextHost) {
        host = nextHost;
        Bukkit.getPluginManager().registerEvents(this, host);
        Bukkit.getServicesManager().register(ResourcePackManager.class, this, host, ServicePriority.Normal);
    }

    public void register(Plugin owner, UUID packId) {
        requireMainThread();
        Plugin previous = owners.putIfAbsent(packId, owner);
        if (previous != null && previous != owner) throw new IllegalArgumentException("Resource pack UUID already registered: " + packId);
    }

    public boolean queue(Plugin owner, Player player, UUID packId, String url, String sha1,
                         String prompt, boolean required, boolean force) {
        return queueAll(owner, player, packId, new String[]{url}, new String[]{sha1}, prompt, required, force);
    }

    /** One pack with several addresses (each with the SHA-1 of its own bytes), tried in order. */
    public boolean queueAll(Plugin owner, Player player, UUID packId, String[] urls, String[] sha1s,
                            String prompt, boolean required, boolean force) {
        requireMainThread();
        if (owners.get(packId) != owner || !owner.isEnabled() || !player.isOnline()) return false;
        if (urls.length == 0 || urls.length != sha1s.length) throw new IllegalArgumentException("Resource pack addresses and hashes differ");
        List<Source> sources = new ArrayList<>();
        for (int i = 0; i < urls.length; i++) sources.add(source(urls[i], sha1s[i]));
        Offer offer = new Offer(packId, List.copyOf(sources), prompt, required);
        Session session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new Session());
        session.unavailable.remove(packId);
        boolean released = session.holds.remove(packId) != null;
        Attempt previous = session.attempts.get(packId);
        if (previous != null && previous.offer.equals(offer)
                && (!force || inFlight(previous) && System.nanoTime() - previous.sentAt < 30_000_000_000L
                    || session.pending.containsKey(packId))) {
            session.skipped++;
            if (released && !session.pending.isEmpty()) schedule();
            return false;
        }
        session.pending.put(packId, offer);
        session.attempts.put(packId, new Attempt(offer));
        schedule();
        if (required) scheduleValidation();
        return true;
    }

    private static Source source(String url, String sha1) {
        URI uri = URI.create(url);
        if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null
                || uri.getUserInfo() != null || uri.getFragment() != null)
            throw new IllegalArgumentException("Invalid resource pack URL");
        if (sha1 == null || !sha1.matches("[a-fA-F0-9]{40}")) throw new IllegalArgumentException("Invalid resource pack SHA-1");
        return new Source(uri, sha1.toLowerCase(Locale.ROOT));
    }

    /**
     * Keeps this player's next batch back until packId is offered too, for at most the given
     * seconds, so a pack that is still being prepared joins the others in one reload.
     */
    public void hold(Plugin owner, Player player, UUID packId, int seconds) {
        requireMainThread();
        if (owners.get(packId) != owner || !player.isOnline()) return;
        Session session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new Session());
        if (session.pending.containsKey(packId)) return;
        session.holds.put(packId, System.nanoTime() + Math.max(1, seconds) * 1_000_000_000L);
        scheduleValidation();
    }

    public void unavailable(Plugin owner, Player player, UUID packId) {
        requireMainThread();
        if (owners.get(packId) != owner || !player.isOnline()) return;
        Session session = sessions.computeIfAbsent(player.getUniqueId(), ignored -> new Session());
        session.pending.remove(packId);
        session.attempts.remove(packId);
        session.unavailable.add(packId);
        if (session.holds.remove(packId) != null && !session.pending.isEmpty()) schedule();
        scheduleValidation();
    }

    private void scheduleValidation() {
        if(validationTask==null)validationTask=Bukkit.getScheduler().runTaskTimer(host,this::validateRequired,20,20);
    }

    private int timeoutSeconds() {
        int seconds = 0;
        for (Plugin owner : owners.values()) seconds = Math.max(seconds, owner.getConfig().getInt(TIMEOUT_KEY, DEFAULT_TIMEOUT_SECONDS));
        return Math.max(30, seconds == 0 ? DEFAULT_TIMEOUT_SECONDS : seconds);
    }

    private void validateRequired() {
        boolean waiting = false;
        long now = System.nanoTime();
        int timeout = timeoutSeconds();
        // Disconnects may synchronously remove a session through PlayerQuitEvent.
        for(var entry:List.copyOf(sessions.entrySet())) {
            Player player=Bukkit.getPlayer(entry.getKey());
            if(player==null||!player.isOnline())continue;
            Session session=entry.getValue();
            if(!session.holds.isEmpty()) {
                // A pack that is never ready must not keep the others back.
                if(session.holds.values().removeIf(deadline->deadline<=now)&&session.holds.isEmpty()&&!session.pending.isEmpty())schedule();
                waiting|=!session.holds.isEmpty();
            }
            boolean failed=!session.unavailable.isEmpty();
            boolean timedOut=false;
            for(Attempt attempt:session.attempts.values()) {
                if(!attempt.offer.required||attempt.status.equals("SUCCESSFULLY_LOADED"))continue;
                if(!inFlight(attempt))failed=true;
                else {
                    waiting=true;
                    long started=attempt.sentAt==0?attempt.queuedAt:attempt.sentAt;
                    if(now-started>=timeout*1_000_000_000L)timedOut=true;
                }
            }
            if(failed||timedOut) {
                waiting=true;
                player.kick(Component.text(timedOut
                    ? "โหลดแพ็กทรัพยากรของเซิร์ฟเวอร์ไม่เสร็จภายใน "+(timeout%60==0?timeout/60+" นาที":timeout+" วินาที")
                        +" กรุณาเข้าใหม่และอนุญาต Resource Packs"
                    : "ต้องโหลดแพ็กทรัพยากรของเซิร์ฟเวอร์ให้สำเร็จเพื่อเข้าเล่น กรุณาอนุญาต Resource Packs แล้วเข้าใหม่"));
            }
        }
        if(!waiting&&validationTask!=null){validationTask.cancel();validationTask=null;}
    }

    private static boolean inFlight(Attempt attempt) {
        return Set.of("QUEUED", "OFFERED", "ACCEPTED", "DOWNLOADED").contains(attempt.status);
    }

    private void schedule() {
        if (flushTask == null) flushTask = Bukkit.getScheduler().runTask(host, this::flush);
    }

    private void flush() {
        flushTask = null;
        long now = System.nanoTime();
        for (var entry : sessions.entrySet()) {
            Session session = entry.getValue();
            if (session.pending.isEmpty()) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) { session.pending.clear(); continue; }
            session.holds.values().removeIf(deadline -> deadline <= now);
            if (!session.holds.isEmpty()) continue;
            if (session.recovering) { sendNext(player, session); continue; }
            // Registration order preserves pack priority even when handlers run in a
            // different order. Keep required and optional requests separate.
            List<Offer> batch = new ArrayList<>();
            for (UUID id : owners.keySet()) {
                Offer offer = session.pending.get(id);
                if (offer == null) continue;
                if (!batch.isEmpty() && batch.getFirst().required != offer.required) {
                    send(player, session, batch);
                    batch.clear();
                }
                batch.add(offer);
            }
            if (!batch.isEmpty()) send(player, session, batch);
            session.pending.clear();
        }
    }

    /** After a broken batch, packs go one at a time so a second failure cannot discard them again. */
    private void sendNext(Player player, Session session) {
        if (!idleOnClient(session)) return;
        for (UUID id : owners.keySet()) {
            Offer offer = session.pending.remove(id);
            if (offer != null) { send(player, session, List.of(offer)); return; }
        }
        session.pending.clear();
    }

    private static boolean idleOnClient(Session session) {
        return session.attempts.values().stream().noneMatch(attempt -> attempt.sentAt != 0 && SENT.contains(attempt.status));
    }

    private void send(Player player, Session session, List<Offer> batch) {
        List<ResourcePackInfo> packs = batch.stream().map(offer -> offer.info(session.attempts.get(offer.id()).source)).toList();
        String prompt = String.join("\n", batch.stream().map(Offer::prompt).distinct().toList());
        var request = ResourcePackRequest.resourcePackRequest().packs(packs).replace(false)
                .required(batch.getFirst().required).prompt(Component.text(prompt)).build();
        // Set state before sending: a synchronous callback must not be overwritten.
        for (Offer offer : batch) {
            Attempt attempt = session.attempts.get(offer.id());
            attempt.status = "OFFERED";
            attempt.sentAt = System.nanoTime();
        }
        try {
            player.sendResourcePacks(request);
            session.batches++;
            session.offers += batch.size();
        } catch (RuntimeException error) {
            for (Offer offer : batch) session.attempts.get(offer.id()).status = "SEND_FAILED";
            host.getLogger().warning("Resource pack batch for " + player.getName() + " failed: " + error.getMessage());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void status(PlayerResourcePackStatusEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session == null) return;
        Attempt attempt = session.attempts.get(event.getID());
        if (attempt == null || attempt.status.equals("QUEUED")) return;
        String next = event.getStatus().name();
        if (!inFlight(attempt) && (next.equals("ACCEPTED") || next.equals("DOWNLOADED"))) return;
        // A delayed acknowledgement must not regress DOWNLOADED to ACCEPTED.
        if (attempt.status.equals("DOWNLOADED") && next.equals("ACCEPTED")) return;
        attempt.status = next;
        if (List.of("FAILED_DOWNLOAD", "INVALID_URL", "DISCARDED").contains(next) && retry(event.getPlayer(), session, attempt, next)) return;
        if (session.recovering) {
            if (session.pending.isEmpty() && idleOnClient(session)) session.recovering = false;
            else schedule();
        }
        if(attempt.offer.required&&!next.equals("SUCCESSFULLY_LOADED"))scheduleValidation();
    }

    /** Sends a pack again: the same address when another pack broke its batch, else the next address. */
    private boolean retry(Player player, Session session, Attempt attempt, String failure) {
        boolean discarded = failure.equals("DISCARDED");
        if (attempt.retries >= MAX_RETRIES || !discarded && attempt.source + 1 >= attempt.offer.sources().size()) return false;
        String from = attempt.offer.sources().get(attempt.source).uri().getAuthority();
        if (!discarded) attempt.source++;
        attempt.retries++;
        attempt.status = "QUEUED";
        attempt.sentAt = 0;
        attempt.queuedAt = System.nanoTime();
        session.pending.put(attempt.offer.id(), attempt.offer);
        session.recovering = true;
        session.retries++;
        host.getLogger().info("Resource pack \"" + attempt.offer.prompt() + "\" for " + player.getName() + ": " + failure + " from " + from
                + (discarded ? "; sending it again on its own." : "; trying " + attempt.offer.sources().get(attempt.source).uri().getAuthority() + "."));
        schedule();
        return true;
    }

    @EventHandler public void quit(PlayerQuitEvent event) { sessions.remove(event.getPlayer().getUniqueId()); }

    public String status(Player player, UUID packId) {
        Session session = sessions.get(player.getUniqueId());
        if(session!=null&&session.unavailable.contains(packId))return "UNAVAILABLE";
        Attempt attempt = session == null ? null : session.attempts.get(packId);
        return attempt == null ? "NOT_OFFERED" : attempt.status;
    }

    public String describe(Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session == null ? "No Java pack requests in this connection"
                : "Batches: " + session.batches + " | Pack offers: " + session.offers
                + " | Duplicates skipped: " + session.skipped + " | Re-sent: " + session.retries + " | Queued: " + session.pending.size();
    }

    public void release(Plugin owner, UUID packId) {
        requireMainThread();
        if (owners.get(packId) != owner) return;
        owners.remove(packId);
        for (Session session : sessions.values()) {
            session.pending.remove(packId);
            session.attempts.remove(packId);
            session.unavailable.remove(packId);
            session.holds.remove(packId);
        }
        if (owners.containsValue(host)) return;
        if (flushTask != null) { flushTask.cancel(); flushTask = null; }
        if (validationTask != null) { validationTask.cancel(); validationTask = null; }
        HandlerList.unregisterAll(this);
        Bukkit.getServicesManager().unregister(ResourcePackManager.class, this);
        if (owners.isEmpty()) { sessions.clear(); return; }
        attach(owners.values().iterator().next());
        if (sessions.values().stream().anyMatch(session -> !session.pending.isEmpty())) schedule();
        if (sessions.values().stream().anyMatch(session -> !session.unavailable.isEmpty() || !session.holds.isEmpty()
                || session.attempts.values().stream().anyMatch(attempt -> attempt.offer.required
                    && !attempt.status.equals("SUCCESSFULLY_LOADED")))) scheduleValidation();
    }

    private static void requireMainThread() {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Resource pack delivery must run on the server thread");
    }

    /** Bound handle; reflection is used only when the provider has another loader. */
    public static final class Client implements AutoCloseable {
        private static final Class<?>[] QUEUE = {Plugin.class, Player.class, UUID.class, String.class,
                String.class, String.class, boolean.class, boolean.class};
        private static final Class<?>[] QUEUE_ALL = {Plugin.class, Player.class, UUID.class, String[].class,
                String[].class, String.class, boolean.class, boolean.class};
        private static final Class<?>[] HOLD = {Plugin.class, Player.class, UUID.class, int.class};
        private final Object provider;
        private final Plugin owner;
        private final UUID packId;
        private final Map<String, Method> methods = new HashMap<>();
        private boolean closed;

        private Client(Object provider, Plugin owner, UUID packId) {
            this.provider = provider; this.owner = owner; this.packId = packId;
        }

        public boolean offer(Player player, String url, String sha1, String prompt, boolean required, boolean force) {
            if (closed) return false;
            if (provider instanceof ResourcePackManager manager)
                return manager.queue(owner, player, packId, url, sha1, prompt, required, force);
            return (boolean) call("queue", QUEUE, owner, player, packId, url, sha1, prompt, required, force);
        }

        /** Addresses in order of preference; an older coordinator gets only the first. */
        public boolean offer(Player player, List<String> urls, List<String> sha1s, String prompt, boolean required, boolean force) {
            if (closed) return false;
            String[] u = urls.toArray(String[]::new), h = sha1s.toArray(String[]::new);
            if (provider instanceof ResourcePackManager manager)
                return manager.queueAll(owner, player, packId, u, h, prompt, required, force);
            if (supports("queueAll", QUEUE_ALL))
                return (boolean) call("queueAll", QUEUE_ALL, owner, player, packId, u, h, prompt, required, force);
            if (u.length == 0 || u.length != h.length) throw new IllegalArgumentException("Resource pack addresses and hashes differ");
            return (boolean) call("queue", QUEUE, owner, player, packId, u[0], h[0], prompt, required, force);
        }

        /** Asks the coordinator to send this player's batch only once this pack is offered too. */
        public void hold(Player player, int seconds) {
            if (closed) return;
            if (provider instanceof ResourcePackManager manager) manager.hold(owner, player, packId, seconds);
            else if (supports("hold", HOLD)) call("hold", HOLD, owner, player, packId, seconds);
        }

        public String status(Player player) {
            if (closed) return "NOT_OFFERED";
            if (provider instanceof ResourcePackManager manager) return manager.status(player, packId);
            return (String) call("status", new Class<?>[]{Player.class, UUID.class}, player, packId);
        }

        public void unavailable(Player player) {
            if(closed)return;
            if(provider instanceof ResourcePackManager manager)manager.unavailable(owner,player,packId);
            else call("unavailable",new Class<?>[]{Plugin.class,Player.class,UUID.class},owner,player,packId);
        }

        public String describe(Player player) {
            if (provider instanceof ResourcePackManager manager) return manager.describe(player);
            return (String) call("describe", new Class<?>[]{Player.class}, player);
        }

        @Override public void close() {
            if (closed) return;
            if (provider instanceof ResourcePackManager manager) manager.release(owner, packId);
            else call("release", new Class<?>[]{Plugin.class, UUID.class}, owner, packId);
            closed = true;
        }

        private boolean supports(String name, Class<?>[] types) {
            try { provider.getClass().getMethod(name, types); return true; }
            catch (NoSuchMethodException missing) { return false; }
        }

        private Object call(String name, Class<?>[] types, Object... args) {
            try {
                Method method = methods.get(name);
                if (method == null) { method = provider.getClass().getMethod(name, types); methods.put(name, method); }
                return method.invoke(provider, args);
            } catch (InvocationTargetException error) {
                if (error.getCause() instanceof RuntimeException cause) throw cause;
                throw new IllegalStateException("Resource pack service failed", error.getCause());
            } catch (ReflectiveOperationException error) {
                throw new IllegalStateException("Incompatible resource pack service", error);
            }
        }
    }
}
