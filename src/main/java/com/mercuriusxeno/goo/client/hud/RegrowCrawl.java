package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import net.minecraft.util.RandomSource;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * The crawl a regrowing half of shield draws while it regrows: the slot
 * regrowing next, by the overlay's own regrow rule, and how far the regrow
 * interval has run, which the HUD paints as a front crossing the half
 * (decisions ash-heart-smolders-while-reigniting and wood-crawls-across-regrowing-heart).
 * Kindle's front smolders, ragged row by row and flickering with the tick;
 * Barkskin's creeps evenly.
 */
final class RegrowCrawl {

    /** A half heart's width on the sprite, which one regrowing half spans. */
    static final int HALF_WIDTH = 5;
    /** The pixels a ragged front may run ahead of or lag behind its row's even reach. */
    private static final int RAG = 1;
    /** The offsets a ragged row rolls among: behind, even, ahead. */
    private static final int RAG_CHOICES = 3;
    private static final long ROW_SALT = 0x9E3779B97F4A7C15L;
    private static final long TICK_SALT = 0xC2B2AE3D27D4EB4FL;
    /** Ticks a ragged front holds its shape before it flickers to the next. */
    private static final int FLICKER_TICKS = 3;

    private RegrowCrawl() {
    }

    /**
     * The half of shield regrowing now.
     *
     * @param slot     the heart slot regrowing
     * @param fromHalf the half the crawl crosses: zero for the left, one for the right
     * @param progress how far the regrow interval has run, zero to one
     */
    record Crawl(int slot, int fromHalf, float progress) {
    }

    /**
     * The crawl standing on an overlay at a moment, if a half regrows.
     *
     * @param overlay the player's overlay
     * @param health  the player's real health
     * @param now     the game time, fraction included
     * @return the crawl, or empty when no half regrows
     */
    static Optional<Crawl> crawl(HeartOverlay overlay, float health, float now) {
        OptionalInt slot = overlay.nextRegrowSlot(health);
        if (slot.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Crawl(slot.getAsInt(), overlay.shieldAt(slot.getAsInt()),
                progress(now, overlay.regrowAt(), overlay.regrowInterval())));
    }

    /**
     * How far a regrow interval has run.
     *
     * @param now      the game time, fraction included
     * @param regrowAt the game time the half regrows at
     * @param interval the interval's length in ticks
     * @return zero at the interval's start, one at the regrow time, clamped between
     */
    static float progress(float now, long regrowAt, long interval) {
        if (interval <= 0L) {
            return 1f;
        }
        return Math.clamp(1f - (regrowAt - now) / interval, 0f, 1f);
    }

    /**
     * The pixels a crawl's front has crossed on one row of the half: even
     * for a smooth front, and for a ragged one running a pixel ahead or behind
     * by a roll on the row and the tick, never past the half's edges.
     *
     * @param row      the sprite row
     * @param progress how far the interval has run, zero to one
     * @param tick     the GUI tick
     * @param ragged   whether the front is ragged
     * @return the pixels crossed, zero to the half's width
     */
    static int rowReach(int row, float progress, int tick, boolean ragged) {
        if (progress <= 0f) {
            return 0;
        }
        if (progress >= 1f) {
            return HALF_WIDTH;
        }
        int even = Math.round(progress * HALF_WIDTH);
        int rag = ragged ? jitter(row, tick / FLICKER_TICKS) : 0;
        return Math.clamp(even + rag, 0, HALF_WIDTH);
    }

    private static int jitter(int row, int phase) {
        // ash-heart-smolders-while-reigniting: a procedurally crawling, smoldering front
        return RandomSource.create(row * ROW_SALT ^ phase * TICK_SALT).nextInt(RAG_CHOICES) - RAG;
    }
}
