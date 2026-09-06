package com.mattmx.nametags.bubble;

import com.mattmx.nametags.NameTags;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * Turns a chat line into a speech bubble above the player who sent it.
 *
 * <p>Runs at MONITOR so every other plugin has already had its say: {@code event.message()} is the
 * final component after filtering, rewriting and colour handling, which is what the bubble should
 * show. Cancelled events are skipped, so a muted, blocked or rule-rejected message never produces
 * a bubble. ChatControl narrows the event's viewer set when it routes a line into a channel and
 * only cancels when the message is genuinely not going out, so this reads correctly alongside it.
 *
 * <p>The event fires off the main thread, so the work is handed to the scheduler before anything
 * touches an entity or a manager.
 */
public final class BubbleChatListener implements Listener {

    private final @NotNull NameTags plugin;

    public BubbleChatListener(@NotNull NameTags plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(@NotNull AsyncChatEvent event) {
        if (!plugin.getConfig().getBoolean("bubbles.enabled", true)) {
            return;
        }

        final Player player = event.getPlayer();
        final Component message = event.message();

        Bukkit.getScheduler().runTask(plugin, () -> plugin.getBubbleManager().show(player, message));
    }
}
