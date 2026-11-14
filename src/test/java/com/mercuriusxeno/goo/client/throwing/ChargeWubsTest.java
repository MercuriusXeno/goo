package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
            assertEquals(GooSoundIds.ZONE_WUB, cues.get(i).sound());
            assertEquals(pitches[i], cues.get(i).pitch(), DELTA);
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
