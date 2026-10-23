package com.mercuriusxeno.goo.workbench.checks;

import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Every type an item resolves is a bundled type, so a retired type values nothing
 * (decision type-source-checks-are-rules-that-gate-promote).
 */
@ValuesCheckRule(order = 12)
public final class NoItemResolvesARetiredTypeCheck implements ValuesCheck {
    private static final String TITLE = "no-item-resolves-a-retired-type";
    private static final String RESOLVES = "resolves the retired type ";
    private static final String TYPE_SEPARATOR = ", ";

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public List<Want> wants(DraftValues values) {
        List<Want> wants = new ArrayList<>();
        for (Map.Entry<Identifier, GooValue> entry : values.values().entrySet()) {
            String retired = entry.getValue().exactAll().entrySet().stream()
                    .filter(term -> !term.getValue().isZero() && !GooTypes.BUNDLED.contains(term.getKey()))
                    .map(term -> term.getKey().identifier().toString()).sorted()
                    .collect(Collectors.joining(TYPE_SEPARATOR));
            if (!retired.isEmpty()) {
                wants.add(new Want(entry.getKey().toString(), RESOLVES + retired));
            }
        }
        wants.sort(Comparator.comparing(Want::item));
        return wants;
    }
}
