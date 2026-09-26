package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;

/**
 * A host striking the blocks around its anchor layer by layer (capability
 * {@link HostCapability#LAYER_WALK}). The verbs take the registered names
 * the step's data holds, and the host resolves each to its delegate.
 */
public interface LayerWalkHost extends StepHost {

    /**
     * Applies a block effect to one cell of a layer.
     *
     * @param effect the block effect's registered name
     * @param cell   the block position
     * @return true when the effect changed the block
     */
    boolean applyBlockEffect(String effect, BlockPos cell);

    /**
     * Plays the preview of a layer about to be struck.
     *
     * @param visuals the layer visuals' registered name
     * @param layer   the layer index, from zero
     */
    void previewLayer(String visuals, int layer);

    /**
     * Plays the fx of a layer just struck.
     *
     * @param visuals   the layer visuals' registered name
     * @param audio     the layer audio's registered name
     * @param layer     the layer index, from zero
     * @param destroyed how many cells the effect changed
     */
    void strikeLayerFx(String visuals, String audio, int layer, int destroyed);

    /**
     * Reports how many layers the walk has struck, which the host's
     * renderer reads to shrink its outline.
     *
     * @param layers the struck layer count
     */
    void reportMinedLayers(int layers);
}
