package com.mattmx.nametags.bubble.typewriter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Exercises the {@code withX} copy methods on {@link TypewriterStyle}. */
class TypewriterStyleTest {

  @Test
  void withMethodsOverrideOnlyTheNamedField() {
    TypewriterStyle base = TypewriterStyle.defaults();

    TypewriterStyle changed = base.withCharsPerTick(5.0);

    assertEquals(5.0, changed.charsPerTick());
    assertEquals(base.sentencePauseTicks(), changed.sentencePauseTicks());
    assertEquals(base.clausePauseTicks(), changed.clausePauseTicks());
    assertEquals(base.cursor(), changed.cursor());
    assertEquals(base.holdBaseTicks(), changed.holdBaseTicks());
    assertEquals(base.fadeTicks(), changed.fadeTicks());
  }

  @Test
  void baseInstanceIsUnchangedByWithMethods() {
    TypewriterStyle base = TypewriterStyle.defaults();

    base.withCharsPerTick(99.0);

    assertEquals(2.0, base.charsPerTick());
  }

  @Test
  void withCursorAcceptsNullToDisableTheCursor() {
    TypewriterStyle style = TypewriterStyle.defaults().withCursor(null);

    assertEquals(null, style.cursor());
  }
}
