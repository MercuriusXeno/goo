package com.mercuriusxeno.goo.ability.program;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Molten's melts_into map read by block id: each key is the block a melt leaves, and its array names what melts into
 * it by loose block, #group or #tag (decision molten-melts-by-a-meltable-group-and-its-parallel-melted-group).
 */
class MoltenStepTest {

    private static final Identifier LAVA = Identifier.parse("minecraft:lava");
    private static final Identifier MOLTEN_IRON = Identifier.parse("goo:molten_iron");
    private static final Identifier STONE = Identifier.parse("minecraft:stone");
    private static final Identifier IRON_CORE = Identifier.parse("goo:iron_core");
    private static final Identifier PACK_ROCK = Identifier.parse("pack:rock");
    private static final Identifier DIRT = Identifier.parse("minecraft:dirt");
    private static final Identifier MELTABLE = Identifier.parse("goo:meltable");
    private static final Map<Identifier, List<String>> MELTS_INTO = Map.of(
            LAVA, List.of("#goo:meltable", "#pack_rocks"),
            MOLTEN_IRON, List.of("goo:iron_core"));
    private static final Map<String, List<Identifier>> GROUPS = Map.of("pack_rocks", List.of(PACK_ROCK));
    /** The meltable tag holds stone alone. */
    private static final Predicate<Identifier> STONE_IN_MELTABLE = tag -> tag.equals(MELTABLE);

    private static Optional<Identifier> meltedFormOf(Identifier block, Set<Identifier> tagsHolding) {
        return MoltenStep.meltedFormOf(MELTS_INTO, block, tagsHolding::contains, GROUPS);
    }

    @Test
    void aBlockTheTagHoldsMeltsIntoLava() {
        assertEquals(Optional.of(LAVA), MoltenStep.meltedFormOf(MELTS_INTO, STONE, STONE_IN_MELTABLE, GROUPS));
    }

    @Test
    void aLooseBlockMeltsIntoItsKey() {
        assertEquals(Optional.of(MOLTEN_IRON), meltedFormOf(IRON_CORE, Set.of()));
    }

    @Test
    void aMemberOfAGroupTheArrayNamesMeltsIntoItsKey() {
        assertEquals(Optional.of(LAVA), meltedFormOf(PACK_ROCK, Set.of()));
    }

    @Test
    void aBlockNoArrayNamesMeltsIntoNothing() {
        assertEquals(Optional.empty(), meltedFormOf(DIRT, Set.of()));
    }
}
