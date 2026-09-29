package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.block.crystallizer.CrystalReach;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlock;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlockEntity;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerShapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Makes a crystal the player's target from any side (operator ruling: the
 * crystal's shape is the thing to interact with), growing or grown; a click on a
 * growing crystal passes, the take staying gated on maturity.
 * The game's own ray misses a crystal standing above its crystallizer when the look is
 * level, so after each pick the crystal shapes on the sight line are tested through
 * {@link CrystalReach}, and a nearer crystal replaces the pick: the outline draws and
 * the game's own click reaches it.
 */
public final class CrystalClickRedirect {

    private CrystalClickRedirect() {
    }

    /**
     * Called after the game picks the player's target each frame and tick.
     *
     * @param mc the client
     */
    public static void retarget(Minecraft mc) {
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1f).scale(player.blockInteractionRange()));
        BlockHitResult crystal = CrystalReach.nearestCrystalHit(pos -> crystalShapeAt(level, pos), eye, end);
        HitResult target = CrystalReach.retarget(mc.hitResult, crystal, eye);
        if (target != mc.hitResult) {
            mc.hitResult = target;
            mc.crosshairPickEntity = null;
        }
    }

    /**
     * A growing crystal is a target too, so its HUD shows from any side (decision crystal-hud-shows-on-crystal-look).
     *
     * @param level the client level
     * @param pos   a position on the sight line
     * @return the crystal's shape there, empty where no crystallizer holds one
     */
    private static VoxelShape crystalShapeAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CrystallizerBlock)
                || !(level.getBlockEntity(pos) instanceof CrystallizerBlockEntity crystallizer)) {
            return Shapes.empty();
        }
        return CrystallizerShapes.crystalShape(state.getValue(CrystallizerBlock.FACING), crystallizer.crystallized());
    }
}
