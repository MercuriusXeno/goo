package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.throwing.StreamCone;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * The blocks a stream holds: every standing block whose center lies in the
 * cone and which the apex sees, so a wall's face is held and what stands
 * behind it is not.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class StreamedBlocks {

    private StreamedBlocks() {
    }

    /**
     * Collects the blocks the cone holds this tick.
     *
     * @param level    the server level
     * @param player   the streaming player, whom the sight lines ignore
     * @param apex     the cone's apex
     * @param axis     the cone's axis
     * @param delivery the stream delivery
     * @return the held blocks
     */
    public static List<BlockPos> held(ServerLevel level, ServerPlayer player, Vec3 apex, Vec3 axis,
                                      Delivery delivery) {
        List<BlockPos> held = new ArrayList<>();
        AABB reach = new AABB(apex, apex).inflate(delivery.range());
        for (BlockPos pos : BlockPos.betweenClosed(reach)) {
            Vec3 center = Vec3.atCenterOf(pos);
            if (!level.getBlockState(pos).isAir()
                    && StreamCone.contains(apex, axis, delivery.range(), delivery.coneDegrees(), center)
                    && apexSees(level, player, apex, pos)) {
                held.add(pos.immutable());
            }
        }
        return held;
    }

    /**
     * Whether the sight line from the apex to the block's center first meets that block.
     *
     * @param level  the server level
     * @param player the streaming player
     * @param apex   the cone's apex
     * @param pos    the block
     * @return true when nothing stands between the apex and the block
     */
    private static boolean apexSees(ServerLevel level, ServerPlayer player, Vec3 apex, BlockPos pos) {
        BlockHitResult hit = level.clip(new ClipContext(apex, Vec3.atCenterOf(pos), ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
    }
}
