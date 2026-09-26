package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.client.TargetResult;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The aim resolves once per client tick, and every reader between ticks,
 * the frames, the throw and the chain marker's targeted look, reads that
 * one answer (decision render-context-is-the-one-emitter).
 */
class AimStateTest {

    private static final int FRAMES = 3;
    private static final BlockPos MARKER = new BlockPos(4, 70, -2);
    private static final BlockPos OTHER = new BlockPos(5, 70, -2);

    /** A resolver that records each seed it is handed and answers a marker hit. */
    private static final class CountingResolver implements AimState.AimResolver {
        private final List<AimAssistResolver.AimHit> seeds = new ArrayList<>();
        private final BlockPos marker;

        CountingResolver(BlockPos marker) {
            this.marker = marker;
        }

        @Override
        public AimState.Resolution resolve(AimAssistResolver.AimHit seed) {
            seeds.add(seed);
            return new AimState.Resolution(TargetResult.chainMarker(marker),
                    new AimAssistResolver.AimHit.ChainMarkerHit(marker));
        }
    }

    @Test
    void oneTickResolvesOnceAndEveryFrameAndTheThrowReadItsAnswer() {
        AimState aim = new AimState();
        CountingResolver resolver = new CountingResolver(MARKER);

        aim.update(resolver, 0);
        List<TargetResult> frameReads = new ArrayList<>();
        for (int frame = 0; frame < FRAMES; frame++) {
            frameReads.add(aim.target());
        }
        TargetResult throwRead = aim.target();

        assertEquals(1, resolver.seeds.size());
        TargetResult resolved = TargetResult.chainMarker(MARKER);
        for (TargetResult read : frameReads) {
            assertEquals(resolved, read);
        }
        assertEquals(resolved, throwRead);
        assertTrue(aim.isAimedAtMarker(MARKER));
        assertFalse(aim.isAimedAtMarker(OTHER));
    }

    @Test
    void theNextTickSeedsFromTheHitThisTickFound() {
        AimState aim = new AimState();
        CountingResolver first = new CountingResolver(MARKER);
        CountingResolver second = new CountingResolver(OTHER);

        aim.update(first, 0);
        aim.update(second, 0);

        assertNull(first.seeds.get(0));
        assertEquals(new AimAssistResolver.AimHit.ChainMarkerHit(MARKER), second.seeds.get(0));
        assertTrue(aim.isAimedAtMarker(OTHER));
    }

    @Test
    void clearingDropsTheTargetAndTheSeed() {
        AimState aim = new AimState();
        aim.update(new CountingResolver(MARKER), 0);

        aim.clear();

        assertSame(TargetResult.NONE, aim.target());
        assertFalse(aim.isAimedAtMarker(MARKER));
    }
}
