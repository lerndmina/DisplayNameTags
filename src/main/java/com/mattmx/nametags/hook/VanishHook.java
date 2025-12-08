package com.mattmx.nametags.hook;

import de.myzelyam.api.vanish.VanishAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.metadata.MetadataValue;
import org.jetbrains.annotations.NotNull;

/**
 * Hook for PremiumVanish and SuperVanish integration.
 * Supports checking if players are vanished and if one player can see another.
 */
public class VanishHook {

    /**
     * Checks if PremiumVanish or SuperVanish is available.
     */
    public static boolean isVanishPluginPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("SuperVanish") 
            || Bukkit.getPluginManager().isPluginEnabled("PremiumVanish");
    }

    /**
     * Checks if a player is vanished using the metadata approach.
     * This is compatible with PremiumVanish, SuperVanish, EssentialsX, VanishNoPacket and more.
     * 
     * @param player The player to check
     * @return true if the player is vanished
     */
    public static boolean isVanished(@NotNull Player player) {
        for (MetadataValue meta : player.getMetadata("vanished")) {
            if (meta.asBoolean()) return true;
        }
        return false;
    }

    /**
     * Checks if a viewer can see a target player.
     * Uses the VanishAPI if available, otherwise falls back to metadata check.
     * 
     * @param viewer The player who is viewing
     * @param target The player being viewed
     * @return true if the viewer can see the target
     */
    public static boolean canSee(@NotNull Player viewer, @NotNull Player target) {
        // If target is not vanished, everyone can see them
        if (!isVanished(target)) {
            return true;
        }

        // If vanish plugin is present, use the proper API
        if (isVanishPluginPresent()) {
            return VanishAPI.canSee(viewer, target);
        }

        // Fallback: if target is vanished and no vanish plugin API available,
        // assume they can't be seen (conservative approach)
        return false;
    }
}
