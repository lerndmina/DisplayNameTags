package com.mattmx.nametags;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.potion.PotionTypes;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerRemoveEntityEffect;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.mattmx.nametags.entity.NameTagEntity;
import com.mattmx.nametags.entity.SpectatorHead;
import com.mattmx.nametags.packet.PlayServerEntityMetaDataHandler;
import com.mattmx.nametags.packet.PlayServerSetPassengersHandler;
import com.mattmx.nametags.packet.PlayServerSpawnEntityHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.UUID;

public class OutgoingPacketListener extends PacketListenerAbstract {
    private final @NotNull NameTags plugin;

    public OutgoingPacketListener(@NotNull NameTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPacketSend(@NotNull PacketSendEvent event) {
        switch (event.getPacketType()) {
            case PacketType.Play.Server.SPAWN_ENTITY -> PlayServerSpawnEntityHandler.handlePacket(event);
            case PacketType.Play.Server.ENTITY_METADATA -> PlayServerEntityMetaDataHandler.handlePacket(event);
            case PacketType.Play.Server.SET_PASSENGERS -> PlayServerSetPassengersHandler.handlePacket(event);
            case PacketType.Play.Server.DESTROY_ENTITIES -> {
                WrapperPlayServerDestroyEntities packet = new WrapperPlayServerDestroyEntities(event);

                for (int entityId : packet.getEntityIds()) {
                    // The client is dropping a bubble entity of ours, so stop counting this
                    // player as one of its viewers.
                    UUID bubbleOwner = plugin.getBubbleManager().getOwnerByBubbleEntityId(entityId);
                    if (bubbleOwner != null) {
                        plugin.getBubbleManager().removeViewer(bubbleOwner, event.getUser().getUUID());
                        continue;
                    }

                    NameTagEntity nameTagEntity = plugin.getEntityManager().getNameTagEntityById(entityId);

                    if (nameTagEntity == null)
                        continue;

                    nameTagEntity.getPassenger().removeViewer(event.getUser());
                    plugin.getBubbleManager().removeViewer(
                            nameTagEntity.getBukkitEntity().getUniqueId(), event.getUser().getUUID());
                    SpectatorHead head = nameTagEntity.getStandin();
                    if (head != null) {
                        head.removeViewer(event.getUser().getUUID());
                    }
                }
            }
            case PacketType.Play.Server.ENTITY_EFFECT -> {
                // TODO per-player impl (teams may be able to see invisible players)
                final WrapperPlayServerEntityEffect packet = new WrapperPlayServerEntityEffect(event);

                if (packet.getPotionType() != PotionTypes.INVISIBILITY)
                    return;

                final NameTagEntity nameTagEntity = plugin.getEntityManager()
                        .getNameTagEntityById(packet.getEntityId());

                if (nameTagEntity == null)
                    return;

                nameTagEntity.updateVisibility(true);
                // Immediately refresh to send updated metadata to all viewers
                nameTagEntity.getPassenger().refresh();
            }
            case PacketType.Play.Server.REMOVE_ENTITY_EFFECT -> {
                // TODO per-player impl (teams may be able to see invisible players)
                final WrapperPlayServerRemoveEntityEffect packet = new WrapperPlayServerRemoveEntityEffect(event);

                if (packet.getPotionType() != PotionTypes.INVISIBILITY)
                    return;

                final NameTagEntity nameTagEntity = plugin.getEntityManager()
                        .getNameTagEntityById(packet.getEntityId());

                if (nameTagEntity == null)
                    return;

                nameTagEntity.updateVisibility(false);

                // Add the viewer if they don't already have the nametag
                // (e.g., if the entity was invisible when they first spawned it)
                event.getTasksAfterSend().add(() -> plugin.getExecutor().execute(() -> {
                    if (!nameTagEntity.getPassenger().getViewers().contains(event.getUser().getUUID())) {
                        // Don't re-add the viewer if the nametag is admin-disabled
                        if (plugin.getEntityManager()
                                .isNameTagDisabled(nameTagEntity.getBukkitEntity().getUniqueId())) {
                            return;
                        }
                        Player viewer = Bukkit.getPlayer(event.getUser().getUUID());
                        if (viewer == null || !plugin.canViewerSeeNametag(viewer, nameTagEntity)) {
                            return;
                        }
                        plugin.showTagToViewer(nameTagEntity, viewer);
                    }
                }));
            }
            default -> {
            }
        }
    }
}
