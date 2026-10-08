package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.CuboidBounds;
import java.util.ArrayList;
import java.util.List;

/**
 * The shape of a goo copy sagging in place as it melts: whole it is the
 * block or body it copies, and as it melts it slumps lower, its foot spreads
 * and its top rounds, drawn as stacked layers narrowing toward the top.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class GooSag {

    /** Layers stacked to round the top. */
    static final int LAYERS = 4;
    /** The share of its height a fully melted copy loses. */
    static final float SLUMP = 0.6f;
    /** How far a fully melted copy's foot spreads past its footprint, as a share of its width. */
    static final float SPREAD = 0.25f;
    /** How far a fully melted copy's top layer narrows, as a share of its width. */
    static final float ROUND = 0.45f;
    private static final float HALF = 0.5f;

    private GooSag() {
    }

    /**
     * The layers of a sagging copy, in coordinates whose origin is the copy's
     * footprint corner, its footprint centered where the original stood.
     *
     * @param width  the original's width along x
     * @param depth  the original's depth along z
     * @param height the original's height
     * @param melt   how far it has melted, 0 whole to 1 slumped
     * @return the layers, bottom first
     */
    public static List<CuboidBounds> layers(float width, float depth, float height, float melt) {
        float layerHeight = height * (1f - SLUMP * melt) / LAYERS;
        float foot = 1f + SPREAD * melt;
        List<CuboidBounds> layers = new ArrayList<>(LAYERS);
        for (int layer = 0; layer < LAYERS; layer++) {
            float up = (float) layer / (LAYERS - 1);
            float scale = foot * (1f - ROUND * melt * up * up);
            float halfWidth = width * scale * HALF;
            float halfDepth = depth * scale * HALF;
            layers.add(new CuboidBounds(width * HALF - halfWidth, width * HALF + halfWidth,
                    depth * HALF - halfDepth, depth * HALF + halfDepth,
                    layer * layerHeight, (layer + 1) * layerHeight));
        }
        return layers;
    }
}
