package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.crystallizer.CrystalReach;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlock;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/**
 * Sends a right click aimed at a grown crystal to it from any side (operator ruling:
 * the crystal's shape is the thing to interact with). The game's own ray misses a
 * crystal standing above its crystallizer when the look is level, so the use key
 * tests the crystal shapes along the sight line through {@link CrystalReach} and,
 * where one lies nearer than what the game hit, clicks it instead.
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CrystalClickRedirect {

    private CrystalClickRedirect() {
    }

    /**
     * @param event the use-key interaction for one hand
     */
    @SubscribeEvent
    public static void onUseKeyInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;
        if (!event.isUseItem() || event.isCanceled() || player == null || level == null) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1f).scale(player.blockInteractionRange()));
        BlockHitResult crystal = CrystalReach.nearestCrystalHit(pos -> takeableCrystalShape(level, pos), eye, end);
        if (crystal == null || mc.gameMode == null || gameHitIsNearer(mc.hitResult, eye, crystal)) {
            return;
        }
        InteractionResult result = mc.gameMode.useItemOn(player, event.getHand(), CrystalReach.reachableByServer(crystal));
        if (result.consumesAction()) {
            event.setCanceled(true);
        }
    }

    private static boolean gameHitIsNearer(HitResult gameHit, Vec3 eye, BlockHitResult crystal) {
        return gameHit != null && gameHit.getType() != HitResult.Type.MISS
                && gameHit.getLocation().distanceToSqr(eye) <= crystal.getLocation().distanceToSqr(eye);
    }

    private static VoxelShape takeableCrystalShape(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof CrystallizerBlock)
                || !(level.getBlockEntity(pos) instanceof CrystallizerBlockEntity crystallizer)
                || !crystallizer.isMature(CrystallizerBlock.knobTier(state))) {
            return Shapes.empty();
        }
        return CrystallizerBlock.crystalShape(state.getValue(CrystallizerBlock.FACING), crystallizer.crystallized());
    }
}
