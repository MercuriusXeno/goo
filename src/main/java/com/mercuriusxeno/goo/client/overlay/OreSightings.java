package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.crystal.OreVeins;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The gem ore veins Glitter has revealed on this client: each vein's blocks
 * show through walls from the tick the sphere's front reaches it to the end
 * of its life, fading in and fading away at its end, and a vein a later
 * hold finds again keeps showing rather than blinking out.
 * decision glitter-sphere-icons-gem-ore-groups
 */
public final class OreSightings {

    /** The client's revealed veins. */
    public static final OreSightings CLIENT = new OreSightings();

    /** Ticks a vein takes to fade in. */
    static final float FADE_IN_TICKS = 4;
    /** Ticks a vein takes to fade away at its life's end. */
    static final float FADE_OUT_TICKS = 20;
    /** Blocks apart a vein found again lies from its earlier sighting and still counts as the same vein. */
    private static final double SAME_VEIN = 1;
    /** The answer of a search finding no earlier sighting. */
    private static final int NONE = -1;

    /**
     * One revealed vein.
     *
     * @param vein     the vein
     * @param appearAt the game time it shows from
     * @param endAt    the game time it is gone by
     */
    record Seen(OreVeins.Vein vein, double appearAt, double endAt) {

        /**
         * How fully the vein shows at a moment: nothing before it shows and
         * once gone, fading in as it appears and away at its life's end.
         *
         * @param now the game time with its partial tick
         * @return the share shown in [0, 1]
         */
        float shownAt(double now) {
            float in = (float) ((now - appearAt) / FADE_IN_TICKS);
            float out = (float) ((endAt - now) / FADE_OUT_TICKS);
            return Math.clamp(Math.min(in, out), 0f, 1f);
        }
    }

    /**
     * One ore block to draw through walls.
     *
     * @param pos   the block
     * @param shown how fully it shows, in [0, 1]
     */
    public record Sighting(BlockPos pos, float shown) {
    }

    private final List<Seen> seen = new ArrayList<>();

    private OreSightings() {
    }

    /**
     * Takes the veins the front has just reached: each shows from now for
     * its life; a vein already showing keeps showing until then.
     *
     * @param now   the game time the front reached them
     * @param veins the veins the front reached
     * @param life  the ticks each shows once revealed
     */
    public void reveal(long now, List<OreVeins.Vein> veins, int life) {
        for (OreVeins.Vein vein : veins) {
            double appearAt = now;
            int earlier = sightingOf(vein, now);
            if (earlier >= 0) {
                appearAt = Math.min(appearAt, seen.get(earlier).appearAt());
                seen.remove(earlier);
            }
            seen.add(new Seen(vein, appearAt, now + life));
        }
    }

    private int sightingOf(OreVeins.Vein vein, long now) {
        for (int index = 0; index < seen.size(); index++) {
            Seen earlier = seen.get(index);
            if (earlier.endAt() > now && earlier.vein().ore().equals(vein.ore())
                    && earlier.vein().centroid().distanceTo(vein.centroid()) <= SAME_VEIN) {
                return index;
            }
        }
        return NONE;
    }

    /**
     * The ore blocks showing at a moment, each as fully as its vein shows.
     *
     * @param now the game time with its partial tick
     * @return the blocks to draw
     */
    public List<Sighting> showingAt(double now) {
        seen.removeIf(entry -> entry.endAt() <= now);
        List<Sighting> showing = new ArrayList<>();
        for (Seen entry : seen) {
            float shown = entry.shownAt(now);
            if (shown > 0f) {
                entry.vein().blocks().forEach(pos -> showing.add(new Sighting(pos, shown)));
            }
        }
        return showing;
    }

    /**
     * The veins that begin to show after one moment and by another, where
     * each bursts into sparkles.
     *
     * @param after the moment the last check ran, exclusive
     * @param upTo  the moment now, inclusive
     * @return each such vein's centroid
     */
    public List<Vec3> burstsBetween(double after, double upTo) {
        return seen.stream().filter(entry -> entry.appearAt() > after && entry.appearAt() <= upTo)
                .map(entry -> entry.vein().centroid()).toList();
    }

    /** Drops every sighting, as a disconnect does. */
    public void clear() {
        seen.clear();
    }
}
