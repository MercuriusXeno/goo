package com.mercuriusxeno.goo.client.radial;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * One item icon a locked petal draws cut at its border: the stack the GUI
 * renders into a picture of its own, and the quads that picture draws
 * across, each corner carrying where it sits within the item's square, so
 * only the part of the item under the petal's face shows.
 * decision icons-slide-in-from-behind-the-tip
 *
 * @param stack       the item's stack
 * @param x0          the item square's left edge
 * @param y0          the item square's top edge
 * @param cut         the quads' corners on screen, four per quad, u and v 0 to 1 across the square
 * @param pose        the GUI pose at submit time
 * @param scissorArea the scissor in force at submit time
 */
record CutItemRenderState(ItemStack stack, int x0, int y0, List<PetalRenderState.ScreenVertex> cut,
                          Matrix3x2f pose, @Nullable ScreenRectangle scissorArea)
        implements PictureInPictureRenderState {

    /** The scale an item model renders at into a 16 pixel square, as the GUI's own items do. */
    private static final float ITEM_SCALE = 16.0f;

    @Override
    public int x1() {
        return x0 + RadialWheelRenderer.ITEM_ICON_SIZE;
    }

    @Override
    public int y1() {
        return y0 + RadialWheelRenderer.ITEM_ICON_SIZE;
    }

    @Override
    public float scale() {
        return ITEM_SCALE;
    }

    @Override
    public @Nullable ScreenRectangle bounds() {
        ScreenRectangle square = new ScreenRectangle(x0, y0, RadialWheelRenderer.ITEM_ICON_SIZE,
                RadialWheelRenderer.ITEM_ICON_SIZE).transformMaxBounds(pose);
        return scissorArea != null ? scissorArea.intersection(square) : square;
    }
}
