package com.mercuriusxeno.goo.block.alienstructure;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;
import java.util.Locale;

/**
 * The refined materials the alien structure family is cut from, each impervious and unmarked: a glossy violet-black
 * glaze, an iridescent alloy, and machined facets.
 * decision warrens-hold-xeno-rich-alien-blocks
 */
public enum AlienMaterial implements StringRepresentable {
    /** Near-black violet glaze, each block catching one soft specular glow. */
    GLAZED,
    /** Black with a faint oil-slick sheen drifting blue, violet and teal. */
    IRIDESCENT,
    /** Machined black cut into crisp chevron facets. */
    FACETED;

    @Override
    public @NonNull String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
