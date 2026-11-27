package com.mercuriusxeno.goo.block.alienstructure;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;
import java.util.Locale;

/**
 * The glyphs an alien glyph block carries etched into its material.
 * decision warrens-hold-xeno-rich-alien-blocks
 */
public enum GlyphMark implements StringRepresentable {
    /** A glowing violet eye. */
    EYE,
    /** A maze cut deep, lit only by its edge. */
    MAZE,
    /** A ring of alternating violet marks round a bright core. */
    RING;

    @Override
    public @NonNull String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
