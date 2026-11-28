package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.lunar.LunarClientMeshes;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps the meshes the client built through the camera's jump across lunar's seam: each mesh is lifted off its section
 * as vanilla re-keys the sections and set on the section now standing where it lands. Outside a lunar crossing both
 * hooks return at once, and a renderer mod that replaced ViewArea leaves the hooks unapplied, so the landing sections
 * mesh again as vanilla's do.
 * decision moon-wraps-at-its-border-like-the-zone
 */
@Mixin(ViewArea.class)
public abstract class ViewAreaLunarCrossingMixin {

    /**
     * Lifts each built mesh before vanilla re-keys the sections for the jump.
     *
     * @param camera the section the camera moves to
     * @param cir    vanilla's answer, untouched
     */
    @Inject(method = "repositionCamera", at = @At("HEAD"), require = 0)
    private void goo$liftsTheMeshesBeforeTheSeamJump(SectionPos camera, CallbackInfoReturnable<Boolean> cir) {
        LunarClientMeshes.liftBeforeTheJump((ViewArea) (Object) this, camera);
    }

    /**
     * Sets each lifted mesh on the section now standing where it lands.
     *
     * @param camera the section the camera moved to
     * @param cir    vanilla's answer, untouched
     */
    @Inject(method = "repositionCamera", at = @At("RETURN"), require = 0)
    private void goo$setsTheMeshesAfterTheSeamJump(SectionPos camera, CallbackInfoReturnable<Boolean> cir) {
        LunarClientMeshes.setAfterTheJump((ViewArea) (Object) this);
    }
}
