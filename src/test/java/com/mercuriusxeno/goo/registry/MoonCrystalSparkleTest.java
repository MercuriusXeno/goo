package com.mercuriusxeno.goo.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static com.mercuriusxeno.goo.registry.SpritePixels.gooBlock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The moon crystals' animated strips, read with their mcmeta off the classpath: each holds still most of its cycle and
 * glints once, and the four forms of a crystal loop at four lengths so a cluster glints now and then.
 * decision moon-crystals-sparkle-frames-redrawn
 */
class MoonCrystalSparkleTest {

    private static final List<String> CRYSTALS = List.of("astral_crystal", "void_crystal");

    private static final int SHORTEST_CYCLE_TICKS = 80;
    private static final int LONGEST_CYCLE_TICKS = 140;
    private static final int SHORTEST_GLINT_TICKS = 10;
    private static final int LONGEST_GLINT_TICKS = 30;

    /** One entry of a strip's frame list: the frame drawn and the ticks it shows. */
    record ListedFrame(int index, int ticks) {
    }

    /** A strip's frames as its PNG draws them and its animation as its mcmeta lists them. */
    record Strip(String sprite, BufferedImage image, List<ListedFrame> listed, boolean interpolates) {

        int cycleTicks() {
            return listed.stream().mapToInt(ListedFrame::ticks).sum();
        }

        /** The frame index the cycle shows longest, summed over every entry listing it. */
        int stillIndex() {
            Map<Integer, Integer> ticksByIndex = new HashMap<>();
            listed.forEach(frame -> ticksByIndex.merge(frame.index(), frame.ticks(), Integer::sum));
            return ticksByIndex.entrySet().stream()
                    .max(Map.Entry.<Integer, Integer>comparingByValue()
                            .thenComparing(Map.Entry.comparingByKey(Comparator.reverseOrder())))
                    .orElseThrow().getKey();
        }

        int stillTicks() {
            int still = stillIndex();
            return listed.stream().filter(frame -> frame.index() == still).mapToInt(ListedFrame::ticks).sum();
        }

        boolean drawsLikeTheStill(ListedFrame frame) {
            int side = image.getWidth();
            int still = stillIndex();
            for (int y = 0; y < side; y++) {
                for (int x = 0; x < side; x++) {
                    if (image.getRGB(x, frame.index() * side + y) != image.getRGB(x, still * side + y)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    static List<String> formsOf(String crystal) {
        return LunarPaletteTest.variantsOf(crystal);
    }

    static Stream<String> strips() {
        return CRYSTALS.stream().flatMap(crystal -> formsOf(crystal).stream());
    }

    static Strip strip(String sprite) throws IOException {
        BufferedImage image = gooBlock(sprite);
        String path = "/assets/goo/textures/block/" + sprite + ".png.mcmeta";
        try (InputStream stream = MoonCrystalSparkleTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, path + " is not on the classpath");
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                JsonObject animation = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("animation");
                assertNotNull(animation, path + " holds no animation");
                return new Strip(sprite, image, listedFrames(animation, image),
                        animation.has("interpolate") && animation.get("interpolate").getAsBoolean());
            }
        }
    }

    /** The frame list as vanilla reads it: the listed frames, or every frame in order when none are listed. */
    static List<ListedFrame> listedFrames(JsonObject animation, BufferedImage image) {
        int frametime = animation.has("frametime") ? animation.get("frametime").getAsInt() : 1;
        if (!animation.has("frames")) {
            int count = image.getHeight() / image.getWidth();
            return IntStream.range(0, count).mapToObj(index -> new ListedFrame(index, frametime)).toList();
        }
        List<ListedFrame> listed = new ArrayList<>();
        for (JsonElement entry : animation.getAsJsonArray("frames")) {
            if (entry.isJsonPrimitive()) {
                listed.add(new ListedFrame(entry.getAsInt(), frametime));
            } else {
                JsonObject frame = entry.getAsJsonObject();
                listed.add(new ListedFrame(frame.get("index").getAsInt(),
                        frame.has("time") ? frame.get("time").getAsInt() : frametime));
            }
        }
        return listed;
    }

    @Nested
    class Cycle {

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.registry.MoonCrystalSparkleTest#strips")
        void eachStripLoopsInFourToSevenSeconds(String sprite) throws IOException {
            int cycle = strip(sprite).cycleTicks();
            assertTrue(cycle >= SHORTEST_CYCLE_TICKS && cycle <= LONGEST_CYCLE_TICKS,
                    sprite + " loops in " + cycle + " ticks");
        }

        @ParameterizedTest
        @ValueSource(strings = {"astral_crystal", "void_crystal"})
        void theFourFormsOfACrystalLoopAtFourLengths(String crystal) throws IOException {
            List<Integer> cycles = new ArrayList<>();
            for (String form : formsOf(crystal)) {
                cycles.add(strip(form).cycleTicks());
            }
            Set<Integer> distinct = Set.copyOf(cycles);
            assertEquals(formsOf(crystal).size(), distinct.size(), crystal + "'s forms loop in " + cycles + " ticks");
        }
    }

    @Nested
    class Glint {

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.registry.MoonCrystalSparkleTest#strips")
        void theStillFrameHoldsMostOfTheCycle(String sprite) throws IOException {
            Strip strip = strip(sprite);
            assertTrue(strip.stillTicks() * 2 > strip.cycleTicks(),
                    sprite + " holds its still frame " + strip.stillTicks() + " of " + strip.cycleTicks() + " ticks");
        }

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.registry.MoonCrystalSparkleTest#strips")
        void theFramesLeavingTheStillFormOneGlintOfAboutASecond(String sprite) throws IOException {
            Strip strip = strip(sprite);
            List<ListedFrame> listed = strip.listed();
            List<Boolean> glinting = listed.stream().map(frame -> !strip.drawsLikeTheStill(frame)).toList();
            long glintsBegun = IntStream.range(0, listed.size())
                    .filter(k -> glinting.get(k) && !glinting.get((k + listed.size() - 1) % listed.size())).count();
            assertEquals(1, glintsBegun, sprite + " glints " + glintsBegun + " times a cycle: " + describe(listed));
            int glintTicks = IntStream.range(0, listed.size()).filter(glinting::get)
                    .map(k -> listed.get(k).ticks()).sum();
            assertTrue(glintTicks >= SHORTEST_GLINT_TICKS && glintTicks <= LONGEST_GLINT_TICKS,
                    sprite + " glints for " + glintTicks + " ticks");
        }

        @ParameterizedTest
        @MethodSource("com.mercuriusxeno.goo.registry.MoonCrystalSparkleTest#strips")
        void theGlintFadesBetweenFrames(String sprite) throws IOException {
            assertTrue(strip(sprite).interpolates(), sprite + " sets no interpolate, so its glint pops");
        }
    }

    static String describe(List<ListedFrame> listed) {
        return listed.stream().map(frame -> frame.index() + "x" + frame.ticks()).collect(Collectors.joining(" "));
    }
}
