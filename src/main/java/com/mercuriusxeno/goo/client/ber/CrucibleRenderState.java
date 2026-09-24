package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.client.RenderContext;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * Render state snapshot for the crucible BER. Extracted on the main thread,
 * consumed on the render thread. Only liquid surface rendering remains.
 */
public class CrucibleRenderState extends BlockEntityRenderState {

    /** Total mB remaining in the PMI pool (drives liquid level). */
    public long poolVolume;

    /** Total mB in the reservoir (drives liquid level alongside pool). */
    public long reservoirVolume;

    /** Debounce-stabilized dominant goo type from the block entity. Drives liquid surface texture. */
    @Nullable
    public ResourceKey<GooTypeDefinition> dominantType;

    /** Outgoing type during a crossfade transition. Null when not crossfading. */
    @Nullable
    public ResourceKey<GooTypeDefinition> outgoingType;

    /** Crossfade alpha [0, 1]: 0 = fully outgoing, 1 = fully incoming. */
    public float crossfadeAlpha = 1f;

    /** Ripple amplitude of the liquid surface in blocks. */
    public float rippleAmplitude = RenderContext.RESTING_RIPPLE_AMPLITUDE;
}
