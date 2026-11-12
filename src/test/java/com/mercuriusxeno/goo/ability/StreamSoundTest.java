package com.mercuriusxeno.goo.ability;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stream's sound plays on the hold's first tick and every so many ticks
 * after, each play's pitch strayed within its spread either way
 * (decision mycosis-spore-stream-buds-and-poisons).
 */
class StreamSoundTest {

    private static final float EPSILON = 1e-6f;
    private static final StreamSound BUBBLES =
            new StreamSound(Identifier.withDefaultNamespace("block.bubble_column.bubble_pop"), 2, 0.4f, 0.2f);

    @Test
    void itPlaysOnTheFirstTickAndEveryOtherAfter() {
        assertTrue(BUBBLES.playsOn(1));
        assertFalse(BUBBLES.playsOn(2));
        assertTrue(BUBBLES.playsOn(3));
        assertFalse(BUBBLES.playsOn(4));
    }

    @Test
    void itsPitchStraysWithinTheSpreadEitherWay() {
        assertEquals(0.8f, BUBBLES.pitchFor(0f), EPSILON);
        assertEquals(1f, BUBBLES.pitchFor(0.5f), EPSILON);
        assertEquals(1.2f, BUBBLES.pitchFor(1f), EPSILON);
    }

    // decision decay-gnats-degrade-each-block-once: the holder's client reads the loop off the ability sync
    @Test
    void aLoopedSoundCrossesTheNetworkWhole() {
        StreamSound buzz = new StreamSound(Identifier.withDefaultNamespace("entity.bee.loop"), 30, 0.12f, 0.05f,
                1.95f, true);
        io.netty.buffer.ByteBuf buf = io.netty.buffer.Unpooled.buffer();
        StreamSound.STREAM_CODEC.encode(buf, buzz);
        assertEquals(buzz, StreamSound.STREAM_CODEC.decode(buf));
    }

    // decision decay-gnats-degrade-each-block-once: Decay's buzz plays a bee's loop pitched high
    @Test
    void aPitchedSoundStraysAroundItsOwnPitch() {
        StreamSound buzz = new StreamSound(Identifier.withDefaultNamespace("entity.bee.loop"), 30, 0.12f, 0.05f, 1.95f);
        assertEquals(1.9f, buzz.pitchFor(0f), EPSILON);
        assertEquals(1.95f, buzz.pitchFor(0.5f), EPSILON);
        assertEquals(2f, buzz.pitchFor(1f), EPSILON);
    }

    /**
     * Goop's and Slick's streams both play the honey block slide while held,
     * through one and the same sound block.
     * goop-and-slick-spray-sounds-the-honey-block-slide
     */
    @Nested
    class GoopAndSlickSlide {

        private static final Identifier HONEY_SLIDE = Identifier.withDefaultNamespace("block.honey_block.slide");

        private StreamSound soundOf(String ability) {
            Optional<StreamSound> sound = AbilityJson.decode(ability).delivery().sound();
            assertTrue(sound.isPresent(), ability + " should name a stream sound");
            return sound.get();
        }

        @Test
        void goopAndSlickPlayTheHoneyBlockSlide() {
            assertEquals(HONEY_SLIDE, soundOf("weird_goop").sound());
            assertEquals(HONEY_SLIDE, soundOf("kinetic_slick").sound());
        }

        @Test
        void goopAndSlickCarryOneAndTheSameSound() {
            assertEquals(soundOf("weird_goop"), soundOf("kinetic_slick"));
        }
    }

    /**
     * While Bore runs it loops Spire's earth rumble as it is, at the holder's
     * ears, and the plays the server sends nearby players never stack on
     * themselves (decision bore-loops-spires-earth-rumble).
     */
    @Nested
    class BoreRumble {

        private static final String SOUNDS_JSON = "assets/goo/sounds.json";
        private static final String RUMBLE_ENTRY = "effects.earth_rumble";
        private static final double TICKS_A_SECOND = 20;
        private static final byte[] OGG_PAGE = "OggS".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        private static final byte[] VORBIS_ID = "\u0001vorbis".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        /** Bytes from a Vorbis identification header's start to its sample rate. */
        private static final int RATE_OFFSET = 12;
        /** Bytes from an Ogg page's start to its granule position. */
        private static final int GRANULE_OFFSET = 6;

        private StreamSound boreSound() {
            Optional<StreamSound> sound = AbilityJson.decode("rock_bore").delivery().sound();
            assertTrue(sound.isPresent(), "rock_bore should name a stream sound");
            return sound.get();
        }

        @Test
        void boreLoopsSpiresEarthRumbleAsItIs() {
            StreamSound sound = boreSound();
            assertEquals(com.mercuriusxeno.goo.registry.GooSoundIds.EARTH_RUMBLE, sound.sound());
            assertTrue(sound.loop(), "Bore's rumble should loop at the holder's ears");
            assertEquals(1f, sound.pitch(), EPSILON);
            assertEquals(0f, sound.pitchSpread(), EPSILON);
        }

        @Test
        void boresRumbleRepeatsNoSoonerThanItsLongestTake() throws java.io.IOException {
            double longest = 0;
            for (String take : rumbleTakes()) {
                longest = Math.max(longest, secondsOf("assets/goo/sounds/" + take + ".ogg"));
            }
            assertTrue(longest > 0, "no earth_rumble take was read");
            assertTrue(boreSound().every() >= Math.ceil(longest * TICKS_A_SECOND),
                    "Bore plays every " + boreSound().every() + " ticks, under the longest take's "
                            + longest * TICKS_A_SECOND);
        }

        private java.util.List<String> rumbleTakes() throws java.io.IOException {
            try (java.io.InputStream in = resource(SOUNDS_JSON)) {
                com.google.gson.JsonObject sounds = com.google.gson.JsonParser.parseReader(
                        new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                java.util.List<String> takes = new java.util.ArrayList<>();
                for (com.google.gson.JsonElement take : sounds.getAsJsonObject(RUMBLE_ENTRY).getAsJsonArray("sounds")) {
                    takes.add(take.getAsJsonObject().get("name").getAsString().replace("goo:", ""));
                }
                return takes;
            }
        }

        /** A Vorbis take's length: its last page's granule position over its sample rate. */
        private double secondsOf(String path) throws java.io.IOException {
            byte[] bytes;
            try (java.io.InputStream in = resource(path)) {
                bytes = in.readAllBytes();
            }
            java.nio.ByteBuffer little = java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN);
            int rate = little.getInt(indexOf(bytes, VORBIS_ID, false) + RATE_OFFSET);
            long granule = little.getLong(indexOf(bytes, OGG_PAGE, true) + GRANULE_OFFSET);
            return (double) granule / rate;
        }

        private int indexOf(byte[] bytes, byte[] wanted, boolean last) {
            int found = -1;
            for (int at = 0; at + wanted.length <= bytes.length; at++) {
                if (java.util.Arrays.equals(bytes, at, at + wanted.length, wanted, 0, wanted.length)) {
                    found = at;
                    if (!last) {
                        return found;
                    }
                }
            }
            assertTrue(found >= 0, "no marker found");
            return found;
        }

        private java.io.InputStream resource(String path) {
            java.io.InputStream in = StreamSoundTest.class.getClassLoader().getResourceAsStream(path);
            assertTrue(in != null, "the classpath holds no " + path);
            return in;
        }
    }
}
