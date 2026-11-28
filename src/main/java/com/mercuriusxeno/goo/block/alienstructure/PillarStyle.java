package com.mercuriusxeno.goo.block.alienstructure;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;
import java.util.Locale;

/**
 * The styles an alien pillar is carved in.
 * decision warrens-hold-xeno-rich-alien-blocks
 */
public enum PillarStyle implements StringRepresentable {
    /** Vertical flutes under a lit capital band. */
    FLUTED,
    /** A recessed band of violet studs, a rune ring on its end. */
    BANDED,
    /** A channel of xeno runes, in the monolith marker's style. */
    RUNED;

    @Override
    public @NonNull String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
