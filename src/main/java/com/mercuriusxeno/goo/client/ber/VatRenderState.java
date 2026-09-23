package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.client.RenderContext;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.resources.ResourceKey;
import org.jspecify.annotations.Nullable;

/**
 * Render state snapshot for the vat BER. Captures the dominant goo type,
 * fill fraction, and stack geometry for the render thread. When vats are
 * vertically stacked, fill and dominant type are computed from the merged
 * stack contents so all vats in the column render one contiguous fluid body.
 */
public class VatRenderState extends BlockEntityRenderState {

    /** Dominant goo type across the stack, or null if empty. */
    public @Nullable ResourceKey<GooTypeDefinition> dominantType;

    /** Stack-wide fill fraction in [0, 1] (total volume / total capacity). */
    public float fillFraction;

    /** True if another vat is directly above (interior ceiling extends to 1.0). */
    public boolean vatAbove;

    /** True if another vat is directly below (interior floor extends to 0.0). */
    public boolean vatBelow;

    /** Total number of vats in the vertical stack (1 = solo). */
    public int stackSize;

    /** This vat's zero-based index from the bottom of the stack. */
    public int indexFromBottom;

    /** Stream type (non-null when goo is actively flowing in via cap gasket). */
    public @Nullable ResourceKey<GooTypeDefinition> streamType;

    /** Stream rate in mB/tick (used for stream width calculation). */
    public float streamRate;

    /** Ripple amplitude of the surface in blocks, the same for every vat in a stack. */
    public float rippleAmplitude = RenderContext.RESTING_RIPPLE_AMPLITUDE;

    /** Animation time for sin-wave pulsing. */
    public float animationTime;
}
