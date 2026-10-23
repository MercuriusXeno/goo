package com.mercuriusxeno.goo.workbench.checks;

import com.mercuriusxeno.goo.type.GooTypes;
import java.util.List;

/**
 * No item carries quantum, which comes from goo ratios alone (decision quantum-ships-from-goo-ratios).
 */
@ValuesCheckRule(order = 9)
public final class QuantumShipsFromGooRatiosCheck implements ValuesCheck {
    private static final String TITLE = "quantum-ships-from-goo-ratios";

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public List<Want> wants(DraftValues values) {
        return TypeSources.noItemCarries(values, GooTypes.QUANTUM);
    }
}
