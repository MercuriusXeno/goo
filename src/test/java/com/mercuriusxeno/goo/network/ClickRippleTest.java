package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A Click thrown from inside the ground ripples along the look through
 * whatever stands there, a sourceless vibration and the click sound at the
 * wave's point each tick out to the range and none past it; a Click thrown
 * from the surface still lands at the first wall alone (decision
 * submerged-click-ripples-through-the-ground-to-its-range). The level is a
 * mock capturing the game event and sound calls, so the stone the wave
 * passes through needs no blocks: the ripple reads none.
 */
class ClickRippleTest {

    private static final int THROW_TICK = 400;
    private static final Vec3 EYE = new Vec3(0.5, 40.62, 0.5);
    private static final Vec3 LOOK = new Vec3(0, 0, 1);
    /** The wall face a surfaced look meets, ten blocks south. */
    private static final BlockHitResult WALL = new BlockHitResult(new Vec3(0.5, 40.62, 10), Direction.NORTH,
            new BlockPos(0, 40, 10), false);
    /** Ticks past the ripple's end the test keeps draining, to see nothing more. */
    private static final int AFTER = 5;
    private static final double SAME_POINT = 1e-9;

    private final AbilityDefinition click = AbilityJson.decode("deep_click");
    private final ServerLevel level = mock(ServerLevel.class);
    private final Vec3 wallLanding = GooScanHandler.landingAt(EYE, WALL.getLocation(), click.delivery().range());
    private final int rippleTicks = (int) Math.ceil(click.delivery().range() / click.delivery().blocksPerTick());

    private ScanWaves thrown(boolean submerged) {
        ScanWaves waves = new ScanWaves();
        GooScanHandler.wavesFor(submerged, level, EYE, LOOK, wallLanding, click, THROW_TICK).forEach(waves::schedule);
        return waves;
    }

    @SuppressWarnings("unchecked")
    @Test
    void aSubmergedClickVibratesAndSoundsAtAnAdvancingPointEachTickToItsRange() {
        ScanWaves waves = thrown(true);
        double reached = 0;
        for (int tick = THROW_TICK + 1; tick <= THROW_TICK + rippleTicks; tick++) {
            clearInvocations(level);
            waves.drainArrived(tick);
            ArgumentCaptor<Vec3> at = ArgumentCaptor.forClass(Vec3.class);
            ArgumentCaptor<GameEvent.Context> context = ArgumentCaptor.forClass(GameEvent.Context.class);
            verify(level, times(1)).gameEvent(any(Holder.class), at.capture(), context.capture());
            // the click sounds on the way only as the client's trailing echoes, and from the server once, at the range
            int clicks = tick == THROW_TICK + rippleTicks ? 1 : 0;
            verify(level, times(clicks)).playSound(nullable(Entity.class), anyDouble(), anyDouble(), anyDouble(),
                    any(Holder.class), any(), anyFloat(), anyFloat());
            assertNull(context.getValue().sourceEntity());
            double along = at.getValue().subtract(EYE).dot(LOOK);
            assertTrue(along > reached, "tick " + tick + " should reach past " + reached + ", reached " + along);
            reached = along;
        }
        assertEquals(click.delivery().range(), reached, SAME_POINT);
    }

    @SuppressWarnings("unchecked")
    @Test
    void aSubmergedClickFallsSilentPastItsRange() {
        ScanWaves waves = thrown(true);
        for (int tick = THROW_TICK; tick <= THROW_TICK + rippleTicks; tick++) {
            waves.drainArrived(tick);
        }
        clearInvocations(level);
        for (int tick = THROW_TICK + rippleTicks + 1; tick <= THROW_TICK + rippleTicks + AFTER; tick++) {
            waves.drainArrived(tick);
        }
        verify(level, never()).gameEvent(any(Holder.class), any(Vec3.class), any(GameEvent.Context.class));
    }

    @SuppressWarnings("unchecked")
    @Test
    void aSurfacedClickLandsAtTheWallAlone() {
        ScanWaves waves = thrown(false);
        for (int tick = THROW_TICK; tick <= THROW_TICK + rippleTicks + AFTER; tick++) {
            waves.drainArrived(tick);
        }
        ArgumentCaptor<Vec3> at = ArgumentCaptor.forClass(Vec3.class);
        verify(level, times(1)).gameEvent(any(Holder.class), at.capture(), any(GameEvent.Context.class));
        assertEquals(wallLanding, at.getValue());
    }

    /** The last ripple carries the click's whole program, and the ones before it all of it but the sound. */
    @Test
    void theLastRippleRunsTheWholeProgram() {
        List<ScanWaves.Wave> waves = GooScanHandler.wavesFor(true, level, EYE, LOOK, wallLanding, click, THROW_TICK);
        assertEquals(click.behaviors(), waves.getLast().steps());
    }
}
