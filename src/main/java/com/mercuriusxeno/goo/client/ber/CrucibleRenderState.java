package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import java.util.List;

/**
 * Render state snapshot for the crucible BER. Extracted on the main thread,
 * consumed on the render thread. Only liquid surface rendering remains.
 */
public class CrucibleRenderState extends BlockEntityRenderState {

    /** The melted goo the surface stands for, in mB; drives the puddle and the level. */
    public long surfaceVolume;

    /** One band per goo type the surface shows, largest first; empty when the crucible shows none. */
    public List<TypeBand> typeBands = List.of();

    /** Ripple amplitude of the liquid surface in blocks. */
    public float rippleAmplitude = RenderContext.RESTING_RIPPLE_AMPLITUDE;
}
