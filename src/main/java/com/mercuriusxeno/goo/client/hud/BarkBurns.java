package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.ability.hearts.HeartKind;
import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import java.util.ArrayList;
import java.util.List;

/**
 * The bark halves burning away on the HUD (decision
 * bark-hearts-burn-away-right-to-left). The client reads a burn from the
 * bark halves its overlay sync drops while the player is on fire, so no
 * packet carries it: each lost half burns from its right edge to its left,
 * the rightmost first and each half after it a few ticks behind. A drop with
 * the player not burning plays nothing.
 */
final class BarkBurns {

    /** Ticks one half takes to burn across. */
    static final float BURN_TICKS = 10f;
    /** Ticks each half inward waits behind the half to its right. */
    static final float STAGGER_TICKS = 4f;

    private List<Integer> lastBark = List.of();
    private final List<Burn> burning = new ArrayList<>();

    /**
     * One half of bark burning away.
     *
     * @param slot  the heart slot
     * @param half  the half: zero for the left, one for the right
     * @param start the GUI time the flame front sets off
     */
    record Burn(int slot, int half, float start) {

        /**
         * How far the front has crossed the half.
         *
         * @param now the GUI time
         * @return zero before it starts, one once the half is gone
         */
        float progress(float now) {
            return Math.clamp((now - start) / BURN_TICKS, 0f, 1f);
        }

        /**
         * The pixels the front has burned in from the half's right edge.
         *
         * @param now the GUI time
         * @return zero to the half's width
         */
        int burnedFromRight(float now) {
            return Math.round(progress(now) * RegrowCrawl.HALF_WIDTH);
        }
    }

    /**
     * The halves a drop in bark lost, rightmost first.
     *
     * @param before the bark halves per slot at the last sync
     * @param after  the bark halves per slot now
     * @param burning whether the player is on fire
     * @param now    the GUI time
     * @return a burn per lost half, staggered inward; empty when the player is not burning
     */
    static List<Burn> burnsFor(List<Integer> before, List<Integer> after, boolean burning, float now) {
        List<Burn> burns = new ArrayList<>();
        if (!burning) {
            return burns;
        }
        for (int slot = before.size() - 1; slot >= 0; slot--) {
            int kept = slot < after.size() ? after.get(slot) : 0;
            for (int half = before.get(slot) - 1; half >= kept; half--) {
                // bark-hearts-burn-away-right-to-left: burning down from right to left
                burns.add(new Burn(slot, half, now + burns.size() * STAGGER_TICKS));
            }
        }
        return burns;
    }

    /**
     * Reads the overlay as the HUD sees it this frame, starting a burn on
     * every bark half lost since the last frame while the player burns, and
     * dropping burns that have finished.
     *
     * @param overlay the player's overlay
     * @param onFire  whether the player is on fire
     * @param now     the GUI time
     * @return the burns playing
     */
    List<Burn> update(HeartOverlay overlay, boolean onFire, float now) {
        List<Integer> bark = overlay.stands() && overlay.kind() == HeartKind.BARKSKIN ? overlay.shields() : List.of();
        burning.addAll(burnsFor(lastBark, bark, onFire, now));
        lastBark = bark;
        burning.removeIf(burn -> burn.progress(now) >= 1f);
        return List.copyOf(burning);
    }
}
