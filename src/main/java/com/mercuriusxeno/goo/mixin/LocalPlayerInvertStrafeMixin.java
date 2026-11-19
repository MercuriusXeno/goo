package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.InvertView;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

/**
 * An inverted player whose camera rolls half a turn strafes toward the side
 * it sees: the roll shows its left as the screen's right, so the strafe keys
 * turn over with it, and forward stays forward.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerInvertStrafeMixin {

    /**
     * Turns an inverted player's strafe over while its camera is rolled.
     *
     * @param input vanilla's move input, strafe then forward
     * @return the input the player moves on
     */
    @ModifyReturnValue(method = "modifyInput", at = @At("RETURN"))
    private Vec2 goo$strafesTowardTheSideItSees(Vec2 input) {
        float sign = InvertView.steerSign((LocalPlayer) (Object) this);
        return sign == 1F ? input : new Vec2(input.x * sign, input.y);
    }
}
