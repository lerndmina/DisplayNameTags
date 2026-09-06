package com.mattmx.nametags;

import com.mattmx.nametags.entity.NameTagEntity;
import com.mattmx.nametags.entity.trait.SneakTrait;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.GameMode;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class EventsListener implements Listener {

    private final @NotNull NameTags plugin;

    public EventsListener(@NotNull NameTags plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        if (!event.getPlayer().isConnected()) {
            return;
        }

        plugin.getEntityManager()
                .getOrCreateNameTagEntity(event.getPlayer())
                .updateVisibility();
    }

    // @EventHandler
    // public void onEntityRemove(@NotNull EntityRemoveFromWorldEvent event) {
    // plugin.getEntityManager().removeLastSentPassengersCache(event.getEntity().getEntityId());
    //
    // NameTagEntity entity = plugin.getEntityManager()
    // .removeEntity(event.getEntity());
    //
    // if (entity != null) {
    // entity.destroy();
    // }
    // }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        UUID playerUuid = event.getPlayer().getUniqueId();
        // Before the tag goes away, so the bubble can still resolve its owner's passenger list.
        plugin.getBubbleManager().clear(playerUuid);
        plugin.getEntityManager().removeLastSentPassengersCache(event.getPlayer().getEntityId());
        // TODO(matt): might not be sending de-spawn packet to viewers all the time?

        // Remove as a viewer from all entities
        for (final NameTagEntity entity : plugin.getEntityManager().getAllEntities()) {
            entity.getPassenger().removeViewer(playerUuid);
        }

        NameTagEntity entity = plugin.getEntityManager().removeEntity(event.getPlayer());

        if (entity != null) {
            entity.destroy();
        }

        // Clear per-session debug view on logout
        if (plugin.getEntityManager().hasDebugView(playerUuid)) {
            plugin.getEntityManager().toggleDebugView(playerUuid);
        }
    }

    @EventHandler
    public void onPlayerChangeWorld(@NotNull PlayerChangedWorldEvent event) {
        // Viewers in the old world are dropped below, so a bubble mid-animation would be left
        // mounted on nobody. Cheaper and less surprising to just end it.
        plugin.getBubbleManager().clear(event.getPlayer());

        NameTagEntity nameTagEntity = plugin.getEntityManager().getNameTagEntity(event.getPlayer());

        if (nameTagEntity == null)
            return;

        // Clear stale passenger cache from the previous world
        plugin.getEntityManager().removeLastSentPassengersCache(event.getPlayer().getEntityId());

        // Remove all viewers from old world - they can't see entities in other worlds
        // The spawn packet handler will add viewers from the new world automatically
        Player player = event.getPlayer();
        for (Player oldWorldPlayer : event.getFrom().getPlayers()) {
            if (!oldWorldPlayer.equals(player)) {
                nameTagEntity.getPassenger().removeViewer(oldWorldPlayer.getUniqueId());
            }
        }

        nameTagEntity.updateLocation();
        nameTagEntity.updateVisibility();

        if (plugin.getConfig().getBoolean("show-self", false)) {
            nameTagEntity.getPassenger().removeViewer(nameTagEntity.getBukkitEntity().getUniqueId());
            nameTagEntity.getPassenger().addViewer(nameTagEntity.getBukkitEntity().getUniqueId());
            nameTagEntity.sendPassengerPacket(event.getPlayer());
        }
    }

    @EventHandler
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        plugin.getBubbleManager().clear(event.getPlayer());

        NameTagEntity nameTagEntity = plugin.getEntityManager()
                .getNameTagEntity(event.getPlayer());

        if (nameTagEntity == null)
            return;

        if (plugin.getConfig().getBoolean("show-self", false)) {
            // Hides/removes tag on death/respawn screen
            nameTagEntity.getPassenger().removeViewer(nameTagEntity.getBukkitEntity().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {
        NameTagEntity nameTagEntity = plugin.getEntityManager()
                .getNameTagEntity(event.getPlayer());

        if (nameTagEntity == null)
            return;

        if (plugin.getConfig().getBoolean("show-self", false)) {

            String respawnWorld = event.getRespawnLocation().getWorld().getName();
            String playerWorld = event.getPlayer().getWorld().getName();
            // Ignoring since same action is handled at EventListener#onPlayerChangeWorld if
            // player was killed in another world.
            if (!playerWorld.equalsIgnoreCase(respawnWorld))
                return;

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                // Update entity location.
                nameTagEntity.updateLocation();
                // Add player back as viewer
                nameTagEntity.getPassenger().addViewer(nameTagEntity.getBukkitEntity().getUniqueId());
                // Send passenger packet
                nameTagEntity.sendPassengerPacket(event.getPlayer());
            });
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        if (!plugin.isSpectatorVisibleEnabled()) {
            return;
        }
        if (event.getPlayer().getGameMode() != GameMode.SPECTATOR) {
            return;
        }
        NameTagEntity nameTagEntity = plugin.getEntityManager().getNameTagEntity(event.getPlayer());
        if (nameTagEntity == null) {
            return;
        }
        nameTagEntity.syncStandin();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onGameModeChange(@NotNull PlayerGameModeChangeEvent event) {
        // Bubbles never ride the spectator stand-in head, so end one that is playing when its
        // owner goes into spectator.
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            plugin.getBubbleManager().clear(event.getPlayer());
        }

        if (!plugin.isSpectatorVisibleEnabled()) {
            return;
        }
        NameTagEntity nameTagEntity = plugin.getEntityManager().getNameTagEntity(event.getPlayer());
        if (nameTagEntity == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().getGameMode() != GameMode.SPECTATOR) {
                nameTagEntity.destroyStandin();
            }
            nameTagEntity.updateLocation();
            for (Player tracker : event.getPlayer().getTrackedBy()) {
                if (tracker.equals(event.getPlayer()) && !plugin.getConfig().getBoolean("show-self", false)) {
                    continue;
                }
                if (plugin.canViewerSeeNametag(tracker, nameTagEntity)) {
                    plugin.showTagToViewer(nameTagEntity, tracker);
                } else {
                    plugin.hideTagFromViewer(nameTagEntity, tracker);
                }
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerSneak(@NotNull PlayerToggleSneakEvent event) {
        if (!plugin.getConfig().getBoolean("sneak.enabled")) {
            return;
        }

        if (event.getPlayer().isInsideVehicle())
            return;

        NameTagEntity nameTagEntity = plugin.getEntityManager()
                .getNameTagEntity(event.getPlayer());

        if (nameTagEntity == null)
            return;

        nameTagEntity.getTraits()
                .getOrAddTrait(SneakTrait.class, SneakTrait::new)
                .updateSneak(event.isSneaking());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPotionEffect(@NotNull EntityPotionEffectEvent event) {
        // Only handle invisibility effect changes on players
        if (!(event.getEntity() instanceof Player player))
            return;

        // Check if this is an invisibility effect change
        boolean isInvisibilityChange = (event.getOldEffect() != null
                && event.getOldEffect().getType().equals(PotionEffectType.INVISIBILITY)) ||
                (event.getNewEffect() != null && event.getNewEffect().getType().equals(PotionEffectType.INVISIBILITY));

        if (!isInvisibilityChange)
            return;

        NameTagEntity nameTagEntity = plugin.getEntityManager().getNameTagEntity(player);
        if (nameTagEntity == null)
            return;

        // Check if this is gaining or losing invisibility
        boolean gainingInvisibility = event.getNewEffect() != null
                && event.getNewEffect().getType().equals(PotionEffectType.INVISIBILITY);

        // Delay the visibility update slightly to ensure the effect has been applied
        Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
            nameTagEntity.updateVisibility();

            // If losing invisibility, we need to re-add viewers since they may have been
            // skipped
            // (e.g., if nametag was toggled on while invisible)
            if (!gainingInvisibility && !plugin.getEntityManager().isNameTagDisabled(player.getUniqueId())) {
                boolean showSelf = plugin.getConfig().getBoolean("show-self", false);
                for (final Player viewer : Bukkit.getOnlinePlayers()) {
                    if (viewer.equals(player) && !showSelf) {
                        continue;
                    }
                    if (!viewer.getWorld().equals(player.getWorld())) {
                        continue;
                    }
                    if (!plugin.canViewerSeeNametag(viewer, nameTagEntity)) {
                        continue;
                    }
                    if (!nameTagEntity.getPassenger().getViewers().contains(viewer.getUniqueId())) {
                        plugin.showTagToViewer(nameTagEntity, viewer);
                    }
                }
            }

            // Refresh to send updated metadata to all viewers
            nameTagEntity.getPassenger().refresh();
        }, 1L);
    }
}
