package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * goo:void_crystal_cluster places through goo:dark_side_count alone, decoded from its placed feature's JSON: some
 * attempts on a dark-side chunk and none on a light-side chunk, so void crystal grows on the dark side only.
 * decision void-crystals-spawn-on-the-moon
 */
class VoidCrystalPlacementTest {

    private static final String PLACED_FEATURE = "/data/goo/worldgen/placed_feature/void_crystal_cluster.json";
    private static final String DARK_SIDE_COUNT = "goo:dark_side_count";
    private static final String TYPE = "type";
    private static final BlockPos DARK_SIDE_CHUNK = new BlockPos(3_750_016, 64, 0);
    private static final BlockPos LIGHT_SIDE_CHUNK = new BlockPos(0, 64, 0);

    private final RandomSource random = mock(RandomSource.class);

    private static List<JsonObject> placement() throws IOException {
        try (InputStream stream = VoidCrystalPlacementTest.class.getResourceAsStream(PLACED_FEATURE)) {
            assertNotNull(stream, PLACED_FEATURE + " is not on the classpath");
            JsonObject placed = JsonParser.parseString(new String(stream.readAllBytes(), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertEquals("goo:void_crystal_cluster", placed.get("feature").getAsString());
            List<JsonObject> modifiers = new ArrayList<>();
            for (JsonElement modifier : placed.getAsJsonArray("placement")) {
                modifiers.add(modifier.getAsJsonObject());
            }
            return modifiers;
        }
    }

    private static DarkSideCount darkSideCount() throws IOException {
        List<JsonObject> counts = placement().stream()
                .filter(modifier -> DARK_SIDE_COUNT.equals(modifier.get(TYPE).getAsString())).toList();
        assertEquals(1, counts.size(), "the placement names goo:dark_side_count once: " + placement());
        JsonObject sided = counts.getFirst().deepCopy();
        sided.remove(TYPE);
        return DarkSideCount.CODEC.codec().parse(JsonOps.INSTANCE, sided).getOrThrow();
    }

    @Test
    void aDarkSideChunkTakesAttempts() throws IOException {
        assertTrue(darkSideCount().count(random, DARK_SIDE_CHUNK) > 0, "no attempt on the dark side");
    }

    @Test
    void aLightSideChunkTakesNone() throws IOException {
        assertEquals(0, darkSideCount().count(random, LIGHT_SIDE_CHUNK));
    }

    /** Only the sided count sets how many attempts a chunk takes, so no other count adds light-side attempts. */
    @Test
    void noOtherCountRepeatsTheAttempts() throws IOException {
        List<String> repeats = placement().stream().map(modifier -> modifier.get(TYPE).getAsString())
                .filter(type -> type.contains("count")).toList();
        assertEquals(List.of(DARK_SIDE_COUNT), repeats);
    }
}
