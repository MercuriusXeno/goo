package com.mercuriusxeno.goo.block.ability;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The wisps' shared knowledge of what their light will reach: a spot within
 * a settled wisp's lit steps of any claimed spot reads lit already, so no
 * wisp heads for a spot another wisp will light before it arrives.
 * wisps-travel-as-entities-then-settle-as-glowy-air-blocks
 */
class WispClaimsTest {

    /** A settled wisp's light, 15, keeps cells lit seven steps out at the default threshold of 8. */
    private static final int LIT_STEPS = 7;
    private static final BlockPos CLAIMED = new BlockPos(10, 64, 10);

    private final Level level = mock(Level.class);
    private final WispClaims claims = new WispClaims();

    WispClaimsTest() {
        when(level.dimension()).thenReturn(Level.OVERWORLD);
        when(level.getGameTime()).thenReturn(100L);
        claims.claim(level, CLAIMED);
    }

    @Test
    void aSpotWithinAClaimsLightReadsLitAlready() {
        assertTrue(claims.foreseesLight(level, CLAIMED.offset(2, 1, 0), LIT_STEPS));
    }

    @Test
    void aSpotPastAClaimsLightReadsDark() {
        assertFalse(claims.foreseesLight(level, CLAIMED.offset(5, 3, 0), LIT_STEPS));
    }
}
