package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.UnmakeDrops;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.world.phys.Vec3;
import com.mercuriusxeno.goo.item.GooContents;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Remains morph from the tick they were unmade for the morph's length, then
 * are forgotten as the item drops (decision unmake-waves-dissolve-by-crucible-cost).
 */
class MorphingRemainsTest {

    private static final float DELTA = 1e-5f;

    @Test
    void aMorphRunsItsLengthThenEnds() {
        MorphingRemains morphing = new MorphingRemains();
        morphing.begin(Vec3.ZERO, new GooContents(Map.of(GooTypes.ROCK, 100)), 1f, 1f, 100);

        assertEquals(0.5f, morphing.morphs(100f + MorphingRemains.MORPH_TICKS / 2f).getFirst().progress(), DELTA);
        assertTrue(morphing.morphs(100f + MorphingRemains.MORPH_TICKS).isEmpty());
    }

    @Test
    void theClientMorphRunsAsLongAsTheServerHoldsTheDrop() {
        assertEquals(UnmakeDrops.MORPH_TICKS, MorphingRemains.MORPH_TICKS);
    }
}
