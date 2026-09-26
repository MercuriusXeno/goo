package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.block.canister.HudAnchor;
import com.mercuriusxeno.goo.block.canister.HudViewer;
import com.mercuriusxeno.goo.block.canister.ICanisterHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Resolves crosshair aim into the canister holder's HUD target: the holder
 * answers the slot and anchor, this class supplies what the client knows of the
 * viewer (decision hosts-answer-bounds-through-interfaces).
 */
final class CanisterTargetResolver {

    private CanisterTargetResolver() {
    }

    /**
     * Returns the targeted canister slot, or null if not looking at a canister holder.
     *
     * @return the target
     */
    static CanisterHudRenderer.@Nullable Target getTarget() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.hitResult == null || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos pos = hit.getBlockPos();
        if (!(mc.level.getBlockEntity(pos) instanceof ICanisterHolder holder)) {
            return null;
        }
        HudAnchor anchor = holder.hudAnchor(hit, viewer(mc.player, mc.level, pos));
        if (anchor == null) {
            return null;
        }
        return new CanisterHudRenderer.Target(pos, anchor.slot(), anchor.x(), anchor.z(),
                anchor.lift(), anchor.face(), false);
    }

    /**
     * What the client knows of the viewer that a holder's anchor turns on.
     *
     * @param player the local player, or null
     * @param level  the client level
     * @param pos    the aimed holder's position
     * @return the viewer
     */
    private static HudViewer viewer(@Nullable LocalPlayer player, Level level, BlockPos pos) {
        Direction facing = player != null ? player.getDirection() : Direction.NORTH;
        Vec3 look = player != null ? player.getLookAngle() : Vec3.ZERO;
        BlockPos above = pos.above();
        boolean blockAbove = level.getBlockState(above).isCollisionShapeFullBlock(level, above);
        return new HudViewer(facing, InWorldHud.bestPerpendicularFace(look), blockAbove);
    }
}
