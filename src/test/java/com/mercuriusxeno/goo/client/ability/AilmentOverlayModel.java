package com.mercuriusxeno.goo.client.ability;

/**
 * goo_ailment_overlay's skin reads on the CPU: the vertex shader's unpacking of
 * the overlay coordinates into the pattern and the skin size, and the
 * fragment shader's pattern cell coordinate, so a test reads what the shader
 * reads off the value the layer packs.
 */
final class AilmentOverlayModel {

    /** The lines goo_ailment_overlay.vsh unpacks the overlay coordinates with, as the model mirrors them. */
    static final String[] UNPACK_LINES = {
        "const int ORDINAL_SHIFT = 12;",
        "const int WIDTH_MASK = 0xFFF;",
        "const int SHORT_MASK = 0xFFFF;",
        "int packedU = UV1.x & SHORT_MASK;",
        "int ailmentPattern = packedU >> ORDINAL_SHIFT;",
        "pattern = ailmentPattern;",
        "skinTexels = vec2(packedU & WIDTH_MASK, UV1.y & SHORT_MASK);",
    };

    /** The scale goo_ailment_overlay.fsh lays each pattern's cells at, as the model mirrors it. */
    static final String CELL_ASPECT = "vec2 cellAspect = vec2(1.0, skinTexels.y / skinTexels.x);";

    private static final int ORDINAL_SHIFT = 12;
    private static final int WIDTH_MASK = 0xFFF;
    private static final int SHORT_MASK = 0xFFFF;
    private static final int V_SHIFT = 16;

    private AilmentOverlayModel() {
    }

    /**
     * What the vertex shader reads off the overlay coordinates: UV1 arrives as two
     * signed shorts, sign-extended, which the shader masks back to sixteen bits.
     *
     * @param overlayCoords the overlay coordinates the layer packs
     * @return {pattern ordinal, skin width, skin height}
     */
    static int[] unpack(int overlayCoords) {
        int uvOneX = (short) overlayCoords;
        int uvOneY = (short) (overlayCoords >>> V_SHIFT);
        int packedU = uvOneX & SHORT_MASK;
        return new int[] {packedU >> ORDINAL_SHIFT, packedU & WIDTH_MASK, uvOneY & SHORT_MASK};
    }

    /**
     * A pattern's cell coordinate at a skin coordinate.
     *
     * @param skinU      the skin coordinate's u, 0 to 1
     * @param skinV      the skin coordinate's v, 0 to 1
     * @param skinWidth  the skin's texels across
     * @param skinHeight the skin's texels down
     * @param cells      the pattern's cells across the skin
     * @return the cell coordinate, {u, v}
     */
    static float[] cellCoord(float skinU, float skinV, int skinWidth, int skinHeight, float cells) {
        return new float[] {skinU * cells, skinV * ((float) skinHeight / skinWidth) * cells};
    }
}
