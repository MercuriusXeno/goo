package com.mercuriusxeno.goo.client.entity;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.entity.CompressionSphere;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

/**
 * Draws the compression sphere as a dark nether orb about a quarter block
 * wide, maroon-black with a faint nether glow at its rim, bobbing and
 * spinning like a dropped item (decision black-hole-leaves-a-compression-sphere).
 */
public final class CompressionSphereRenderer extends BobbingDiscRenderer<CompressionSphere> {

    private static final Identifier TEXTURE =
            Identifier.fromNamespaceAndPath(Goo.MODID, "textures/entity/compression_sphere.png");
    private static final float SIZE = 0.3f;
    /** The rim glow keeps the orb lit seven levels above its surroundings, as an experience orb does. */
    private static final int GLOW_LEVELS = 7;

    /**
     * @param context the renderer context
     */
    public CompressionSphereRenderer(EntityRendererProvider.Context context) {
        super(context, TEXTURE, SIZE, GLOW_LEVELS);
    }
}
