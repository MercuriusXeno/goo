package com.mercuriusxeno.goo.block.alienstructure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every look the alien structure blocks carry, as the block state properties each look's stack places: the creative
 * tab lists one stack per look from here, and a test reads the looks without the game's registries.
 * decision alien-looks-are-block-states-never-separate-blocks
 */
public final class AlienLooks {

    /** The material state's name on every alien structure block. */
    public static final String MATERIAL = "material";
    /** The carving style state's name on alien_pillar. */
    public static final String STYLE = "style";

    private AlienLooks() {
    }

    /**
     * The looks of alien_stone and alien_tile: one per material.
     *
     * @return each look's state properties
     */
    public static List<Map<String, String>> materialLooks() {
        List<Map<String, String>> looks = new ArrayList<>();
        for (AlienMaterial material : AlienMaterial.values()) {
            looks.add(Map.of(MATERIAL, material.getSerializedName()));
        }
        return looks;
    }

    /**
     * The looks of alien_glyph: one per material, each material bearing its own rune.
     * decision alien-looks-are-block-states-never-separate-blocks
     *
     * @return each look's state properties
     */
    public static List<Map<String, String>> glyphLooks() {
        return materialLooks();
    }

    /**
     * The looks of alien_pillar: one per material and carving style.
     *
     * @return each look's state properties
     */
    public static List<Map<String, String>> pillarLooks() {
        List<Map<String, String>> looks = new ArrayList<>();
        for (AlienMaterial material : AlienMaterial.values()) {
            for (PillarStyle style : PillarStyle.values()) {
                Map<String, String> look = new LinkedHashMap<>();
                look.put(MATERIAL, material.getSerializedName());
                look.put(STYLE, style.getSerializedName());
                looks.add(look);
            }
        }
        return looks;
    }
}
