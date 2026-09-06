package com.mattmx.nametags.bubble.typewriter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * Cuts a styled {@link Component} tree down to its first N visible characters without losing any
 * colour, decoration, hover/click event or nesting on the characters that remain.
 *
 * <p>The component tree is walked depth-first in render order: a component's own text comes
 * first, then each of its children in order. {@link TextComponent} content is a string of real
 * characters (a newline counts as one, same as any other character) and can be cut mid-string.
 * Every other component kind ({@code TranslatableComponent}, {@code KeybindComponent}, score and
 * selector components, NBT components, …) has no meaningful "partial" rendering, so it counts as
 * exactly one character and is either kept in full or dropped in full — never trimmed internally.
 */
public final class ComponentTruncator {

    private ComponentTruncator() {
    }

    /**
     * Returns a component showing only the first {@code visibleChars} characters of {@code full},
     * preserving the style, event and nesting of everything kept. Requesting more characters than
     * {@code full} has simply returns the whole thing; requesting zero or fewer returns an empty
     * component with none of the original style attached.
     */
    public static Component truncate(Component full, int visibleChars) {
        int[] budget = {Math.max(0, visibleChars)};
        Component result = truncate(full, budget);
        return result == null ? Component.empty() : result;
    }

    /**
     * Counts the visible characters in {@code component} using the same rule {@link #truncate}
     * cuts by: real characters for text content, exactly one per non-text component regardless of
     * what that component itself contains.
     */
    public static int visibleLength(Component component) {
        if (component instanceof TextComponent text) {
            int length = text.content().length();
            for (Component child : component.children()) {
                length += visibleLength(child);
            }
            return length;
        }
        return 1;
    }

    /**
     * Recursive worker. {@code budget[0]} is the number of characters still allowed; it is
     * decremented in place as characters are consumed. Returns {@code null} when the component is
     * dropped entirely (budget was already exhausted before it was reached).
     */
    private static Component truncate(Component component, int[] budget) {
        if (budget[0] <= 0) {
            return null;
        }
        if (component instanceof TextComponent text) {
            String content = text.content();
            int take = Math.min(content.length(), budget[0]);
            budget[0] -= take;
            String kept = take == content.length() ? content : content.substring(0, take);

            List<Component> newChildren = new ArrayList<>();
            for (Component child : component.children()) {
                if (budget[0] <= 0) {
                    break;
                }
                Component truncatedChild = truncate(child, budget);
                if (truncatedChild != null) {
                    newChildren.add(truncatedChild);
                }
            }
            return Component.text(kept, text.style()).children(newChildren);
        }
        // Atomic: one character's worth of budget for the whole subtree, kept whole.
        budget[0] -= 1;
        return component;
    }
}
