package com.mercuriusxeno.goo.client.zone;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.astral.LunarSight;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers lunar's sky: goo:lunar keeps the overworld's clock, draws the void, a sea of dim stars with a sun crossing
 * it by day, and the blue planet, the overworld, four moons wide in the moon's place, lit by its phase.
 * decision astral-sky-is-the-void-with-one-bright-blue-overworld-star
 * decision blue-star-is-a-planet-four-moons-wide-drawn-in-phases
 * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
 */
class LunarSkyTest {

    private static final double TOLERANCE = 1e-9;

    @Nested
    class TheSkyLunarDraws {

        @Test
        void lunarDrawsTheVoid() {
            assertEquals(PocketStarSky.Sky.LUNAR_VOID,
                    PocketStarSky.skyOf(SkyResources.customSkyboxOf(SkyResources.LUNAR)));
        }
    }

    /**
     * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
     */
    @Nested
    class TheClockLunarKeeps {

        private static final String OVERWORLD_CLOCK = "minecraft:overworld";
        private static final List<String> LUNAR_TIMELINES = List.of("goo:lunar_day", "goo:lunar_moon");
        private static final List<String> DAY_AND_NIGHT_TRACKS = List.of("minecraft:visual/sun_angle",
                "minecraft:visual/moon_angle", "minecraft:visual/moon_phase", "minecraft:gameplay/sky_light_level",
                "minecraft:visual/sky_light_factor");

        @Test
        void lunarRunsOnTheOverworldsClock() {
            JsonObject lunar = json(SkyResources.LUNAR);
            assertEquals(OVERWORLD_CLOCK, lunar.get("default_clock").getAsString());
            assertFalse(lunar.has("has_fixed_time") && lunar.get("has_fixed_time").getAsBoolean(),
                    "lunar's time should run, not stand fixed");
        }

        @Test
        void lunarNamesTheTimelinesCarryingItsSunMoonPhaseAndSkyLight() {
            List<String> named = new ArrayList<>();
            json(SkyResources.LUNAR).getAsJsonArray("timelines").forEach(timeline -> named.add(timeline.getAsString()));
            assertEquals(LUNAR_TIMELINES, named);
            Set<String> tracks = new HashSet<>();
            for (String timeline : named) {
                JsonObject read = json(timelineResource(timeline));
                assertEquals(OVERWORLD_CLOCK, read.get("clock").getAsString(), timeline);
                tracks.addAll(read.getAsJsonObject("tracks").keySet());
            }
            assertTrue(tracks.containsAll(DAY_AND_NIGHT_TRACKS), String.valueOf(tracks));
        }

        private static String timelineResource(String timeline) {
            Identifier id = Identifier.parse(timeline);
            return "data/" + id.getNamespace() + "/timeline/" + id.getPath() + ".json";
        }

        private static JsonObject json(String resource) {
            return JsonParser.parseString(SkyResources.text(resource)).getAsJsonObject();
        }
    }

    /**
     * decision lunar-has-a-day-and-night-and-a-sun-on-the-overworlds-clock
     */
    @Nested
    class TheSun {

        private static final float NOON = 0F;
        private static final float SUNSET = 90F;
        private static final float MIDNIGHT = 180F;
        private static final float SUNRISE = 270F;
        private static final Vec3 OVERHEAD = new Vec3(0, 1, 0);
        private static final Vec3 EAST = new Vec3(1, 0, 0);
        private static final Vec3 WEST = new Vec3(-1, 0, 0);
        private static final Vec3 UNDERFOOT = new Vec3(0, -1, 0);
        private static final double QUAD_TOLERANCE = 1e-4;

        @Test
        void theSunStandsOverheadAtNoon() {
            assertDirection(OVERHEAD, LunarSky.sunDirection(NOON));
        }

        @Test
        void theSunRisesOnTheEasternHorizon() {
            assertDirection(EAST, LunarSky.sunDirection(SUNRISE));
        }

        @Test
        void theSunSetsOnTheWesternHorizon() {
            assertDirection(WEST, LunarSky.sunDirection(SUNSET));
        }

        @Test
        void theSunStandsUnderfootAtMidnight() {
            assertDirection(UNDERFOOT, LunarSky.sunDirection(MIDNIGHT));
        }

        @Test
        void theSunsQuadIsBuiltAlongTheSunsDirectionAtTheSkysDistance() {
            for (float angle : new float[] {NOON, SUNSET, MIDNIGHT, SUNRISE, 37F}) {
                List<Vector3f> corners = LunarSkyRenderer.sunCorners(angle);
                Vector3f center = new Vector3f();
                corners.forEach(center::add);
                center.div(corners.size());
                Vec3 along = LunarSky.sunDirection(angle).scale(PocketStarSkyRenderer.SKY_DISTANCE);
                assertEquals(along.x(), center.x(), QUAD_TOLERANCE, "at " + angle);
                assertEquals(along.y(), center.y(), QUAD_TOLERANCE, "at " + angle);
                assertEquals(along.z(), center.z(), QUAD_TOLERANCE, "at " + angle);
                Vector3f across = new Vector3f(corners.get(2)).sub(corners.get(0));
                assertEquals(0.0, across.dot((float) along.x(), (float) along.y(), (float) along.z()),
                        QUAD_TOLERANCE, "the quad should face the camera at " + angle);
            }
        }

        private static void assertDirection(Vec3 expected, Vec3 actual) {
            assertEquals(expected.x(), actual.x(), TOLERANCE, String.valueOf(actual));
            assertEquals(expected.y(), actual.y(), TOLERANCE, String.valueOf(actual));
            assertEquals(expected.z(), actual.z(), TOLERANCE, String.valueOf(actual));
        }
    }

    /**
     * decision blue-star-is-a-planet-four-moons-wide-drawn-in-phases
     * decision blue-star-takes-the-moons-place-with-the-opposite-phase
     */
    @Nested
    class TheBluePlanet {

        /** Vanilla's moon quad's half width at the sky's distance, SkyRenderer's moon size. */
        private static final float VANILLA_MOON_SIZE = 20F;
        /** The share of vanilla's moon sprite its disc spans: eight of its thirty-two texels. */
        private static final float MOON_DISC_SHARE = 8F / 32F;
        private static final float MOONS_WIDE = 4F;
        private static final float[] MOON_ANGLES = {0F, 45F, 300F};
        private static final double QUAD_TOLERANCE = 1e-4;
        /** The texels across vanilla's moon sprite, and across the planet's, as basic as the moon's eight-texel face. */
        private static final float MOON_SPRITE_TEXELS = 32F;
        private static final float PLANET_SPRITE_TEXELS = 8F;

        @Test
        void theDiscStandsFourVanillaMoonDiscsWide() {
            assertEquals(MOONS_WIDE * VANILLA_MOON_SIZE * MOON_DISC_SHARE, LunarSky.PLANET_HALF_SIZE, QUAD_TOLERANCE);
        }

        @Test
        void theDiscStandsWhereTheMoonAngleSetsIt() {
            for (float angle : MOON_ANGLES) {
                List<Vector3f> corners = LunarSky.planetCorners(angle);
                Vector3f center = new Vector3f();
                corners.forEach(center::add);
                center.div(corners.size());
                Vec3 along = LunarSight.moonDirection(angle).scale(PocketStarSkyRenderer.SKY_DISTANCE);
                assertEquals(along.x(), center.x(), QUAD_TOLERANCE, "at " + angle);
                assertEquals(along.y(), center.y(), QUAD_TOLERANCE, "at " + angle);
                assertEquals(along.z(), center.z(), QUAD_TOLERANCE, "at " + angle);
                assertEquals(LunarSky.PLANET_HALF_SIZE * Math.sqrt(2), corners.get(0).distance(center),
                        QUAD_TOLERANCE, "at " + angle);
            }
        }

        @ParameterizedTest
        @MethodSource("eachPlanetPhaseAndItsSprite")
        void thePlanetDrawsTheSpriteOfItsPhase(MoonPhase planetPhase, String sprite) {
            assertEquals(Identifier.fromNamespaceAndPath("goo", "textures/environment/blue_planet/" + sprite + ".png"),
                    LunarSky.planetSprite(planetPhase));
        }

        @Test
        void aPlanetTexelIsFourMoonTexelsWide() {
            assertEquals(MOONS_WIDE * VANILLA_MOON_SIZE * 2 / MOON_SPRITE_TEXELS,
                    LunarSky.PLANET_HALF_SIZE * 2 / PLANET_SPRITE_TEXELS, QUAD_TOLERANCE);
        }

        static Stream<Arguments> eachPlanetPhaseAndItsSprite() {
            return Stream.of(
                    Arguments.of(MoonPhase.FULL_MOON, "full"),
                    Arguments.of(MoonPhase.WANING_GIBBOUS, "waning_gibbous"),
                    Arguments.of(MoonPhase.THIRD_QUARTER, "third_quarter"),
                    Arguments.of(MoonPhase.WANING_CRESCENT, "waning_crescent"),
                    Arguments.of(MoonPhase.NEW_MOON, "new"),
                    Arguments.of(MoonPhase.WAXING_CRESCENT, "waxing_crescent"),
                    Arguments.of(MoonPhase.FIRST_QUARTER, "first_quarter"),
                    Arguments.of(MoonPhase.WAXING_GIBBOUS, "waxing_gibbous"));
        }
    }
}
