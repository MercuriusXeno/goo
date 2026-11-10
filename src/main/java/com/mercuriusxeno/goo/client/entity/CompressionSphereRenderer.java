package com.mercuriusxeno.goo.client.entity;

import com.mercuriusxeno.goo.entity.CompressionSphere;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.Identifier;

/**
 * Draws the compression sphere as an orb about a quarter block wide, bobbing
 * and spinning like a dropped item, its core drawn from the texture the
 * sphere's core names: the black hole's maroon-black with a faint nether glow
 * at its rim, or a large Blast's neon green.
 * decision black-hole-leaves-a-compression-sphere
 * decision large-blast-gathers-its-mined-drops-into-a-green-cored-sphere
 */
public final class CompressionSphereRenderer extends BobbingDiscRenderer<CompressionSphere> {

    private static final float SIZE = 0.3f;
    /** The rim glow keeps the orb lit seven levels above its surroundings, as an experience orb does. */
    private static final int GLOW_LEVELS = 7;

    /**
     * @param context the renderer context
     */
    public CompressionSphereRenderer(EntityRendererProvider.Context context) {
        super(context, SIZE, GLOW_LEVELS);
    }

    @Override
    protected Identifier textureOf(CompressionSphere sphere) {
        return sphere.core().texture();
    }
}
