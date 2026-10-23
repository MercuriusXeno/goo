package com.mercuriusxeno.goo.workbench.checks;

import com.mercuriusxeno.goo.type.GooTypes;
import java.util.List;

/**
 * Slime balls, frogspawn, froglights, honeycomb and wet sponges carry weird, and the sculk family carries none
 * (decision weird-ships-from-slime-and-sculk).
 */
@ValuesCheckRule(order = 6)
public final class WeirdShipsFromSlimeAndSculkCheck implements ValuesCheck {
    private static final String TITLE = "weird-ships-from-slime-and-sculk";
    private static final List<String> WEIRD = List.of("minecraft:slime_ball", "minecraft:frogspawn",
            "minecraft:ochre_froglight", "minecraft:honeycomb", "minecraft:wet_sponge");

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public List<Want> wants(DraftValues values) {
        return TypeSources.all(TypeSources.carry(values, GooTypes.WEIRD, WEIRD),
                TypeSources.carryNone(values, GooTypes.WEIRD, TypeSources.SCULK_FAMILY));
    }
}
