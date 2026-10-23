package com.mercuriusxeno.goo.workbench.checks;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import java.util.ArrayList;
import java.util.List;

/**
 * The shapes a type-source check takes: these items carry a type, these carry none of it, no item carries it.
 */
final class TypeSources {
    static final List<String> SCULK_FAMILY = List.of("minecraft:sculk", "minecraft:sculk_vein",
            "minecraft:sculk_sensor", "minecraft:sculk_shrieker", "minecraft:sculk_catalyst");
    private static final String CARRIES_NO = "carries no ";
    private static final String CARRIES = "carries ";
    private static final String WANTS_NONE = ", which it should not";
    private static final String SPACE = " ";

    private TypeSources() {
    }

    /**
     * @param values the draft's values
     * @param type   the type each item carries
     * @param items  the items
     * @return each item carrying none of the type
     */
    static List<Want> carry(DraftValues values, ResourceKey<GooTypeDefinition> type, List<String> items) {
        List<Want> wants = new ArrayList<>();
        for (String item : items) {
            if (!values.amount(item, type).isPositive()) {
                wants.add(new Want(item, CARRIES_NO + GooTypes.id(type)));
            }
        }
        return wants;
    }

    /**
     * @param values the draft's values
     * @param type   the type no item listed carries
     * @param items  the items
     * @return each item carrying some of the type
     */
    static List<Want> carryNone(DraftValues values, ResourceKey<GooTypeDefinition> type, List<String> items) {
        List<Want> wants = new ArrayList<>();
        for (String item : items) {
            GooFraction amount = values.amount(item, type);
            if (amount.isPositive()) {
                wants.add(new Want(item, CARRIES + amount + SPACE + GooTypes.id(type) + WANTS_NONE));
            }
        }
        return wants;
    }

    /**
     * @param values the draft's values
     * @param type   the type no item carries
     * @return each item the draft values carrying some of the type
     */
    static List<Want> noItemCarries(DraftValues values, ResourceKey<GooTypeDefinition> type) {
        return carryNone(values, type, values.values().keySet().stream().map(Object::toString).sorted().toList());
    }

    /**
     * @param parts the wants of each part of a check
     * @return every part's wants, in order
     */
    @SafeVarargs
    static List<Want> all(List<Want>... parts) {
        List<Want> wants = new ArrayList<>();
        for (List<Want> part : parts) {
            wants.addAll(part);
        }
        return wants;
    }
}
