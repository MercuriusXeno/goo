package com.mercuriusxeno.goo.ability.frost;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What Rime freezes a source to, by block id: water to Rime ice, and each
 * lava type to the block the molten groups pair it with, so a pack's third
 * pair freezes as lava and molten iron do
 * (decisions rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless,
 * frost-cools-molten-blocks-into-molten-cooled-blocks,
 * every-frost-freeze-turns-a-molten-iron-source-to-iron-core).
 */
class RimeFreezeTest {

    private static final Identifier WATER = Identifier.parse("minecraft:water");
    private static final Identifier RIME_ICE = Identifier.parse("goo:rime_ice");
    private static final Identifier LAVA = Identifier.parse("minecraft:lava");
    private static final Identifier MOLTEN_IRON = Identifier.parse("goo:molten_iron");
    private static final Identifier OBSIDIAN = Identifier.parse("minecraft:obsidian");
    private static final Identifier IRON_CORE = Identifier.parse("goo:iron_core");
    private static final Identifier THIRD_LIQUID = Identifier.parse("pack:molten_gold");
    private static final Identifier THIRD_COOLED = Identifier.parse("pack:gold_core");

    private final Map<String, List<Identifier>> groups = Map.of(
            "molten_blocks", List.of(LAVA, MOLTEN_IRON, THIRD_LIQUID),
            "molten_cooled_blocks", List.of(OBSIDIAN, IRON_CORE, THIRD_COOLED));

    @Test
    void waterFreezesToRimeIce() {
        assertEquals(Optional.of(RIME_ICE), RimeFreeze.frozenFormOf(groups, WATER));
    }

    @Test
    void aThirdPairFreezesItsLiquidIntoItsCooledBlock() {
        assertEquals(Optional.of(THIRD_COOLED), RimeFreeze.frozenFormOf(groups, THIRD_LIQUID));
    }

    @Test
    void whatAlreadyStandsFrozenFreezesToNothing() {
        assertEquals(List.of(Optional.empty(), Optional.empty()),
                List.of(RimeFreeze.frozenFormOf(groups, RIME_ICE), RimeFreeze.frozenFormOf(groups, OBSIDIAN)));
    }
}
