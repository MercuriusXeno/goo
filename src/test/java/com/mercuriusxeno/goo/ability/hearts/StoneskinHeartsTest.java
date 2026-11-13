package com.mercuriusxeno.goo.ability.hearts;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stoneskin's stone hearts: an explosion or a pickaxe finds each worth half a heart, a physical hit takes the
 * brew's multiplier, and a hit bypassing armor lands whole on them
 * (decision stoneskin-stone-hearts-block-regeneration).
 */
class StoneskinHeartsTest {

    /** A multiplier no shipped kind uses, so a hit answering it can only have read the brew's. */
    private static final float BREW_MULTIPLIER = 0.37f;
    private static final float HALF_A_HEART_SHARE = 2f;

    private static DamageSource source(boolean explosion, boolean bypassesArmor, @Nullable ItemStack weapon) {
        DamageSource source = mock(DamageSource.class);
        when(source.is(DamageTypeTags.IS_EXPLOSION)).thenReturn(explosion);
        when(source.is(DamageTypeTags.BYPASSES_ARMOR)).thenReturn(bypassesArmor);
        when(source.getWeaponItem()).thenReturn(weapon);
        return source;
    }

    @Test
    void anExplosionFindsAStoneHeartWorthHalfAHeart() {
        assertEquals(HALF_A_HEART_SHARE, HeartOverlayEvents.stoneShare(source(true, false, null), BREW_MULTIPLIER));
    }

    @Test
    void aPickaxeFindsAStoneHeartWorthHalfAHeart() {
        ItemStack pickaxe = mock(ItemStack.class);
        when(pickaxe.is(ItemTags.PICKAXES)).thenReturn(true);
        assertEquals(HALF_A_HEART_SHARE, HeartOverlayEvents.stoneShare(source(false, false, pickaxe), BREW_MULTIPLIER));
    }

    @Test
    void aPhysicalHitTakesTheBrewsMultiplier() {
        assertEquals(BREW_MULTIPLIER, HeartOverlayEvents.stoneShare(source(false, false, null), BREW_MULTIPLIER));
    }

    @Test
    void aHitBypassingArmorLandsWholeOnStoneHearts() {
        assertEquals(HeartOverlay.WHOLE_HIT, HeartOverlayEvents.stoneShare(source(false, true, null), BREW_MULTIPLIER));
    }
}
