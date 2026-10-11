package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityJson;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.OptionalInt;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Crystal's tap drops one glass shard per the drips its JSON counts, and a
 * shard runs its steps only on a mob it strikes
 * (decision shards-drip-falls-as-a-glass-shard).
 */
class ShardFallStepTest {

    /** crystal_shards_tap.json's drips per shard. */
    private static final int DRIPS_PER_SHARD = 4;
    private static final int STRUCK_ID = 7;

    /** A tap landing the shard falls through, as TapHost is. */
    private interface ShardTap extends DripHost, ShardFallHost {
    }

    @Test
    void crystalTapDropsAShardOnTheFourthDripOnly() {
        ShardTap tap = mock(ShardTap.class);
        when(tap.kind()).thenReturn(HostKind.TAP);
        when(tap.countDrip()).thenReturn(1, 2, 3, DRIPS_PER_SHARD);
        when(tap.fallShard()).thenReturn(OptionalInt.empty());
        List<Step> program = AbilityJson.decode("crystal_shards_tap").behaviors();

        for (int drip = 1; drip < DRIPS_PER_SHARD; drip++) {
            ProgramBehavior.forHost(program, HostKind.TAP).tick(tap);
        }
        verify(tap, never()).fallShard();

        ProgramBehavior.forHost(program, HostKind.TAP).tick(tap);
        verify(tap, times(1)).fallShard();
        verify(tap).resetDrips();
    }

    @Test
    void theTapProgramLoadsOnATapLanding() {
        assertDoesNotThrow(() -> ProgramBehavior.forHost(AbilityJson.decode("crystal_shards_tap").behaviors(),
                HostKind.TAP));
    }

    @Test
    void aShardStrikingAMobRunsItsStepsOnThatMob() {
        ShardFallHost host = mock(ShardFallHost.class);
        when(host.fallShard()).thenReturn(OptionalInt.of(STRUCK_ID));
        new ShardFallStep(List.of()).tick(new StepContext(host, 0, 0));
        verify(host).forEntity(eq(STRUCK_ID), any());
    }

    @Test
    void aShardStrikingTheLandingRunsNothing() {
        ShardFallHost host = mock(ShardFallHost.class);
        when(host.fallShard()).thenReturn(OptionalInt.empty());
        new ShardFallStep(List.of()).tick(new StepContext(host, 0, 0));
        verify(host, never()).forEntity(anyInt(), any());
    }
}
