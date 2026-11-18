package com.mercuriusxeno.goo.client.tartarus;

import com.mercuriusxeno.goo.client.zone.ClientConjuredDoors;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Tartarus' doorway as it stands at a moment, ported from take A of round
 * two of the artifact draft the operator settled, "Texel by texel": a plain
 * doorway one block wide and two tall, the size of the vanilla portal's,
 * whose texels of nether portal appear scattered across its face until it
 * stands whole, nothing about it moving but the texture's own swirl. It holds,
 * moves into its caster the same size throughout, and on arrival backs off
 * and goes a texel at a time; a channel let go early puts its texels out.
 * A few purple motes drift in toward it slowly, as vanilla's portal gives off.
 * decision tartarus-draws-its-own-portal-with-the-nether-portal-texture
 *
 * @param open  how far it has appeared, 0 none to 1 whole
 * @param close how far it has gone, 0 whole to 1 none
 * @param time  the client game tick and the partial tick
 */
public record TexelDoorway(double open, double close, double time) {

    /** The texture's texels a block, and the doorway's texels across and up. */
    public static final int TEXELS = 16;
    public static final int ACROSS = TEXELS;
    public static final int UP = 2 * TEXELS;
    /** The conjured door grows over the charge less its enveloping ticks, 28 of Tartarus' 40; the doorway appears in 20. */
    static final double GROWING_TICKS = 28;
    static final double APPEARING_TICKS = 20;

    private static final double CLOSE_EASE = 2.2;
    private static final double ORDER_ACROSS = 31.7;
    private static final double ORDER_UP = 17.3;
    private static final double SHOWN_GROWS = 2;
    private static final double SHOWN_AT_LEAST = 0.01;
    private static final int MOTES = 14;
    private static final double MOTE_LIFE = 30;
    private static final double MOTE_LIFE_SPREAD = 20;
    private static final double MOTE_LIFE_SEED = 3.1;
    private static final double SIDE_SEED = 5.3;
    private static final double OUT_SEED = 7.7;
    private static final double UP_SEED = 1.3;
    private static final double OUT_NEAREST = 0.6;
    private static final double OUT_SPREAD = 0.5;
    private static final double UP_LOWEST = 0.2;
    private static final double UP_SPREAD = 1.7;
    private static final double DRIFTS_IN = 0.7;
    private static final double DRIFTS_TO_MIDDLE = 0.2;
    private static final double MIDDLE = 1;
    private static final double MOTE_BRIGHTEST = 0.9;
    private static final double HALF = 0.5;
    private static final double RIGHT = 1;
    private static final double LEFT = -RIGHT;
    private static final int[] MOTE_SHADES = {0x8228DC, 0xAA50F0, 0x6414BE};
    private static final double HASH_SCALE = 12.9898;
    private static final double HASH_SHIFT = 4.1414;
    private static final double HASH_SPREAD = 43_758.545_3;
    private static final int BYTE = 0xFF;
    private static final int ALPHA_SHIFT = 24;

    /**
     * One mote as it stands this moment.
     *
     * @param across where it stands across the doorway's plane from its middle, in blocks
     * @param up     where it stands up from its foot, in blocks
     * @param color  its ARGB color, its alpha rising and falling over its life
     */
    public record Mote(double across, double up, int color) {
    }

    /**
     * The doorway a drawn door stands as, its shares and phase read as the
     * draft's appear, hold, move in, back off, go and let-go.
     *
     * @param door the door as the frame draws it
     * @param time the client game tick and the frame's partial tick
     * @return the doorway, empty while none of it shows
     */
    public static Optional<TexelDoorway> of(ClientConjuredDoors.Drawn door, double time) {
        double unzipped = uncollapsed(door.unzipped(), door.collapsed());
        double widened = uncollapsed(door.widened(), door.collapsed());
        TexelDoorway doorway = switch (door.phase()) {
            case GROWING -> new TexelDoorway(clamp(unzipped * GROWING_TICKS / APPEARING_TICKS), door.collapsed(),
                    time);
            case ENVELOPING -> new TexelDoorway(1, door.collapsed(), time);
            case RELEASING -> new TexelDoorway(1, goneOf(unzipped, widened, door.collapsed()), time);
        };
        return doorway.shown() <= 0 ? Optional.empty() : Optional.of(doorway);
    }

    /**
     * Whether a texel of the doorway shows: each texel has its place in a
     * scattered order, showing once the doorway has appeared past it and until it has gone past it.
     *
     * @param across the texel across, 0 at the doorway's left to 15
     * @param up     the texel up, 0 at its foot to 31
     * @return true where it shows
     */
    public boolean shows(int across, int up) {
        double order = hash(across * ORDER_ACROSS + up * ORDER_UP);
        return order < easeOut(open) && order >= easeIn(close);
    }

    /**
     * Each mote this moment, a pure function of the time so every frame of
     * a moment shows the same ones, fading in as the doorway appears and out as it goes.
     *
     * @param each takes each mote
     */
    public void motes(Consumer<Mote> each) {
        double shown = clamp(open * SHOWN_GROWS) * (1 - easeIn(close));
        if (shown <= SHOWN_AT_LEAST) {
            return;
        }
        for (int index = 0; index < MOTES; index++) {
            double life = MOTE_LIFE + MOTE_LIFE_SPREAD * hash(index * MOTE_LIFE_SEED);
            double age = mod(time + hash(index) * life, life);
            double share = age / life;
            double side = hash(index * SIDE_SEED) > HALF ? RIGHT : LEFT;
            double out = side * (OUT_NEAREST + OUT_SPREAD * hash(index * OUT_SEED));
            double up = UP_LOWEST + UP_SPREAD * hash(index * UP_SEED);
            double alpha = Math.sin(Math.PI * share) * MOTE_BRIGHTEST * shown;
            each.accept(new Mote(out * (1 - DRIFTS_IN * share), up + (MIDDLE - up) * DRIFTS_TO_MIDDLE * share,
                    withAlpha(MOTE_SHADES[index % MOTE_SHADES.length], alpha)));
        }
    }

    private double shown() {
        return easeOut(open) - easeIn(close);
    }

    private static double uncollapsed(double share, double collapsed) {
        double left = 1 - collapsed;
        return left > 0 ? share / left : 0;
    }

    /**
     * How far an arrival's doorway has gone: none while it backs off, then as it zips shut.
     *
     * @param unzipped  the releasing door's unzipped share, before any collapse
     * @param widened   the releasing door's widened share, before any collapse
     * @param collapsed how far it has collapsed since its updates stopped
     * @return the share gone, 0 to 1
     */
    private static double goneOf(double unzipped, double widened, double collapsed) {
        return clamp(Math.max(collapsed, widened > 0 ? 0 : 1 - unzipped));
    }

    private static int withAlpha(int rgb, double alpha) {
        return ((int) Math.round(BYTE * clamp(alpha)) << ALPHA_SHIFT) | rgb;
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
}
