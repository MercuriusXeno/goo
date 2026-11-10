package com.mercuriusxeno.goo.ability.reap;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One of Reap's leaf crescent blades, the one shape the server reaps and
 * cuts with and the client draws. It forms ahead of the slasher's eye along
 * the look as a small skinny sliver to one side; its width, side to side,
 * and its depth, how far its middle bows away from the slasher, grow at
 * their own rates until it stands a full crescent centred on its offset and
 * lift around the aim, drifting on all the while, and then it fades. A point on
 * it is named by how far across it lies, -1 at one tip to 1 at the other,
 * and by its layer, 1 on the leading edge and {@link #INNER_SHARE} on the
 * trailing edge, the two meeting at the tips.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 *
 * @param origin    the slasher's eye, where the blade swings from
 * @param forward   its unit heading, the slasher's look turned within 22.5 degrees to either side
 * @param side      the unit axis its width spans, nearly flat
 * @param offset    how far its middle stands to the side of the aim once full grown, in blocks
 * @param lift      how far its middle stands above the aim, below it where negative, in blocks
 * @param startSide the side it starts on, 1 along the side axis and -1 against it
 * @param shape     its size and timing
 */
public record ReapBlade(Vec3 origin, Vec3 forward, Vec3 side, double offset, double lift, int startSide,
                        Shape shape) {

    /** The share of the leading edge's bow the trailing edge bows, so the two meet at the tips. */
    public static final double INNER_SHARE = 0.45;
    /** The farthest a blade stands to either side of the aim, in blocks. */
    static final double MAX_OFFSET = 1;
    /** How far below the aim a blade's middle stands, in blocks, so the whole blade swings under the eye line. */
    static final double DROP = 0.9;
    /** How far the drop varies either way, in blocks: a sliver, so the flurry fans sideways. */
    static final double DROP_SPREAD = 0.05;
    /** How far the blade's face tips up toward the eye from lying flat along the look, so its depth shows. */
    static final double FACE_TIP = Math.toRadians(15);
    /** The farthest a blade's heading turns from the look to either side, so the flurry fans out in a 45 degree cone. */
    static final double MAX_TURN = Math.toRadians(22.5);
    /** The farthest a blade tilts from flat, in degrees, so it swings like a scythe and its far side stays under the eye line. */
    static final double MAX_TILT_DEGREES = 5;
    /** The share of its full width and depth a blade starts at. */
    private static final double START_SHARE = 0.12;
    /** How far apart the samples across a blade lie, in blocks. */
    private static final double SAMPLE_STEP = 0.25;
    /** The layers sampled through the blade's body, trailing edge to leading. */
    private static final double[] SAMPLE_LAYERS = {INNER_SHARE, (INNER_SHARE + 1) / 2, 1};
    private static final double HALF = 0.5;
    /** A blade starting along its side axis. */
    private static final int ALONG_SIDE = 1;
    /** A blade starting against its side axis. */
    private static final int AGAINST_SIDE = -ALONG_SIDE;
    private static final double TIP_TO_TIP = 2;
    private static final Vec3 UP = new Vec3(0, 1, 0);
    private static final Vec3 EAST = new Vec3(1, 0, 0);
    /** Below this squared length a heading runs straight up or down and has no level side. */
    private static final double VERTICAL_HEADING = 1e-6;

    /** Network codec, so the client draws the blade the server swung. */
    public static final StreamCodec<ByteBuf, ReapBlade> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, ReapBlade::origin,
            Vec3.STREAM_CODEC, ReapBlade::forward,
            Vec3.STREAM_CODEC, ReapBlade::side,
            ByteBufCodecs.DOUBLE, ReapBlade::offset,
            ByteBufCodecs.DOUBLE, ReapBlade::lift,
            ByteBufCodecs.VAR_INT, ReapBlade::startSide,
            Shape.STREAM_CODEC, ReapBlade::shape,
            ReapBlade::new);

    /**
     * A blade's size and timing.
     *
     * @param start      how far ahead of the eye it forms, in blocks
     * @param drift      how far it drifts on over its life, in blocks
     * @param width      its full width, side to side, in blocks
     * @param depth      its full depth, how far its middle bows away from the slasher, in blocks
     * @param widthTicks the ticks its width takes to grow full
     * @param depthTicks the ticks its depth takes to grow full
     * @param fadeTicks  the ticks it fades once full grown
     */
    public record Shape(double start, double drift, double width, double depth, int widthTicks, int depthTicks,
                        int fadeTicks) {

        /** Network codec. */
        public static final StreamCodec<ByteBuf, Shape> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Shape::start,
                ByteBufCodecs.DOUBLE, Shape::drift,
                ByteBufCodecs.DOUBLE, Shape::width,
                ByteBufCodecs.DOUBLE, Shape::depth,
                ByteBufCodecs.VAR_INT, Shape::widthTicks,
                ByteBufCodecs.VAR_INT, Shape::depthTicks,
                ByteBufCodecs.VAR_INT, Shape::fadeTicks,
                Shape::new);

        /**
         * The ticks the blade takes to stand full grown, width and depth both.
         *
         * @return the growing ticks
         */
        public int growTicks() {
            return Math.max(widthTicks, depthTicks);
        }

        /**
         * The ticks the blade lives, growing and drifting.
         *
         * @return the life in ticks
         */
        public int lifeTicks() {
            return growTicks() + fadeTicks;
        }

        /**
         * How far the blade's middle flies in all.
         *
         * @return the reach in blocks
         */
        public double reach() {
            return start + drift;
        }
    }

    /**
     * A blade swung from the slasher's eye: its heading turned at random
     * within 22.5 degrees to either side of the look, keeping the look's
     * pitch, so the blades fan out rather than converge; its width tilted
     * at random within five degrees of flat, its middle standing at random
     * within a block of the aim to the side and just under it, so the whole
     * blade swings below the eye line, and starting on a random side.
     *
     * @param eye    the slasher's eye
     * @param look   the slasher's unit look
     * @param shape  its size and timing
     * @param random the random source
     * @return the blade
     */
    public static ReapBlade launch(Vec3 eye, Vec3 look, Shape shape, RandomSource random) {
        Vec3 heading = look.yRot((float) ((random.nextDouble() - HALF) * TIP_TO_TIP * MAX_TURN));
        double tilt = Math.toRadians((random.nextDouble() - HALF) * TIP_TO_TIP * MAX_TILT_DEGREES);
        double offset = (random.nextDouble() - HALF) * TIP_TO_TIP * MAX_OFFSET;
        double lift = -DROP + (random.nextDouble() - HALF) * TIP_TO_TIP * DROP_SPREAD;
        int startSide = random.nextBoolean() ? ALONG_SIDE : AGAINST_SIDE;
        return new ReapBlade(eye, heading, sideOf(heading, tilt), offset, lift, startSide, shape);
    }

    /**
     * The axis the blade's lift raises it along, square to its heading and
     * its width.
     *
     * @return the unit lift axis
     */
    public Vec3 liftAxis() {
        return side.cross(forward).normalize();
    }

    /**
     * The axis the blade's middle bows along: away from the slasher along the
     * look, tipped up toward the eye, so the eye sees the crescent's depth
     * rather than its edge.
     *
     * @return the unit depth axis
     */
    public Vec3 depthAxis() {
        return forward.scale(Math.cos(FACE_TIP)).add(liftAxis().scale(Math.sin(FACE_TIP)));
    }

    /**
     * The axis a blade's width spans: level across its heading, tilted about
     * the heading.
     *
     * @param forward the unit heading
     * @param tilt    the tilt, in radians
     * @return the unit side axis
     */
    private static Vec3 sideOf(Vec3 forward, double tilt) {
        Vec3 level = forward.cross(UP);
        Vec3 flat = level.lengthSqr() > VERTICAL_HEADING ? level.normalize() : EAST;
        Vec3 rise = flat.cross(forward);
        return flat.scale(Math.cos(tilt)).add(rise.scale(Math.sin(tilt)));
    }

    /**
     * The blade's width an age into its swing.
     *
     * @param age ticks since it swung
     * @return the width in blocks
     */
    public double widthAt(double age) {
        return shape.width() * grown(age, shape.widthTicks());
    }

    /**
     * The blade's depth an age into its swing.
     *
     * @param age ticks since it swung
     * @return the depth in blocks
     */
    public double depthAt(double age) {
        return shape.depth() * grown(age, shape.depthTicks());
    }

    private static double grown(double age, int ticks) {
        return START_SHARE + (1 - START_SHARE) * Math.clamp(age / ticks, 0, 1);
    }

    /**
     * How far ahead of the eye the blade's middle stands: formed at its start
     * from its first tick, so its growth plays out in view, and drifting on at
     * an even pace across its whole life.
     *
     * @param age ticks since it swung
     * @return the distance in blocks
     */
    private double distanceAt(double age) {
        return shape.start() + shape.drift() * Math.clamp(age / shape.lifeTicks(), 0, 1);
    }

    /**
     * The middle of the blade's span an age into its swing: its starting tip
     * holds at its start side while the width grows across, so the span
     * stands centred on its offset and lift once its width is full.
     *
     * @param age ticks since it swung
     * @return the point
     */
    public Vec3 centreAt(double age) {
        double lateral = offset + startSide * (shape.width() - widthAt(age)) * HALF;
        return origin.add(forward.scale(distanceAt(age))).add(side.scale(lateral)).add(liftAxis().scale(lift));
    }

    /**
     * A point on the blade an age into its swing.
     *
     * @param age    ticks since it swung
     * @param across how far across it lies, -1 at one tip to 1 at the other
     * @param layer  its layer, 1 on the leading edge and {@link #INNER_SHARE} on the trailing edge
     * @return the point
     */
    public Vec3 pointAt(double age, double across, double layer) {
        double bow = depthAt(age) * (1 - across * across) * layer;
        return centreAt(age).add(side.scale(across * widthAt(age) * HALF)).add(depthAxis().scale(bow));
    }

    /**
     * Points through the blade's body an age into its swing, across it and
     * between its edges, close enough together to land in every cell it covers.
     *
     * @param age ticks since it swung
     * @return the points
     */
    public List<Vec3> samplesAt(double age) {
        int steps = Math.max(1, (int) Math.ceil(widthAt(age) / SAMPLE_STEP));
        List<Vec3> samples = new ArrayList<>((steps + 1) * SAMPLE_LAYERS.length);
        for (int step = 0; step <= steps; step++) {
            double across = TIP_TO_TIP * step / steps - 1;
            for (double layer : SAMPLE_LAYERS) {
                samples.add(pointAt(age, across, layer));
            }
        }
        return samples;
    }

    /**
     * The cells the blade covers an age into its swing, each once.
     *
     * @param age ticks since it swung
     * @return the cells
     */
    public List<BlockPos> cellsAt(double age) {
        Set<BlockPos> cells = new LinkedHashSet<>();
        samplesAt(age).forEach(point -> cells.add(BlockPos.containing(point)));
        return List.copyOf(cells);
    }

    /**
     * How bright the blade is an age into its swing: whole as it grows,
     * fading as it drifts.
     *
     * @param age ticks since it swung
     * @return its strength, 0 to 1
     */
    public float strengthAt(double age) {
        return 1f - (float) Math.clamp((age - shape.growTicks()) / shape.fadeTicks(), 0, 1);
    }
}
