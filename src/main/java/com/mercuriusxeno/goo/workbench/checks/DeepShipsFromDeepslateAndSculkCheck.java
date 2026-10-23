package com.mercuriusxeno.goo.workbench.checks;

import com.mercuriusxeno.goo.type.GooTypes;
import java.util.ArrayList;
import java.util.List;

/**
 * Deepslate, its variants and ores, and the sculk family carry deep; iron ore carries none, and deepslate iron
 * ore holds the same metal as iron ore (decision deep-ships-from-deepslate-and-sculk).
 */
@ValuesCheckRule(order = 2)
public final class DeepShipsFromDeepslateAndSculkCheck implements ValuesCheck {
    private static final String TITLE = "deep-ships-from-deepslate-and-sculk";
    private static final List<String> DEEPSLATE = List.of("minecraft:deepslate", "minecraft:cobbled_deepslate",
            "minecraft:polished_deepslate", "minecraft:chiseled_deepslate", "minecraft:deepslate_bricks",
            "minecraft:cracked_deepslate_bricks", "minecraft:deepslate_tiles", "minecraft:cracked_deepslate_tiles",
            "minecraft:infested_deepslate", "minecraft:deepslate_iron_ore", "minecraft:deepslate_diamond_ore");
    private static final String IRON_ORE = "minecraft:iron_ore";
    private static final String DEEPSLATE_IRON_ORE = "minecraft:deepslate_iron_ore";
    private static final String METAL_DIFFERS = "holds other metal than " + IRON_ORE;

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public List<Want> wants(DraftValues values) {
        List<String> deep = new ArrayList<>(DEEPSLATE);
        deep.addAll(TypeSources.SCULK_FAMILY);
        List<Want> wants = TypeSources.all(TypeSources.carry(values, GooTypes.DEEP, deep),
                TypeSources.carryNone(values, GooTypes.DEEP, List.of(IRON_ORE)));
        if (!values.amount(IRON_ORE, GooTypes.METAL).equals(values.amount(DEEPSLATE_IRON_ORE, GooTypes.METAL))) {
            wants.add(new Want(DEEPSLATE_IRON_ORE, METAL_DIFFERS));
        }
        return wants;
    }
}
