package com.mercuriusxeno.goo.client.ability;

/**
 * A spray ability's visual, drawn from the cone its caster pours through.
 * decision spray-abilities-render-procedurally-bound-to-their-cone
 */
@FunctionalInterface
public interface ConeVisual {

    /**
     * Hands the visual the cone its ability pours through this frame; the visual draws its frame from that cone
     * alone, and a frame it is handed none it reads as the pour having stopped.
     *
     * @param cone the cone poured through
     */
    void pour(SprayCone cone);
}
