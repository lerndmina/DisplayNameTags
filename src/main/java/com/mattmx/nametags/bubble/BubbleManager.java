package com.mattmx.nametags.bubble;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.mattmx.nametags.NameTags;
import com.mattmx.nametags.bubble.typewriter.ComponentTruncator;
import com.mattmx.nametags.bubble.typewriter.TypewriterScript;
import com.mattmx.nametags.entity.NameTagEntity;
import com.mattmx.nametags.hook.VanishHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns every live speech bubble and drives their animation.
 *
 * <p>A bubble is a second packet-only text display riding the same player as their nametag. It is
 * wired into the nametag's passenger pipeline rather than living on its own: the bubble entity id
 * is appended to the same SET_PASSENGERS packets the nametag id goes into, and its viewer set is
 * a subset of the nametag's viewer set, so a player sees a bubble exactly when they can already
 * see the nametag it sits above.
 *
 * <p>Every public method other than {@link #withBubble} must be called on the main thread.
 */
public final class BubbleManager {

    private static final long TICK_PERIOD = 1L;

    private final @NotNull NameTags plugin;
    private final Map<UUID, SpeechBubble> active = new ConcurrentHashMap<>();
    private final Map<Integer, UUID> ownerByBubbleId = new ConcurrentHashMap<>();
    private @Nullable BukkitTask task = null;

    public BubbleManager(@NotNull NameTags plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) {
            return;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, TICK_PERIOD, TICK_PERIOD);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        for (UUID owner : Set.copyOf(active.keySet())) {
            clear(owner);
        }
    }

    /**
     * Shows {@code message} as a speech bubble above {@code player}, replacing any bubble they
     * already have. Silently does nothing when bubbles are off, when the player should not be
     * showing one (spectator, vanished, invisible, nametag admin-disabled) or when the message is
     * blank. This is the entry point other plugins should use.
     */
    public void show(@NotNull Player player, @NotNull Component message) {
        BubbleConfig config = BubbleConfig.load(plugin.getConfig());
        if (!config.isEnabled() || !player.isOnline()) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR || VanishHook.isVanished(player)) {
            return;
        }
        if (plugin.getEntityManager().isNameTagDisabled(player.getUniqueId())) {
            return;
        }

        NameTagEntity tag = plugin.getEntityManager().getNameTagEntity(player);
        if (tag == null || tag.isInvisible()) {
            return;
        }

        if (PlainTextComponentSerializer.plainText().serialize(message).trim().isEmpty()) {
            return;
        }

        Component text = message;
        if (ComponentTruncator.visibleLength(text) > config.maxChars()) {
            text = ComponentTruncator.truncate(text, config.maxChars()).append(Component.text("…"));
        }

        tag.updateLocation();

        SpeechBubble bubble = active.get(player.getUniqueId());
        boolean isNew = bubble == null;
        if (isNew) {
            bubble = new SpeechBubble(player.getUniqueId(), tag.getPassenger().getLocation(), config);
            active.put(player.getUniqueId(), bubble);
            ownerByBubbleId.put(bubble.getEntityId(), player.getUniqueId());
        } else {
            bubble.setLocation(tag.getPassenger().getLocation());
        }

        bubble.play(TypewriterScript.compile(text, config.style()));

        if (isNew) {
            attachToTagViewers(tag, bubble);
        }
    }

    /** Removes {@code player}'s bubble immediately, if they have one. */
    public void clear(@NotNull Player player) {
        clear(player.getUniqueId());
    }

    /** Removes {@code owner}'s bubble immediately, if they have one. */
    public void clear(@NotNull UUID owner) {
        SpeechBubble bubble = active.remove(owner);
        if (bubble == null) {
            return;
        }
        ownerByBubbleId.remove(bubble.getEntityId());

        // Re-send the passenger list without the bubble before destroying it, so the cached
        // "last sent passengers" array never keeps a dead entity id in it.
        Player player = Bukkit.getPlayer(owner);
        NameTagEntity tag = player == null ? null : plugin.getEntityManager().getNameTagEntity(player);
        if (player != null && tag != null) {
            int[] passengers = tag.currentPassengerIds();
            plugin.getEntityManager().setLastSentPassengers(player.getEntityId(), passengers);
            sendPassengers(player.getEntityId(), passengers, bubble.getViewers());
        }

        bubble.despawn();
    }

    /**
     * Appends {@code owner}'s bubble entity id to a passenger list bound for {@code viewer}.
     *
     * <p>The id is only added when that viewer has actually been sent the bubble's spawn packet.
     * A tag viewer who never received the bubble (a stand-in transition, or a vanilla tracker
     * re-send that arrives before the bubble is attached) would otherwise be told to mount an
     * entity id it has never seen, and nothing later corrects it.
     *
     * <p>Returns the array it was given when there is nothing to add, so the common case
     * allocates nothing. Read-only, so it is safe to call from a netty thread.
     */
    public int[] withBubble(@NotNull UUID owner, @Nullable UUID viewer, int @NotNull [] passengers) {
        if (viewer == null) {
            return passengers;
        }

        SpeechBubble bubble = active.get(owner);
        if (bubble == null || !bubble.getEntity().hasViewer(viewer)) {
            return passengers;
        }

        return appendBubbleId(bubble, passengers);
    }

    private static int[] appendBubbleId(@NotNull SpeechBubble bubble, int @NotNull [] passengers) {
        final int bubbleId = bubble.getEntityId();
        for (int passenger : passengers) {
            if (passenger == bubbleId) {
                return passengers;
            }
        }

        int[] out = Arrays.copyOf(passengers, passengers.length + 1);
        out[out.length - 1] = bubbleId;
        return out;
    }

    /** The player a bubble entity id belongs to, or {@code null} if it is not one of ours. */
    public @Nullable UUID getOwnerByBubbleEntityId(int entityId) {
        return ownerByBubbleId.get(entityId);
    }

    public boolean hasBubble(@NotNull UUID owner) {
        return active.containsKey(owner);
    }

    /**
     * Mirrors a nametag viewer onto the bubble. Called from the same places that show a nametag to
     * a viewer, so a bubble reaches everyone who can see the tag and nobody who cannot. Spectator
     * stand-in viewers are skipped; bubbles do not follow the stand-in head.
     */
    public void addViewer(@NotNull NameTagEntity tag, @NotNull Player viewer) {
        SpeechBubble bubble = active.get(tag.getBukkitEntity().getUniqueId());
        if (bubble == null || tag.shouldUseStandinFor(viewer)) {
            return;
        }
        bubble.getEntity().addViewer(viewer.getUniqueId());
    }

    /** Mirrors a nametag viewer removal onto the bubble, sending the client a destroy packet. */
    public void removeViewer(@NotNull UUID owner, @NotNull UUID viewer) {
        SpeechBubble bubble = active.get(owner);
        if (bubble == null) {
            return;
        }
        bubble.getEntity().removeViewer(viewer);
    }

    private void tick() {
        if (active.isEmpty()) {
            return;
        }
        // ConcurrentHashMap iteration tolerates the clear() calls below removing entries.
        for (SpeechBubble bubble : active.values()) {
            Player owner = Bukkit.getPlayer(bubble.getOwner());
            if (owner == null || !owner.isOnline() || bubble.advance()) {
                clear(bubble.getOwner());
            }
        }
    }

    /**
     * Spawns a freshly created bubble for everyone who can currently see the nametag and tells
     * their clients to mount it on the player.
     */
    private void attachToTagViewers(@NotNull NameTagEntity tag, @NotNull SpeechBubble bubble) {
        for (UUID viewerId : Set.copyOf(tag.getPassenger().getViewers())) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline() || tag.shouldUseStandinFor(viewer)) {
                continue;
            }
            bubble.getEntity().addViewer(viewerId);
        }

        int ownerEntityId = tag.getBukkitEntity().getEntityId();
        // Everyone this array is sent to was just added as a viewer above, so the id always belongs.
        int[] passengers = appendBubbleId(bubble, tag.currentPassengerIds());
        plugin.getEntityManager().setLastSentPassengers(ownerEntityId, passengers);
        sendPassengers(ownerEntityId, passengers, bubble.getViewers());
    }

    private void sendPassengers(int vehicleEntityId, int @NotNull [] passengers, @NotNull Set<UUID> viewers) {
        if (viewers.isEmpty()) {
            return;
        }
        WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(vehicleEntityId, passengers);
        for (UUID viewerId : Set.copyOf(viewers)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) {
                continue;
            }
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
        }
    }
}
