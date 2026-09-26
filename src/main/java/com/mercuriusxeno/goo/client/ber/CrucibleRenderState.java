package com.mercuriusxeno.goo.client.ber;

import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.client.RenderContext;
import com.mercuriusxeno.goo.client.TypeBand;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import java.util.List;

/**
 * Render state snapshot for the crucible BER: the liquid surface and the items
 * melting on it. Extracted on the main thread, consumed on the render thread.
 */
public class CrucibleRenderState extends BlockEntityRenderState {

    /** The reservoir and pool volumes; the reservoir alone drives the puddle and the level. */
    public CrucibleBasin.Volumes volumes = CrucibleBasin.Volumes.EMPTY;

    /** One band per goo type the surface shows, largest first; empty when the crucible shows none. */
    public List<TypeBand> typeBands = List.of();

    /** Ripple amplitude of the liquid surface in blocks. */
    public float rippleAmplitude = RenderContext.RESTING_RIPPLE_AMPLITUDE;

    /** The dissolving item's model, meaningful while {@link #hasHead}. */
    public final ItemStackRenderState headItem = new ItemStackRenderState();

    /** True when an item is dissolving and its model resolved. */
    public boolean hasHead;

    /** How far the head has dissolved and the color its edge glows. */
    public DissolveGlow headGlow = new DissolveGlow(0f, 0);

    /** The waiting items' models, the first {@link #waitingShown} meaningful. */
    public final ItemStackRenderState[] waitingItems = newWaitingItems();

    /** How many waiting items resolved a model to draw. */
    public int waitingShown;

    private static ItemStackRenderState[] newWaitingItems() {
        ItemStackRenderState[] items = new ItemStackRenderState[CrucibleItemLayout.WAITING_SLOTS];
        for (int i = 0; i < items.length; i++) {
            items[i] = new ItemStackRenderState();
        }
        return items;
    }
}
