package com.mercuriusxeno.goo.ability.hearts;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Iceborn's frozen hearts: a physical hit finds each worth half a heart,
 * any other hit lands whole on them, they deplete before the bar beneath,
 * and fire thaws them, which only a standing Iceborn overlay heeds.
 */
class IcebornHeartsTest {

    private static final long NOW = 1_000L;
    private static final float FULL_HEALTH = 20f;
    private static final float DELTA = 1e-6f;

    private static DamageSource source(boolean bypassesArmor, boolean fire) {
        DamageSource source = mock(DamageSource.class);
        when(source.is(DamageTypeTags.BYPASSES_ARMOR)).thenReturn(bypassesArmor);
        when(source.is(DamageTypeTags.IS_FIRE)).thenReturn(fire);
        return source;
    }

    private static HeartOverlay frozenHearts() {
        return HeartOverlay.NONE.hold(HeartKind.ICEBORN, FULL_HEALTH, FULL_HEALTH, HeartOverlay.WHOLE_HIT, NOW);
    }

    @Test
    void aPhysicalHitFindsAFrozenHeartWorthHalfAHeart() {
        assertEquals(2f, HeartOverlayEvents.iceShare(source(false, false)));
    }

    @Test
    void aHitBypassingArmorLandsWholeOnFrozenHearts() {
        assertEquals(HeartOverlay.WHOLE_HIT, HeartOverlayEvents.iceShare(source(true, false)));
    }

    @Test
    void aPhysicalHitBurnsTwiceItsDamageInFrozenHalvesBeforeTheBar() {
        HeartOverlay laid = frozenHearts();
        HeartOverlay.Drained drained = laid.drainScaled(1f, HeartOverlayEvents.iceShare(source(false, false)), NOW);
        assertEquals(laid.shieldHalves() - 2, drained.overlay().shieldHalves());
        assertEquals(0f, drained.remainder(), DELTA);
    }

    @Test
    void fireThawsAStandingIcebornOverlay() {
        assertTrue(HeartOverlayEvents.thawsIceborn(frozenHearts(), source(true, true)));
    }

    @Test
    void fireLeavesAnotherKindAlone() {
        HeartOverlay bark = HeartOverlay.NONE.hold(HeartKind.BARKSKIN, FULL_HEALTH, FULL_HEALTH,
                HeartOverlay.WHOLE_HIT, NOW);
        assertFalse(HeartOverlayEvents.thawsIceborn(bark, source(true, true)));
    }

    @Test
    void aHitThatIsNoFireThawsNothing() {
        assertFalse(HeartOverlayEvents.thawsIceborn(frozenHearts(), source(false, false)));
    }
}
