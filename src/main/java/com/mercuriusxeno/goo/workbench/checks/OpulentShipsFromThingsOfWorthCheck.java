package com.mercuriusxeno.goo.workbench.checks;

import com.mercuriusxeno.goo.type.GooTypes;
import java.util.List;

/**
 * Gold, emeralds, diamonds and honey carry opulent (decision opulent-ships-from-things-of-worth).
 */
@ValuesCheckRule(order = 5)
public final class OpulentShipsFromThingsOfWorthCheck implements ValuesCheck {
    private static final String TITLE = "opulent-ships-from-things-of-worth";
    private static final List<String> WORTH = List.of("minecraft:gold_ingot", "minecraft:emerald",
            "minecraft:diamond", "minecraft:honey_bottle");

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public List<Want> wants(DraftValues values) {
        return TypeSources.carry(values, GooTypes.OPULENT, WORTH);
    }
}
