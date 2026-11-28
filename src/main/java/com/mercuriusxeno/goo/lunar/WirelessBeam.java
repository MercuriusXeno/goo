package com.mercuriusxeno.goo.lunar;

import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The wireless emitter's scan: every position within 16 Manhattan of the emitter, nearest first and equally near ones
 * in one fixed order, and the first of them that takes a signal. The order never changes, so an emitter finds the same
 * receiver each time it looks, which is how it finds its own source again without holding a position.
 * decision wireless-emitter-beams-its-signal-to-the-closest-receiver
 */
public final class WirelessBeam {

    /** How far an emitter reaches, in Manhattan distance. */
    public static final int RANGE = 16;
    /** Every offset the scan reads, nearest first, ties broken by height, then east-west, then north-south. */
    static final List<BlockPos> OFFSETS = offsetsInReach();

    private WirelessBeam() {
    }

    private static List<BlockPos> offsetsInReach() {
        List<BlockPos> offsets = new ArrayList<>();
        for (int dy = -RANGE; dy <= RANGE; dy++) {
            for (int dx = -RANGE; dx <= RANGE; dx++) {
                for (int dz = -RANGE; dz <= RANGE; dz++) {
                    int distance = manhattan(dx, dy, dz);
                    if (distance >= 1 && distance <= RANGE) {
                        offsets.add(new BlockPos(dx, dy, dz));
                    }
                }
            }
        }
        offsets.sort(Comparator.comparingInt((BlockPos o) -> manhattan(o.getX(), o.getY(), o.getZ()))
                .thenComparingInt(BlockPos::getY).thenComparingInt(BlockPos::getX).thenComparingInt(BlockPos::getZ));
        return List.copyOf(offsets);
    }

    private static int manhattan(int dx, int dy, int dz) {
        return Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
    }

    /**
     * The closest position within reach that takes a signal, equally close ones taken in the scan's fixed order.
     *
     * @param emitter     the emitter
     * @param takesSignal whether a position holds a block that takes a signal
     * @return the closest such position, or empty
     */
    public static Optional<BlockPos> closest(BlockPos emitter, Predicate<BlockPos> takesSignal) {
        for (BlockPos offset : OFFSETS) {
            BlockPos at = emitter.offset(offset);
            if (takesSignal.test(at)) {
                return Optional.of(at);
            }
        }
        return Optional.empty();
    }

    /**
     * Whether two positions touch, so a source standing in one would power a block in the other.
     *
     * @param a one position
     * @param b the other
     * @return whether they are one step apart
     */
    static boolean touching(BlockPos a, BlockPos b) {
        return a.distManhattan(b) <= 1;
    }
}
