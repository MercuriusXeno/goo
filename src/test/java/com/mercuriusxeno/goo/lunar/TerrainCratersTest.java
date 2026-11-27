package com.mercuriusxeno.goo.lunar;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import net.minecraft.world.level.levelgen.synth.Noise;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Lunar's terrain sinks giant craters alone: its density function names goo:giant_craters as its one crater term, and
 * over a wide area, with crater centres reading each surface biome in turn, every column the craters sink lies inside
 * a giant crater's rim. The terrain is read from the classpath and the noises from the shipped settings, with no
 * registry.
 * decision craters-are-rare-giant-landmarks-only
 */
class TerrainCratersTest {

    private static final String TERRAIN = "/data/goo/worldgen/density_function/lunar/terrain.json";
    private static final String TYPE = "type";
    private static final String CRATER = "crater";
    /** The sampled area runs this many blocks on a side, read every few blocks. */
    private static final int AREA = 2048;
    private static final int STEP = 4;
    /** Maria reads above zero; the shadowed basins from GiantCraterField.BASINS_FLOOR up. */
    private static final double IN_MARIA = 0.3;
    private static final double OUTSIDE_MARIA = -0.3;
    private static final double IN_BASINS = 0.6;
    private static final double OUTSIDE_BASINS = -0.4;
    /**
     * The biome noises a crater centred in each surface biome reads; eldritch ground reads as neither maria nor the
     * basins, as the highlands do.
     */
    private static final Map<String, CentreGround> GROUNDS = Map.of(
            "maria", new FixedGround(IN_MARIA, OUTSIDE_BASINS),
            "highlands", new FixedGround(OUTSIDE_MARIA, OUTSIDE_BASINS),
            "shadowed basins", new FixedGround(OUTSIDE_MARIA, IN_BASINS),
            "eldritch ground", new FixedGround(OUTSIDE_MARIA, OUTSIDE_BASINS - 0.2));
    private static final int NEIGHBOUR_REACH = 1;

    private static void collectCraterTypes(JsonElement element, Set<String> types) {
        if (element.isJsonArray()) {
            element.getAsJsonArray().forEach(child -> collectCraterTypes(child, types));
            return;
        }
        if (!element.isJsonObject()) {
            return;
        }
        element.getAsJsonObject().entrySet().forEach(entry -> {
            JsonElement value = entry.getValue();
            if (entry.getKey().equals(TYPE) && value.isJsonPrimitive() && value.getAsString().contains(CRATER)) {
                types.add(value.getAsString());
            }
            collectCraterTypes(value, types);
        });
    }

    @Test
    void theTerrainNamesGiantCratersAsItsOneCraterTerm() throws IOException {
        Set<String> types = new TreeSet<>();
        try (InputStream terrain = TerrainCratersTest.class.getResourceAsStream(TERRAIN)) {
            assertNotNull(terrain, TERRAIN + " should stand on the classpath");
            collectCraterTypes(JsonParser.parseReader(new InputStreamReader(terrain, StandardCharsets.UTF_8)), types);
        }
        assertEquals(Set.of("goo:giant_craters"), types);
    }

    @Test
    void everySunkColumnLiesInsideAGiantCratersRim() {
        Noise noise = LunarTestNoises.crater();
        LunarRolls.CellRolls rolls = (cellX, cellZ, roll) -> LunarRolls.rollFrom(noise, cellX, cellZ, roll);
        GROUNDS.forEach((biome, ground) -> {
            int sunk = 0;
            for (int x = 0; x < AREA; x += STEP) {
                for (int z = 0; z < AREA; z += STEP) {
                    if (GiantCraterField.heightAt(x, z, rolls, ground) < 0.0) {
                        sunk++;
                        assertTrue(insideAGiantRim(x, z, rolls, ground),
                                "the column " + x + ", " + z + " sinks outside every giant crater's rim in " + biome);
                    }
                }
            }
            assertTrue(sunk > 0, "no column of the area sinks in " + biome);
        });
    }

    private static boolean insideAGiantRim(int x, int z, LunarRolls.CellRolls rolls, CentreGround ground) {
        int cellX = Math.floorDiv(x, GiantCraterField.CELL);
        int cellZ = Math.floorDiv(z, GiantCraterField.CELL);
        for (int dx = -NEIGHBOUR_REACH; dx <= NEIGHBOUR_REACH; dx++) {
            for (int dz = -NEIGHBOUR_REACH; dz <= NEIGHBOUR_REACH; dz++) {
                GiantCraterField.Crater crater = GiantCraterField.craterIn(cellX + dx, cellZ + dz, rolls, ground);
                if (crater.reach(x, z) < 1.0 && crater.radius() >= GiantCraterField.MIN_RADIUS) {
                    return true;
                }
            }
        }
        return false;
    }
}
