package com.mercuriusxeno.goo.lunar;

import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The looks a monolith marker stands in, each the same one-block standing slab.
 * decision black-runic-material-families-grow-from-the-marker-drafts
 * decision alien-looks-are-block-states-never-separate-blocks
 */
public enum MarkerLook implements StringRepresentable {
    /** Smooth black with a column of runes in xeno's violets, the marker's first look. */
    XENO_RUNES,
    /** Polished black glass with a soft sheen and a lit rim, draft A. */
    POLISHED_GLASS,
    /** Black split by one thin violet seam, draft D. */
    XENO_SEAM;

    /** The look state's name on goo:monolith_marker. */
    public static final String STATE = "look";

    @Override
    public @NonNull String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Every look's state properties, as each look's creative stack places them.
     *
     * @return one map per look
     */
    public static List<Map<String, String>> looks() {
        return Arrays.stream(values()).map(look -> Map.of(STATE, look.getSerializedName())).toList();
    }
}
