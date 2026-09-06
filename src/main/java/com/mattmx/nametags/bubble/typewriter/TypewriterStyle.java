package com.mattmx.nametags.bubble.typewriter;

/**
 * Timing and appearance knobs for a single typewriter playback.
 *
 * <p>Every field has a sane default reachable via {@link #defaults()}; callers override only the
 * fields they care about with the {@code withX} methods, which each return a new instance (this
 * type is immutable).
 *
 * @param charsPerTick        characters revealed per tick while typing (accumulates fractionally,
 *                            so e.g. {@code 1.5} reveals 1 char one tick and 2 the next); default
 *                            {@code 2.0} is roughly 40 chars/second at 20 ticks/second.
 * @param sentencePauseTicks  ticks with no reveal inserted right after a sentence-ending
 *                            character ({@code . ! ? …}) is shown.
 * @param clausePauseTicks    ticks with no reveal inserted right after a clause-ending character
 *                            ({@code , ; :}) is shown.
 * @param cursor              the blinking cursor glyph appended while typing, or {@code null} for
 *                            no cursor at all.
 * @param cursorBlinkTicks    how many ticks the cursor stays visible, then how many it stays
 *                            hidden, alternating, while typing is in progress.
 * @param holdBaseTicks       flat number of ticks the fully revealed text is held before fading.
 * @param holdPerCharTicks    extra hold ticks added per visible character, so longer messages sit
 *                            on screen longer.
 * @param holdMinTicks        floor applied to the computed hold duration.
 * @param holdMaxTicks        ceiling applied to the computed hold duration.
 * @param fadeTicks           ticks spent fading the held text out before the display is removed.
 * @param soundEveryChars     emit a sound cue every N characters revealed while typing; {@code 0}
 *                            disables the cue entirely.
 */
public record TypewriterStyle(
        double charsPerTick,
        int sentencePauseTicks,
        int clausePauseTicks,
        String cursor,
        int cursorBlinkTicks,
        int holdBaseTicks,
        double holdPerCharTicks,
        int holdMinTicks,
        int holdMaxTicks,
        int fadeTicks,
        int soundEveryChars) {

    /** The out-of-the-box style described in the field docs above. */
    public static TypewriterStyle defaults() {
        return new TypewriterStyle(2.0, 6, 3, "▏", 6, 40, 1.5, 40, 300, 10, 3);
    }

    public TypewriterStyle withCharsPerTick(double charsPerTick) {
        return new TypewriterStyle(charsPerTick, sentencePauseTicks, clausePauseTicks, cursor,
                cursorBlinkTicks, holdBaseTicks, holdPerCharTicks, holdMinTicks, holdMaxTicks, fadeTicks,
                soundEveryChars);
    }

    public TypewriterStyle withSentencePauseTicks(int sentencePauseTicks) {
        return new TypewriterStyle(charsPerTick, sentencePauseTicks, clausePauseTicks, cursor,
                cursorBlinkTicks, holdBaseTicks, holdPerCharTicks, holdMinTicks, holdMaxTicks, fadeTicks,
                soundEveryChars);
    }

    public TypewriterStyle withClausePauseTicks(int clausePauseTicks) {
        return new TypewriterStyle(charsPerTick, sentencePauseTicks, clausePauseTicks, cursor,
                cursorBlinkTicks, holdBaseTicks, holdPerCharTicks, holdMinTicks, holdMaxTicks, fadeTicks,
                soundEveryChars);
    }

    public TypewriterStyle withCursor(String cursor) {
        return new TypewriterStyle(charsPerTick, sentencePauseTicks, clausePauseTicks, cursor,
                cursorBlinkTicks, holdBaseTicks, holdPerCharTicks, holdMinTicks, holdMaxTicks, fadeTicks,
                soundEveryChars);
    }

    public TypewriterStyle withCursorBlinkTicks(int cursorBlinkTicks) {
        return new TypewriterStyle(charsPerTick, sentencePauseTicks, clausePauseTicks, cursor,
                cursorBlinkTicks, holdBaseTicks, holdPerCharTicks, holdMinTicks, holdMaxTicks, fadeTicks,
                soundEveryChars);
    }

    public TypewriterStyle withHoldBaseTicks(int holdBaseTicks) {
        return new TypewriterStyle(charsPerTick, sentencePauseTicks, clausePauseTicks, cursor,
                cursorBlinkTicks, holdBaseTicks, holdPerCharTicks, holdMinTicks, holdMaxTicks, fadeTicks,
                soundEveryChars);
    }

    public TypewriterStyle withHoldPerCharTicks(double holdPerCharTicks) {
        return new TypewriterStyle(charsPerTick, sentencePauseTicks, clausePauseTicks, cursor,
                cursorBlinkTicks, holdBaseTicks, holdPerCharTicks, holdMinTicks, holdMaxTicks, fadeTicks,
                soundEveryChars);
    }

    public TypewriterStyle withHoldMinTicks(int holdMinTicks) {
        return new TypewriterStyle(charsPerTick, sentencePauseTicks, clausePauseTicks, cursor,
                cursorBlinkTicks, holdBaseTicks, holdPerCharTicks, holdMinTicks, holdMaxTicks, fadeTicks,
                soundEveryChars);
    }

    public TypewriterStyle withHoldMaxTicks(int holdMaxTicks) {
        return new TypewriterStyle(charsPerTick, sentencePauseTicks, clausePauseTicks, cursor,
                cursorBlinkTicks, holdBaseTicks, holdPerCharTicks, holdMinTicks, holdMaxTicks, fadeTicks,
                soundEveryChars);
    }

    public TypewriterStyle withFadeTicks(int fadeTicks) {
        return new TypewriterStyle(charsPerTick, sentencePauseTicks, clausePauseTicks, cursor,
                cursorBlinkTicks, holdBaseTicks, holdPerCharTicks, holdMinTicks, holdMaxTicks, fadeTicks,
                soundEveryChars);
    }

    public TypewriterStyle withSoundEveryChars(int soundEveryChars) {
        return new TypewriterStyle(charsPerTick, sentencePauseTicks, clausePauseTicks, cursor,
                cursorBlinkTicks, holdBaseTicks, holdPerCharTicks, holdMinTicks, holdMaxTicks, fadeTicks,
                soundEveryChars);
    }
}
