package com.mattmx.nametags.entity;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.wrapper.PacketWrapper;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.mattmx.nametags.NameTags;
import com.mattmx.nametags.entity.trait.TraitHolder;
import com.mattmx.nametags.hook.VanishHook;
import io.github.retrooper.packetevents.util.SpigotConversionUtil;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Consumer;

public class NameTagEntity {
    private final @NotNull TraitHolder traits = new TraitHolder(this);
    private final @NotNull Entity bukkitEntity;
    private final @NotNull WrapperEntity passenger;
    private @Nullable SpectatorHead standin;
    private float cachedViewRange = -1f;

    public NameTagEntity(@NotNull Entity entity) {
        this.bukkitEntity = entity;
        this.passenger = new WrapperEntity(EntityTypes.TEXT_DISPLAY);
    }

    public void initialize() {
        Location location = updateLocation();

        this.passenger.spawn(location);

        final NameTags plugin = NameTags.getInstance();
        final boolean showSelf = plugin.getConfig().getBoolean("show-self", false);

        if (showSelf && this.bukkitEntity instanceof Player self) {
            this.passenger.addViewer(self.getUniqueId());
            sendPassengerPacket(self);
        }

        // Players who are already tracking this entity will never receive another
        // SPAWN_ENTITY packet, so the packet-interception path in
        // PlayServerSpawnEntityHandler would never fire for them — they would
        // silently not see the nametag. Seed the viewer set now for them.
        // Entity#getTrackedBy() must be called on the main thread.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!this.bukkitEntity.isValid())
                return;

            final boolean isInvis = isInvisible();
            final boolean adminDisabled = plugin.getEntityManager()
                    .isNameTagDisabled(this.bukkitEntity.getUniqueId());

            if (adminDisabled)
                return;

            for (final Player viewer : this.bukkitEntity.getTrackedBy()) {
                // show-self=false: skip the owner (already handled above if show-self=true)
                if (this.bukkitEntity instanceof Player owner
                        && viewer.equals(owner)
                        && !showSelf) {
                    continue;
                }

                if (!plugin.canViewerSeeNametag(viewer, this)) {
                    continue;
                }

                plugin.showTagToViewer(this, viewer);
            }
        });
    }

    public boolean isInvisible() {
        boolean hasInvisibilityEffect = bukkitEntity instanceof LivingEntity e
                && e.hasPotionEffect(PotionEffectType.INVISIBILITY);

        return bukkitEntity.isInvisible() || hasInvisibilityEffect;
    }

    public void updateVisibility() {
        updateVisibility(isInvisible());
    }

    public void updateVisibility(final boolean isInvisible) {
        modify((meta) -> {
            // Use the meta's invisibility flag to track if we've hidden the nametag
            if (isInvisible && !meta.isInvisible()) {
                // Going invisible: cache view range, set to 0, mark as invisible
                this.cachedViewRange = meta.getViewRange();
                meta.setViewRange(0f);
                meta.setInvisible(true);
            } else if (!isInvisible && meta.isInvisible()) {
                // Becoming visible: restore cached view range, mark as visible
                meta.setViewRange(this.cachedViewRange);
                meta.setInvisible(false);
            }
        });
    }

    public @NotNull TraitHolder getTraits() {
        return traits;
    }

    public void modify(Consumer<TextDisplayMeta> consumer) {
        this.passenger.consumeEntityMeta(TextDisplayMeta.class, consumer);
    }

    public @NotNull TextDisplayMeta getMeta() {
        return this.passenger.getEntityMeta(TextDisplayMeta.class);
    }

    public void sendPassengerPacket(Player target) {
        PacketEvents.getAPI()
                .getPlayerManager()
                .sendPacket(target, getPassengersPacket(target));
    }

    public PacketWrapper<?> getPassengersPacket() {
        return getPassengersPacket(null);
    }

    public PacketWrapper<?> getPassengersPacket(@Nullable Player viewer) {
        if (viewer != null && shouldUseStandinFor(viewer)) {
            SpectatorHead head = ensureStandin();
            if (head != null) {
                int[] passengers = new int[]{getPassenger().getEntityId()};
                NameTags.getInstance().getEntityManager()
                        .setLastSentPassengers(head.getEntityId(), passengers);
                return new WrapperPlayServerSetPassengers(head.getEntityId(), passengers);
            }
        }

        int[] previousPackets = NameTags.getInstance()
                .getEntityManager()
                .getLastSentPassengers(getBukkitEntity().getEntityId())
                .orElseGet(this::currentPassengerIds);

        // A speech bubble rides alongside the tag, so it has to be in the same packet, but only
        // for a viewer that has already been sent the bubble.
        previousPackets = NameTags.getInstance()
                .getBubbleManager()
                .withBubble(
                        bukkitEntity.getUniqueId(),
                        viewer == null ? null : viewer.getUniqueId(),
                        previousPackets);

        return new WrapperPlayServerSetPassengers(bukkitEntity.getEntityId(), previousPackets);
    }

    /**
     * The entity's real passengers plus this nametag, ignoring anything previously sent. Used when
     * a fresh, authoritative passenger list is needed, such as when a speech bubble is added or
     * removed. Must be called on the main thread.
     */
    public int[] currentPassengerIds() {
        int[] bukkitPassengers = this.bukkitEntity.getPassengers()
                .stream()
                .mapToInt(Entity::getEntityId)
                .toArray();

        int[] passengers = Arrays.copyOf(bukkitPassengers, bukkitPassengers.length + 1);
        passengers[passengers.length - 1] = getPassenger().getEntityId();

        return passengers;
    }

    public boolean shouldUseStandinFor(@NotNull Player viewer) {
        Player owner = bukkitEntity instanceof Player player ? player : null;
        return SpectatorStandinPolicy.showStandinTo(
                NameTags.getInstance().isSpectatorVisibleEnabled(), owner, viewer);
    }

    public @Nullable SpectatorHead ensureStandin() {
        if (!(bukkitEntity instanceof Player owner) || owner.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            destroyStandin();
            return null;
        }
        if (!NameTags.getInstance().isSpectatorVisibleEnabled()) {
            destroyStandin();
            return null;
        }
        if (standin == null) {
            standin = new SpectatorHead(owner, NameTags.getInstance().spectatorTeleportDuration());
        }
        return standin;
    }

    public void syncStandin() {
        if (standin == null) {
            return;
        }
        if (!(bukkitEntity instanceof Player owner) || owner.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            destroyStandin();
            return;
        }
        standin.updateLocation(owner);
    }

    public void destroyStandin() {
        if (standin == null) {
            return;
        }
        NameTags.getInstance().getEntityManager().removeLastSentPassengersCache(standin.getEntityId());
        standin.destroy();
        standin = null;
    }

    public @Nullable SpectatorHead getStandin() {
        return standin;
    }

    public @NotNull Entity getBukkitEntity() {
        return bukkitEntity;
    }

    public @NotNull WrapperEntity getPassenger() {
        return passenger;
    }

    public @NotNull Location updateLocation() {
        org.bukkit.Location bukkitLocation = bukkitEntity.getLocation();
        bukkitLocation.setY(bukkitEntity.getBoundingBox().getMaxY());

        Location location = SpigotConversionUtil.fromBukkitLocation(bukkitLocation);

        location.setYaw(0f);
        location.setPitch(0f);

        this.passenger.setLocation(location);
        syncStandin();

        return location;
    }

    public void destroy() {
        destroyStandin();
        this.passenger.despawn();
        this.getTraits().destroy();
    }
}
