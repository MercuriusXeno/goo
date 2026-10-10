package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.block.crucible.CrucibleMath;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Unmake's drink rules: the cone it drinks, what a block costs, how long its
 * choreography takes and how long a block takes to stream in. The cone picks
 * every block whose middle stands in it and, whatever the cone's width there,
 * every block the aim's own line passes through, so what the cursor is on is
 * always drunk and the cone widens about that line. A block costs
 * the unstable fuel the unstable crucible burns melting it plus the square
 * root of that, the price of doing it at will, paid the tick it is picked as
 * a zoop of unstable goo leaves the hand for it; the zoop takes
 * {@link #INJECT_TICKS} to reach the block, which then streams in over the
 * crucible's own time for it, and its goo is in hand once its tail has
 * travelled back at the base pace.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class SiphonRule {

    /** How far from the eye the cone reaches, in blocks. */
    public static final double RANGE = 7;
    /** The distance the cone is as wide as its square, the middle of its far half. */
    public static final double MID_RANGE = 5.5;
    /** Ticks the zoop of unstable goo takes from the hand into a picked block before it starts to melt. */
    public static final int INJECT_TICKS = 9;
    /** Blocks a tick a lone stream flows back to the hand: four blocks a second. */
    public static final double BASE_PACE = 0.2;
    private static final double SLOWEST_SPEED = 0.01;
    private static final double HALF_BLOCK = 0.5;
    private static final double EDGE_TO_EDGE = 2;

    private SiphonRule() {
    }

    /**
     * The unstable goo a block costs: the fuel the unstable crucible burns
     * melting it, one mB buying {@code ticksPerMb} ticks of its clock, plus
     * the square root of that fuel rounded up.
     *
     * @param slowestUnit the mB of the block's slowest melting unit
     * @param exponent    the unstable crucible's melt exponent
     * @param ticksPerMb  the heat ticks one mB of unstable goo buys
     * @return the mB burned
     */
    public static int fuelFor(long slowestUnit, double exponent, int ticksPerMb) {
        long ticks = CrucibleMath.meltTicks(slowestUnit, exponent);
        long fuel = Math.ceilDiv(ticks, Math.max(1, ticksPerMb));
        return (int) Math.min(Integer.MAX_VALUE, fuel + (long) Math.ceil(Math.sqrt(fuel)));
    }

    /**
     * The ticks a block takes to stream into the glove: the unstable
     * crucible's own time melting it, divided by the drink's speed.
     *
     * @param totalGoo the mB of goo the block holds
     * @param exponent the unstable crucible's melt exponent
     * @param speed    how much faster than the crucible the drink goes, 1 at its pace
     * @return the ticks, at least 1
     */
    public static int siphonTicks(long totalGoo, double exponent, double speed) {
        long ticks = CrucibleMath.meltTicks(totalGoo, exponent);
        return (int) Math.max(1, Math.round(ticks / Math.max(SLOWEST_SPEED, speed)));
    }

    /**
     * The ticks a drained block's tail takes to reach the hand, as the server
     * reckons it: the straight way at the base pace.
     *
     * @param distance blocks from the block's middle to the drinker's eye
     * @return the ticks, at least 1
     */
    public static int travelTicks(double distance) {
        return (int) Math.max(1, Math.ceil(distance / BASE_PACE));
    }

    /**
     * The cone's apex angle, edge to edge: a point at the eye, and at
     * {@link #MID_RANGE} half a block wider each side than the radius names,
     * so a radius of 0 is one block wide there, penetrating deep but not
     * wide, and 0.75 is two and a half.
     *
     * @param radius how far past half a block the cone reaches from its axis at mid range, 0 for one block wide
     * @return the apex angle in degrees
     */
    public static double coneDegrees(double radius) {
        return EDGE_TO_EDGE * Math.toDegrees(Math.atan((radius + HALF_BLOCK) / MID_RANGE));
    }

    /**
     * The blocks the drink picks, nearest the eye first: every block whose
     * middle stands in the cone from the eye toward the aim, and every block
     * the aim's line itself passes through within the range, however narrow
     * the cone is there, so the block under the cursor is always among them.
     *
     * @param eye      the drinker's eye
     * @param aimPoint the point the drinker aims at, which sets the cone's axis
     * @param radius   how far past half a block the cone reaches from its axis at mid range
     * @return the blocks, standing or not, nearest first
     */
    public static List<BlockPos> blocksToDrink(Vec3 eye, Vec3 aimPoint, double radius) {
        Vec3 line = aimPoint.subtract(eye);
        if (line.lengthSqr() == 0) {
            return List.of();
        }
        Vec3 reach = eye.add(line.normalize().scale(RANGE));
        Set<BlockPos> picked = new LinkedHashSet<>(CalcifyStep.blocksInCone(eye, reach, coneDegrees(radius)));
        picked.addAll(blocksAlong(eye, reach));
        List<BlockPos> ordered = new ArrayList<>(picked);
        ordered.sort(Comparator.comparingDouble(pos -> Vec3.atCenterOf(pos).distanceToSqr(eye)));
        return ordered;
    }

    /**
     * @param from where a line starts
     * @param to   where it ends
     * @return every block the line passes through
     */
    static List<BlockPos> blocksAlong(Vec3 from, Vec3 to) {
        List<BlockPos> along = new ArrayList<>();
        BlockPos low = BlockPos.containing(Math.min(from.x, to.x), Math.min(from.y, to.y), Math.min(from.z, to.z));
        BlockPos high = BlockPos.containing(Math.max(from.x, to.x), Math.max(from.y, to.y), Math.max(from.z, to.z));
        for (BlockPos pos : BlockPos.betweenClosed(low, high)) {
            AABB box = new AABB(pos);
            if (box.contains(from) || box.clip(from, to).isPresent()) {
                along.add(pos.immutable());
            }
        }
        return along;
    }
}
