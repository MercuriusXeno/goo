package com.mercuriusxeno.goo.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.function.BiConsumer;

/**
 * Submits one lifted fluid surface per type band (decision
 * noise-mingled-type-textures), for the canister and the slotted containers
 * until they blend in one pass too: the emitter receives a context whose
 * every vertex names a one-row table holding the band, and the band type's
 * fluid sprite, on which it writes atlas UVs.
 */
@FunctionalInterface
public interface LayeredSurfaceSubmitter {

    /**
     * Submits the surface one band draws.
     *
     * @param band    the type band the surface shows
     * @param emitter emits the surface through a band-carrying context on the type's sprite
     */
    void submit(TypeBand band, BiConsumer<RenderContext, TextureAtlasSprite> emitter);
}
