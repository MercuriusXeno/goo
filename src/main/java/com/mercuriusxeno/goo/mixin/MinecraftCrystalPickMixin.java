package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.CrystalClickRedirect;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * After the game picks the player's target, a grown crystal on the sight line nearer
 * than the pick becomes the target (operator ruling: the crystal's shape is the thing
 * to interact with); the game has no event after its pick.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftCrystalPickMixin {

    /**
     * @param partialTicks the frame's partial tick
     * @param ci           the callback
     */
    @Inject(method = "pick", at = @At("TAIL"))
    private void goo$retargetCrystal(float partialTicks, CallbackInfo ci) {
        CrystalClickRedirect.retarget((Minecraft) (Object) this);
    }
}
