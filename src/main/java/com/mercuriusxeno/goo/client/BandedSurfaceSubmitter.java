package com.mercuriusxeno.goo.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.function.BiConsumer;

/**
 * Submits one mingled fluid surface per type band (decision
 * noise-mingled-type-textures): the emitter receives a context whose every
 * vertex carries the band, and the band type's fluid sprite.
 */
@FunctionalInterface
public interface BandedSurfaceSubmitter {

    /**
     * Submits the surface one band draws.
     *
     * @param band    the type band the surface shows
     * @param emitter emits the surface through a band-carrying context on the type's sprite
     */
    void submit(TypeBand band, BiConsumer<RenderContext, TextureAtlasSprite> emitter);
}
