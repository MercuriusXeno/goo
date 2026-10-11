package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.program.ColonizeStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.Variables;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import java.util.List;
import java.util.SplittableRandom;

/**
 * A held ghost drawn as a noisy cloud of motes about the throw's reach, each
 * mote its own: a random place over the whole sphere, its own depth in a
 * fuzzy outer shell, its own slow wander and wobble, its own size and
 * twinkle, with rings born at the aim point travelling outward to the same
 * reach. Shroom's cloud is mauve spores about Spore's reach; Jelly's is amber
 * crumbs about the reach Feed's laid feed draws mobs from.
 * held-visual-ghosts-the-landing-in-two-passes
 * colonize-blob-grows-the-network
 * feed-blob-feeds-and-draws-mobs
 */
public final class MoteCloudGhost implements HeldGhostVisual {

    /** Shroom's mauve spore cloud. */
    public static final MoteCloudGhost SHROOM = new MoteCloudGhost(GooTypes.SHROOM, 0xB57FC0);
    /** Jelly's amber crumb cloud. */
    public static final MoteCloudGhost JELLY = new MoteCloudGhost(GooTypes.JELLY, 0xF0A040);

    /** Motes in the cloud. */
    static final int MOTES = 600;
    /** The shallowest a mote sits, as a share of the reach. */
    static final double INNER_SHARE = 0.80;
    /** The deepest a mote sits, as a share of the reach. */
    static final double OUTER_SHARE = 1.05;
    /** How far a mote wobbles in and out, as a share of the reach. */
    static final double WOBBLE_SHARE = 0.04;
    /** The dimmest a mote twinkles to, as a share of its alpha. */
    static final float DIMMEST = 0.35f;
    /** The slowest and the span of a mote's wander about its own axis, radians per second. */
    private static final double MIN_WANDER = 0.04;
    private static final double WANDER_SPAN = 0.22;
    /** The slowest and the span of a mote's wobble, radians per second. */
    private static final double MIN_WOBBLE = 0.8;
    private static final double WOBBLE_SPAN = 1.6;
    /** The smallest and the span of a mote's size, as a share of the mote's half width. */
    private static final double MIN_SIZE = 0.6;
    private static final double SIZE_SPAN = 0.8;
    /** The slowest and the span of a mote's twinkle, radians per second. */
    private static final double MIN_TWINKLE = 1.5;
    private static final double TWINKLE_SPAN = 3.5;
    /** The seed every client scatters the cloud with, so it holds its shape. */
    private static final long SCATTER_SEED = 0x5B0BEL;
    /** A mote's half width, in blocks. */
    private static final float MOTE_HALF = 0.035f;
    /** A mote's alpha at full held opacity. */
    private static final float MOTE_ALPHA = 0.75f;
    private static final double FULL_TURN = Math.PI * 2;
    /** A unit sphere spans two from its top to its bottom. */
    private static final double TOP_TO_BOTTOM = 2;
    private static final double HALF = 0.5;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;
    private static final int AXES = 3;
    private static final int OPAQUE = 255;

    /** Each mote's own traits, fixed at load. */
    private static final Mote[] CLOUD = scatter(new SplittableRandom(SCATTER_SEED));

    /**
     * One mote's own traits.
     *
     * @param heading      its place over the sphere at rest, unit length
     * @param axis         the axis it wanders about, unit length
     * @param wanderSpeed  how fast it wanders, radians per second
     * @param depth        its share of the reach at rest
     * @param wobbleSpeed  how fast it wobbles in and out, radians per second
     * @param wobblePhase  where its wobble starts, in radians
     * @param size         its size as a share of the mote's half width
     * @param twinkleSpeed how fast it twinkles, radians per second
     * @param twinklePhase where its twinkle starts, in radians
     */
    private record Mote(double[] heading, double[] axis, double wanderSpeed, double depth, double wobbleSpeed,
                        double wobblePhase, double size, double twinkleSpeed, double twinklePhase) {
    }

    private final ResourceKey<GooTypeDefinition> gooType;
    private final int moteRgb;

    private MoteCloudGhost(ResourceKey<GooTypeDefinition> gooType, int moteRgb) {
        this.gooType = gooType;
        this.moteRgb = moteRgb;
    }

    private static Mote[] scatter(SplittableRandom random) {
        Mote[] cloud = new Mote[MOTES];
        for (int i = 0; i < MOTES; i++) {
            cloud[i] = new Mote(randomUnit(random), randomUnit(random),
                    MIN_WANDER + random.nextDouble() * WANDER_SPAN,
                    INNER_SHARE + random.nextDouble() * (OUTER_SHARE - INNER_SHARE),
                    MIN_WOBBLE + random.nextDouble() * WOBBLE_SPAN, random.nextDouble() * FULL_TURN,
                    MIN_SIZE + random.nextDouble() * SIZE_SPAN,
                    MIN_TWINKLE + random.nextDouble() * TWINKLE_SPAN, random.nextDouble() * FULL_TURN);
        }
        return cloud;
    }

    private static double[] randomUnit(SplittableRandom random) {
        double y = random.nextDouble() * TOP_TO_BOTTOM - 1;
        double ring = Math.sqrt(Math.max(0, 1 - y * y));
        double around = random.nextDouble() * FULL_TURN;
        return new double[] {Math.cos(around) * ring, y, Math.sin(around) * ring};
    }

    @Override
    public ResourceKey<GooTypeDefinition> gooType() {
        return gooType;
    }

    /**
     * The dome and the outward rings at the colonize step's radius, falling
     * back to the area's size, the reach the program's own steps give, where
     * the program holds none.
     *
     * @param area      the ability's synced area
     * @param behaviors the ability's synced program
     * @return the ghost
     */
    @Override
    public HeldGhost ghost(AbilityArea area, List<Step> behaviors) {
        float reach = SyncedSteps.first(behaviors, ColonizeStep.class).map(ColonizeStep::radius)
                .filter(radius -> radius.variables().isEmpty()).map(radius -> radius.evaluateFloat(Variables.NONE))
                .filter(radius -> radius > 0).orElse((float) area.size());
        return HeldGhost.outwardTo(reach);
    }

    @Override
    public List<HeldLayer> heldLayers() {
        return List.of(new HeldLayer(GooRenderTypes.SPORE_SHELL_TYPE, GooRenderTypes.SPORE_SHELL_THROUGH_BLOCKS_TYPE,
                this::emitMotes));
    }

    /**
     * Where a mote sits about the cloud's center at unit reach at a moment: its
     * heading turned about its own axis by its wander, at its depth swayed by its wobble.
     *
     * @param index   the mote's index
     * @param seconds seconds on the real-time clock
     * @return the mote's offset: x, y and z
     */
    static double[] moteAt(int index, double seconds) {
        Mote mote = CLOUD[index];
        double[] turned = rotated(mote.heading(), mote.axis(), seconds * mote.wanderSpeed());
        double reach = mote.depth() + WOBBLE_SHARE * Math.sin(seconds * mote.wobbleSpeed() + mote.wobblePhase());
        return new double[] {turned[X] * reach, turned[Y] * reach, turned[Z] * reach};
    }

    /**
     * A mote's alpha share at a moment: its own twinkle between dim and full.
     *
     * @param index   the mote's index
     * @param seconds seconds on the real-time clock
     * @return the share of its alpha, from the dimmest to one
     */
    static float twinkleAt(int index, double seconds) {
        Mote mote = CLOUD[index];
        float wave = (float) (Math.sin(seconds * mote.twinkleSpeed() + mote.twinklePhase()) * HALF + HALF);
        return DIMMEST + (1f - DIMMEST) * wave;
    }

    /**
     * Turns a unit vector about a unit axis, by Rodrigues' formula.
     *
     * @param v     the vector
     * @param k     the axis
     * @param angle the turn in radians
     * @return the turned vector
     */
    private static double[] rotated(double[] v, double[] k, double angle) {
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double dot = k[X] * v[X] + k[Y] * v[Y] + k[Z] * v[Z];
        double[] cross = {k[Y] * v[Z] - k[Z] * v[Y], k[Z] * v[X] - k[X] * v[Z], k[X] * v[Y] - k[Y] * v[X]};
        double[] out = new double[AXES];
        for (int a = X; a <= Z; a++) {
            out[a] = v[a] * cos + cross[a] * sin + k[a] * dot * (1 - cos);
        }
        return out;
    }

    private void emitMotes(PoseStack.Pose pose, VertexConsumer c, HeldGhost ghost, Direction face,
                           float opacity, double nowSeconds) {
        float radius = ghost.domeRadius();
        for (int i = 0; i < MOTES; i++) {
            double[] at = moteAt(i, nowSeconds);
            float alpha = MOTE_ALPHA * opacity * twinkleAt(i, nowSeconds);
            int color = ARGB.color(Math.round(Mth.clamp(alpha, 0f, 1f) * OPAQUE), moteRgb);
            SporeMotes.emit(pose, c, (float) (at[X] * radius), (float) (at[Y] * radius), (float) (at[Z] * radius),
                    (float) (MOTE_HALF * CLOUD[i].size()), color);
        }
    }
}
