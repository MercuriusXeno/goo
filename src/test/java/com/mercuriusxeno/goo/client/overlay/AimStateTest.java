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
 * The aim resolves once per frame, and every reader until the next frame,
 * the draw, the throw and the chain marker's targeted look, reads that one
 * answer (decisions render-context-is-the-one-emitter,
 * aim-target-follows-client-aim).
 */
class AimStateTest {

    private static final int READS = 3;
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
    void oneFrameResolvesOnceAndEveryReaderAndTheThrowReadItsAnswer() {
        AimState aim = new AimState();
        CountingResolver resolver = new CountingResolver(MARKER);

        aim.update(resolver, 0);
        List<TargetResult> reads = new ArrayList<>();
        for (int read = 0; read < READS; read++) {
            reads.add(aim.target());
        }
        TargetResult throwRead = aim.target();

        assertEquals(1, resolver.seeds.size());
        TargetResult resolved = TargetResult.chainMarker(MARKER);
        for (TargetResult read : reads) {
            assertEquals(resolved, read);
        }
        assertEquals(resolved, throwRead);
        assertTrue(aim.isAimedAtMarker(MARKER));
        assertFalse(aim.isAimedAtMarker(OTHER));
    }

    @Test
    void theNextFrameSeedsFromTheHitThisFrameFound() {
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
    void twoFramesInsideOneTickEachSetTheTarget() {
        AimState aim = new AimState();

        aim.update(new CountingResolver(MARKER), 0);
        aim.update(new CountingResolver(OTHER), 0);

        assertEquals(TargetResult.chainMarker(OTHER), aim.target());
    }

    @Test
    void aFrozenResolutionHoldsAcrossFrames() {
        AimState aim = new AimState();
        TargetResult frozen = TargetResult.chainMarker(MARKER);

        for (int frame = 0; frame < READS; frame++) {
            aim.update(seed -> new AimState.Resolution(frozen, seed), 0);
        }

        assertEquals(frozen, aim.target());
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
