package com.mercuriusxeno.goo.ability.ornate;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

/**
 * Which block a dyeable block becomes painted a color: vanilla names each
 * dyed block its color then its family, white_wool or light_blue_bed, so a
 * dyed block becomes the block of its family the new color names, and an
 * undyed terracotta, candle or shulker box becomes its family's block of it.
 * paint-recolors-dyeable-things-to-the-off-hand-dye
 */
public final class PaintedColors {

    private static final String VANILLA = "minecraft";
    private static final char JOIN = '_';
    /** The families each dye color names a block of. */
    private static final Set<String> FAMILIES = Set.of("wool", "carpet", "concrete", "concrete_powder",
            "terracotta", "glazed_terracotta", "stained_glass", "stained_glass_pane", "bed", "candle", "banner",
            "wall_banner", "shulker_box");
    /** The families whose undyed block takes a dye: terracotta, a candle and a shulker box. */
    private static final Set<String> UNDYED_FAMILIES = Set.of("terracotta", "candle", "shulker_box");

    private PaintedColors() {
    }

    /**
     * The block a block becomes painted a color.
     *
     * @param block the block's id
     * @param color the dye's color
     * @return the painted block's id, empty for a block no dye colors
     */
    public static Optional<Identifier> recolored(Identifier block, DyeColor color) {
        if (!VANILLA.equals(block.getNamespace())) {
            return Optional.empty();
        }
        return familyOf(block.getPath())
                .map(family -> Identifier.withDefaultNamespace(color.getName() + JOIN + family));
    }

    private static Optional<String> familyOf(String path) {
        if (UNDYED_FAMILIES.contains(path)) {
            return Optional.of(path);
        }
        return Arrays.stream(DyeColor.values()).map(dye -> dye.getName() + JOIN).filter(path::startsWith)
                .map(prefix -> path.substring(prefix.length())).filter(FAMILIES::contains).findFirst();
    }
}
