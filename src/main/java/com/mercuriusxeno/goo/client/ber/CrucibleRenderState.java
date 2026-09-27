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

    /** The fraction of the day the surface shader's GameTime reads this frame, which the tiles bob by. */
    public float dayFraction;

    /** The dissolving item's model, meaningful while {@link #hasHead}. */
    public final ItemStackRenderState headItem = new ItemStackRenderState();

    /** The pose the dissolving item is drawn at: flat at rest, or easing from its item entity's pose. */
    public CrucibleHeadHandoff.ItemPose headPose = new CrucibleHeadHandoff.ItemPose(0f, 0f, 0f, 0f,
        CrucibleHeadHandoff.FLAT_TILT_DEGREES, 1f);

    /** True while the head eases in from its item entity's pose, drawn whole until it lies at rest. */
    public boolean headEasing;

    /** True when an item is dissolving and its model resolved. */
    public boolean hasHead;

    /** How far the head has dissolved and the goo type layers its edge glows in. */
    public DissolveGlow headGlow = new DissolveGlow(0f, List.of());

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
