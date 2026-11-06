package com.mercuriusxeno.goo.block.ability;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import org.junit.jupiter.api.Nested;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The slime coat's bounce: a landing with jump held launches higher than it
 * arrived and higher than the bounce before, ramping each bounce; without
 * jump held the bounce decays, and sneaking decays it far faster; each
 * bounce sounds a boing made of the vanilla slime sounds
 * (decision bounce-ramps-jump-height-while-jump-is-held).
 */
class SlimeCoatBounceTest {

    private static final double ARRIVING = 0.5;
    private static final double NO_PREVIOUS = 0;
    private static final String SOUNDS_JSON = "/assets/goo/sounds.json";
    private static final String VANILLA_SLIME = "minecraft:mob/slime/";

    @Nested
    class JumpHeld {

        @Test
        void launchesHigherThanItArrived() {
            double launch = SlimeCoatBounce.launch(ARRIVING, NO_PREVIOUS, true, false);

            assertEquals(ARRIVING * SlimeCoatBounce.RAMP_PER_BOUNCE, launch, 1e-9);
        }

        @Test
        void launchesHigherThanTheBounceBefore() {
            double previous = 0.8;

            double launch = SlimeCoatBounce.launch(ARRIVING, previous, true, false);

            assertEquals(previous * SlimeCoatBounce.RAMP_PER_BOUNCE, launch, 1e-9);
        }

        @Test
        void rampsEachBounceUntilTheCeiling() {
            double launch = ARRIVING;
            double before = 0;
            for (int bounce = 0; bounce < 3; bounce++) {
                before = launch;
                launch = SlimeCoatBounce.launch(launch, launch, true, false);
                assertTrue(launch > before, "bounce " + bounce + " launched " + launch + " after " + before);
            }
            for (int bounce = 0; bounce < 50; bounce++) {
                launch = SlimeCoatBounce.launch(launch, launch, true, false);
            }

            assertEquals(SlimeCoatBounce.HIGHEST_LAUNCH, launch, 1e-9);
        }
    }

    @Nested
    class JumpReleased {

        @Test
        void decaysBelowWhatArrived() {
            double launch = SlimeCoatBounce.launch(ARRIVING, 1.2, false, false);

            assertEquals(ARRIVING * SlimeCoatBounce.RELEASED_DECAY, launch, 1e-9);
        }

        @Test
        void settlesOnceTheBounceIsSmall() {
            double launch = SlimeCoatBounce.launch(SlimeCoatBounce.SETTLED_LAUNCH / 2, NO_PREVIOUS, false, false);

            assertEquals(0, launch);
        }
    }

    @Nested
    class Boing {

        @Test
        void aBounceSoundsTheBoing() {
            FakeLander lander = new FakeLander(ARRIVING, false, false, 0);

            SlimeCoatBounce.bounce(lander);

            assertEquals(ARRIVING * SlimeCoatBounce.RELEASED_DECAY, lander.launched, 1e-9);
            assertEquals(1, lander.boings);
        }

        @Test
        void aSettledLandingSoundsNone() {
            FakeLander lander = new FakeLander(SlimeCoatBounce.SETTLED_LAUNCH / 2, false, false, 0);

            SlimeCoatBounce.bounce(lander);

            assertEquals(0, lander.launched);
            assertEquals(0, lander.boings);
        }

        @Test
        void theBoingIsMadeOfTheVanillaSlimeSounds() throws IOException {
            JsonArray sounds = soundsJson().getAsJsonObject(GooSoundIds.BOING.getPath()).getAsJsonArray("sounds");

            assertTrue(!sounds.isEmpty(), "the boing names no sound");
            for (JsonElement sound : sounds) {
                String name = sound.getAsJsonObject().get("name").getAsString();
                assertTrue(name.startsWith(VANILLA_SLIME), name + " is no vanilla slime sound");
            }
        }

        private static JsonObject soundsJson() throws IOException {
            try (InputStream in = SlimeCoatBounceTest.class.getResourceAsStream(SOUNDS_JSON)) {
                assertTrue(in != null, SOUNDS_JSON + " is not on the classpath");
                return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
    }

    @Nested
    class Memory {

        @Test
        void aHeldBounceRampsOffTheLaunchBefore() {
            FakeLander lander = new FakeLander(ARRIVING, true, false, 0);
            SlimeCoatBounce.bounce(lander);
            double first = lander.launched;

            lander.fall(ARRIVING, 1);
            SlimeCoatBounce.bounce(lander);

            assertEquals(first * SlimeCoatBounce.RAMP_PER_BOUNCE, lander.launched, 1e-9);
        }

        @Test
        void aLanderBackAfterAPauseStartsItsRampAfresh() {
            FakeLander lander = new FakeLander(ARRIVING, true, false, 0);
            for (int bounce = 0; bounce < 50; bounce++) {
                lander.fall(lander.launched == 0 ? ARRIVING : lander.launched, bounce);
                SlimeCoatBounce.bounce(lander);
            }
            assertEquals(SlimeCoatBounce.HIGHEST_LAUNCH, lander.launched, 1e-9);

            lander.fall(ARRIVING, 50 + SlimeCoatBounce.FORGET_TICKS + 1);
            SlimeCoatBounce.bounce(lander);

            assertEquals(ARRIVING * SlimeCoatBounce.RAMP_PER_BOUNCE, lander.launched, 1e-9);
        }
    }

    /** A lander the test drives: how it falls, what it holds, and what the bounce did to it. */
    private static final class FakeLander implements SlimeCoatBounce.Lander {
        private double falling;
        private final boolean jumpHeld;
        private final boolean sneaking;
        private long gameTime;
        private double launched;
        private int boings;

        FakeLander(double falling, boolean jumpHeld, boolean sneaking, long gameTime) {
            this.falling = falling;
            this.jumpHeld = jumpHeld;
            this.sneaking = sneaking;
            this.gameTime = gameTime;
        }

        @Override
        public Object identity() {
            return this;
        }

        void fall(double speed, long at) {
            falling = speed;
            gameTime = at;
        }

        @Override
        public double fallSpeed() {
            return falling;
        }

        @Override
        public boolean jumpHeld() {
            return jumpHeld;
        }

        @Override
        public boolean sneaking() {
            return sneaking;
        }

        @Override
        public long gameTime() {
            return gameTime;
        }

        @Override
        public void launch(double speed) {
            launched = speed;
        }

        @Override
        public void boing(double launch) {
            boings++;
        }
    }

    @Nested
    class Sneaking {

        @Test
        void decaysFarFasterThanAReleasedJump() {
            double sneaking = SlimeCoatBounce.launch(ARRIVING, NO_PREVIOUS, false, true);

            assertEquals(ARRIVING * SlimeCoatBounce.SNEAK_DECAY, sneaking, 1e-9);
            assertTrue(SlimeCoatBounce.SNEAK_DECAY * 2 < SlimeCoatBounce.RELEASED_DECAY,
                    "sneaking should decay at least twice as hard as a released jump");
        }

        @Test
        void decaysEvenWithJumpHeld() {
            double launch = SlimeCoatBounce.launch(ARRIVING, 1.2, true, true);

            assertEquals(ARRIVING * SlimeCoatBounce.SNEAK_DECAY, launch, 1e-9);
        }
    }
}
