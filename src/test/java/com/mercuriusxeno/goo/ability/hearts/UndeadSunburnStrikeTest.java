package com.mercuriusxeno.goo.ability.hearts;

import com.mercuriusxeno.goo.ability.nether.UndeadEvents;
import net.minecraft.world.damagesource.DamageSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Sunburn is aggravated against an Undead player's nether hearts: the whole hit reaches health past the shields
 * (decision undead-nether-hearts-burn-in-sunlight).
 */
class UndeadSunburnStrikeTest {

    private static final long NOW = 1_000L;
    private static final float FULL_HEALTH = 20f;
    private static final float HIT = 4f;
    /** Nether hearts fill only missing hearts, so the player is wounded to stand shields to strike. */
    private static final float WOUNDED_HEALTH = 12f;

    @Test
    void sunburnOnNetherHeartsIsAggravated() {
        HeartOverlay nether = HeartOverlay.NONE.hold(HeartKind.UNDEAD, WOUNDED_HEALTH, FULL_HEALTH,
                HeartOverlay.WHOLE_HIT, NOW);
        DamageSource sunburn = mock(DamageSource.class);
        when(sunburn.is(UndeadEvents.SUNBURN)).thenReturn(true);

        assertEquals(nether.aggravate(HIT, NOW), HeartOverlayEvents.strikeAt(nether, sunburn, HIT, WOUNDED_HEALTH, NOW));
    }
}