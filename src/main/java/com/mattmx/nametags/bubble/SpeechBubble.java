package com.mattmx.nametags.bubble;

import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3f;
import com.mattmx.nametags.bubble.typewriter.TypewriterScript;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import me.tofaa.entitylib.wrapper.WrapperEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * One packet-only text display riding a player, playing back a compiled typewriter script.
 *
 * <p>The entity is created once and kept for as long as the player keeps talking: a new chat line
 * replaces the script and restarts playback from tick 0 rather than respawning anything, so the
 * client never sees the bubble blink out and back in mid-conversation.
 *
 * <p>Metadata change notifications are switched off on the entity, so the three setters an
 * animation frame touches do not each send their own packet; one metadata packet per changed
 * frame is sent explicitly instead. Frames are sparse, so most ticks send nothing at all.
 */
public final class SpeechBubble {

    private final @NotNull UUID owner;
    private final @NotNull WrapperEntity entity;
    private final float baseScale;

    private @NotNull List<TypewriterScript.Frame> frames = List.of();
    private int frameIndex = 0;
    private int tick = 0;
    private float lastScale = Float.NaN;
    private int lastOpacity = Integer.MIN_VALUE;

    SpeechBubble(@NotNull UUID owner, @NotNull Location location, @NotNull BubbleConfig config) {
        this.owner = owner;
        this.baseScale = config.scale();
        this.entity = new WrapperEntity(EntityTypes.TEXT_DISPLAY);
        this.entity.spawn(location);
        this.entity.getEntityMeta().setNotifyAboutChanges(false);
        this.entity.consumeEntityMeta(TextDisplayMeta.class, config::applyBaseMeta);
    }

    public @NotNull UUID getOwner() {
        return owner;
    }

    public int getEntityId() {
        return entity.getEntityId();
    }

    public @NotNull WrapperEntity getEntity() {
        return entity;
    }

    public @NotNull Set<UUID> getViewers() {
        return entity.getViewers();
    }

    /** Starts (or restarts) playback with a freshly compiled script. */
    void play(@NotNull List<TypewriterScript.Frame> frames) {
        this.frames = frames;
        this.frameIndex = 0;
        this.tick = 0;
        this.lastScale = Float.NaN;
        this.lastOpacity = Integer.MIN_VALUE;
    }

    void setLocation(@NotNull Location location) {
        entity.setLocation(location);
    }

    /**
     * Applies every frame that has come due and advances the clock by one tick.
     *
     * @return {@code true} once the script has run out, meaning the caller should destroy this
     *         bubble.
     */
    boolean advance() {
        TypewriterScript.@Nullable Frame due = null;
        while (frameIndex < frames.size() && frames.get(frameIndex).tick() <= tick) {
            due = frames.get(frameIndex);
            frameIndex++;
        }
        tick++;

        if (due == null) {
            return frameIndex >= frames.size();
        }
        if (due.phase() == TypewriterScript.Phase.DONE) {
            return true;
        }

        apply(due);
        return false;
    }

    private void apply(TypewriterScript.@NotNull Frame frame) {
        TextDisplayMeta meta = entity.getEntityMeta(TextDisplayMeta.class);
        meta.setText(frame.text());

        if (frame.textOpacity() != lastOpacity) {
            meta.setTextOpacity((byte) frame.textOpacity());
            lastOpacity = frame.textOpacity();
        }

        float scale = baseScale * frame.scale();
        if (scale != lastScale) {
            meta.setScale(new Vector3f(scale, scale, scale));
            lastScale = scale;
        }

        entity.sendPacketToViewers(meta.createPacket());
    }

    /** Sends a destroy packet to every viewer and marks the entity gone. */
    void despawn() {
        entity.despawn();
    }
}
