package com.mattmx.nametags.hook;

import com.mattmx.nametags.NameTags;
import com.mattmx.nametags.entity.NameTagEntity;
import de.myzelyam.api.vanish.PlayerHideEvent;
import de.myzelyam.api.vanish.PlayerShowEvent;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * Listens to PremiumVanish/SuperVanish events to properly show/hide nametags.
 */
public class VanishEventListener implements Listener {

  private final @NotNull NameTags plugin;

  public VanishEventListener(@NotNull NameTags plugin) {
    this.plugin = plugin;
  }

  /**
   * Injects the vanish event listener if a compatible vanish plugin is present.
   */
  public static void inject(@NotNull NameTags plugin) {
    if (!VanishHook.isVanishPluginPresent()) {
      return;
    }

    plugin.getLogger().info("PremiumVanish/SuperVanish detected, registering vanish event listener.");
    Bukkit.getPluginManager().registerEvents(new VanishEventListener(plugin), plugin);
  }

  /**
   * When a player vanishes, remove them as a viewer from all nametags they can't
   * see,
   * and remove all viewers from their nametag who can't see them.
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerHide(@NotNull PlayerHideEvent event) {
    Player vanishedPlayer = event.getPlayer();
    NameTagEntity vanishedTag = plugin.getEntityManager().getNameTagEntity(vanishedPlayer);

    if (vanishedTag == null)
      return;

    // Remove all viewers who can no longer see the vanished player
    for (Player viewer : Bukkit.getOnlinePlayers()) {
      if (viewer.equals(vanishedPlayer))
        continue;

      // After this event completes, the viewer won't be able to see the vanished
      // player
      // So we need to remove them as a viewer of the vanished player's nametag
      if (!VanishHook.canSee(viewer, vanishedPlayer)) {
        vanishedTag.getPassenger().removeViewer(viewer.getUniqueId());
      }
    }
  }

  /**
   * When a player reappears (un-vanishes), add viewers back to their nametag.
   */
  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onPlayerShow(@NotNull PlayerShowEvent event) {
    Player shownPlayer = event.getPlayer();
    NameTagEntity shownTag = plugin.getEntityManager().getNameTagEntity(shownPlayer);

    if (shownTag == null)
      return;

    boolean showSelf = plugin.getConfig().getBoolean("show-self", false);

    // Add all online players who can now see the player back as viewers
    for (Player viewer : Bukkit.getOnlinePlayers()) {
      if (viewer.equals(shownPlayer) && !showSelf)
        continue;
      if (!viewer.getWorld().equals(shownPlayer.getWorld()))
        continue;

      // After this event completes, viewers will be able to see the player
      // Re-add them as viewers of the nametag
      shownTag.updateLocation();
      shownTag.getPassenger().removeViewer(viewer.getUniqueId());
      shownTag.getPassenger().addViewer(viewer.getUniqueId());
      shownTag.sendPassengerPacket(viewer);
    }
  }
}
