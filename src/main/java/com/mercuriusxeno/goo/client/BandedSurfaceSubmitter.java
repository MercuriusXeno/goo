package com.mercuriusxeno.goo.client;

import java.util.List;
import java.util.function.Consumer;

/**
 * Submits one mingled fluid surface carrying every type band it holds
 * (decision one-pass-blends-every-type): the shader blends the bands itself,
 * so the emitter draws the surface once, at the surface's own bounds, with
 * sprite-relative UVs the shader maps into each band type's sprite.
 */
@FunctionalInterface
public interface BandedSurfaceSubmitter {

    /**
     * Submits the surface every band draws on.
     *
     * @param bands   the type bands in the fixed type order
     * @param emitter emits the surface once, its UVs spanning 0 to 1 across a whole sprite
     */
    void submit(List<TypeBand> bands, Consumer<RenderContext> emitter);
}
