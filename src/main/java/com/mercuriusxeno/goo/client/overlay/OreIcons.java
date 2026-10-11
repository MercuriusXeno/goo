package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.crystal.OreVeins;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The gem ore veins Glitter has revealed on this client: each shows from
 * the tick the sphere's front reaches it to the end of its life, popping in
 * and shrinking away at its end, and a vein a later ping finds again keeps
 * showing rather than blinking out. Veins of one ore lying close together
 * show as one icon.
 * decision glitter-sphere-icons-gem-ore-groups
 */
public final class OreIcons {

    /** The client's revealed veins. */
    public static final OreIcons CLIENT = new OreIcons();

    /** Blocks apart two veins of one ore lie and still show as one icon (operator ruling 2026-10-10). */
    public static final double MERGE_DISTANCE = 4;
    /** Ticks an icon takes to pop in. */
    static final float POP_TICKS = 4;
    /** Ticks an icon takes to shrink away at its life's end. */
    static final float SHRINK_TICKS = 20;
    /** Blocks apart a vein found again lies from its earlier sighting and still counts as the same vein. */
    private static final double SAME_VEIN = 1;
    private static final float HALF = 0.5f;
    /** The answer of a search finding no earlier sighting. */
    private static final int NONE = -1;

    /**
     * One revealed vein.
     *
     * @param vein     the vein
     * @param appearAt the game time it shows from
     * @param endAt    the game time it is gone by
     */
    record Shown(OreVeins.Vein vein, double appearAt, double endAt) {

        /**
         * The icon's scale at a moment: 0 before it shows and once gone,
         * growing to 1 as it pops in and shrinking to 0 over its life's end.
         *
         * @param now the game time with its partial tick
         * @return the scale in [0, 1]
         */
        float scaleAt(double now) {
            float in = (float) ((now - appearAt) / POP_TICKS);
            float out = (float) ((endAt - now) / SHRINK_TICKS);
            return Math.clamp(Math.min(in, out), 0f, 1f);
        }
    }

    /**
     * One icon to draw.
     *
     * @param ore      the ore whose item the icon shows
     * @param centroid where it shows
     * @param scale    its scale in [0, 1]
     */
    public record Icon(Identifier ore, Vec3 centroid, float scale) {
    }

    private final List<Shown> shown = new ArrayList<>();

    private OreIcons() {
    }

    /**
     * Takes one ping's veins: each shows from the tick the front reaches it
     * for its life; a vein already showing keeps showing until then.
     *
     * @param now    the game time the ping arrived at
     * @param veins  the veins the ping found
     * @param reveal for each vein, the ticks after the ping the front reaches it
     * @param life   the ticks each shows once revealed
     */
    public void reveal(long now, List<OreVeins.Vein> veins, List<Integer> reveal, int life) {
        for (int index = 0; index < veins.size(); index++) {
            OreVeins.Vein vein = veins.get(index);
            double appearAt = now + reveal.get(index);
            int earlier = sightingOf(vein, now);
            if (earlier >= 0) {
                appearAt = Math.min(appearAt, shown.get(earlier).appearAt());
                shown.remove(earlier);
            }
            shown.add(new Shown(vein, appearAt, now + reveal.get(index) + life));
        }
    }

    private int sightingOf(OreVeins.Vein vein, long now) {
        for (int index = 0; index < shown.size(); index++) {
            Shown earlier = shown.get(index);
            if (earlier.endAt() > now && earlier.vein().ore().equals(vein.ore())
                    && earlier.vein().centroid().distanceTo(vein.centroid()) <= SAME_VEIN) {
                return index;
            }
        }
        return NONE;
    }

    /**
     * The icons showing at a moment, veins of one ore lying close together
     * merged into one, each at the largest scale among the veins it holds.
     *
     * @param now the game time with its partial tick
     * @return the icons
     */
    public List<Icon> iconsAt(double now) {
        shown.removeIf(entry -> entry.endAt() <= now);
        List<Shown> showing = shown.stream().filter(entry -> entry.scaleAt(now) > 0f).toList();
        List<Icon> icons = new ArrayList<>();
        for (OreVeins.Vein merged : OreVeins.merge(showing.stream().map(Shown::vein).toList(), MERGE_DISTANCE)) {
            float scale = (float) showing.stream()
                    .filter(entry -> entry.vein().ore().equals(merged.ore())
                            && entry.vein().centroid().distanceTo(merged.centroid()) <= MERGE_DISTANCE)
                    .mapToDouble(entry -> entry.scaleAt(now)).max().orElse(0);
            icons.add(new Icon(merged.ore(), merged.centroid(), scale));
        }
        return icons;
    }

    /**
     * The veins whose icons begin to show after one moment and by another,
     * where each bursts into sparkles.
     *
     * @param after the moment the last check ran, exclusive
     * @param upTo  the moment now, inclusive
     * @return each such vein's centroid
     */
    public List<Vec3> burstsBetween(double after, double upTo) {
        return shown.stream().filter(entry -> entry.appearAt() > after && entry.appearAt() <= upTo)
                .map(entry -> entry.vein().centroid()).toList();
    }

    /** Drops every icon, as a disconnect does. */
    public void clear() {
        shown.clear();
    }

    /**
     * Where a point shows on the gui, through the camera's projection and
     * view rotation; empty for a point behind the camera.
     *
     * @param clip     the projection times the view rotation
     * @param relative the point less the camera's position
     * @param guiWidth the gui's width
     * @param guiHeight the gui's height
     * @return the gui x and y
     */
    public static Optional<float[]> toGui(Matrix4fc clip, Vec3 relative, int guiWidth, int guiHeight) {
        Vector4f point = clip.transform(new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1f));
        if (point.w <= 0f) {
            return Optional.empty();
        }
        float x = (point.x / point.w * HALF + HALF) * guiWidth;
        float y = (HALF - point.y / point.w * HALF) * guiHeight;
        return Optional.of(new float[]{x, y});
    }
}
