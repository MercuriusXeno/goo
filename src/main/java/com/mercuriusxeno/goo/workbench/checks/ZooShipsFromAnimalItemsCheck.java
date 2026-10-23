package com.mercuriusxeno.goo.workbench.checks;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.type.GooTypes;
import java.util.ArrayList;
import java.util.List;

/**
 * A spawn egg, leather, bone and string carry zoo, while ink, slime and honeycomb, which an animal produces
 * rather than is made of, carry none; the eggs carry yore as the scutes do, and some item carries jelly
 * (decision zoo-ships-from-animal-items).
 */
@ValuesCheckRule(order = 11)
public final class ZooShipsFromAnimalItemsCheck implements ValuesCheck {
    private static final String TITLE = "zoo-ships-from-animal-items";
    private static final List<String> ANIMAL = List.of("minecraft:cow_spawn_egg", "minecraft:leather",
            "minecraft:bone", "minecraft:string");
    private static final List<String> PRODUCED = List.of("minecraft:ink_sac", "minecraft:slime_ball",
            "minecraft:honeycomb");
    private static final List<String> EGGS = List.of("minecraft:turtle_egg", "minecraft:sniffer_egg",
            "minecraft:frogspawn");
    private static final String SCUTE = "minecraft:turtle_scute";
    private static final String YORE_UNLIKE_SCUTE = "carries other yore than " + SCUTE;
    private static final String ANY_ITEM = "any item";
    private static final String NO_JELLY = "no item carries jelly";

    @Override
    public String title() {
        return TITLE;
    }

    @Override
    public List<Want> wants(DraftValues values) {
        List<Want> wants = TypeSources.all(TypeSources.carry(values, GooTypes.ZOO, ANIMAL),
                TypeSources.carryNone(values, GooTypes.ZOO, PRODUCED));
        wants.addAll(eggsUnlikeScutes(values));
        boolean jelly = values.values().values().stream().anyMatch(value -> value.exact(GooTypes.JELLY).isPositive());
        if (!jelly) {
            wants.add(new Want(ANY_ITEM, NO_JELLY));
        }
        return wants;
    }

    private static List<Want> eggsUnlikeScutes(DraftValues values) {
        GooFraction scuteYore = values.amount(SCUTE, GooTypes.YORE);
        List<Want> wants = new ArrayList<>();
        for (String egg : EGGS) {
            if (!scuteYore.isPositive() || !scuteYore.equals(values.amount(egg, GooTypes.YORE))) {
                wants.add(new Want(egg, YORE_UNLIKE_SCUTE));
            }
        }
        return wants;
    }
}
