package com.mercuriusxeno.goo.client.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * What Scry's sweep reveals and how long it shows: the blocks its front
 * crosses between two radii, the faces of each that open onto air, and the
 * fade once the hold lets go.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 */
public final class ScryReveal {

    /** Ticks without a new radius before the sweep reads as let go. */
    static final int RELEASE_GRACE_TICKS = 2;
    /** Ticks the revealed faces and the sphere take to fade once let go: about a second. */
    static final int FADE_TICKS = 20;
    /** Ticks a newly revealed face flashes brighter before it settles. */
    static final int FLASH_TICKS = 10;
    private static final double HALF = 0.5;
    /** A column's inner span where the inner sphere misses it: below any distance, so the whole column counts. */
    private static final double NO_INNER_SPAN = -1;

    private ScryReveal() {
    }

    /**
     * The faces of a block that open onto air: none for air itself, else each
     * side whose neighbor is air.
     *
     * @param isAir whether a position holds air
     * @param pos   the block
     * @return the sides of the block open to air
     */
    public static List<Direction> exposedFaces(Predicate<BlockPos> isAir, BlockPos pos) {
        List<Direction> exposed = new ArrayList<>();
        if (isAir.test(pos)) {
            return exposed;
        }
        for (Direction side : Direction.values()) {
            if (isAir.test(pos.relative(side))) {
                exposed.add(side);
            }
        }
        return exposed;
    }

    /**
     * The blocks whose centers lie past one radius and within another of a
     * point: the shell a growing front crossed in one tick.
     *
     * @param center the sphere's center
     * @param inner  the radius the front stood at last tick
     * @param outer  the radius it stands at now
     * @return the blocks in the shell
     */
    public static List<BlockPos> shell(Vec3 center, double inner, double outer) {
        List<BlockPos> crossed = new ArrayList<>();
        if (outer <= inner) {
            return crossed;
        }
        double innerSq = Math.max(0, inner) * Math.max(0, inner);
        double outerSq = outer * outer;
        int lowX = (int) Math.floor(center.x - outer - HALF);
        int lowY = (int) Math.floor(center.y - outer - HALF);
        for (int x = lowX; x <= (int) Math.ceil(center.x + outer); x++) {
            for (int y = lowY; y <= (int) Math.ceil(center.y + outer); y++) {
                double ox = x + HALF - center.x;
                double oy = y + HALF - center.y;
                double columnSq = ox * ox + oy * oy;
                if (columnSq <= outerSq) {
                    addColumn(crossed, x, y, center.z, columnSq, innerSq, outerSq);
                }
            }
        }
        return crossed;
    }

    /**
     * Adds the blocks of one column whose centers fall in the shell: the
     * column's span within the outer radius, less its span within the inner.
     *
     * @param crossed  the blocks collected
     * @param x        the column's x
     * @param y        the column's y
     * @param centerZ  the sphere center's z
     * @param columnSq the column's squared distance from the center across x and y
     * @param innerSq  the inner radius squared
     * @param outerSq  the outer radius squared
     */
    private static void addColumn(List<BlockPos> crossed, int x, int y, double centerZ, double columnSq,
                                  double innerSq, double outerSq) {
        double outerSpan = Math.sqrt(outerSq - columnSq);
        double innerSpan = innerSq > columnSq ? Math.sqrt(innerSq - columnSq) : NO_INNER_SPAN;
        int low = (int) Math.floor(centerZ - outerSpan - HALF);
        int high = (int) Math.ceil(centerZ + outerSpan);
        // the inner sphere's span was swept on earlier ticks: walk the two ends of the column around it
        int nearEnd = innerSpan < 0 ? high : (int) Math.ceil(centerZ - innerSpan - HALF);
        int farStart = Math.max(nearEnd + 1, (int) Math.floor(centerZ + innerSpan - HALF));
        addInShell(crossed, x, y, low, nearEnd, centerZ, innerSpan, outerSpan);
        addInShell(crossed, x, y, farStart, high, centerZ, innerSpan, outerSpan);
    }

    private static void addInShell(List<BlockPos> crossed, int x, int y, int fromZ, int toZ, double centerZ,
                                   double innerSpan, double outerSpan) {
        for (int z = fromZ; z <= toZ; z++) {
            double oz = Math.abs(z + HALF - centerZ);
            if (oz > innerSpan && oz <= outerSpan) {
                crossed.add(new BlockPos(x, y, z));
            }
        }
    }

    /**
     * How strongly the sweep still shows: full while held, then fading to
     * nothing over about a second once no new radius arrives.
     *
     * @param ticksSinceRadius ticks since the last radius arrived
     * @return the strength, zero to one
     */
    public static float fade(long ticksSinceRadius) {
        long faded = ticksSinceRadius - RELEASE_GRACE_TICKS;
        if (faded <= 0) {
            return 1f;
        }
        return Math.max(0f, 1f - (float) faded / FADE_TICKS);
    }

    /**
     * How much brighter a face shows for being newly revealed: a flash that
     * settles over half a second, so the front reads as a sonar ping.
     *
     * @param ticksSinceRevealed ticks since the front crossed the face
     * @return the extra brightness, one at the crossing to zero once settled
     */
    public static float flash(long ticksSinceRevealed) {
        return Math.max(0f, 1f - (float) ticksSinceRevealed / FLASH_TICKS);
    }
}
