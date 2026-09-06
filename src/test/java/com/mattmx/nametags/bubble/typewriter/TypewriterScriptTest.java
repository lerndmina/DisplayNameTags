package com.mattmx.nametags.bubble.typewriter;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.mattmx.nametags.bubble.typewriter.TypewriterScript.Frame;
import static com.mattmx.nametags.bubble.typewriter.TypewriterScript.Phase;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Exercises {@link TypewriterScript#compile} and its helpers. */
class TypewriterScriptTest {

  private static String plain(Frame frame) {
    return PlainTextComponentSerializer.plainText().serialize(frame.text());
  }

  private static Frame firstFrameOfPhase(List<Frame> frames, Phase phase) {
    return frames.stream().filter(f -> f.phase() == phase).findFirst()
        .orElseThrow(() -> new AssertionError("no frame with phase " + phase));
  }

  @Test
  void firstFrameIsAtTickZeroAndLastFrameIsDone() {
    List<Frame> frames = TypewriterScript.compile(Component.text("Hi"), TypewriterStyle.defaults());

    assertEquals(0, frames.get(0).tick());
    assertEquals(Phase.DONE, frames.get(frames.size() - 1).phase());
    assertEquals(0f, frames.get(frames.size() - 1).scale());
  }

  @Test
  void ticksAreStrictlyIncreasing() {
    List<Frame> frames = TypewriterScript.compile(Component.text("Hello, world! This is a test."),
        TypewriterStyle.defaults());

    for (int i = 1; i < frames.size(); i++) {
      assertTrue(frames.get(i).tick() > frames.get(i - 1).tick(),
          "tick " + frames.get(i).tick() + " did not strictly increase after " + frames.get(i - 1).tick());
    }
  }

  @Test
  void totalTicksMatchesTheLastFrame() {
    List<Frame> frames = TypewriterScript.compile(Component.text("Hi"), TypewriterStyle.defaults());

    assertEquals(frames.get(frames.size() - 1).tick(), TypewriterScript.totalTicks(frames));
  }

  @Test
  void sentencePunctuationLengthensTheScriptByExactlyThePauseTicks() {
    TypewriterStyle style = TypewriterStyle.defaults()
        .withCharsPerTick(1.0)
        .withCursor(null)
        .withSoundEveryChars(0)
        .withSentencePauseTicks(6)
        .withClausePauseTicks(3);

    List<Frame> withoutPunctuation = TypewriterScript.compile(Component.text("aXb"), style);
    List<Frame> withSentenceEnd = TypewriterScript.compile(Component.text("a.b"), style);

    int baseHold = firstFrameOfPhase(withoutPunctuation, Phase.HOLD).tick();
    int pausedHold = firstFrameOfPhase(withSentenceEnd, Phase.HOLD).tick();

    assertEquals(style.sentencePauseTicks(), pausedHold - baseHold);
  }

  @Test
  void clausePunctuationLengthensTheScriptByExactlyThePauseTicks() {
    TypewriterStyle style = TypewriterStyle.defaults()
        .withCharsPerTick(1.0)
        .withCursor(null)
        .withSoundEveryChars(0)
        .withSentencePauseTicks(6)
        .withClausePauseTicks(3);

    List<Frame> withoutPunctuation = TypewriterScript.compile(Component.text("aXb"), style);
    List<Frame> withClauseEnd = TypewriterScript.compile(Component.text("a,b"), style);

    int baseHold = firstFrameOfPhase(withoutPunctuation, Phase.HOLD).tick();
    int pausedHold = firstFrameOfPhase(withClauseEnd, Phase.HOLD).tick();

    assertEquals(style.clausePauseTicks(), pausedHold - baseHold);
  }

  @Test
  void holdTicksClampsToTheConfiguredMaximum() {
    TypewriterStyle style = TypewriterStyle.defaults()
        .withHoldBaseTicks(0)
        .withHoldPerCharTicks(10.0)
        .withHoldMinTicks(0)
        .withHoldMaxTicks(100);

    assertEquals(100, TypewriterScript.holdTicks(50, style));
  }

  @Test
  void holdTicksClampsToTheConfiguredMinimum() {
    TypewriterStyle style = TypewriterStyle.defaults()
        .withHoldBaseTicks(0)
        .withHoldPerCharTicks(0.0)
        .withHoldMinTicks(50)
        .withHoldMaxTicks(300);

    assertEquals(50, TypewriterScript.holdTicks(0, style));
  }

  @Test
  void soundFiresOnlyOnFramesWhereARevealMultipleWasJustCrossed() {
    TypewriterStyle style = TypewriterStyle.defaults()
        .withCharsPerTick(1.0)
        .withCursor(null)
        .withSoundEveryChars(3);

    List<Frame> frames = TypewriterScript.compile(Component.text("abcdef"), style);
    List<Frame> typing = frames.stream().filter(f -> f.phase() == Phase.TYPING).toList();

    assertEquals(6, typing.size());
    assertEquals("abc", plain(typing.get(2)));
    assertTrue(typing.get(2).sound(), "revealing the 3rd character should trigger a sound");
    assertEquals("abcdef", plain(typing.get(5)));
    assertTrue(typing.get(5).sound(), "revealing the 6th character should trigger a sound");
    assertFalse(typing.get(0).sound());
    assertFalse(typing.get(1).sound());
    assertFalse(typing.get(3).sound());
    assertFalse(typing.get(4).sound());
  }

  @Test
  void soundNeverFiresWhenSoundEveryCharsIsZero() {
    TypewriterStyle style = TypewriterStyle.defaults().withSoundEveryChars(0);

    List<Frame> frames = TypewriterScript.compile(Component.text("abcdefghi"), style);

    assertTrue(frames.stream().noneMatch(Frame::sound));
  }

  @Test
  void cursorIsAbsentFromHoldFrames() {
    TypewriterStyle style = TypewriterStyle.defaults().withCursor("▏");

    List<Frame> frames = TypewriterScript.compile(Component.text("Hi"), style);
    Frame hold = firstFrameOfPhase(frames, Phase.HOLD);

    assertEquals("Hi", plain(hold));
    assertFalse(plain(hold).contains("▏"));
  }

  @Test
  void miniMessageInputSurvivesCompilationWithoutLeakingTagText() {
    List<Frame> frames = TypewriterScript.compile("<red>Hello</red> <bold>world</bold>!",
        TypewriterStyle.defaults());

    Frame hold = firstFrameOfPhase(frames, Phase.HOLD);
    String plain = plain(hold);

    assertEquals("Hello world!", plain);
    assertFalse(plain.contains("<"));
    assertFalse(plain.contains(">"));
  }

  @Test
  void fadePhaseEndsWithDecreasingOpacityAndScale() {
    List<Frame> frames = TypewriterScript.compile(Component.text("Hi"), TypewriterStyle.defaults());
    List<Frame> fade = frames.stream().filter(f -> f.phase() == Phase.FADE).toList();

    assertFalse(fade.isEmpty());
    for (int i = 1; i < fade.size(); i++) {
      assertTrue(fade.get(i).textOpacity() <= fade.get(i - 1).textOpacity());
      assertTrue(fade.get(i).scale() <= fade.get(i - 1).scale());
    }
  }
}
