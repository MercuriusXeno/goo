package com.mercuriusxeno.goo.mixin;

import com.mercuriusxeno.goo.client.outsider.RoomCopyRenderer;
import com.mercuriusxeno.goo.client.outsider.VoidstoneFaces;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tells voidstone's face cache when a section's blocks change: every block change and chunk load the level
 * renderer hears marks sections dirty through this one method, so the cache drops just those sections
 * (decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform), and the room copies lay
 * their meshes out afresh where the room stands in one (decision room-tiles-to-render-distance-held-to-the-picture).
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererVoidstoneMixin {

    @Inject(method = "setSectionDirty(IIIZ)V", at = @At("HEAD"))
    private void goo$forgetVoidstoneFaces(int sectionX, int sectionY, int sectionZ, boolean playerChanged,
                                          CallbackInfo ci) {
        VoidstoneFaces.markDirty(sectionX, sectionY, sectionZ);
        RoomCopyRenderer.markDirty(sectionX, sectionZ);
    }
}
