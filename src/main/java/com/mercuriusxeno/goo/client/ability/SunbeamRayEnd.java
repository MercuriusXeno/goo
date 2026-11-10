package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Where a sunbeam ray's drawn end sits each frame. The server's ray arrives
 * once a tick, a round trip behind the look, so the local caster's own ray
 * is cast each frame from its eye along its aim that frame; another
 * caster's ray ends where the server struck.
 * decision diagnose-and-correct-the-sunbeam-jitter
 */
final class SunbeamRayEnd {

    /** Below this squared gap the aim point sits on the eye and gives no direction. */
    private static final double SHORTEST_AIM_SQUARED = 1e-6;
    /** How far past the struck spot, along the ray, the struck block is read, in blocks. */
    private static final double INTO_THE_STRUCK = 1e-3;

    private SunbeamRayEnd() {
    }

    /**
     * A sight line cast against the world.
     */
    @FunctionalInterface
    interface SightLine {

        /**
         * Where a sight line from a point toward its reach stops.
         *
         * @param from  where the line starts
         * @param reach the furthest point it reaches
         * @return the point it stops at, the reach where nothing stops it
         */
        Vec3 stop(Vec3 from, Vec3 reach);
    }

    /**
     * The local caster's aim this frame.
     *
     * @param eye      the caster's eye this frame
     * @param aimPoint the point the hold aims at this frame
     */
    record OwnAim(Vec3 eye, Vec3 aimPoint) {
    }

    /**
     * Where the ray's light gathers: the middle of a prism it struck that
     * refracts it, so the ray runs into the core its refracted beams leave
     * from; else the struck spot.
     * decision diagnose-and-correct-the-sunbeam-prism-depth
     *
     * @param from              where the ray leaves the caster
     * @param end               where the ray struck
     * @param refractingPrismAt whether a prism that refracts the ray stands at a position
     * @return the point the ray's light gathers at
     */
    static Vec3 gathersAt(Vec3 from, Vec3 end, Predicate<BlockPos> refractingPrismAt) {
        Vec3 along = end.subtract(from);
        if (along.lengthSqr() < SHORTEST_AIM_SQUARED) {
            return end;
        }
        BlockPos struck = BlockPos.containing(end.add(along.normalize().scale(INTO_THE_STRUCK)));
        return refractingPrismAt.test(struck) ? Vec3.atCenterOf(struck) : end;
    }

    /**
     * Whether the ray ends in a prism: its hit is a block holding one, so its
     * layers bloom out inside the prism rather than stop square.
     * decision sunbeam-ray-blooms-out-inside-the-prism
     *
     * @param hit           what the ray's sight line struck, null where none was cast
     * @param blockEntityAt the block entity at a position, null where none stands
     * @return true for a ray ending in a prism
     */
    static boolean endsInPrism(@Nullable HitResult hit, Function<BlockPos, @Nullable BlockEntity> blockEntityAt) {
        return hit instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK
                && blockEntityAt.apply(block.getBlockPos()) instanceof PrismBlockEntity;
    }

    /**
     * The ray's drawn end this frame.
     *
     * @param serverEnd where the server's latest ray struck
     * @param own       the local caster's aim this frame, null for another caster's ray
     * @param range     how far the ray reaches, in blocks
     * @param sight     the sight line the world casts
     * @return the drawn end
     */
    static Vec3 drawnEnd(Vec3 serverEnd, @Nullable OwnAim own, double range, SightLine sight) {
        if (own == null) {
            return serverEnd;
        }
        Vec3 aim = own.aimPoint().subtract(own.eye());
        if (aim.lengthSqr() < SHORTEST_AIM_SQUARED) {
            return serverEnd;
        }
        return sight.stop(own.eye(), own.eye().add(aim.normalize().scale(range)));
    }
}
