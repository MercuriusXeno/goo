package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomEdges;
import com.mercuriusxeno.goo.ability.quantum.PortalTransform;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the pocket's star sky: goo:pocket draws it and the overworld keeps
 * vanilla's, each star sits along a view direction alone so a room edge view
 * meets the stars the frame meets, and the stars are fainter than the
 * voidstone's in the one star color both draw (decisions
 * outsider-room-draws-a-skybox-of-stars and
 * sky-stars-sit-distant-sparser-and-dimmer-than-voidstone).
 */
class PocketStarSkyTest {

    private static final String VOID_STARS = "assets/goo/shaders/include/void_stars.glsl";
    private static final String VOIDSTONE_SHADER = "assets/goo/shaders/core/voidstone.fsh";
    private static final String SKY_SHADER = "assets/goo/shaders/core/pocket_sky.fsh";
    private static final String VOID_STARS_IMPORT = "#moj_import <goo:void_stars.glsl>";
    private static final Pattern STAR_COLOR = Pattern.compile("vec3\\s+STAR\\s*=");
    /** How many view directions the sweep asks along, spread evenly over the sphere. */
    private static final int SWEEP = 20_000;
    private static final double GOLDEN_TURN = Math.PI * (3 - Math.sqrt(5));
    private static final double PARTWAY = 2.3;

    @Nested
    class TheSkyADimensionDraws {

        @Test
        void thePocketDrawsTheStarSky() {
            assertEquals(PocketStarSky.Sky.STARS,
                    PocketStarSky.skyOf(SkyResources.customSkyboxOf(SkyResources.POCKET)));
        }

        @Test
        void theOverworldDrawsVanillasOwnSky() {
            assertEquals(PocketStarSky.Sky.VANILLA,
                    PocketStarSky.skyOf(SkyResources.customSkyboxOf(SkyResources.OVERWORLD)));
        }
    }

    @Nested
    class StarsSitAlongTheViewDirectionAlone {

        @Test
        void aRoomEdgeViewMeetsTheStarsTheFrameMeets() {
            RoomEdges.Edges edges = RoomEdges.around(OutsiderRooms.voidstoneOf(5), PARTWAY);
            PortalTransform through = PortalTransform.between(edges.east(), edges.west());
            Vec3 frameCamera = Vec3.atCenterOf(OutsiderRooms.voidstoneOf(5)).add(1.5, 1.6, 0.2);
            assertTrue(through.point(frameCamera).distanceTo(frameCamera) > 1,
                    "the edge view's camera should stand apart from the frame's");
            int starred = 0;
            for (Vec3 direction : sweep()) {
                Optional<PocketStarSky.Star> seen = PocketStarSky.starAlong(direction);
                assertEquals(seen, PocketStarSky.starAlong(through.direction(direction)),
                        "the edge view should meet the frame's star along " + direction);
                starred += seen.isPresent() ? 1 : 0;
            }
            assertTrue(starred > 0, "some direction should hold a star");
            assertTrue(starred < SWEEP, "some direction should hold none");
        }
    }

    @Nested
    class FainterThanTheVoidstone {

        @Test
        void theSkysBrightestStarIsFainterThanTheVoidstonesBrightest() {
            double skyPeak = PocketStarSky.stars().stream().mapToDouble(PocketStarSky.Star::brightness).max()
                    .orElse(0);

            assertTrue(skyPeak > 0, "the sky should hold a lit star");
            assertTrue(skyPeak < constant("BRIGHT_STAR_PEAK"),
                    "the sky's brightest star " + skyPeak + " should sit below the voidstone's");
        }

        @Test
        void bothDrawsReadTheOneStarColor() {
            assertTrue(STAR_COLOR.matcher(SkyResources.text(VOID_STARS)).find(),
                    "the shared star include should define the star color");
            for (String shader : List.of(VOIDSTONE_SHADER, SKY_SHADER)) {
                String text = SkyResources.text(shader);
                assertTrue(text.contains(VOID_STARS_IMPORT), shader + " should import the shared star color");
                assertFalse(STAR_COLOR.matcher(text).find(), shader + " should define no star color of its own");
            }
        }
    }

    private static double constant(String name) {
        Matcher value = Pattern.compile("const\\s+float\\s+" + name + "\\s*=\\s*([0-9.]+)\\s*;")
                .matcher(SkyResources.text(VOID_STARS));
        assertTrue(value.find(), VOID_STARS + " should define " + name);
        return Double.parseDouble(value.group(1));
    }

    /**
     * View directions spread evenly over the sphere, a golden-angle spiral.
     *
     * @return the directions
     */
    private static List<Vec3> sweep() {
        List<Vec3> directions = new ArrayList<>(SWEEP);
        for (int i = 0; i < SWEEP; i++) {
            double y = 1 - 2 * (i + 0.5) / SWEEP;
            double across = Math.sqrt(1 - y * y);
            double turn = i * GOLDEN_TURN;
            directions.add(new Vec3(Math.cos(turn) * across, y, Math.sin(turn) * across));
        }
        return directions;
    }
}
