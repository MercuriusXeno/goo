package com.mercuriusxeno.goo.client.tartarus;

import com.mercuriusxeno.goo.client.zone.ClientConjuredDoors;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * The rift Tartarus' gate stands as at a moment, ported from take A of the
 * artifact draft the operator settled, formula for formula: a slit at the
 * feet climbs into a pointed tongue a little over two blocks tall, widening
 * as it rises, its tip licking and its edge flickering, rimmed in flat hot
 * orange; it holds, then swells as it lunges into its caster; on arrival it
 * backs off at full size and sinks into the floor; a channel let go early
 * drops it back into the floor. Embers rise off it, each one texel square.
 * The draft draws all of it cell by cell, 64 cells a block, which the
 * renderer keeps.
 * decision tartarus-draws-its-own-portal-with-the-nether-portal-texture
 *
 * @param open  how far it has opened, 0 shut to 1 full
 * @param close how far it has sunk back into the floor, 0 open to 1 gone
 * @param swell how far it has swollen lunging into its caster, 0 to 1
 * @param time  the client game tick and the partial tick
 */
public record RiftShape(double open, double close, double swell, double time) {

    /** The draft's cells a block: four to each of the texture's sixteen texels. */
    public static final int CELLS = 64;
    /** The draft's texels a block, the ember's size. */
    public static final int TEXELS = 16;
    /** The tallest a rift stands, swollen with its tip licking up, and the widest either side. */
    public static final double TALLEST = 2.15 * 1.15 + 0.08;
    public static final double WIDEST = 0.5 * 1.15 + 0.06;
    /** The conjured door grows over the charge less its enveloping ticks, 14 of Tartarus' 20; the rift opens in 11. */
    static final double GROWING_TICKS = 14;
    static final double OPENING_TICKS = 11;

    private static final double FULL_TALL = 2.15;
    private static final double FULL_HALF = 0.5;
    private static final double SWELL = 0.15;
    private static final double SLIT = 0.2;
    private static final double WIDENS = 1.1;
    private static final double SINK_NARROWS = 0.55;
    private static final double SHUT = 0.01;
    private static final double JAG = 0.06;
    private static final double JAG_ROWS = 7;
    private static final double JAG_SPEED = 0.35;
    private static final double FLICKER_GROWS = 3;
    private static final double LICK = 0.08;
    private static final double LICK_SPEED = 0.5;
    private static final double LICK_ACROSS = 3;
    private static final double LICK_GROWS = 2;
    private static final double RIM = 0.045;
    private static final double CLOSE_EASE = 2.2;
    private static final double HALF = 0.5;
    private static final double SIGNED_SPREAD = 2;
    private static final double SMOOTH_CUBIC = 3;
    private static final double SMOOTH_SQUARE = 2;
    private static final int RIM_RED = 255;
    private static final int RIM_GREEN = 150;
    private static final int RIM_GREEN_SPREAD = 60;
    private static final int RIM_BLUE = 50;
    private static final int RIM_ALPHA = 235;
    private static final double RIM_ROWS = 9;
    private static final int EMBERS = 60;
    private static final double EMBER_LIFE = 10;
    private static final double EMBER_LIFE_SPREAD = 8;
    private static final double EMBER_SPREAD = 0.9;
    private static final double EMBER_LOWEST = 0.1;
    private static final double EMBER_START_RISE = 1.6;
    private static final double EMBER_SWAY = 0.1;
    private static final double EMBER_SWAY_SPEED = 0.6;
    private static final double EMBER_RISES = 0.07;
    private static final double EMBERS_START = 0.05;
    private static final double EMBERS_STOP = 0.95;
    private static final int EMBER_ORANGE = 0xFF_FF963C;
    private static final int EMBER_PURPLE = 0xFF_BE5AFF;
    private static final double LIFE_SEED_SCALE = 3.1;
    private static final double X_SEED = 7.7;
    private static final double Y_SEED = 1.3;
    private static final int NOISE_ROW = 57;
    private static final double HASH_SCALE = 12.9898;
    private static final double HASH_SHIFT = 4.1414;
    private static final double HASH_SPREAD = 43_758.545_3;
    private static final int BYTE = 0xFF;
    private static final int RGB = 0x00_FFFFFF;
    private static final int ALPHA_SHIFT = 24;
    private static final int RED_SHIFT = 16;
    private static final int GREEN_SHIFT = 8;

    /** What one cell of the gate's plane shows. */
    public enum Fill {
        /** Nothing: the cell lies outside the rift. */
        NONE,
        /** The nether portal's texture. */
        MEMBRANE,
        /** The flat hot rim. */
        RIM
    }

    /**
     * One ember as it stands this moment.
     *
     * @param across where it stands across the gate's plane, in blocks
     * @param up     where it stands up the gate's plane, in blocks
     * @param color  its ARGB color, its alpha fading over its life
     */
    public record Ember(double across, double up, int color) {
    }

    /**
     * The rift a drawn door stands as, its shares and phase read as the
     * draft's open, hold, lunge, back off, sink and let-go.
     *
     * @param door the door as the frame draws it
     * @param time the client game tick and the frame's partial tick
     * @return the rift, empty while it stands shut
     */
    public static Optional<RiftShape> of(ClientConjuredDoors.Drawn door, double time) {
        double unzipped = uncollapsed(door.unzipped(), door.collapsed());
        double widened = uncollapsed(door.widened(), door.collapsed());
        RiftShape rift = switch (door.phase()) {
            case GROWING -> new RiftShape(clamp(unzipped * GROWING_TICKS / OPENING_TICKS), door.collapsed(), 0, time);
            case ENVELOPING -> new RiftShape(1, door.collapsed(), widened, time);
            case RELEASING -> new RiftShape(1, sinkingOf(unzipped, widened, door.collapsed()), 0, time);
        };
        return rift.top() <= SHUT ? Optional.empty() : Optional.of(rift);
    }

    /**
     * A share as it stood before the door began collapsing.
     *
     * @param share     the share as collapsing has shrunk it
     * @param collapsed how far the door has collapsed
     * @return the share before collapsing, 0 once it is gone
     */
    private static double uncollapsed(double share, double collapsed) {
        double left = 1 - collapsed;
        return left > 0 ? share / left : 0;
    }

    /**
     * How far an arrival's rift has sunk: none while it backs off, then as it zips shut.
     *
     * @param unzipped  the releasing door's unzipped share, before any collapse
     * @param widened   the releasing door's widened share, before any collapse
     * @param collapsed how far it has collapsed since its updates stopped
     * @return the share sunk, 0 to 1
     */
    private static double sinkingOf(double unzipped, double widened, double collapsed) {
        return clamp(Math.max(collapsed, widened > 0 ? 0 : 1 - unzipped));
    }

    private double top() {
        return FULL_TALL * easeOut(open) * (1 - easeIn(close));
    }

    /**
     * What one cell shows: the draft's rift function at the cell's middle.
     *
     * @param across the cell's middle across the gate's plane, in blocks
     * @param up     the cell's middle up the gate's plane, in blocks
     * @return the fill
     */
    public Fill fillAt(double across, double up) {
        if (up < 0) {
            return Fill.NONE;
        }
        double base = FULL_HALF * Math.min(1, SLIT + open * WIDENS) * (1 - SINK_NARROWS * close) * (1 + SWELL * swell);
        double jag = JAG * (noise2(up * JAG_ROWS, time * JAG_SPEED) - HALF) * SIGNED_SPREAD
                * Math.min(1, open * FLICKER_GROWS);
        double lick = LICK * Math.sin(time * LICK_SPEED + across * LICK_ACROSS) * Math.min(1, open * LICK_GROWS);
        double height = top() * (1 + SWELL * swell) + lick;
        if (up > height) {
            return Fill.NONE;
        }
        double edge = base * Math.sqrt(Math.max(0, 1 - up / height)) + jag - Math.abs(across);
        if (edge < 0) {
            return Fill.NONE;
        }
        return edge < RIM ? Fill.RIM : Fill.MEMBRANE;
    }

    /**
     * The rim's color along a row, its green flickering with height and time.
     *
     * @param up the row's middle, in blocks
     * @return the ARGB color
     */
    public int rimColorAt(double up) {
        int green = (int) (RIM_GREEN + RIM_GREEN_SPREAD * noise1(up * RIM_ROWS + time));
        return argb(RIM_ALPHA, RIM_RED, green, RIM_BLUE);
    }

    /**
     * Each ember this moment, a pure function of the time so every frame
     * of a moment shows the same ones; none while the rift barely stands.
     *
     * @param each takes each ember
     */
    public void embers(Consumer<Ember> each) {
        if (open <= EMBERS_START || close >= EMBERS_STOP) {
            return;
        }
        for (int index = 0; index < EMBERS; index++) {
            double life = EMBER_LIFE + EMBER_LIFE_SPREAD * hash(index * LIFE_SEED_SCALE);
            double age = mod(time + hash(index) * life, life);
            double fade = 1 - age / life;
            double across = (hash(index * X_SEED) - HALF) * EMBER_SPREAD * open
                    + EMBER_SWAY * Math.sin(age * EMBER_SWAY_SPEED + index);
            double up = EMBER_LOWEST + hash(index * Y_SEED) * EMBER_START_RISE * open + age * EMBER_RISES;
            int color = hash(index) > HALF ? EMBER_ORANGE : EMBER_PURPLE;
            each.accept(new Ember(across, up, withAlpha(color, fade)));
        }
    }

    private static int withAlpha(int color, double alpha) {
        return ((int) Math.round(BYTE * clamp(alpha)) << ALPHA_SHIFT) | (color & RGB);
    }

    private static int argb(int alpha, int red, int green, int blue) {
        return (alpha << ALPHA_SHIFT) | (red << RED_SHIFT) | (green << GREEN_SHIFT) | blue;
    }

    private static double clamp(double share) {
        return Math.clamp(share, 0, 1);
    }

    private static double easeOut(double share) {
        double left = 1 - clamp(share);
        return 1 - left * left * left;
    }

    private static double easeIn(double share) {
        return Math.pow(clamp(share), CLOSE_EASE);
    }

    private static double mod(double value, double span) {
        return ((value % span) + span) % span;
    }

    /** The draft's hash: the fraction of a scaled sine. */
    private static double hash(double n) {
        double wave = Math.sin(n * HASH_SCALE + HASH_SHIFT) * HASH_SPREAD;
        return wave - Math.floor(wave);
    }

    private static double smooth(double f) {
        return f * f * (SMOOTH_CUBIC - SMOOTH_SQUARE * f);
    }

    /** The draft's value noise along one coordinate. */
    private static double noise1(double x) {
        double i = Math.floor(x);
        double u = smooth(x - i);
        return hash(i) * (1 - u) + hash(i + 1) * u;
    }

    /** The draft's value noise over two coordinates. */
    private static double noise2(double x, double y) {
        double i = Math.floor(x);
        double j = Math.floor(y);
        double ux = smooth(x - i);
        double uy = smooth(y - j);
        double below = hash(i + j * NOISE_ROW) * (1 - ux) + hash(i + 1 + j * NOISE_ROW) * ux;
        double above = hash(i + (j + 1) * NOISE_ROW) * (1 - ux) + hash(i + 1 + (j + 1) * NOISE_ROW) * ux;
        return below * (1 - uy) + above * uy;
    }
}
