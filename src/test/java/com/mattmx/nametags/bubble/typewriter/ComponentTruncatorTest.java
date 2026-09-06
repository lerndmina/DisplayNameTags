package com.mattmx.nametags.bubble.typewriter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link ComponentTruncator} against hand-built and MiniMessage-parsed component trees.
 */
class ComponentTruncatorTest {

  @Test
  void truncatesPlainTextMidString() {
    Component full = Component.text("Hello, world!");

    Component cut = ComponentTruncator.truncate(full, 5);

    assertEquals("Hello", PlainTextComponentSerializer.plainText().serialize(cut));
  }

  @Test
  void keepsStyleAndDecorationOnNestedChildren() {
    Component full = Component.text("Hi ")
        .append(Component.text("there", NamedTextColor.RED, TextDecoration.BOLD));

    Component cut = ComponentTruncator.truncate(full, 6);

    assertEquals("Hi the", PlainTextComponentSerializer.plainText().serialize(cut));
    Component child = cut.children().get(0);
    assertEquals(NamedTextColor.RED, child.style().color());
    assertTrue(child.style().hasDecoration(TextDecoration.BOLD));
  }

  @Test
  void preservesClickEventsOnKeptText() {
    Component full = Component.text("click me").clickEvent(ClickEvent.runCommand("/foo"));

    Component cut = ComponentTruncator.truncate(full, 5);

    assertEquals("click", PlainTextComponentSerializer.plainText().serialize(cut));
    assertTrue(cut.clickEvent().payload() instanceof ClickEvent.Payload.Text text && text.value().equals("/foo"));
  }

  @Test
  void requestingZeroCharsReturnsEmptyComponent() {
    Component full = Component.text("hello", NamedTextColor.BLUE);

    Component cut = ComponentTruncator.truncate(full, 0);

    assertEquals("", PlainTextComponentSerializer.plainText().serialize(cut));
  }

  @Test
  void requestingMoreThanTheFullLengthReturnsEverything() {
    Component full = Component.text("hi").append(Component.text(" there"));

    Component cut = ComponentTruncator.truncate(full, 1000);

    assertEquals("hi there", PlainTextComponentSerializer.plainText().serialize(cut));
  }

  @Test
  void translatableComponentCountsAsOneCharacterAndIsKeptWhole() {
    Component full = Component.text("a").append(Component.translatable("block.minecraft.stone"))
        .append(Component.text("bc"));

    // "a" (1 char, the root's own content) + the translatable (1 slot) = budget 2: the
    // translatable should survive whole as the root's only remaining child, and "bc" is dropped.
    Component cut = ComponentTruncator.truncate(full, 2);

    assertEquals("a", ((TextComponent) cut).content());
    assertEquals(1, cut.children().size());
    assertEquals("block.minecraft.stone", ((TranslatableComponent) cut.children().get(0)).key());
  }

  @Test
  void translatableComponentIsDroppedEntirelyWhenBudgetRunsOutBeforeIt() {
    Component full = Component.text("a").append(Component.translatable("block.minecraft.stone"));

    Component cut = ComponentTruncator.truncate(full, 1);

    assertEquals("a", ((TextComponent) cut).content());
    assertTrue(cut.children().isEmpty());
  }

  @Test
  void visibleLengthCountsNewlinesAsOneCharacter() {
    Component full = Component.text("a\nb");

    assertEquals(3, ComponentTruncator.visibleLength(full));
  }

  @Test
  void visibleLengthCountsAnAtomicComponentAsOneRegardlessOfItsOwnDepth() {
    Component full = Component.text("x").append(Component.translatable("some.key")
        .append(Component.text("nested and long content that would be many chars")));

    assertEquals(2, ComponentTruncator.visibleLength(full));
  }

  @Test
  void miniMessageTagsDoNotLeakIntoTruncatedPlainText() {
    Component full = MiniMessage.miniMessage().deserialize("<red>Hello</red> <bold>world</bold>!");

    Component cut = ComponentTruncator.truncate(full, 7);

    String plain = PlainTextComponentSerializer.plainText().serialize(cut);
    assertEquals("Hello w", plain);
    assertFalse(plain.contains("<"));
    assertFalse(plain.contains(">"));
  }

  @Test
  void miniMessageStyleSurvivesTruncation() {
    Component full = MiniMessage.miniMessage().deserialize("<red>Hello</red> <bold>world</bold>!");

    Component cut = ComponentTruncator.truncate(full, 7);

    Optional<Component> redPart = findContaining(cut, "Hello");
    assertTrue(redPart.isPresent());
    assertEquals(NamedTextColor.RED, redPart.get().style().color());

    Optional<Component> boldPart = findContaining(cut, "w");
    assertTrue(boldPart.isPresent());
    assertTrue(boldPart.get().style().hasDecoration(TextDecoration.BOLD));
  }

  /** Depth-first search for the first component whose own text content contains {@code needle}. */
  private static Optional<Component> findContaining(Component component, String needle) {
    if (component instanceof TextComponent text && text.content().contains(needle)) {
      return Optional.of(component);
    }
    for (Component child : component.children()) {
      Optional<Component> found = findContaining(child, needle);
      if (found.isPresent()) {
        return found;
      }
    }
    return Optional.empty();
  }
}
