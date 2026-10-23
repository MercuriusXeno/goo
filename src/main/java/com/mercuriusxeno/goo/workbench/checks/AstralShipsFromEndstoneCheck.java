package com.mercuriusxeno.goo.workbench.checks;

import com.mercuriusxeno.goo.type.GooTypes;
import java.util.List;

/**
 * End stone and its bricks carry astral beside the ender they always carried (decision astral-ships-from-endstone).
 */
@ValuesCheckRule(order = 4)
public final class AstralShipsFromEndstoneCheck implements ValuesCheck {
    private static final String TITLE = "astral-ships-from-endstone";
    private static final List<String> END_STONE = List.of("minecraft:end_stone", "minecraft:end_stone_bricks");

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public List<Want> wants(DraftValues values) {
        return TypeSources.all(TypeSources.carry(values, GooTypes.ASTRAL, END_STONE),
                TypeSources.carry(values, GooTypes.ENDER, END_STONE));
    }
}
