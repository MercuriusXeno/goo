package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.LunarFixationView;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * The mouse's turn of the local caster, taken on every frame between ticks, goes through LunarFixationView, which
 * turns nothing while a Lunar fixation holds the look; NeoForge's turn event can scale the mouse's sensitivity but
 * cannot stop the turn.
 * decision diagnose-and-correct-the-moon-twitch-on-the-first-grab
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerLunarHoldMixin {

    /**
     * @param player    the local caster
     * @param yawTurn   the mouse's sideways turn
     * @param pitchTurn the mouse's upward turn
     */
    @Redirect(method = "turnPlayer",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void goo$holdTheLunarLook(LocalPlayer player, double yawTurn, double pitchTurn) {
        LunarFixationView.turnByTheMouse(player, yawTurn, pitchTurn);
    }
}
