package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * A Click thrown at a wall along the look lands at the wall's face and, on
 * the tick its wave arrives there and on no tick before, sets off one game
 * event carrying no source entity and plays one sound, both at the landing
 * (decisions click-is-a-hit-scan-drawn-as-a-delayed-wave,
 * click-vibration-carries-no-source-entity). The level is a mock capturing
 * the game event and sound calls.
 */
class ScanWavesTest {

    private static final int THROW_TICK = 400;
    private static final Vec3 EYE = new Vec3(0.5, 65.62, 0.5);
    /** The face of the wall the look meets, ten blocks south of the eye's cell. */
    private static final BlockHitResult WALL = new BlockHitResult(new Vec3(0.5, 65.62, 10), Direction.NORTH,
            new BlockPos(0, 65, 10), false);
    /** How near the wall's face a landing on it stands. */
    private static final double AT_THE_FACE = 0.1;

    private final AbilityDefinition click = AbilityJson.decode("deep_click");
    private final ServerLevel level = mock(ServerLevel.class);
    private final Vec3 landing = GooScanHandler.landingAt(EYE, WALL.getLocation(), click.delivery().range());
    private final int delay = click.delivery().waveTicks(EYE.distanceTo(landing));

    private ScanWaves thrownAtTheWall() {
        ScanWaves waves = new ScanWaves();
        waves.schedule(ScanWaves.thrown(level, EYE, landing, click.delivery(), click.id(), click.behaviors(),
                THROW_TICK));
        return waves;
    }

    @Test
    void landsAtTheWallsFace() {
        assertTrue(landing.distanceTo(WALL.getLocation()) <= AT_THE_FACE, landing.toString());
    }

    @Test
    void waitsTheDelayItsSpeedGivesTheDistance() {
        assertTrue(delay > 1, "a wall ten blocks off should take more than a tick to reach: " + delay);
    }

    @SuppressWarnings("unchecked")
    @Test
    void nothingSoundsOrVibratesBeforeTheWaveArrives() {
        ScanWaves waves = thrownAtTheWall();
        for (int tick = THROW_TICK; tick < THROW_TICK + delay; tick++) {
            waves.drainArrived(tick);
        }
        verify(level, never()).gameEvent(any(Holder.class), any(Vec3.class), any(GameEvent.Context.class));
        verify(level, never()).playSound(nullable(Entity.class), anyDouble(), anyDouble(), anyDouble(),
                any(Holder.class), any(SoundSource.class), anyFloat(), anyFloat());
    }

    @SuppressWarnings("unchecked")
    @Test
    void theArrivalVibratesOnceWithNoSourceAtTheLanding() {
        ScanWaves waves = thrownAtTheWall();
        waves.drainArrived(THROW_TICK + delay);
        waves.drainArrived(THROW_TICK + delay + 1);
        ArgumentCaptor<Vec3> at = ArgumentCaptor.forClass(Vec3.class);
        ArgumentCaptor<GameEvent.Context> context = ArgumentCaptor.forClass(GameEvent.Context.class);
        verify(level, times(1)).gameEvent(any(Holder.class), at.capture(), context.capture());
        assertEquals(landing, at.getValue());
        assertNull(context.getValue().sourceEntity());
    }

    @SuppressWarnings("unchecked")
    @Test
    void theArrivalSoundsOnceAtTheLanding() {
        ScanWaves waves = thrownAtTheWall();
        waves.drainArrived(THROW_TICK + delay);
        waves.drainArrived(THROW_TICK + delay + 1);
        ArgumentCaptor<Double> x = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> y = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> z = ArgumentCaptor.forClass(Double.class);
        verify(level, times(1)).playSound(nullable(Entity.class), x.capture(), y.capture(), z.capture(),
                any(Holder.class), any(SoundSource.class), anyFloat(), anyFloat());
        assertEquals(landing, new Vec3(x.getValue(), y.getValue(), z.getValue()));
    }
}
