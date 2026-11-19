package com.mercuriusxeno.goo.client.ber.style;

import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A hive prism stands as a meat brown pillar: the plain prism's quartz column
 * in zombie goo's meat brown, keeping the quartz's alpha
 * (decisions hive-prism-pillar-eats-the-living,
 * zombie-goo-is-colored-after-zombie-flesh-and-decay-stops-being-maroon).
 */
class HivePrismStyleTest {

    private static final int QUARTZ = 0xC8F0E8E0;

    @Test
    void theColumnIsTheQuartzInZombiesMeatBrown() {
        int column = HivePrismStyle.columnColor(QUARTZ);
        assertEquals(0x5E4428, column & 0xFFFFFF);
        assertEquals(ARGB.alpha(QUARTZ), ARGB.alpha(column));
    }
}
