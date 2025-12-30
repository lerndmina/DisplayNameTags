package com.mattmx.nametags;

import com.mattmx.nametags.entity.NameTagEntity;
import com.mattmx.nametags.hook.VanishHook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class NameTagsCommand implements CommandExecutor, TabCompleter {
    private final @NotNull NameTags plugin;

    public NameTagsCommand(@NotNull NameTags plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
        // SHUT UP EVA

        if (args.length == 0) {
            return false;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            reload();
            sender.sendMessage(Component.text("Reloaded!").color(NamedTextColor.GREEN));
        } else if (args[0].equalsIgnoreCase("toggle")) {
            if (!sender.hasPermission("nametags.admin.toggle")) {
                sender.sendMessage(
                        Component.text("You don't have permission to use this command.").color(NamedTextColor.RED));
                return true;
            }

            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /nametags toggle <player>").color(NamedTextColor.RED));
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player not found.").color(NamedTextColor.RED));
                return true;
            }

            boolean nowDisabled = plugin.getEntityManager().toggleNameTag(target.getUniqueId());
            NameTagEntity tag = plugin.getEntityManager().getNameTagEntity(target);

            if (tag != null) {
                if (nowDisabled) {
                    // Hide the nametag from all viewers
                    tag.getPassenger().despawn();
                } else {
                    // Show the nametag again
                    tag.getPassenger().spawn(tag.updateLocation());
                    tag.updateVisibility();

                    // If the player is invisible (potion) or vanished, don't add viewers yet
                    // The nametag will be shown automatically when they become visible
                    // via the potion effect listener or when vanish is toggled off
                    if (tag.isInvisible()) {
                        sender.sendMessage(Component
                                .text(target.getName() + "'s nametag is now enabled (but hidden due to invisibility).")
                                .color(NamedTextColor.YELLOW));
                        return true;
                    }

                    // Re-add viewers and send passenger packets
                    for (final Player viewer : Bukkit.getOnlinePlayers()) {
                        if (viewer.equals(target) && !plugin.getConfig().getBoolean("show-self", false)) {
                            continue;
                        }
                        if (!viewer.getWorld().equals(target.getWorld())) {
                            continue;
                        }
                        // Skip if target is vanished from this viewer
                        if (!VanishHook.canSee(viewer, target)) {
                            continue;
                        }
                        tag.getPassenger().addViewer(viewer.getUniqueId());
                        tag.sendPassengerPacket(viewer);
                    }
                    tag.getPassenger().refresh();
                }
            }

            sender.sendMessage(Component
                    .text(target.getName() + "'s nametag is now " + (nowDisabled ? "disabled" : "enabled") + ".")
                    .color(nowDisabled ? NamedTextColor.RED : NamedTextColor.GREEN));
            return true;
        } else if (args[0].equalsIgnoreCase("debug")) {
            sender.sendMessage(
                    Component.text("NameTags debug")
                            .appendNewline()
                            .append(
                                    Component.text("Total NameTags: " + plugin.getEntityManager().getCacheSize())
                                            .hoverEvent(HoverEvent.showText(
                                                    Component
                                                            .text("By Entity UUID: "
                                                                    + plugin.getEntityManager().getCacheSize())
                                                            .appendNewline()
                                                            .append(Component.text("By Entity ID: "
                                                                    + plugin.getEntityManager().getEntityIdMapSize()))
                                                            .appendNewline()
                                                            .append(Component.text("By Passenger ID: " + plugin
                                                                    .getEntityManager().getPassengerIdMapSize()))))
                                            .color(NamedTextColor.WHITE))
                            .appendNewline()
                            .append(
                                    Component
                                            .text("Cached last sent passengers: "
                                                    + plugin.getEntityManager().getLastSentPassengersSize())
                                            .color(NamedTextColor.WHITE))
                            .appendNewline()
                            .append(
                                    Component.text("Viewers:")
                                            .appendNewline()
                                            .append(
                                                    Component.text(
                                                            String.join("\n",
                                                                    plugin.getEntityManager()
                                                                            .getAllEntities()
                                                                            .stream()
                                                                            .map((nameTag) -> " - " + nameTag
                                                                                    .getBukkitEntity().getUniqueId()
                                                                                    + ": "
                                                                                    + nameTag.getPassenger()
                                                                                            .getViewers())
                                                                            .toList()))))
                            .color(NamedTextColor.GOLD));
        }

        return false;
    }

    private void reload() {
        for (final Player player : Bukkit.getOnlinePlayers()) {
            final NameTagEntity tag = plugin.getEntityManager().getNameTagEntity(player);

            if (tag != null) {
                tag.getTraits().destroy();
            }
        }

        this.plugin.reloadConfig();

        final boolean showSelf = plugin.getConfig().getBoolean("show-self", false);

        for (final Player player : Bukkit.getOnlinePlayers()) {
            final NameTagEntity tag = plugin.getEntityManager().removeEntity(player);

            if (tag != null) {
                tag.destroy();
            }

            final NameTagEntity newTag = plugin.getEntityManager().getOrCreateNameTagEntity(player);

            // Add all online players in the same world as viewers
            for (final Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(player) && !showSelf) {
                    continue; // Skip self unless show-self is enabled
                }

                if (!viewer.getWorld().equals(player.getWorld())) {
                    continue; // Skip players in different worlds
                }

                // Skip if player is vanished and viewer can't see them
                if (!VanishHook.canSee(viewer, player)) {
                    continue;
                }

                // Update location before adding viewers to ensure correct position
                newTag.updateLocation();

                // Remove and re-add viewer to ensure spawn packets are sent fresh
                // (mirrors the behavior in
                // PlayServerSpawnEntityHandler.attachPassengerToEntity)
                newTag.getPassenger().removeViewer(viewer.getUniqueId());
                newTag.getPassenger().addViewer(viewer.getUniqueId());
                newTag.sendPassengerPacket(viewer);
            }

            newTag.updateVisibility();
            // Refresh to send metadata to viewers immediately (fixes invisible nametags
            // after reload)
            newTag.getPassenger().refresh();
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String @NotNull [] args) {
        if (args.length == 1) {
            String lastArg = args[0].toLowerCase();
            List<String> completions = new ArrayList<>();
            for (String cmd : new String[] { "reload", "debug", "toggle" }) {
                if (cmd.startsWith(lastArg)) {
                    if (cmd.equals("toggle") && !sender.hasPermission("nametags.admin.toggle")) {
                        continue;
                    }
                    completions.add(cmd);
                }
            }
            return completions;
        } else if (args.length == 2 && args[0].equalsIgnoreCase("toggle")) {
            if (!sender.hasPermission("nametags.admin.toggle")) {
                return List.of();
            }
            String lastArg = args[1].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(lastArg))
                    .toList();
        }
        return List.of();
    }
}
