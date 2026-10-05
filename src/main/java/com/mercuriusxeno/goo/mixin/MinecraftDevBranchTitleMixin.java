package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.DevBranchTitle;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Tags the window title with the dev run's branch at the return of createTitle, which
 * updateTitle calls on world join and leave, so the tag survives both
 * (decision dev-window-title-carries-the-branch).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftDevBranchTitleMixin {

    /**
     * @param cir the callback holding the built title
     */
    @Inject(method = "createTitle", at = @At("RETURN"), cancellable = true)
    private void goo$tagTitleWithDevBranch(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(DevBranchTitle.tagWithDevBranch(cir.getReturnValue()));
    }
}
