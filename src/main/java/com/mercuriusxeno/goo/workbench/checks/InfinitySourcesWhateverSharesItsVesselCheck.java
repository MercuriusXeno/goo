package com.mercuriusxeno.goo.workbench.checks;

import com.mercuriusxeno.goo.type.GooTypes;
import java.util.List;

/**
 * No item carries infinity, so no item breaks down into it (decision infinity-sources-whatever-shares-its-vessel).
 */
@ValuesCheckRule(order = 10)
public final class InfinitySourcesWhateverSharesItsVesselCheck implements ValuesCheck {
    private static final String TITLE = "infinity-sources-whatever-shares-its-vessel";

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public List<Want> wants(DraftValues values) {
        return TypeSources.noItemCarries(values, GooTypes.INFINITY);
    }
}
