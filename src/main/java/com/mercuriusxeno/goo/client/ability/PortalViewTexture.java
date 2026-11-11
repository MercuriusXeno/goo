package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.renderer.texture.AbstractTexture;

/**
 * A portal view's render target, registered as a texture so the portal's
 * face can sample it. The target owns the texture, so closing this lets go
 * of nothing.
 * portals-link-by-looking-through-portal-vision
 */
final class PortalViewTexture extends AbstractTexture {

    /**
     * Points the texture at a target's color.
     *
     * @param target the portal view's render target
     */
    void show(RenderTarget target) {
        texture = target.getColorTexture();
        textureView = target.getColorTextureView();
    }

    @Override
    public void close() {
        texture = null;
        textureView = null;
    }
}
