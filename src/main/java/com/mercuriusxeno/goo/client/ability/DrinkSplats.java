package com.mercuriusxeno.goo.client.ability;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The spheres an Unmake stream splats into the drink's fluid target this
 * frame: the sphere at every ring of its path and spheres between each pair
 * of rings, spaced under the narrower ring's radius so the swept segment is
 * covered with no gap, each wearing the block's color crossfading to the
 * goo's by its share of the route; and its block, standing as a rounded cube
 * shrinking alone until it has rounded into a sphere and as that sphere after,
 * the stream flowing only once it has. Spheres overlap where they meet, and
 * the screen-space fluid makes them one skin.
 * decision unmake-drink-draws-as-a-screen-space-fluid
 */
public final class DrinkSplats {

    /**
     * The most a step between two spheres along a stream may be, as a share of the narrower ring's radius, so
     * neighbours overlap by half and the segment between them shows no waist.
     */
    static final double SPACING = 0.5;
    /** The narrowest radius splatted, in blocks: half the thread, so the thread into the palm always shows. */
    static final double NARROWEST = DrinkStream.THINNEST / 2;
    /** How far a cube's corner lies from its middle, in half widths. */
    private static final double CORNER = Math.sqrt(3);

    private DrinkSplats() {
    }

    /**
     * What a stream is drawn with: two solid colors crossfaded along the route, in the light along its path.
     *
     * @param blockColor the block's color, worn at the block, ARGB
     * @param gooColor   the goo's color, worn at the hand, ARGB
     * @param blockLight the block light along the stream, in lightmap coordinates
     * @param skyLight   the sky light along the stream, in lightmap coordinates
     */
    public record Coat(int blockColor, int gooColor, int blockLight, int skyLight) {
    }

    /**
     * One shape splatted into the fluid target: a sphere, or the block's rounded cube.
     *
     * @param center   its middle, in the world
     * @param half     its radius, or the cube's half width
     * @param rounding how far the cube's edges are rounded, in blocks; its half for a sphere
     * @param color    its color, ARGB
     * @param coat     the coat its light is read from
     */
    public record Splat(Vec3 center, double half, double rounding, int color, Coat coat) {

        /**
         * @return whether the splat is a sphere, its rounding its whole half width
         */
        public boolean sphere() {
            return rounding >= half;
        }

        /**
         * @return the radius of the sphere bounding the shape: its own for a sphere, out to the rounded corner for a
         *         cube
         */
        public double bound() {
            return (half - rounding) * CORNER + rounding;
        }

        /**
         * @param camera a camera-relative origin
         * @return the splat moved to be relative to it
         */
        public Splat relativeTo(Vec3 camera) {
            return new Splat(center.subtract(camera), half, rounding, color, coat);
        }
    }

    /**
     * Every splat of a stream this frame: its block's shape while the block
     * stands, then the spheres along its path; nothing while its square is
     * still on its way, the block standing as itself.
     *
     * @param stream the stream
     * @param coat   what it is drawn with
     * @return the splats, the block's first and then the path's from its far side on
     */
    public static List<Splat> of(DrinkTree.Stream stream, Coat coat) {
        List<Splat> splats = new ArrayList<>();
        if (stream.awaiting()) {
            return splats;
        }
        DrinkTree.Block block = stream.block();
        double progress = block.progressAt(stream.now());
        if (progress < 1) {
            DrinkBody.Box box = DrinkBody.boxAt(block.center(), progress);
            if (box.half() > 0) {
                splats.add(new Splat(box.center(), box.half(), box.rounding(), coat.blockColor(), coat));
            }
        }
        along(DrinkTree.rings(stream), coat, splats);
        return splats;
    }

    /**
     * Adds the spheres along a path's rings: each ring's own and, between
     * each pair, as many evenly spaced as keep every step under
     * {@link #SPACING} of the narrower ring's radius, each interpolated in
     * place, radius and route share; a sphere narrower than
     * {@link #NARROWEST}, where the stream tapers to nothing, is left out.
     *
     * @param rings  the path's rings
     * @param coat   what the stream is drawn with
     * @param splats added to
     */
    static void along(List<DrinkStream.Ring> rings, Coat coat, List<Splat> splats) {
        for (int index = 0; index < rings.size(); index++) {
            DrinkStream.Ring ring = rings.get(index);
            add(ring.center(), ring.radius(), ring.share(), coat, splats);
            if (index + 1 < rings.size()) {
                between(ring, rings.get(index + 1), coat, splats);
            }
        }
    }

    private static void between(DrinkStream.Ring from, DrinkStream.Ring to, Coat coat, List<Splat> splats) {
        double narrower = Math.max(NARROWEST, Math.min(from.radius(), to.radius()));
        if (Math.max(from.radius(), to.radius()) < NARROWEST) {
            return;
        }
        int steps = (int) Math.ceil(from.center().distanceTo(to.center()) / (SPACING * narrower));
        for (int step = 1; step < steps; step++) {
            double t = (double) step / steps;
            add(from.center().lerp(to.center(), t), lerp(from.radius(), to.radius(), t),
                    lerp(from.share(), to.share(), t), coat, splats);
        }
    }

    private static void add(Vec3 center, double radius, double share, Coat coat, List<Splat> splats) {
        if (radius >= NARROWEST) {
            splats.add(new Splat(center, radius, radius, DrinkColors.skinAt(coat.blockColor(), coat.gooColor(), share),
                    coat));
        }
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * t;
    }
}
