package com.mercuriusxeno.goo.client.ability;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A forming prism's sounds: growing crunches climbing in pitch across the
 * forming, then one ring the tick it stands whole
 * (decision prism-blob-becomes-a-milky-quartz-crystal).
 */
class PrismFormingSoundsTest {

    @Test
    void theCrunchesClimbAcrossTheFormingAndTheRingSoundsAsItSetsSolid() {
        List<PrismFormingSounds.Cue> cues = PrismFormingSounds.cuesOf(100, 16);

        assertEquals(PrismFormingSounds.GROW_AT.length + 1, cues.size());
        assertEquals(100, cues.getFirst().tick());
        for (int index = 1; index < cues.size() - 1; index++) {
            assertFalse(cues.get(index).solid());
            assertTrue(cues.get(index).tick() > cues.get(index - 1).tick());
            assertTrue(cues.get(index).pitch() > cues.get(index - 1).pitch(), "each crunch climbs in pitch");
        }
        PrismFormingSounds.Cue ring = cues.getLast();
        assertTrue(ring.solid());
        assertEquals(116, ring.tick());
    }
}
