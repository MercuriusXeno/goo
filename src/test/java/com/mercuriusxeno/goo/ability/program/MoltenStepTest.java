package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
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
    private static final Identifier HOT_ROCKS = Identifier.parse("pack:hot_rocks");
    private static final Map<Identifier, List<String>> MELTS_INTO = Map.of(
            LAVA, List.of("#pack:hot_rocks", "#pack_rocks"),
            MOLTEN_IRON, List.of("goo:iron_core"));
    private static final Map<String, List<Identifier>> GROUPS = Map.of("pack_rocks", List.of(PACK_ROCK));
    /** The pack's hot rocks tag holds stone alone. */
    private static final Predicate<Identifier> STONE_IN_HOT_ROCKS = tag -> tag.equals(HOT_ROCKS);

    private static Optional<Identifier> meltedFormOf(Identifier block, Set<Identifier> tagsHolding) {
        return MoltenStep.meltedFormOf(MELTS_INTO, block, tagsHolding::contains, GROUPS);
    }

    @Test
    void aBlockTheTagHoldsMeltsIntoLava() {
        assertEquals(Optional.of(LAVA), MoltenStep.meltedFormOf(MELTS_INTO, STONE, STONE_IN_HOT_ROCKS, GROUPS));
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

    /**
     * The shipped blaze_molten.json melts obsidian and magma into lava and iron core into molten iron, and stone
     * into nothing; every block reads as standing in every tag, so a #tag left in the shipped map would melt stone
     * (decision molten-melts-only-obsidian-magma-and-iron-core-one-layer-each).
     */
    @Nested
    class Shipped {

        private final Map<Identifier, List<String>> shipped =
                MoltenStep.in(AbilityJson.decode("blaze_molten").behaviors()).orElseThrow().meltsInto();

        private Optional<Identifier> shippedMeltOf(String block) {
            return MoltenStep.meltedFormOf(shipped, Identifier.parse(block), tag -> true, Map.of());
        }

        @Test
        void obsidianAndMagmaMeltIntoLavaAndIronCoreIntoMoltenIron() {
            assertEquals(List.of(Optional.of(LAVA), Optional.of(LAVA), Optional.of(MOLTEN_IRON)),
                    List.of(shippedMeltOf("minecraft:obsidian"), shippedMeltOf("minecraft:magma_block"),
                            shippedMeltOf("goo:iron_core")));
        }

        @Test
        void stoneMeltsIntoNothing() {
            assertEquals(Optional.empty(), shippedMeltOf("minecraft:stone"));
        }
    }
}
