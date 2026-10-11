package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;
import org.joml.Vector3f;

/**
 * Urchin's spear, one per strike in flight: a chrome diamond spearhead on
 * three square rods that telescope out of the urchin toward the struck
 * mob. It arms over three ticks, the head poking out past the bristles;
 * fires over the next three, the rods sliding out of one another until
 * the head stands in the mob on the tick the damage lands; then retracts
 * over six. Each rod keeps its length the whole way, so the spear reads as
 * forged metal extending rather than goo stretching. The geometry is drawn
 * through Metal's chrome shader, the vertex color packing the band's place
 * in red and the vertex's place along the spear in green.
 * urchin-rests-bristled-and-spears
 */
final class UrchinSpear {

    /** Ticks the head takes to poke out past the bristles. */
    static final int ARM_TICKS = 3;
    /** How far from the urchin's base the armed head's tip stands, just past the bristles' reach. */
    static final float ARM_REACH = 1.2f;
    /** How far past the mob's body center the fired tip reaches. */
    static final float OVERSHOOT = 0.4f;
    /** The length of every rod, so three of them, overlapping, reach the trap's radius and past. */
    static final float ROD_LENGTH = 1.6f;
    /**
     * Ticks after the stab lands that the spear's retract shinks: a tick
     * into the retract, so the shink back follows the stab's shink rather
     * than sounding over it (decision urchin-spikes-shink-out-and-shink-back).
     */
    static final int RETRACT_SHINK_FRAME = 1;
    /** The spearhead's length, back point to tip. */
    static final float HEAD_LENGTH = 0.4f;
    /** Each rod's half width, base rod to head rod, each sliding out of the one before it. */
    private static final float[] ROD_HALF_WIDTHS = {0.06f, 0.045f, 0.032f};
    private static final float HEAD_HALF_WIDTH = 0.12f;
    private static final float HEAD_HALF_THICKNESS = 0.035f;
    /** Where the head is widest, as a share of its length back from the tip. */
    private static final float HEAD_SHOULDER = 0.45f;
    private static final int OPAQUE = 0xFF;
    /** A square rod's corners counted around it, as signs on perp and cross. */
    private static final float[][] SQUARE_CORNERS = {{1, 1}, {-1, 1}, {-1, -1}, {1, -1}};
    /** Below this squared distance the aim sits on the base and no spear points anywhere. */
    private static final float NO_AXIS = 1e-6f;
    /** The smoothstep's cubic coefficients, 3t^2 - 2t^3. */
    private static final float SMOOTH_SQUARE = 3f;
    private static final float SMOOTH_CUBE = 2f;

    private UrchinSpear() {
    }

    /**
     * How far the spear's tip stands from the urchin's base through a
     * strike: easing out to the armed reach, then punching out to the mob,
     * faster as it goes, and easing back in over the retract.
     *
     * @param age         the strike's age in ticks
     * @param partialTick the partial tick
     * @param strikeTick  the age the spear stands in the mob and its damage lands
     * @param length      the age the strike ends, fully retracted
     * @param reach       the tip's distance from the base at full extension
     * @return the tip's distance in blocks, zero before the strike and after it
     */
    static float tipDistance(int age, float partialTick, int strikeTick, int length, float reach) {
        float t = age + partialTick;
        float armed = Math.min(ARM_REACH, reach);
        if (t <= 0f || t >= length) {
            return 0f;
        }
        if (t < ARM_TICKS) {
            return armed * BurnoutGeometry.easeOutCubic(t / ARM_TICKS);
        }
        if (t < strikeTick) {
            float fired = (t - ARM_TICKS) / (strikeTick - ARM_TICKS);
            return armed + (reach - armed) * fired * fired;
        }
        return reach * bristleShare(age, partialTick, strikeTick, length);
    }

    /**
     * How far out the urchin's spines stand through a strike, as a share of
     * the landing flare's: easing out over the arm, held through the fire,
     * easing back in over the retract, so the spines bristle with each shot
     * and the urchin rests smooth between them (operator ruling 2026-10-11).
     *
     * @param age         the strike's age in ticks
     * @param partialTick the partial tick
     * @param strikeTick  the age the spear stands in the mob
     * @param length      the age the strike ends, fully retracted
     * @return the share in [0, 1], zero before the strike and after it
     */
    static float bristleShare(int age, float partialTick, int strikeTick, int length) {
        float t = age + partialTick;
        if (t <= 0f || t >= length) {
            return 0f;
        }
        if (t < ARM_TICKS) {
            return BurnoutGeometry.easeOutCubic(t / ARM_TICKS);
        }
        if (t < strikeTick) {
            return 1f;
        }
        float retracted = (t - strikeTick) / (length - strikeTick);
        return 1f - retracted * retracted * (SMOOTH_SQUARE - SMOOTH_CUBE * retracted);
    }

    /**
     * Where each rod's far end stands along the spear for a shaft of the
     * given length: the head rod's at the shaft's end, and each rod behind
     * it only as far out as the one in front needs to stay sleeved in it.
     * A rod spans its end less {@link #ROD_LENGTH}, clipped at the base,
     * where the rest of it sits inside the urchin.
     *
     * @param shaft the distance from the base to the head's back point
     * @return the far end of each rod, base rod first
     */
    static float[] rodEnds(float shaft) {
        int rods = ROD_HALF_WIDTHS.length;
        float[] ends = new float[rods];
        for (int rod = 0; rod < rods; rod++) {
            int ahead = rods - 1 - rod;
            ends[rod] = Math.max(shaft - ahead * ROD_LENGTH, Math.min(shaft, ROD_LENGTH));
        }
        return ends;
    }

    /**
     * Emits one spear from the base toward the aimed point.
     *
     * @param pose  the pose entry, block-local
     * @param c     the chrome vertex consumer
     * @param base  the urchin's base, block-local
     * @param aim   the mob's body center, block-local
     * @param tip   the tip's distance from the base, {@link #tipDistance}
     * @param sweep the chrome band's place, a byte
     */
    static void emit(PoseStack.Pose pose, VertexConsumer c, Vector3f base, Vector3f aim, float tip, int sweep) {
        Vector3f dir = new Vector3f(aim).sub(base);
        if (tip <= 0f || dir.lengthSquared() < NO_AXIS) {
            return;
        }
        dir.normalize();
        float[] basis = ConeGeometry.computeBasis(dir.x(), dir.y(), dir.z());
        Vector3f perp = new Vector3f(basis[ConeGeometry.PERP_X], basis[ConeGeometry.PERP_Y], basis[ConeGeometry.PERP_Z]);
        Vector3f cross = new Vector3f(basis[ConeGeometry.CROSS_X], basis[ConeGeometry.CROSS_Y],
                basis[ConeGeometry.CROSS_Z]);
        Frame frame = new Frame(new FlatQuadContext(pose, c), base, dir, perp, cross, tip, sweep);
        float shaft = tip - HEAD_LENGTH;
        if (shaft > 0f) {
            float[] ends = rodEnds(shaft);
            for (int rod = 0; rod < ends.length; rod++) {
                frame.rod(Math.max(0f, ends[rod] - ROD_LENGTH), ends[rod], ROD_HALF_WIDTHS[rod]);
            }
        }
        frame.head(Math.max(0f, shaft));
    }

    /**
     * One spear's placement: its base, axis and cross-section basis, the
     * tip's distance and the band's place.
     *
     * @param quads the quad emitter
     * @param base  the urchin's base, block-local
     * @param dir   the spear's unit axis
     * @param perp  the first unit axis across the spear
     * @param cross the second unit axis across the spear
     * @param tip   the tip's distance from the base
     * @param sweep the chrome band's place, a byte
     */
    private record Frame(FlatQuadContext quads, Vector3f base, Vector3f dir, Vector3f perp, Vector3f cross,
                         float tip, int sweep) {

        /**
         * A point along the spear, offset across it on the basis.
         *
         * @param along       the distance from the base along the axis
         * @param acrossPerp  the offset along perp
         * @param acrossCross the offset along cross
         * @return the point, block-local
         */
        Vector3f at(float along, float acrossPerp, float acrossCross) {
            return new Vector3f(base).add(new Vector3f(dir).mul(along)).add(new Vector3f(perp).mul(acrossPerp))
                    .add(new Vector3f(cross).mul(acrossCross));
        }

        /**
         * The packed chrome color of a vertex: the band's place in red,
         * its share of the way to the tip in green.
         *
         * @param along the vertex's distance from the base
         * @return the packed color
         */
        int color(float along) {
            return ARGB.color(OPAQUE, sweep, Math.round(Math.clamp(along / tip, 0f, 1f) * OPAQUE), 0);
        }

        /**
         * Emits one square rod, its four sides each lit by its outward normal.
         *
         * @param start the rod's near end, from the base
         * @param end   the rod's far end, from the base
         * @param half  the rod's half width
         */
        void rod(float start, float end, float half) {
            if (end <= start) {
                return;
            }
            for (int side = 0; side < SQUARE_CORNERS.length; side++) {
                float[] a = SQUARE_CORNERS[side];
                float[] b = SQUARE_CORNERS[(side + 1) % SQUARE_CORNERS.length];
                Vector3f normal = new Vector3f(perp).mul(a[0] + b[0]).add(new Vector3f(cross).mul(a[1] + b[1]))
                        .normalize();
                vertex(at(start, a[0] * half, a[1] * half), color(start), normal);
                vertex(at(start, b[0] * half, b[1] * half), color(start), normal);
                vertex(at(end, b[0] * half, b[1] * half), color(end), normal);
                vertex(at(end, a[0] * half, a[1] * half), color(end), normal);
            }
        }

        /**
         * Emits the flat diamond spearhead: four faces from its shoulder to
         * the tip and four back to the rear point.
         *
         * @param back the rear point's distance from the base
         */
        void head(float back) {
            float shoulder = tip - HEAD_LENGTH * HEAD_SHOULDER;
            Vector3f rear = at(back, 0, 0);
            Vector3f point = at(tip, 0, 0);
            Vector3f[] ring = {at(shoulder, HEAD_HALF_WIDTH, 0), at(shoulder, 0, HEAD_HALF_THICKNESS),
                    at(shoulder, -HEAD_HALF_WIDTH, 0), at(shoulder, 0, -HEAD_HALF_THICKNESS)};
            for (int side = 0; side < ring.length; side++) {
                Vector3f a = ring[side];
                Vector3f b = ring[(side + 1) % ring.length];
                triangle(new Facet(a, b, point), color(shoulder), color(tip));
                triangle(new Facet(b, a, rear), color(shoulder), color(back));
            }
        }

        /**
         * Emits a facet as a quad with its apex doubled, lit by its face normal.
         *
         * @param facet     the facet's corners
         * @param edgeColor the color at its two edge corners
         * @param apexColor the color at its apex
         */
        private void triangle(Facet facet, int edgeColor, int apexColor) {
            Vector3f normal = new Vector3f(facet.end()).sub(facet.start())
                    .cross(new Vector3f(facet.apex()).sub(facet.start())).normalize();
            ConeGeometry.emitTriangle(corner -> {
                switch (corner) {
                    case ConeGeometry.BASE_START -> vertex(facet.start(), edgeColor, normal);
                    case ConeGeometry.BASE_END -> vertex(facet.end(), edgeColor, normal);
                    default -> vertex(facet.apex(), apexColor, normal);
                }
            });
        }

        private void vertex(Vector3f p, int color, Vector3f normal) {
            quads.vertex(p.x(), p.y(), p.z(), color, normal.x(), normal.y(), normal.z());
        }
    }

    /**
     * One triangular face of the spearhead.
     *
     * @param start the first edge corner
     * @param end   the second edge corner
     * @param apex  the corner opposite the edge
     */
    private record Facet(Vector3f start, Vector3f end, Vector3f apex) {
    }
}
