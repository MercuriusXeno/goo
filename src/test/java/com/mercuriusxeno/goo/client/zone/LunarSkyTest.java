package com.mercuriusxeno.goo.client.zone;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.astral.LunarSight;
import com.mercuriusxeno.goo.client.BlendComposite;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.lunar.LunarSun;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    private static final long FULL_MOON_MIDNIGHT = 18_000;
    private static final long WHOLE_CYCLE = 192_000;

    /**
     * The overworld clock time the sun stands at an angle over an x, in the first phase cycle.
     *
     * @param x            the x, in blocks
     * @param angleDegrees the sun angle, in degrees
     * @return the overworld clock time
     */
    private static long clockWhenTheSunStandsAt(double x, double angleDegrees) {
        double turn = Math.floorMod(Math.round((angleDegrees - Lunar.planetAngleDegrees(x)) * 1e6), 360_000_000L) / 1e6;
        return FULL_MOON_MIDNIGHT + Math.round(turn / 360 * WHOLE_CYCLE);
    }

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
                "minecraft:visual/moon_phase", "minecraft:gameplay/sky_light_level",
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
        /** Partway from sunrise to noon, the sun well up in the east. */
        private static final float MORNING = 315F;
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

        /**
         * decision sun-follows-the-phase-and-lights-the-far-side-too
         */
        @Test
        void theSunDrawsOverTheDarkSideWhileItStandsAboveItsHorizon() {
            double darkSideX = 5_000_000;
            Optional<Vec3> risen = LunarSky.sunOver(darkSideX, clockWhenTheSunStandsAt(darkSideX, NOON));
            assertTrue(risen.isPresent(), "the sun over the dark side at its noon");
            assertDirection(OVERHEAD, risen.get());
            Optional<Vec3> morning = LunarSky.sunOver(darkSideX, clockWhenTheSunStandsAt(darkSideX, MORNING));
            assertTrue(morning.isPresent() && morning.get().y() > 0 && morning.get().x() > 0,
                    "the morning sun over the dark side, up in the east: " + morning);
            assertTrue(LunarSky.sunOver(darkSideX, clockWhenTheSunStandsAt(darkSideX, MIDNIGHT)).isEmpty(),
                    "the sun below the dark side's horizon at its midnight");
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
        /** Camera x's on the light side: the planet overhead, halfway down, and low in the east. */
        private static final double[] CAMERA_XS = {0, 1_875_000, -3_000_000};
        /** A camera x on the dark side, past 3.75 million. */
        private static final double DARK_SIDE_X = 4_000_000;
        private static final double QUAD_TOLERANCE = 1e-4;
        private static final int OVERWORLD_DAY = 24_000;
        private static final int MIDNIGHT_TICK = 18_000;
        private static final float HALF_THE_SPRITE = 0.5F;
        /** An unlit texel of a planet sprite: black, and opaque, as every texel of the eight sprites is. */
        private static final float[] UNLIT_PLANET_TEXEL = {0F, 0F, 0F, 1F};
        /** A texel of the sun's face where the sky drew it. */
        private static final float[] SUN_TEXEL = {1F, 0.9F, 0.5F};
        private static final float[] BLACK = {0F, 0F, 0F};
        /** How squarely the lit edge has to face the sun's side, as a share of a head-on facing. */
        private static final double LIT_EDGE_SHARE = 0.5;
        /** The texels across vanilla's moon sprite, and across the planet's, as basic as the moon's eight-texel face. */
        private static final float MOON_SPRITE_TEXELS = 32F;
        private static final float PLANET_SPRITE_TEXELS = 8F;

        @Test
        void theDiscStandsFourVanillaMoonDiscsWide() {
            assertEquals(MOONS_WIDE * VANILLA_MOON_SIZE * MOON_DISC_SHARE, LunarSky.PLANET_HALF_SIZE, QUAD_TOLERANCE);
        }

        /**
         * decision blue-planet-hangs-fixed-with-its-height-set-by-x
         */
        @Test
        void theDiscStandsWhereTheCamerasXSetsIt() {
            for (double cameraX : CAMERA_XS) {
                List<Vector3f> corners = LunarSky.planetCorners(cameraX);
                Vector3f center = new Vector3f();
                corners.forEach(center::add);
                center.div(corners.size());
                Vec3 along = LunarSight.planetDirection(cameraX).scale(PocketStarSkyRenderer.SKY_DISTANCE);
                assertEquals(along.x(), center.x(), QUAD_TOLERANCE, "at " + cameraX);
                assertEquals(along.y(), center.y(), QUAD_TOLERANCE, "at " + cameraX);
                assertEquals(along.z(), center.z(), QUAD_TOLERANCE, "at " + cameraX);
                assertEquals(LunarSky.PLANET_HALF_SIZE * Math.sqrt(2), corners.get(0).distance(center),
                        QUAD_TOLERANCE, "at " + cameraX);
                assertTrue(LunarSky.drawsPlanetOver(cameraX), "at " + cameraX);
            }
        }

        /**
         * decision blue-planet-hangs-fixed-with-its-height-set-by-x
         * decision dark-side-is-the-half-of-the-moon-past-the-light-side
         */
        @Test
        void theDarkSideDrawsNoPlanet() {
            assertFalse(LunarSky.drawsPlanetOver(DARK_SIDE_X));
            assertFalse(LunarSky.drawsPlanetOver(-DARK_SIDE_X));
        }

        @ParameterizedTest
        @MethodSource("eachPlanetPhaseAndItsSprite")
        void thePlanetDrawsTheSpriteOfItsPhase(MoonPhase planetPhase, String sprite) {
            assertEquals(spriteNamed(sprite), LunarSky.planetSprite(planetPhase));
        }

        @ParameterizedTest
        @MethodSource("eachMoonPhaseAndThePlanetsSprite")
        void thePlanetDrawsTheSpriteOfThePhaseOppositeTheMoons(MoonPhase moonPhase, String sprite) {
            assertEquals(spriteNamed(sprite), LunarSky.planetSprite(LunarSight.planetPhase(moonPhase)));
        }

        private static Identifier spriteNamed(String sprite) {
            return Identifier.fromNamespaceAndPath("goo", "textures/environment/blue_planet/" + sprite + ".png");
        }

        static Stream<Arguments> eachMoonPhaseAndThePlanetsSprite() {
            return Stream.of(
                    Arguments.of(MoonPhase.NEW_MOON, "full"),
                    Arguments.of(MoonPhase.WAXING_CRESCENT, "waning_gibbous"),
                    Arguments.of(MoonPhase.FIRST_QUARTER, "third_quarter"),
                    Arguments.of(MoonPhase.WAXING_GIBBOUS, "waning_crescent"),
                    Arguments.of(MoonPhase.FULL_MOON, "new"),
                    Arguments.of(MoonPhase.WANING_GIBBOUS, "waxing_crescent"),
                    Arguments.of(MoonPhase.THIRD_QUARTER, "first_quarter"),
                    Arguments.of(MoonPhase.WANING_CRESCENT, "waxing_gibbous"));
        }

        @Test
        void aPlanetTexelIsFourMoonTexelsWide() {
            assertEquals(MOONS_WIDE * VANILLA_MOON_SIZE * 2 / MOON_SPRITE_TEXELS,
                    LunarSky.PLANET_HALF_SIZE * 2 / PLANET_SPRITE_TEXELS, QUAD_TOLERANCE);
        }

        /**
         * The planet's lit edge faces the sun at x 0, at the overworld midnight of each partial phase: the corners
         * carrying the sprite's brighter half lie on the sun's side of the planet.
         * decision sun-follows-the-phase-and-lights-the-far-side-too
         * decision planet-face-is-eight-hand-drawn-phase-sprites
         */
        @ParameterizedTest
        @MethodSource("eachPartialPlanetPhase")
        void thePlanetsLitEdgeFacesTheSun(MoonPhase planetPhase) throws IOException {
            int day = overworldDayShowing(planetPhase);
            long midnight = (long) day * OVERWORLD_DAY + MIDNIGHT_TICK;
            Vec3 sun = LunarSky.sunDirection((float) LunarSun.sunAngleDegrees(0, midnight));
            List<Vector3f> corners = LunarSky.planetCorners(0);
            boolean litOnTheRight = brighterOnTheRight(planetPhase);
            Vector3f litward = new Vector3f();
            for (int corner = 0; corner < corners.size(); corner++) {
                boolean onTheRight = BluePlanetRenderer.CORNER_UVS[corner][0] > HALF_THE_SPRITE;
                Vector3f toward = new Vector3f(corners.get(corner));
                litward.add(onTheRight == litOnTheRight ? toward : toward.negate());
            }
            double facing = sun.x() * litward.x() + sun.y() * litward.y() + sun.z() * litward.z();
            assertTrue(facing > litward.length() * Math.abs(sun.x()) * LIT_EDGE_SHARE, planetPhase + " on day " + day
                    + ": sun " + sun + ", lit edge toward " + litward);
        }

        /**
         * The sky draws the sun before the planet, and the planet's unlit face, opaque black, hides the sun behind it
         * through the dark phase's eclipse.
         * decision sun-follows-the-phase-and-lights-the-far-side-too
         */
        @Test
        void thePlanetsDarkFaceHidesTheSunBehindIt() {
            float[] shown = BlendComposite.over(LunarSkyRenderer.PLANET_PIPELINE, UNLIT_PLANET_TEXEL, SUN_TEXEL);
            assertArrayEquals(BLACK, shown, (float) TOLERANCE, "the sun behind the dark planet shows " + shown[0]
                    + ", " + shown[1] + ", " + shown[2]);
        }

        private static int overworldDayShowing(MoonPhase planetPhase) {
            for (MoonPhase moon : MoonPhase.values()) {
                if (LunarSight.planetPhase(moon) == planetPhase) {
                    return moon.index();
                }
            }
            throw new IllegalArgumentException(String.valueOf(planetPhase));
        }

        private static boolean brighterOnTheRight(MoonPhase planetPhase) throws IOException {
            Identifier sprite = LunarSky.planetSprite(planetPhase);
            String resource = "/assets/" + sprite.getNamespace() + "/" + sprite.getPath();
            try (InputStream stream = LunarSkyTest.class.getResourceAsStream(resource)) {
                assertNotNull(stream, resource + " is not on the classpath");
                BufferedImage image = ImageIO.read(stream);
                long left = 0;
                long right = 0;
                for (int y = 0; y < image.getHeight(); y++) {
                    for (int x = 0; x < image.getWidth(); x++) {
                        int lit = brightness(image.getRGB(x, y));
                        if (x < image.getWidth() / 2) {
                            left += lit;
                        } else {
                            right += lit;
                        }
                    }
                }
                assertNotEquals(left, right, resource + " lights both halves alike");
                return right > left;
            }
        }

        private static int brightness(int argb) {
            int alpha = argb >>> 24;
            return alpha * (((argb >> 16) & 0xFF) + ((argb >> 8) & 0xFF) + (argb & 0xFF));
        }

        static Stream<MoonPhase> eachPartialPlanetPhase() {
            return Stream.of(MoonPhase.WAXING_CRESCENT, MoonPhase.FIRST_QUARTER, MoonPhase.WAXING_GIBBOUS,
                    MoonPhase.WANING_GIBBOUS, MoonPhase.THIRD_QUARTER, MoonPhase.WANING_CRESCENT);
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
