package com.mattmx.nametags;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.PacketEventsAPI;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mattmx.nametags.bubble.BubbleChatListener;
import com.mattmx.nametags.bubble.BubbleManager;
import com.mattmx.nametags.config.ConfigDefaultsListener;
import com.mattmx.nametags.config.PluginConditionals;
import com.mattmx.nametags.config.TextFormatter;
import com.mattmx.nametags.hearts.HeartBar;
import com.mattmx.nametags.entity.NameTagEntity;
import com.mattmx.nametags.entity.NameTagEntityManager;
import com.mattmx.nametags.entity.SpectatorHead;
import com.mattmx.nametags.hook.NeznamyTABHook;
import com.mattmx.nametags.hook.SkinRestorerHook;
import com.mattmx.nametags.hook.VanishEventListener;
import com.mattmx.nametags.hook.VanishHook;
import com.mattmx.nametags.visibility.ExyliaEventsVisibilityProvider;
import com.mattmx.nametags.visibility.NameTagVisibilityProvider;
import com.mattmx.nametags.visibility.PermissiveNameTagVisibilityProvider;
import com.mattmx.nametags.utils.test.TestPlaceholderExpansion;
import me.tofaa.entitylib.APIConfig;
import me.tofaa.entitylib.EntityLib;
import me.tofaa.entitylib.spigot.SpigotEntityLibPlatform;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.DrilldownPie;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.permissions.Permission;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class NameTags extends JavaPlugin {
    public static final int TRANSPARENT = Color.fromARGB(0).asARGB();
    public static final char LEGACY_CHAR = (char) 167;
    private static @Nullable NameTags instance;
    private final HashMap<String, ConfigurationSection> groups = new HashMap<>();
    private @Nullable Executor executor = null;
    private @NotNull TextFormatter formatter = TextFormatter.MINI_MESSAGE;
    private @NotNull HeartBar.Settings heartBarSettings = HeartBar.Settings.DEFAULTS;
    private @NotNull NameTagVisibilityProvider visibilityProvider = PermissiveNameTagVisibilityProvider.INSTANCE;
    private NameTagEntityManager entityManager;
    private BubbleManager bubbleManager;
    private EventsListener eventsListener;
    private BubbleChatListener bubbleChatListener;
    private OutgoingPacketListener packetListener;
    private Metrics metrics;
    private @Nullable ConfigDefaultsListener defaultsListener = null;

    public static @NotNull NameTags getInstance() {
        return Objects.requireNonNull(instance, "NameTags plugin has not initialized yet! Did you forget to depend?");
    }

    @Override
    public void onEnable() {
        instance = this;

        entityManager = new NameTagEntityManager();
        bubbleManager = new BubbleManager(this);
        eventsListener = new EventsListener(this);
        bubbleChatListener = new BubbleChatListener(this);
        packetListener = new OutgoingPacketListener(this);

        saveDefaultConfig();
        reloadConfig();
        visibilityProvider = ExyliaEventsVisibilityProvider.create(this);

        metrics = new Metrics(this, 25409);
        registerMetrics();

        executor = Executors.newFixedThreadPool(
                getConfig().getInt("options.threads", 2),
                new ThreadFactoryBuilder()
                        .setPriority(Thread.NORM_PRIORITY + 1)
                        .setNameFormat("NameTags-Processor")
                        .build());

        SpigotEntityLibPlatform platform = new SpigotEntityLibPlatform(this);
        APIConfig settings = new APIConfig(PacketEvents.getAPI()).usePlatformLogger();

        EntityLib.init(platform, settings);

        final PacketEventsAPI<?> packetEvents = PacketEvents.getAPI();

        packetEvents.getEventManager().registerListener(packetListener);
        // packetEvents.getEventManager().registerListener(new GlowingEffectHook());

        NeznamyTABHook.inject(this);
        SkinRestorerHook.inject(this);
        VanishEventListener.inject(this);

        Bukkit.getPluginManager().registerEvents(eventsListener, this);
        boolean bubblesEnabled = getConfig().getBoolean("bubbles.enabled", false);
        if (bubblesEnabled) {
            Bukkit.getPluginManager().registerEvents(bubbleChatListener, this);
        }
        Bukkit.getPluginManager().registerEvents(new com.mattmx.nametags.packet.TameableInteractionFix(), this);
        Bukkit.getScheduler().runTaskLater(this, DependencyVersionChecker::checkPacketEventsVersion, 10L);

        Objects.requireNonNull(Bukkit.getPluginCommand("nametags")).setExecutor(new NameTagsCommand(this));

        if (false) {
            new TestPlaceholderExpansion().register();
        }

        // Create nametags for any players already online (e.g., after plugin reload)
        for (Player player : Bukkit.getOnlinePlayers()) {
            entityManager.getOrCreateNameTagEntity(player).updateVisibility();
        }

        // Periodic viewer reconciliation — keeps nametag viewers aligned with
        // the current visibility policy and catches missed transitions.
        Bukkit.getScheduler().runTaskTimer(this, this::reconcileViewers, 20L, 20L);

        if (bubblesEnabled) {
            bubbleManager.start();
        }
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();

        // Plugin presence is cached for the render path; drop it on reload so a lookup
        // made before another plugin had loaded cannot stick around.
        PluginConditionals.clearCache();

        ConfigurationSection defaults = getConfig().getConfigurationSection("defaults");
        if (defaults != null && defaults.getBoolean("enabled")) {
            getLogger().info("Using default behaviour from the config file.");

            if (defaultsListener != null) {
                HandlerList.unregisterAll(defaultsListener);
            }

            defaultsListener = new ConfigDefaultsListener(this);
            Bukkit.getPluginManager().registerEvents(defaultsListener, this);
        }

        String textFormatterIdentifier = getConfig().getString("formatter", "minimessage");
        formatter = TextFormatter.getById(textFormatterIdentifier)
                .orElse(TextFormatter.MINI_MESSAGE);
        heartBarSettings = HeartBar.Settings.fromConfig(getConfig());

        getLogger().info("Using " + formatter.name() + " as text formatter.");

        for (String permissionNode : groups.keySet()) {
            Bukkit.getPluginManager().removePermission(permissionNode);
        }
        groups.clear();

        ConfigurationSection groups = getConfig().getConfigurationSection("groups");

        if (groups == null)
            return;

        for (String key : groups.getKeys(false)) {
            String permissionNode = "nametags.groups." + key;
            ConfigurationSection sub = groups.getConfigurationSection(key);

            if (sub == null)
                continue;

            this.groups.put(permissionNode, sub);

            Bukkit.getPluginManager().addPermission(new Permission(permissionNode));
        }
    }

    public void registerMetrics() {
        metrics.addCustomChart(
                new DrilldownPie("serverName", () -> Map.of(Bukkit.getName(), Map.of(Bukkit.getName(), 1))));
    }

    @Override
    public void onDisable() {
        metrics.shutdown();

        bubbleManager.shutdown();

        // Destroy all nametag entities to prevent orphaned text displays
        for (NameTagEntity entity : entityManager.getAllEntities()) {
            entity.destroy();
        }

        HandlerList.unregisterAll(this.eventsListener);
        HandlerList.unregisterAll(this.bubbleChatListener);

        PacketEvents.getAPI()
                .getEventManager()
                .unregisterListener(this.packetListener);
    }

    public Executor getExecutor() {
        if (this.executor == null) {
            throw new RuntimeException("Executor is not available until the plugin has initialized.");
        }

        return this.executor;
    }

    public @NotNull NameTagEntityManager getEntityManager() {
        return this.entityManager;
    }

    /**
     * Chat speech bubbles. Other plugins can call
     * {@link BubbleManager#show(Player, net.kyori.adventure.text.Component)} on this to put an
     * arbitrary line above a player's head.
     */
    public @NotNull BubbleManager getBubbleManager() {
        return this.bubbleManager;
    }

    public boolean canViewerSeeNametag(@NotNull Player viewer, @NotNull NameTagEntity tag) {
        if (!viewer.isOnline()) {
            return false;
        }

        Entity owner = tag.getBukkitEntity();
        if (!viewer.getWorld().equals(owner.getWorld())) {
            return false;
        }

        boolean debugView = entityManager.hasDebugView(viewer.getUniqueId());
        if (tag.isInvisible() && !debugView) {
            return false;
        }

        if (!(owner instanceof Player target)) {
            return true;
        }

        if (!VanishHook.canSee(viewer, target)) {
            return false;
        }

        return debugView || visibilityProvider.canSee(viewer, target);
    }

    public boolean isSpectatorVisibleEnabled() {
        return getConfig().getBoolean("spectator-visible.enabled", false);
    }

    public int spectatorTeleportDuration() {
        return getConfig().getInt("spectator-visible.teleport-duration", 1);
    }

    public void showTagToViewer(@NotNull NameTagEntity tag, @NotNull Player viewer) {
        tag.updateLocation();
        if (tag.shouldUseStandinFor(viewer)) {
            SpectatorHead head = tag.ensureStandin();
            if (head != null) {
                head.addViewer(viewer.getUniqueId());
            }
        } else {
            SpectatorHead head = tag.getStandin();
            if (head != null && head.hasViewer(viewer.getUniqueId())) {
                head.removeViewer(viewer.getUniqueId());
            }
        }
        tag.getPassenger().removeViewer(viewer.getUniqueId());
        tag.getPassenger().addViewer(viewer.getUniqueId());
        // Spawn the bubble before the passenger packet references its entity id.
        bubbleManager.addViewer(tag, viewer);
        tag.sendPassengerPacket(viewer);
    }

    public void hideTagFromViewer(@NotNull NameTagEntity tag, @NotNull Player viewer) {
        tag.getPassenger().removeViewer(viewer.getUniqueId());
        bubbleManager.removeViewer(tag.getBukkitEntity().getUniqueId(), viewer.getUniqueId());
        SpectatorHead head = tag.getStandin();
        if (head != null && head.hasViewer(viewer.getUniqueId())) {
            head.removeViewer(viewer.getUniqueId());
        }
        PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, new WrapperPlayServerDestroyEntities(tag.getPassenger().getEntityId()));
    }

    public HashMap<String, ConfigurationSection> getGroups() {
        return groups;
    }

    public @NotNull TextFormatter getFormatter() {
        return this.formatter;
    }

    public @NotNull HeartBar.Settings heartBarSettings() {
        return this.heartBarSettings;
    }

    /**
     * Periodic safety-net that reconciles both missing and stale viewers.
     * Runs on the main thread every second.
     */
    private void reconcileViewers() {
        boolean showSelf = getConfig().getBoolean("show-self", false);

        for (NameTagEntity tag : entityManager.getAllEntities()) {
            if (entityManager.isNameTagDisabled(tag.getBukkitEntity().getUniqueId()))
                continue;

            for (UUID viewerId : Set.copyOf(tag.getPassenger().getViewers())) {
                Player viewer = Bukkit.getPlayer(viewerId);
                boolean sameWorld = viewer != null && viewer.isOnline() && viewer.getWorld().equals(tag.getBukkitEntity().getWorld());
                boolean tracked = sameWorld && tag.getBukkitEntity().getTrackedBy().contains(viewer);
                boolean canSee = tracked && !(viewer.equals(tag.getBukkitEntity()) && !showSelf) && canViewerSeeNametag(viewer, tag);
                if (!canSee) {
                    if (viewer != null) {
                        hideTagFromViewer(tag, viewer);
                    } else {
                        tag.getPassenger().removeViewer(viewerId);
                    }
                }
            }

            for (Player tracker : tag.getBukkitEntity().getTrackedBy()) {
                if (tracker.equals(tag.getBukkitEntity()) && !showSelf)
                    continue;
                if (!canViewerSeeNametag(tracker, tag))
                    continue;

                if (!tag.getPassenger().getViewers().contains(tracker.getUniqueId())) {
                    showTagToViewer(tag, tracker);
                }
            }
        }
    }
}
