package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.lunar.LunarClientMeshes;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Holds the sections a crossing of lunar's seam moved clear of rebuilding while their light moves under them: each
 * keeps the mesh it was built with, light and all, where a rebuild before the moved light lands would draw it dark.
 * A block changing there still marks its section, through vanilla's own path for block changes. Outside a crossing's
 * light move the hook returns at once, and a renderer mod that replaced LevelExtractor leaves it unapplied, so the
 * moved sections rebuild as vanilla's do.
 * decision moon-wraps-at-its-border-like-the-zone
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorLunarCrossingMixin {

    /**
     * Leaves a moved section's mesh standing while its light moves.
     *
     * @param sectionX the section's x
     * @param sectionY the section's y
     * @param sectionZ the section's z
     * @param ci       vanilla's marking, cancelled for a moved section
     */
    @Inject(method = "setSectionDirty(III)V", at = @At("HEAD"), cancellable = true, require = 0)
    private void goo$keepsTheMovedMeshWhileItsLightMoves(int sectionX, int sectionY, int sectionZ, CallbackInfo ci) {
        if (LunarClientMeshes.keepsItsMesh(sectionX, sectionY, sectionZ)) {
            ci.cancel();
        }
    }
}
