package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.ability.AbilityPoses;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Once vanilla has posed a player's model, the ability pose the player
 * holds lays over the glove arm, so a charge winds the arm up and its
 * release flings it out in place of the swing; the game has no event
 * after its model setup that reaches the arm's rotation.
 * decision shards-sling-then-morph-to-flechettes
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelAbilityPoseMixin {

    /**
     * @param state the player's render state
     * @param ci    the callback
     */
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void goo$layAbilityPose(AvatarRenderState state, CallbackInfo ci) {
        AbilityPoses.layOver((PlayerModel) (Object) this, state.id);
    }
}
