package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Keeps the screen clear while the player passes through a gate: the in-wall
 * overlay samples the blocks about the eye, and as the eye nears a gate's
 * face those samples fall in the blocks the gate stands open, which would
 * paint the wall's texture over the whole screen.
 * portals-link-by-looking-through-portal-vision
 * hole-closes-the-space-to-an-opening-ahead
 */
@Mixin(ScreenEffectRenderer.class)
public abstract class ScreenEffectRendererGateMixin {

    /**
     * Answers no view blocking for a gate's block the player is passing into.
     *
     * @param state    the block sampled
     * @param level    the level
     * @param pos      where it stands
     * @param original the game's own check
     * @param player   the player the overlay draws for
     * @return whether the block blocks the view
     */
    @WrapOperation(method = "getOverlayBlock", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/"
            + "state/BlockState;isViewBlocking(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;)Z"))
    private static boolean goo$seeThroughAGate(BlockState state, BlockGetter level, BlockPos pos,
            Operation<Boolean> original, @Local(argsOnly = true) Player player) {
        return !GatePassage.passable(player, pos) && original.call(state, level, pos);
    }
}
