package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Charge;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Blast's charge plays the outsider wub once at each stage it reaches, at
 * pitch 1.0 for the 2 block stage and 0.125 higher for each stage after, and
 * a stage the caster's unstable cannot pay for does not sound
 * (decision blast-charge-wubs-higher-at-each-stage).
 */
class ChargeWubsTest {

    private static final Charge BLAST = AbilityJson.decode("unstable_blast").delivery().charge();
    private static final int FULL_CHARGE = 100;
    private static final int RESTING = 40;
    private static final double PAYS_FOR_ALL = 7;
    private static final double PAYS_FOR_FOUR = 4;
    private static final float DELTA = 1e-6f;
    private static final String SOUNDS_JSON = "/assets/goo/sounds.json";

    private static List<int[]> heardOver(ChargeWubs wubs, int fromTick, int toTick, double paidFor,
                                         List<ChargeWubs.Cue> cues) {
        List<int[]> ticks = new ArrayList<>();
        for (int tick = fromTick; tick <= toTick; tick++) {
            int at = tick;
            wubs.tick(BLAST, tick, paidFor).ifPresent(cue -> {
                cues.add(cue);
                ticks.add(new int[] {at});
            });
        }
        return ticks;
    }

    @Test
    void eachStageWubsOnceHigherThanTheLast() {
        ChargeWubs wubs = new ChargeWubs();
        List<ChargeWubs.Cue> cues = new ArrayList<>();
        List<int[]> ticks = heardOver(wubs, 0, FULL_CHARGE, PAYS_FOR_ALL, cues);

        assertEquals(List.of(0, 20, 40, 60, 80, 100), ticks.stream().map(at -> at[0]).toList());
        float[] pitches = {1.0f, 1.125f, 1.25f, 1.375f, 1.5f, 1.625f};
        for (int i = 0; i < pitches.length; i++) {
            assertEquals(GooSoundIds.CHARGE_WUB, cues.get(i).sound());
            assertEquals(pitches[i], cues.get(i).pitch(), DELTA);
        }
    }

    /**
     * A sound event listing several files plays one of them at random, so
     * stages wubbed from takes on different base tones fall off one harmonic
     * series; the charge's wub lists one take alone.
     */
    @Test
    void everyStageWubsTheOneTake() throws IOException {
        ChargeWubs wubs = new ChargeWubs();
        List<ChargeWubs.Cue> cues = new ArrayList<>();
        heardOver(wubs, 0, FULL_CHARGE, PAYS_FOR_ALL, cues);

        for (ChargeWubs.Cue cue : cues) {
            assertEquals(List.of("goo:effects/zone_wub"), filesOf(cue.sound()), cue.sound().toString());
        }
    }

    private static List<String> filesOf(Identifier sound) throws IOException {
        try (InputStream in = ChargeWubsTest.class.getResourceAsStream(SOUNDS_JSON)) {
            assertNotNull(in, SOUNDS_JSON + " is not on the classpath");
            JsonObject sounds = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject();
            assertTrue(sounds.has(sound.getPath()), "sounds.json names no " + sound.getPath());
            JsonArray takes = sounds.getAsJsonObject(sound.getPath()).getAsJsonArray("sounds");
            List<String> files = new ArrayList<>();
            takes.forEach(take -> files.add(take.getAsJsonObject().get("name").getAsString()));
            return files;
        }
    }

    @Test
    void aHoldRestingAtFullChargeAddsNone() {
        ChargeWubs wubs = new ChargeWubs();
        heardOver(wubs, 0, FULL_CHARGE, PAYS_FOR_ALL, new ArrayList<>());
        List<ChargeWubs.Cue> resting = new ArrayList<>();
        heardOver(wubs, FULL_CHARGE + 1, FULL_CHARGE + RESTING, PAYS_FOR_ALL, resting);

        assertEquals(List.of(), resting);
    }

    @Test
    void aStageTheGooCannotPayForDoesNotSound() {
        ChargeWubs wubs = new ChargeWubs();
        List<ChargeWubs.Cue> cues = new ArrayList<>();
        heardOver(wubs, 0, FULL_CHARGE, PAYS_FOR_FOUR, cues);

        assertEquals(3, cues.size(), "only the 2, 3 and 4 block stages should sound");
    }
}
