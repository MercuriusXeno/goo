package com.mercuriusxeno.goo.ability.ornate;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.DyeColor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Covers which block each dyeable block becomes painted a color: its own
 * family's block of that color, an undyed terracotta, candle or shulker box
 * included, and nothing for a block no dye colors.
 * paint-recolors-dyeable-things-to-the-off-hand-dye
 */
class PaintedColorsTest {

    @ParameterizedTest
    @CsvSource({
        "white_wool, red_wool",
        "white_carpet, red_carpet",
        "blue_concrete, red_concrete",
        "lime_concrete_powder, red_concrete_powder",
        "terracotta, red_terracotta",
        "orange_terracotta, red_terracotta",
        "light_blue_glazed_terracotta, red_glazed_terracotta",
        "white_stained_glass, red_stained_glass",
        "light_gray_stained_glass_pane, red_stained_glass_pane",
        "white_bed, red_bed",
        "candle, red_candle",
        "green_candle, red_candle",
        "white_banner, red_banner",
        "white_wall_banner, red_wall_banner",
        "shulker_box, red_shulker_box",
        "purple_shulker_box, red_shulker_box"
    })
    void aDyeableBlockBecomesItsFamilysRedBlock(String block, String painted) {
        assertEquals(Optional.of(Identifier.withDefaultNamespace(painted)),
                PaintedColors.recolored(Identifier.withDefaultNamespace(block), DyeColor.RED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"stone", "glass", "glass_pane", "oak_planks", "candle_cake", "red_mushroom", "white_tulip"})
    void aBlockNoDyeColorsBecomesNothing(String block) {
        assertEquals(Optional.empty(), PaintedColors.recolored(Identifier.withDefaultNamespace(block), DyeColor.RED));
    }

    @ParameterizedTest
    @ValueSource(strings = {"goo:white_wool", "othermod:white_wool"})
    void aModdedBlockBecomesNothing(String block) {
        assertEquals(Optional.empty(), PaintedColors.recolored(Identifier.parse(block), DyeColor.RED));
    }
}
