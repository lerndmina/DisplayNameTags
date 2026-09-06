package com.mattmx.nametags.bubble;

import com.github.retrooper.packetevents.util.Vector3f;
import com.mattmx.nametags.bubble.typewriter.TypewriterStyle;
import com.mattmx.nametags.config.TextDisplayMetaConfiguration;
import me.tofaa.entitylib.meta.display.AbstractDisplayMeta;
import me.tofaa.entitylib.meta.display.TextDisplayMeta;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the {@code bubbles} config section controls: whether bubbles are on at all, how much
 * of a chat line is shown, how the display entity looks, and the typewriter timings.
 *
 * <p>Every value has a hard-coded default, so a server whose config.yml predates this feature (no
 * {@code bubbles} section at all) still gets working bubbles. The render keys are layered: the
 * defaults below are written onto the meta first, then {@link TextDisplayMetaConfiguration#applyMeta}
 * lets the config override any key it actually declares.
 */
public final class BubbleConfig {

    /** Semi-transparent black, matching the vanilla nametag background but a little darker. */
    private static final int DEFAULT_BACKGROUND = 0x60000000;
    private static final int DEFAULT_LINE_WIDTH = 180;
    private static final float DEFAULT_TRANSLATE_Y = 0.6f;
    private static final int INTERPOLATION_TICKS = 2;

    private final boolean enabled;
    private final int maxChars;
    private final float scale;
    private final @NotNull TypewriterStyle style;
    private final @Nullable ConfigurationSection section;

    private BubbleConfig(
            boolean enabled,
            int maxChars,
            float scale,
            @NotNull TypewriterStyle style,
            @Nullable ConfigurationSection section) {
        this.enabled = enabled;
        this.maxChars = maxChars;
        this.scale = scale;
        this.style = style;
        this.section = section;
    }

    public static @NotNull BubbleConfig load(@NotNull FileConfiguration config) {
        ConfigurationSection section = config.getConfigurationSection("bubbles");
        ConfigurationSection styleSection = section == null ? null : section.getConfigurationSection("style");

        TypewriterStyle style = new TypewriterStyle(
                getDouble(styleSection, "chars-per-tick", 2.0),
                getInt(styleSection, "sentence-pause-ticks", 6),
                getInt(styleSection, "clause-pause-ticks", 3),
                getString(styleSection, "cursor", "▏"),
                getInt(styleSection, "cursor-blink-ticks", 6),
                getInt(styleSection, "hold-base-ticks", 40),
                getDouble(styleSection, "hold-per-char-ticks", 1.5),
                getInt(styleSection, "hold-min-ticks", 40),
                getInt(styleSection, "hold-max-ticks", 300),
                getInt(styleSection, "fade-ticks", 10),
                getInt(styleSection, "sound-every-chars", 0));

        return new BubbleConfig(
                section == null || section.getBoolean("enabled", true),
                section == null ? 120 : section.getInt("max-chars", 120),
                section == null ? 1.0f : (float) section.getDouble("scale", 1.0),
                style,
                section);
    }

    /**
     * Writes the built-in appearance defaults onto {@code meta}, then applies whatever the
     * {@code bubbles} section overrides. Called once when a bubble entity is created; the
     * animation only ever touches text, opacity and scale after that.
     */
    public void applyBaseMeta(@NotNull TextDisplayMeta meta) {
        meta.setUseDefaultBackground(false);
        meta.setBackgroundColor(DEFAULT_BACKGROUND);
        meta.setBillboardConstraints(AbstractDisplayMeta.BillboardConstraints.CENTER);
        meta.setLineWidth(DEFAULT_LINE_WIDTH);
        meta.setShadow(true);
        meta.setSeeThrough(false);
        meta.setTextOpacity((byte) 255);
        meta.setTranslation(new Vector3f(0f, DEFAULT_TRANSLATE_Y, 0f));
        meta.setScale(new Vector3f(scale, scale, scale));
        meta.setViewRange(Bukkit.getSimulationDistance() * 16f);
        meta.setTransformationInterpolationDuration(INTERPOLATION_TICKS);
        meta.setPositionRotationInterpolationDuration(INTERPOLATION_TICKS);

        TextDisplayMetaConfiguration.applyMeta(section, meta);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int maxChars() {
        return maxChars;
    }

    public float scale() {
        return scale;
    }

    public @NotNull TypewriterStyle style() {
        return style;
    }

    private static int getInt(@Nullable ConfigurationSection section, @NotNull String key, int fallback) {
        return section == null ? fallback : section.getInt(key, fallback);
    }

    private static double getDouble(@Nullable ConfigurationSection section, @NotNull String key, double fallback) {
        return section == null ? fallback : section.getDouble(key, fallback);
    }

    private static @Nullable String getString(
            @Nullable ConfigurationSection section,
            @NotNull String key,
            @Nullable String fallback) {
        if (section == null) {
            return fallback;
        }
        String value = section.getString(key, fallback);
        return value == null || value.isEmpty() ? null : value;
    }
}
