package com.mercuriusxeno.goo.client.ber.style;

import net.minecraft.util.ARGB;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A hive prism stands as a maroon pillar: the plain prism's quartz column
 * in the swarm's maroon, keeping the quartz's alpha
 * (decision hive-prism-pillar-eats-the-living).
 */
class HivePrismStyleTest {

    private static final int QUARTZ = 0xC8F0E8E0;

    @Test
    void theColumnIsTheQuartzInMaroon() {
        int column = HivePrismStyle.columnColor(QUARTZ);
        assertEquals(0x7A1A2A, column & 0xFFFFFF);
        assertEquals(ARGB.alpha(QUARTZ), ARGB.alpha(column));
    }
}
