package com.mercuriusxeno.goo.ability.hearts;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Bark hearts take fire and an axe as aggravated hits, whole to health and burning twice their hearts in bark, and
 * an arrow as an ordinary hit that strips bark before health (decision barkskin-bark-hearts-thorn-and-burn).
 */
class BarkskinStrikeTest {

    private static final long NOW = 1_000L;
    private static final float FULL_HEALTH = 20f;
    private static final float HIT = 4f;
    private static final HeartOverlay BARK = HeartOverlay.NONE.hold(HeartKind.BARKSKIN, FULL_HEALTH, FULL_HEALTH,
            HeartOverlay.WHOLE_HIT, NOW);

    private static DamageSource source(boolean fire, @Nullable ItemStack weapon) {
        DamageSource source = mock(DamageSource.class);
        when(source.is(DamageTypeTags.IS_FIRE)).thenReturn(fire);
        when(source.getWeaponItem()).thenReturn(weapon);
        return source;
    }

    private static HeartOverlay.Drained struck(DamageSource source) {
        return HeartOverlayEvents.strikeAt(BARK, source, HIT, FULL_HEALTH, NOW);
    }

    @Test
    void fireAndAnAxeAggravateBarkAndAnArrowStripsIt() {
        ItemStack axe = mock(ItemStack.class);
        when(axe.is(ItemTags.AXES)).thenReturn(true);

        assertEquals(List.of(BARK.aggravate(HIT, NOW), BARK.aggravate(HIT, NOW), BARK.drain(HIT, NOW)),
                List.of(struck(source(true, null)), struck(source(false, axe)), struck(source(false, null))));
    }
}
