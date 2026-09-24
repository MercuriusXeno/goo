package com.mercuriusxeno.goo.lab;

/**
 * One block the lab build sets: where, which state, and the text a sign
 * placement carries.
 *
 * @param offset     the position relative to the lab origin
 * @param blockState the block state in command syntax, such as {@code minecraft:oak_sign[rotation=8]}
 * @param signText   the text written on a sign placement's front, empty for every other block
 */
public record LabPlacement(LabOffset offset, String blockState, String signText) {

    /**
     * Text of a placement that is not a sign.
     */
    private static final String NO_TEXT = "";

    /**
     * Creates a placement that carries no sign text.
     *
     * @param offset     the position relative to the lab origin
     * @param blockState the block state in command syntax
     * @return the placement
     */
    public static LabPlacement block(LabOffset offset, String blockState) {
        return new LabPlacement(offset, blockState, NO_TEXT);
    }

    /**
     * Answers whether this placement writes text onto a sign.
     *
     * @return true when the sign text is non-empty
     */
    public boolean isSign() {
        return !signText.isEmpty();
    }
}
