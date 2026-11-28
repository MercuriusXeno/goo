package com.mercuriusxeno.goo.block.alienstructure;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;
import java.util.Locale;

/**
 * The materials the alien structure family is cut from: the refined glaze and iridescent alloy, and the four black
 * runic families grown from the monolith marker drafts.
 * decision warrens-hold-xeno-rich-alien-blocks
 * decision black-runic-material-families-grow-from-the-marker-drafts
 */
public enum AlienMaterial implements StringRepresentable {
    /** Near-black violet glaze, each block catching one soft specular glow. */
    GLAZED,
    /** Black with a faint oil-slick sheen drifting blue, violet and teal. */
    IRIDESCENT,
    /** Polished black glass, the monolith marker's draft A. */
    POLISHED_GLASS,
    /** Smooth black with runes in xeno's violets, the marker's look C. */
    XENO_RUNES,
    /** Black split by thin violet seams, as if lit from inside, the marker's draft D. */
    XENO_SEAM,
    /** Black panels in a raised grey frame, the marker's draft E. */
    FRAMED_PANELS;

    @Override
    public @NonNull String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
