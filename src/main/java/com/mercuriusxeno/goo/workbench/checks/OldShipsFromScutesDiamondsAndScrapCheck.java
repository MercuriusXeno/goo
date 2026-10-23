package com.mercuriusxeno.goo.workbench.checks;

import com.mercuriusxeno.goo.type.GooTypes;
import java.util.List;

/**
 * Diamond, netherite scrap, the scutes, a spawn egg and the old, hard-to-come-by items whose retired type's term
 * moved carry yore, and dirt carries none (decision old-ships-from-scutes-diamonds-and-scrap).
 */
@ValuesCheckRule(order = 1)
public final class OldShipsFromScutesDiamondsAndScrapCheck implements ValuesCheck {
    private static final String TITLE = "old-ships-from-scutes-diamonds-and-scrap";
    private static final List<String> OLD = List.of("minecraft:diamond", "minecraft:netherite_scrap",
            "minecraft:turtle_scute", "minecraft:armadillo_scute", "minecraft:allay_spawn_egg",
            "minecraft:heart_of_the_sea", "minecraft:echo_shard", "minecraft:elytra", "minecraft:dragon_egg",
            "minecraft:nautilus_shell");
    private static final List<String> MUNDANE = List.of("minecraft:dirt");

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public List<Want> wants(DraftValues values) {
        return TypeSources.all(TypeSources.carry(values, GooTypes.YORE, OLD),
                TypeSources.carryNone(values, GooTypes.YORE, MUNDANE));
    }
}
