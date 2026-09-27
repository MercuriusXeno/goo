package com.mercuriusxeno.goo.client.hud;

import java.util.List;

/**
 * The slice geometry of a HUD panel background cut from the vanilla 24x24
 * effect background (decision diagnose-then-fix-aiming-panel-stretch).
 */
public final class NineSlice {

    /** The border's screen size: the texture's 3px corner, drawn unscaled. */
    public static final float BORDER = 3f;
    /** The border as a fraction of the 24px texture. */
    public static final float BORDER_UV = 3f / 24f;

    private NineSlice() {
    }

    /**
     * Cuts a rectangle into nine slices: four corners kept at the border's size,
     * four edges stretched along one axis, and the center filling the rest.
     *
     * @param rect the panel rectangle
     * @return the corners, then the top, bottom, left and right edges, then the center
     */
    public static List<Slice> of(PanelRectangle rect) {
        float x0 = rect.x();
        float y0 = rect.y();
        float x3 = x0 + rect.w();
        float y3 = y0 + rect.h();
        float x1 = x0 + BORDER;
        float x2 = x3 - BORDER;
        float y1 = y0 + BORDER;
        float y2 = y3 - BORDER;
        float b = BORDER_UV;
        float e = 1f - BORDER_UV;
        return List.of(
                new Slice(x0, y0, x1, y1, 0f, 0f, b, b),
                new Slice(x2, y0, x3, y1, e, 0f, 1f, b),
                new Slice(x0, y2, x1, y3, 0f, e, b, 1f),
                new Slice(x2, y2, x3, y3, e, e, 1f, 1f),
                new Slice(x1, y0, x2, y1, b, 0f, e, b),
                new Slice(x1, y2, x2, y3, b, e, e, 1f),
                new Slice(x0, y1, x1, y2, 0f, b, b, e),
                new Slice(x2, y1, x3, y2, e, b, 1f, e),
                new Slice(x1, y1, x2, y2, b, b, e, e));
    }

    /**
     * One quad of a panel background: where it draws and which part of the texture it reads.
     *
     * @param x0 the left screen edge
     * @param y0 the top screen edge
     * @param x1 the right screen edge
     * @param y1 the bottom screen edge
     * @param u0 the left texture edge, 0 to 1
     * @param v0 the top texture edge, 0 to 1
     * @param u1 the right texture edge, 0 to 1
     * @param v1 the bottom texture edge, 0 to 1
     */
    public record Slice(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1) {
    }
}
