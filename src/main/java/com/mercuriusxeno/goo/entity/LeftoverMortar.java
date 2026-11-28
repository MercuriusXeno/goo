package com.mercuriusxeno.goo.entity;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.ArrayList;
import java.util.List;

/**
 * The Mortar a Grab lift leaves riding its piece once the lift's cost is
 * paid: drawn on the face it coats, quivering for the first half second, then
 * shed a layer at a time over the next two seconds, each layer dripping down
 * off the piece.
 * decision leftover-mortar-quivers-and-drips-down-off-the-lifted-piece
 */
public final class LeftoverMortar {

    /** The ticks the riding Mortar quivers for, from the grab: half a second. */
    static final int QUIVER_TICKS = 10;
    /** The tick the riding Mortar starts shedding on, from the grab. */
    static final int SHED_START = QUIVER_TICKS;
    /** The ticks the riding Mortar sheds over: two seconds. */
    static final int SHED_TICKS = 40;
    /** The farthest the drawn Mortar quivers off its face, in blocks. */
    private static final double QUIVER_REACH = 0.03;
    /** The quiver's rate, in radians a tick, fast enough to read as a shiver. */
    private static final double QUIVER_RATE = 2.3;

    /**
     * Mortar riding one block of a piece.
     *
     * @param offset the piece's block it rides, by its offset from the piece's origin cell
     * @param face   the face of that block it coats, the way the piece was pulled out
     * @param layers the layers riding there
     */
    public record Riding(BlockPos offset, Direction face, int layers) {

        /** Codec a saved hunk stores its riding Mortar by. */
        public static final Codec<Riding> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                BlockPos.CODEC.fieldOf("offset").forGetter(Riding::offset),
                Direction.CODEC.fieldOf("face").forGetter(Riding::face),
                Codec.INT.fieldOf("layers").forGetter(Riding::layers)
        ).apply(inst, Riding::new));
    }

    private LeftoverMortar() {
    }

    /**
     * How far the drawn Mortar shakes off its face at an age: a shiver
     * easing to still across the first half second, zero from then on.
     *
     * @param age the ticks since the grab
     * @return the offset, in blocks
     */
    public static double quiverOffset(double age) {
        if (age <= 0 || age >= QUIVER_TICKS) {
            return 0;
        }
        return QUIVER_REACH * Math.sin(age * QUIVER_RATE) * (1 - age / QUIVER_TICKS);
    }

    /**
     * How many of the riding layers have shed by an age: none before the
     * quiver ends, then evenly over the two seconds after, all by its end.
     *
     * @param age   the ticks since the grab
     * @param total the layers riding at the grab
     * @return the layers shed
     */
    public static int shedBy(long age, int total) {
        long shedding = Math.clamp(age - SHED_START, 0, SHED_TICKS);
        return (int) (total * shedding / SHED_TICKS);
    }

    /**
     * The Mortar left riding once some layers have shed, each taken from the
     * rider holding the most, the first of equals.
     *
     * @param riding the Mortar riding at the grab
     * @param shed   the layers shed
     * @return what still rides, every rider kept in order, an emptied one at zero
     */
    public static List<Riding> ridingAfter(List<Riding> riding, int shed) {
        List<Riding> left = new ArrayList<>(riding);
        for (int layer = 0; layer < shed && layersOf(left) > 0; layer++) {
            int next = nextToShed(left);
            Riding from = left.get(next);
            left.set(next, new Riding(from.offset(), from.face(), from.layers() - 1));
        }
        return List.copyOf(left);
    }

    /**
     * The rider the next layer sheds from: the one holding the most, the first of equals.
     *
     * @param riding the Mortar riding, holding a layer at least
     * @return its index
     */
    public static int nextToShed(List<Riding> riding) {
        int most = 0;
        for (int index = 1; index < riding.size(); index++) {
            if (riding.get(index).layers() > riding.get(most).layers()) {
                most = index;
            }
        }
        return most;
    }

    /**
     * The layers riding in all.
     *
     * @param riding the Mortar riding
     * @return the layers
     */
    public static int layersOf(List<Riding> riding) {
        return riding.stream().mapToInt(Riding::layers).sum();
    }
}
