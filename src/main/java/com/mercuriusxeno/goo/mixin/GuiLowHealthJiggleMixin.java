package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.hud.LowHealthJiggle;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Holds vanilla's heart row still while other abilities' filled hearts back
 * low health: vanilla rolls each heart's low-health shake in extractHearts,
 * and the roll answers no shake while LowHealthJiggle says the bar is steady.
 * The roll still runs, so the gui's random sequence, which Goo's overlay
 * replays, stays vanilla's.
 * health-bar-stays-steady-while-filled-hearts-stand
 */
@Mixin(Gui.class)
public abstract class GuiLowHealthJiggleMixin {

    /**
     * Answers a heart's low-health shake: vanilla's roll while the bar
     * jiggles, none while filled hearts hold it steady.
     *
     * @param random   the gui's random
     * @param bound    the roll's bound
     * @param player   the player whose hearts draw
     * @param original vanilla's roll
     * @return the heart's downward shake
     */
    @WrapOperation(method = "extractHearts",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/RandomSource;nextInt(I)I"))
    private int goo$steadyBehindFilledHearts(RandomSource random, int bound, Operation<Integer> original,
            @Local(argsOnly = true) Player player) {
        int roll = original.call(random, bound);
        return LowHealthJiggle.jiggles(player) ? roll : 0;
    }
}
