package com.mercuriusxeno.goo.data;

import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.recipe;
import static com.mercuriusxeno.goo.data.TestRecipeBuilder.slot;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the value registry, seeded from the shipped base values and deriving the compass the recovery compass
 * reads its value through, gives dire goo to ominous and deadly things, more to the strong rare ones than to a trial
 * key, and none to a bone.
 * decision dire-is-found-in-ominous-and-deadly-things
 * decision dire-costs-and-values-are-spitballed-then-adjusted
 */
class DireSourcesTest {

    private static final String BASE_VALUES = "/data/goo/goo_values/base_values.json";
    private static final List<String> DIRE_SOURCES = List.of(
            "ominous_bottle", "ominous_trial_key", "trial_key", "heavy_core", "trial_spawner", "vault",
            "totem_of_undying", "evoker_spawn_egg", "pillager_spawn_egg", "vindicator_spawn_egg",
            "wither_skeleton_skull", "wither_rose", "nether_star",
            "zombie_head", "skeleton_skull", "creeper_head", "piglin_head", "dragon_head",
            "recovery_compass");
    private static final List<String> STRONG_RARE_SOURCES = List.of(
            "heavy_core", "nether_star", "totem_of_undying", "ominous_trial_key");
    private static GooValueRegistry registry;

    @BeforeAll
    static void deriveTheShippedValues() throws IOException {
        registry = new GooValueRegistry();
        try (InputStream in = DireSourcesTest.class.getResourceAsStream(BASE_VALUES)) {
            assertNotNull(in, BASE_VALUES + " is not on the classpath");
            registry.parseBaseValues(in);
        }
        registry.publishBaseAsEffective();
        registry.deriveFromRecipeInputs(List.of(
                recipe("minecraft:iron_ingot", 1, nine("minecraft:iron_nugget")),
                recipe("minecraft:compass", 1, slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"),
                        slot("minecraft:iron_ingot"), slot("minecraft:iron_ingot"), slot("minecraft:redstone"))),
                false);
    }

    @ParameterizedTest
    @MethodSource("direSources")
    void ominousOrDeadlyThingYieldsDire(String item) {
        GooFraction dire = direOf(item);
        assertTrue(dire.isPositive(), item + " yields no dire: " + dire);
    }

    @ParameterizedTest
    @MethodSource("strongRareSources")
    void strongRareThingYieldsMoreDireThanATrialKey(String item) {
        GooFraction strong = direOf(item);
        GooFraction trialKey = direOf("trial_key");
        assertTrue(strong.compareTo(trialKey) > 0, item + " " + strong + " is not above trial key " + trialKey);
    }

    @Test
    void boneYieldsNoDire() {
        assertTrue(direOf("bone").isZero(), "bone yields dire");
    }

    static Stream<String> direSources() {
        return DIRE_SOURCES.stream();
    }

    static Stream<String> strongRareSources() {
        return STRONG_RARE_SOURCES.stream();
    }

    private static GooFraction direOf(String item) {
        GooValue value = registry.table().lookup(Identifier.withDefaultNamespace(item));
        assertNotNull(value, "the registry values no " + item);
        return value.exact(GooTypes.bundled("dire"));
    }

    private static String[][] nine(String item) {
        String[][] slots = new String[9][];
        Arrays.fill(slots, slot(item));
        return slots;
    }
}
