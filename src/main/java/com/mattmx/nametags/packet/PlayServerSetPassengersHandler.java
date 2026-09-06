package com.mattmx.nametags.packet;

import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.mattmx.nametags.NameTags;
import com.mattmx.nametags.entity.NameTagEntity;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class PlayServerSetPassengersHandler {

    public static void handlePacket(@NotNull PacketSendEvent event) {
        final NameTags plugin = NameTags.getInstance();
        final WrapperPlayServerSetPassengers packet = new WrapperPlayServerSetPassengers(event);

        final NameTagEntity nameTagEntity = plugin.getEntityManager().getNameTagEntityById(packet.getEntityId());

        if (nameTagEntity == null)
            return;

        // Don't inject nametag if it's been admin-disabled
        if (plugin.getEntityManager().isNameTagDisabled(nameTagEntity.getBukkitEntity().getUniqueId())) {
            return;
        }

        Player viewer = Bukkit.getPlayer(event.getUser().getUUID());
        if (viewer == null || !plugin.canViewerSeeNametag(viewer, nameTagEntity)) {
            return;
        }

        // Survival viewers cannot see a spectator (or its passengers). Parent
        // the tag to the stand-in head instead of injecting onto the player.
        if (nameTagEntity.shouldUseStandinFor(viewer)) {
            event.getTasksAfterSend().add(() -> plugin.getExecutor().execute(() ->
                    plugin.showTagToViewer(nameTagEntity, viewer)));
            return;
        }

        // If the packet doesn't already contain our entity
        boolean containsNameTagPassenger = false;
        for (final int passengerId : packet.getPassengers()) {
            if (passengerId == nameTagEntity.getPassenger().getEntityId()) {
                containsNameTagPassenger = true;
            }
        }

        // TODO(Matt)?: Should we process async and then send another passenger packet
        // afterwards?
        int[] passengers = packet.getPassengers();
        boolean changed = false;

        if (!containsNameTagPassenger) {
            // Add our entity
            passengers = Arrays.copyOf(passengers, passengers.length + 1);
            passengers[passengers.length - 1] = nameTagEntity.getPassenger().getEntityId();
            changed = true;
        }

        // An active speech bubble is a second passenger on the same player, so it has to ride
        // along in the same packet or the client will unmount it. Only for a viewer that has
        // already been sent the bubble.
        int[] withBubble = plugin.getBubbleManager()
                .withBubble(
                        nameTagEntity.getBukkitEntity().getUniqueId(),
                        viewer.getUniqueId(),
                        passengers);

        if (withBubble != passengers) {
            passengers = withBubble;
            changed = true;
        }

        if (!changed) {
            return;
        }

        packet.setPassengers(passengers);

        plugin.getEntityManager()
                .setLastSentPassengers(packet.getEntityId(), passengers);

        event.markForReEncode(true);
    }

}
