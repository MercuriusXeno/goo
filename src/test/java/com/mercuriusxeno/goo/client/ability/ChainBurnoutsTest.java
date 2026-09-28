package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.FieldSource;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ChainBurnouts holds each burnout from the game time it began, resolves its
 * visual by goo type, and drops it once that type's explosion has run.
 */
class ChainBurnoutsTest {

    private static final BlockPos POS = new BlockPos(3, 64, -7);
    private static final long START = 48_200L;
    private static final String ABILITY = "goo:test_ability";

    static final java.util.List<ResourceKey<GooTypeDefinition>> CHAIN_TYPES = BurnoutVisuals.CHAIN_TYPES;

    @ParameterizedTest
    @FieldSource("CHAIN_TYPES")
    void burnoutResolvesItsTypesVisualAndDropsAfterItsDuration(ResourceKey<GooTypeDefinition> gooType) {
        ChainBurnouts burnouts = new ChainBurnouts();
        ChainBurnouts.Burnout burnout = burnouts.add(POS, Direction.UP, gooType, ABILITY, 2, START);

        assertEquals(gooType, burnout.visual().gooType());
        assertEquals(START, burnout.startTick());
        int duration = burnout.visual().durationTicks();
        if (duration > 0) {
            assertTrue(burnouts.live(START + duration - 1).contains(burnout), gooType + " dropped early");
        }
        assertFalse(burnouts.live(START + duration).contains(burnout), gooType + " outlives its duration");
    }

    @Test
    void unstableResolvesItsOwnExplosion() {
        assertSame(UnstableExplosionVisual.INSTANCE, BurnoutVisuals.forType(GooTypes.UNSTABLE));
    }

    @Test
    void rockResolvesItsOwnExplosion() {
        assertSame(RockExplosionVisual.INSTANCE, BurnoutVisuals.forType(GooTypes.ROCK));
    }

    @Test
    void progressRunsFromStartToDuration() {
        ChainBurnouts.Burnout burnout = new ChainBurnouts().add(POS, Direction.UP, GooTypes.UNSTABLE,
                ABILITY, 1, START);
        int duration = burnout.visual().durationTicks();
        assertEquals(0f, burnout.progress(START), 0f);
        assertEquals(0.5f, burnout.progress(START + duration / 2f), 1e-4f);
        assertEquals(1f, burnout.progress(START + duration * 2f), 0f);
    }
}
