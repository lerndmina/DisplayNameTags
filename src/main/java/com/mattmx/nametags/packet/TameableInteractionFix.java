package com.mattmx.nametags.packet;

import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

/**
 * Counterpart to {@link TameableOwnerHider}. With the owner UUID stripped
 * from outgoing metadata, the owner's client mispredicts right-clicks on its
 * own pets: an empty main hand resolves to PASS, so the client also sends
 * the off-hand interact and the server toggles sit twice — net zero. A
 * client that can see it owns the pet always consumes the main-hand click
 * and never reaches the off-hand, so dropping the off-hand event here is
 * vanilla-equivalent and cannot suppress a legitimate action.
 */
public class TameableInteractionFix implements Listener {

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onOffhandInteract(@NotNull PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        if (isOwnPet(event)) {
            event.setCancelled(true);
        }
    }

    /**
     * The client predicted PASS for the empty main hand, so it never plays
     * the swing animation. Broadcast it once the interaction goes through.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMainHandInteract(@NotNull PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!event.getPlayer().getInventory().getItemInMainHand().isEmpty()) {
            return;
        }
        if (isOwnPet(event)) {
            event.getPlayer().swingMainHand();
        }
    }

    private static boolean isOwnPet(@NotNull PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Tameable pet)
                || !(event.getRightClicked() instanceof Sittable)) {
            return false;
        }
        if (!pet.isTamed()) {
            return false;
        }
        return event.getPlayer().getUniqueId().equals(pet.getOwnerUniqueId());
    }
}
