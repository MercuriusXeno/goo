package com.mercuriusxeno.goo.block.crystallizer;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;
import java.util.function.Function;

/**
 * Finds the crystal a player's sight lands on. The grown crystal stands above its
 * crystallizer, in the space over it, and the game tests a block's shape only in the
 * spaces a ray passes through, so a level look from the side reaches the crystal's
 * shape without ever entering the crystallizer's own space. Operator ruling: a click
 * aimed at the crystal's shape takes it from any side.
 */
public final class CrystalReach {

    /** The server takes a click's point only within 1.0000001 of its block's center; the crystal may stand past that. */
    private static final double SERVER_REACH = 0.5;
    private static final double INSIDE = 1e-4;

    private CrystalReach() {
    }

    /**
     * The nearest crystal a ray passes through, testing each crystallizer below the
     * spaces the ray crosses as well as those it crosses.
     *
     * @param crystalShapeAt the crystal shape a position holds, empty where none is takeable
     * @param from           the ray's start
     * @param to             the ray's end
     * @return the nearest hit on a crystal, or null when the ray passes none
     */
    public static @Nullable BlockHitResult nearestCrystalHit(Function<BlockPos, VoxelShape> crystalShapeAt,
                                                             Vec3 from, Vec3 to) {
        AABB span = new AABB(from, to);
        BlockHitResult nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(Mth.floor(span.minX), Mth.floor(span.minY) - 1,
                Mth.floor(span.minZ), Mth.floor(span.maxX), Mth.floor(span.maxY), Mth.floor(span.maxZ))) {
            VoxelShape shape = crystalShapeAt.apply(pos);
            if (shape.isEmpty()) {
                continue;
            }
            BlockHitResult hit = shape.clip(from, to, pos.immutable());
            if (hit != null && hit.getLocation().distanceToSqr(from) < nearestDistance) {
                nearest = hit;
                nearestDistance = hit.getLocation().distanceToSqr(from);
            }
        }
        return nearest;
    }

    /**
     * Pulls a hit's point down into the reach the server grants a click on its block,
     * so a click on the crystal's upper part is not refused; the point stays inside
     * the crystal's box, which starts at the crystallizer's top.
     *
     * @param hit a hit on a crystal
     * @return the hit with its point within the server's reach
     */
    public static BlockHitResult reachableByServer(BlockHitResult hit) {
        Vec3 point = hit.getLocation();
        double top = hit.getBlockPos().getY() + 1 + SERVER_REACH - INSIDE;
        return new BlockHitResult(new Vec3(point.x, Math.min(point.y, top), point.z), hit.getDirection(),
                hit.getBlockPos(), hit.isInside());
    }
}
