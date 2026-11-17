package com.mercuriusxeno.goo.client.tartarus;

import com.mercuriusxeno.goo.ability.zone.ConjuredDoor;
import com.mercuriusxeno.goo.client.zone.ClientConjuredDoors;
import net.minecraft.util.Mth;
import java.util.Optional;

/**
 * The rift Tartarus' gate stands as at a moment, in the gate's own plane,
 * as the operator settled it on take A of the artifact draft: a slit at the
 * feet climbs into a pointed tongue a little over two blocks tall, widening
 * as it rises, its edge flickering; it holds there, then lunges into its
 * caster, swelling as it comes; on arrival it backs off at full size and
 * sinks into the floor. A channel let go early drops it back into the floor.
 * decision tartarus-draws-its-own-portal-with-the-nether-portal-texture
 *
 * @param height how tall it stands this moment, in blocks
 * @param base   half its width at the feet, in blocks
 * @param open   how far it has opened, 0 shut to 1 full, which the flicker grows with
 * @param time   the client game tick and the partial tick, which the flicker moves by
 */
public record RiftShape(double height, double base, double open, double time) {

    /** How tall a full rift stands, and half its width at the feet. */
    static final double FULL_TALL = 2.15;
    static final double FULL_HALF = 0.5;
    /** How much more a rift taking its caster swells. */
    static final double SWELL = 0.15;
    /** How far its tip licks up and down, and how far its edge flickers in and out. */
    static final double LICK = 0.08;
    static final double JAG = 0.06;
    /** The width of the hot rim along its edge. */
    static final double RIM = 0.045;
    /** The conjured door grows over the charge less its enveloping ticks, 14 of Tartarus' 20; the rift opens in 11. */
    static final double GROWING_TICKS = 14;
    static final double OPENING_TICKS = 11;
    /** How narrow its feet start, as a share of its full width, and how fast they reach it. */
    private static final double SLIT = 0.2;
    private static final double WIDENS = 1.1;
    /** How much narrower its feet draw as it sinks. */
    private static final double SINK_NARROWS = 0.55;
    /** Below this height nothing stands. */
    private static final double SHUT = 0.01;
    private static final double LICK_SPEED = 0.5;
    private static final double JAG_ROWS = 7;
    private static final double JAG_SPEED = 0.35;
    private static final double CLOSE_EASE = 2.2;
    private static final double FLICKER_GROWS = 3;
    private static final double LICK_GROWS = 2;
    /** A noise of 0 to 1 spread to -1 to 1. */
    private static final double SIGNED_SPREAD = 2;
    /** Mixes a noise lattice point's row into its column before hashing. */
    private static final int ROW_MIX = 73_856_093;
    /** The span of an unsigned int, which a hash divides by to read 0 to 1. */
    private static final double INT_SPAN = 0x1p32;

    /**
     * The rift a drawn door stands as.
     *
     * @param door the door as the frame draws it
     * @param time the client game tick and the frame's partial tick
     * @return the rift, empty while it stands shut
     */
    public static Optional<RiftShape> of(ClientConjuredDoors.Drawn door, double time) {
        double open = 1;
        double close = 0;
        double swell = 0;
        if (door.phase() == ConjuredDoor.Phase.GROWING) {
            open = clamp(door.unzipped() * GROWING_TICKS / OPENING_TICKS);
        } else if (door.phase() == ConjuredDoor.Phase.ENVELOPING) {
            swell = door.widened();
        } else if (door.widened() <= 0) {
            close = 1 - door.unzipped();
        }
        double swollen = 1 + SWELL * swell;
        double height = FULL_TALL * easeOut(open) * (1 - Math.pow(clamp(close), CLOSE_EASE)) * swollen
                + LICK * Math.sin(time * LICK_SPEED) * Math.min(1, open * LICK_GROWS);
        if (height <= SHUT) {
            return Optional.empty();
        }
        double base = FULL_HALF * Math.min(1, SLIT + open * WIDENS) * (1 - SINK_NARROWS * close) * swollen;
        return Optional.of(new RiftShape(height, base, open, time));
    }

    /**
     * Half the rift's width at a height: its feet's, narrowing to a point at
     * its tip, the edge flickering in and out.
     *
     * @param up the height above its feet, in blocks
     * @return the half width, 0 at and past its tip
     */
    public double halfWidthAt(double up) {
        if (up >= height) {
            return 0;
        }
        double flicker = JAG * (SIGNED_SPREAD * noise(up * JAG_ROWS, time * JAG_SPEED) - 1)
                * Math.min(1, open * FLICKER_GROWS);
        return Math.max(0, base * Math.sqrt(Math.max(0, 1 - up / height)) + flicker);
    }

    private static double clamp(double share) {
        return Math.clamp(share, 0, 1);
    }

    private static double easeOut(double share) {
        double left = 1 - clamp(share);
        return 1 - left * left * left;
    }

    /** Smooth value noise over two coordinates, 0 to 1, the same everywhere for the same inputs. */
    private static double noise(double x, double y) {
        int i = (int) Math.floor(x);
        int j = (int) Math.floor(y);
        double fx = Mth.smoothstep(x - i);
        double fy = Mth.smoothstep(y - j);
        double below = lerp(hash(i, j), hash(i + 1, j), fx);
        double above = lerp(hash(i, j + 1), hash(i + 1, j + 1), fx);
        return lerp(below, above, fy);
    }

    private static double lerp(double from, double to, double share) {
        return from + (to - from) * share;
    }

    private static double hash(int x, int y) {
        return Integer.toUnsignedLong(Mth.murmurHash3Mixer(x + y * ROW_MIX)) / INT_SPAN;
    }
}
