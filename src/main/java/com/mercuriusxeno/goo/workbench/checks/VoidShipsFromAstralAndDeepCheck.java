package com.mercuriusxeno.goo.workbench.checks;

import com.mercuriusxeno.goo.type.GooTypes;
import java.util.List;

/**
 * Chorus fruit, chorus plants and shulker shells carry void (decision void-ships-from-astral-and-deep).
 */
@ValuesCheckRule(order = 8)
public final class VoidShipsFromAstralAndDeepCheck implements ValuesCheck {
    private static final String TITLE = "void-ships-from-astral-and-deep";
    private static final List<String> VOID = List.of("minecraft:chorus_fruit", "minecraft:chorus_plant",
            "minecraft:shulker_shell");

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public List<Want> wants(DraftValues values) {
        return TypeSources.carry(values, GooTypes.VOID, VOID);
    }
}
