package com.mercuriusxeno.goo.workbench.checks;

import com.mercuriusxeno.goo.type.GooTypes;
import java.util.ArrayList;
import java.util.List;

/**
 * The sculk family, echo shards and phantom membranes carry xeno (decision xeno-ships-as-a-type-with-no-source-yet).
 */
@ValuesCheckRule(order = 7)
public final class XenoShipsAsATypeWithNoSourceYetCheck implements ValuesCheck {
    private static final String TITLE = "xeno-ships-as-a-type-with-no-source-yet";
    private static final List<String> STRANGE = List.of("minecraft:echo_shard", "minecraft:phantom_membrane");

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public List<Want> wants(DraftValues values) {
        List<String> xeno = new ArrayList<>(TypeSources.SCULK_FAMILY);
        xeno.addAll(STRANGE);
        return TypeSources.carry(values, GooTypes.XENO, xeno);
    }
}
