package com.mercuriusxeno.goo.workbench.checks;

import com.mercuriusxeno.goo.type.GooTypes;
import java.util.List;

/**
 * Slime balls and gunpowder carry kinetic (decision kinetic-ships-from-slime-and-gunpowder).
 */
@ValuesCheckRule(order = 3)
public final class KineticShipsFromSlimeAndGunpowderCheck implements ValuesCheck {
    private static final String TITLE = "kinetic-ships-from-slime-and-gunpowder";
    private static final List<String> KINETIC = List.of("minecraft:slime_ball", "minecraft:gunpowder");

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public List<Want> wants(DraftValues values) {
        return TypeSources.carry(values, GooTypes.KINETIC, KINETIC);
    }
}
