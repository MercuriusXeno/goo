package com.mercuriusxeno.goo.item;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fleck's motion model by its type's viscosity: a clinging fleck creeps
 * down its face, slower as viscosity rises, and always releases it in time; a
 * fleck on a floor loses its speed faster as viscosity rises
 * (decision flecks-move-by-flexible-goo-physics-and-stay-items,
 * decision each-goo-type-carries-elasticity-and-viscosity).
 */
class FleckClingTest {

    private static final int RUNNY = 500;
    private static final int THICK = 4000;
    private static final String TYPE_JSON_FORMAT = "/data/goo/goo/goo_type/%s.json";

    @Nested
    class Creep {

        @Test
        void aClingingFleckCreepsDownItsFaceSlowerAsViscosityRises() {
            FleckCling.Cling fresh = new FleckCling.Cling(Direction.EAST, 0);

            double runny = FleckCling.stepCling(fresh, Vec3.ZERO, RUNNY, true).velocity().y;
            double thick = FleckCling.stepCling(fresh, Vec3.ZERO, THICK, true).velocity().y;

            assertTrue(runny < 0 && thick < 0, "a clinging fleck should creep down, at " + runny + " and " + thick);
            assertTrue(Math.abs(thick) < Math.abs(runny), "a thicker fleck should creep slower");
        }
    }

    @Nested
    class Roll {

        @Test
        void aFleckOnAFloorLosesItsSpeedFasterAsViscosityRises() {
            Vec3 rolling = new Vec3(0.3, 0, 0.1);

            double runny = FleckCling.rollOn(rolling, RUNNY).horizontalDistance();
            double thick = FleckCling.rollOn(rolling, THICK).horizontalDistance();

            assertTrue(thick < runny, "a thicker fleck should slow faster on a floor, " + thick + " against " + runny);
        }
    }

    @Nested
    class Release {

        @Test
        void aClingAtTheThickestShippedViscosityReleasesWithinTheModelsBound() throws IOException {
            int thickest = thickestShippedViscosity();
            FleckCling.Cling cling = new FleckCling.Cling(Direction.EAST, 0);
            int ticks = 0;
            while (cling != null && ticks <= FleckCling.MAX_CLING_TICKS) {
                cling = FleckCling.stepCling(cling, Vec3.ZERO, thickest, true).cling();
                ticks++;
            }

            assertNull(cling, "a fleck at viscosity " + thickest + " should release its face within "
                    + FleckCling.MAX_CLING_TICKS + " ticks");
        }

        private static int thickestShippedViscosity() throws IOException {
            int thickest = 0;
            for (var key : GooTypes.BUNDLED) {
                String path = TYPE_JSON_FORMAT.formatted(key.identifier().getPath());
                try (InputStream in = FleckClingTest.class.getResourceAsStream(path)) {
                    assertNotNull(in, "Shipped JSON missing on classpath: " + path);
                    try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        thickest = Math.max(thickest,
                                JsonParser.parseReader(reader).getAsJsonObject().get("viscosity").getAsInt());
                    }
                }
            }
            return thickest;
        }
    }
}
