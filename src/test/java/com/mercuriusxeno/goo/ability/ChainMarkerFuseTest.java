package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.AbilityDefinition.ChainConfig;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A chain marker's fuse and stack ceiling hold the values its ability JSON
 * names through placement, a declared throw and every stack, for the
 * unstable abilities and for a datapack type with no chain block
 * (decision diagnose-then-fix-fuse-and-cost).
 */
class ChainMarkerFuseTest {

    /** Ticks the fuse burns between the events of a sequence. */
    private static final int BURN_TICKS = 3;

    private static ChainConfig chainOf(String resource) throws IOException {
        try (InputStream in = ChainMarkerFuseTest.class.getClassLoader().getResourceAsStream(resource)) {
            assertNotNull(in, "Classpath holds no " + resource);
            JsonElement json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(message -> new IllegalStateException(resource + ": " + message)).chain();
        }
    }

    private static void burn(ChainMarkerFuse fuse) {
        for (int tick = 0; tick < BURN_TICKS; tick++) {
            fuse.countDown();
        }
    }

    @ParameterizedTest
    @CsvSource({
            "data/goo/goo_abilities/unstable_proximity_mine.json, -1, 6",
            "data/goo/goo_abilities/unstable_timed_bomb.json, 60, 8",
            "data/goo/goo_abilities/unstable_instant_detonation.json, 1, 4",
            "datapacks/seventeenth_goo_type/data/gootest/goo_abilities/seventeenth_probe.json, 30, 1",
    })
    void resetFuse(String resource, int jsonFuse, int jsonMaxStacks) throws IOException {
        ChainConfig chain = chainOf(resource);
        ChainMarkerFuse fuse = new ChainMarkerFuse();

        fuse.arm(chain);
        assertEquals(jsonFuse, fuse.fuseRemaining(), "fuse at placement");
        assertEquals(jsonMaxStacks, fuse.maxStacks(), "stack ceiling at placement");

        burn(fuse);
        fuse.resetFuse(chain);
        assertEquals(jsonFuse, fuse.fuseRemaining(), "fuse after a declared throw");

        for (int stack = 2; stack <= jsonMaxStacks; stack++) {
            burn(fuse);
            assertTrue(fuse.addStack(chain), "stack " + stack + " under the ceiling");
            fuse.resetFuse(chain);
            assertEquals(jsonFuse, fuse.fuseRemaining(), "fuse after stack " + stack);
            assertEquals(stack, fuse.stackCount());
        }
        assertFalse(fuse.addStack(chain), "a stack past the JSON ceiling");
        assertEquals(jsonMaxStacks, fuse.stackCount());
    }
}
