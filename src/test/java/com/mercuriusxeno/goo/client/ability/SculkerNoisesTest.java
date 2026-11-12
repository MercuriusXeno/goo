package com.mercuriusxeno.goo.client.ability;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A sound heard under Sculker rings once at its rank: a sound the mod's
 * sub-rank table names rings at the table's rank, Click's travel echo at 5,
 * mob noises at 9 and Shriek's and Click's landing at 15; any other sound takes the frequency of
 * the vibration the server forwards beside it, and a sound no vibration
 * meets rings at 5; a vibration with no sound beside it rings nothing
 * (decision sculker-echo-intensity-follows-vibration-rank). Reads the
 * shipped table and Click's ability file through the classpath.
 */
class SculkerNoisesTest {

    private static final Vec3 AT = new Vec3(10.5, 64, -3.5);
    private static final Vec3 BESIDE = AT.add(1, 0, 0.5);
    private static final Vec3 FAR_OFF = AT.add(8, 0, 0);
    private static final long HEARD_AT = 200;
    private static final String STEP_SOUND = "minecraft:block.stone.step";
    private static final String HURT_SOUND = "minecraft:entity.player.hurt";
    private static final String EXPLODE_SOUND = "minecraft:entity.generic.explode";
    private static final String LAVA_POP = "minecraft:block.lava.pop";
    private static final String ZOMBIE_GROAN = "minecraft:entity.zombie.ambient";
    private static final String GHAST_CRY = "minecraft:entity.ghast.ambient";
    private static final String SHRIEK_SOUND = "minecraft:block.sculk_shrieker.shriek";
    private static final String CLICK_SOUND = "goo:ability.click";
    private static final int TRAIL_RANK = 5;
    private static final int LANDING_RANK = 15;
    /** The frequencies VibrationSystem's 26.1 table gives step, entity damage and explode. */
    private static final int STEP_FREQUENCY = 1;
    private static final int DAMAGE_FREQUENCY = 7;
    private static final int EXPLODE_FREQUENCY = 15;

    /** One ring a noise started: where, when and at what rank. */
    private record Rung(Vec3 at, long heardAt, int rank) {
    }

    private final List<Rung> rung = new ArrayList<>();
    private final SculkerNoises noises = new SculkerNoises(SculkerSoundRanks.shipped(),
            (at, heardAt, rank) -> rung.add(new Rung(at, heardAt, rank)));

    private void waitOut() {
        noises.advance(HEARD_AT + SculkerNoises.WAIT_TICKS + 1);
    }

    @Nested
    class GameEventRanks {

        @Test
        void aSoundTakesTheFrequencyOfTheVibrationBesideIt() {
            noises.sound(STEP_SOUND, AT, HEARD_AT);
            noises.vibration(BESIDE, HEARD_AT, STEP_FREQUENCY);
            noises.vibration(BESIDE.add(0, 40, 0), HEARD_AT, DAMAGE_FREQUENCY);
            noises.sound(HURT_SOUND, BESIDE.add(0, 40, 0), HEARD_AT);
            noises.vibration(AT.add(0, 80, 0), HEARD_AT + 1, EXPLODE_FREQUENCY);
            noises.sound(EXPLODE_SOUND, AT.add(0, 80, 0), HEARD_AT + 2);
            waitOut();
            assertEquals(List.of(STEP_FREQUENCY, DAMAGE_FREQUENCY, EXPLODE_FREQUENCY),
                    rung.stream().map(Rung::rank).toList());
        }

        @Test
        void aSoundRingsFromWhenItWasHeardThoughItsVibrationCameLater() {
            noises.sound(EXPLODE_SOUND, AT, HEARD_AT);
            noises.vibration(AT, HEARD_AT + 2, EXPLODE_FREQUENCY);
            assertEquals(List.of(new Rung(AT, HEARD_AT, EXPLODE_FREQUENCY)), rung);
        }

        @Test
        void aVibrationFarFromASoundLendsItNoRank() {
            noises.sound(HURT_SOUND, AT, HEARD_AT);
            noises.vibration(FAR_OFF, HEARD_AT, DAMAGE_FREQUENCY);
            waitOut();
            assertEquals(List.of(new Rung(AT, HEARD_AT, SculkerNoises.UNRANKED)), rung);
        }

        @Test
        void aSoundNoVibrationMeetsRingsAtFive() {
            noises.sound(LAVA_POP, AT, HEARD_AT);
            noises.advance(HEARD_AT + SculkerNoises.WAIT_TICKS - 1);
            assertTrue(rung.isEmpty(), "a sound should wait for its vibration a few ticks");
            waitOut();
            assertEquals(List.of(new Rung(AT, HEARD_AT, 5)), rung);
        }
    }

    @Nested
    class SubRanks {

        @Test
        void mobNoisesRankNine() {
            assertEquals(OptionalInt.of(9), SculkerSoundRanks.shipped().rankOf(ZOMBIE_GROAN));
            assertEquals(OptionalInt.of(9), SculkerSoundRanks.shipped().rankOf(GHAST_CRY));
        }

        @Test
        void shriekAndClickRankFifteen() {
            assertEquals(OptionalInt.of(15), SculkerSoundRanks.shipped().rankOf(SHRIEK_SOUND));
            assertEquals(OptionalInt.of(15), SculkerSoundRanks.shipped().rankOf(CLICK_SOUND));
        }

        // decision click-travel-echoes-ring-at-rank-five
        @Test
        void clickTrailRingsAtFiveTheTickItIsHeard() throws Exception {
            noises.sound(clickStepField("scan_visual", "trail"), AT, HEARD_AT);
            assertEquals(List.of(new Rung(AT, HEARD_AT, TRAIL_RANK)), rung);
            waitOut();
            assertEquals(List.of(new Rung(AT, HEARD_AT, TRAIL_RANK)), rung);
        }

        // decision click-travel-echoes-ring-at-rank-five
        @Test
        void clickTrailRingsAtFiveBesideAVibration() throws Exception {
            noises.vibration(AT, HEARD_AT, EXPLODE_FREQUENCY);
            noises.sound(clickStepField("scan_visual", "trail"), AT, HEARD_AT);
            assertEquals(List.of(new Rung(AT, HEARD_AT, TRAIL_RANK)), rung);
            waitOut();
            assertEquals(List.of(new Rung(AT, HEARD_AT, TRAIL_RANK)), rung);
        }

        // decision click-travel-echoes-ring-at-rank-five
        @Test
        void clickLandingRingsAtFifteen() throws Exception {
            noises.sound(clickStepField("sound", "id"), AT, HEARD_AT);
            assertEquals(List.of(new Rung(AT, HEARD_AT, LANDING_RANK)), rung);
        }

        // decision click-travel-echoes-ring-at-rank-five
        @Test
        void clickTrailPlaysTheLandingsComparatorClick() throws Exception {
            JsonObject sounds = classpathJson("/assets/goo/sounds.json");
            JsonObject trail = sounds.getAsJsonObject(pathOf(clickStepField("scan_visual", "trail")));
            JsonObject landing = sounds.getAsJsonObject(pathOf(clickStepField("sound", "id")));
            assertEquals(landing.get("sounds"), trail.get("sounds"));
            assertEquals(landing.get("subtitle"), trail.get("subtitle"));
        }

        /**
         * A field of the first step of Click's program holding the type.
         *
         * @param type  the step's type
         * @param field the field to read
         * @return the field's value
         */
        private static String clickStepField(String type, String field) throws Exception {
            JsonObject click = classpathJson("/data/goo/goo_abilities/deep_click.json");
            for (var behavior : click.getAsJsonArray("behaviors")) {
                JsonObject step = behavior.getAsJsonObject();
                if (type.equals(step.get("type").getAsString())) {
                    return step.get(field).getAsString();
                }
            }
            throw new AssertionError("Click names no " + type + " step");
        }

        private static String pathOf(String soundId) {
            return soundId.substring(soundId.indexOf(':') + 1);
        }

        @Test
        void aRankedSoundRingsAtOnceAtItsRank() {
            noises.sound(ZOMBIE_GROAN, AT, HEARD_AT);
            noises.sound(CLICK_SOUND, FAR_OFF, HEARD_AT);
            assertEquals(List.of(new Rung(AT, HEARD_AT, 9), new Rung(FAR_OFF, HEARD_AT, 15)), rung);
        }

        @Test
        void aRankedSoundKeepsItsRankBesideAVibration() {
            noises.vibration(AT, HEARD_AT, 2);
            noises.sound(CLICK_SOUND, AT, HEARD_AT);
            waitOut();
            assertEquals(List.of(new Rung(AT, HEARD_AT, 15)), rung);
        }

        private static JsonObject classpathJson(String path) throws Exception {
            try (InputStream in = SculkerNoisesTest.class.getResourceAsStream(path);
                 Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }

    @Nested
    class OneNoiseRingsOnce {

        private final SculkerEchoes echoes = new SculkerEchoes();
        private final SculkerNoises joined = new SculkerNoises(SculkerSoundRanks.shipped(), echoes::hear);

        @Test
        void soundThenVibration() {
            joined.sound(EXPLODE_SOUND, AT, HEARD_AT);
            joined.vibration(AT, HEARD_AT + 1, EXPLODE_FREQUENCY);
            joined.advance(HEARD_AT + SculkerNoises.WAIT_TICKS + 1);
            assertEquals(SculkerEchoes.RINGS, echoes.ripples().size());
        }

        @Test
        void vibrationThenSound() {
            joined.vibration(AT, HEARD_AT, EXPLODE_FREQUENCY);
            joined.sound(EXPLODE_SOUND, AT, HEARD_AT);
            joined.advance(HEARD_AT + SculkerNoises.WAIT_TICKS + 1);
            assertEquals(SculkerEchoes.RINGS, echoes.ripples().size());
        }

        @Test
        void aVibrationWithNoSoundRingsNothing() {
            joined.vibration(AT, HEARD_AT, EXPLODE_FREQUENCY);
            joined.advance(HEARD_AT + SculkerNoises.WAIT_TICKS + 1);
            assertTrue(echoes.ripples().isEmpty());
        }
    }
}
