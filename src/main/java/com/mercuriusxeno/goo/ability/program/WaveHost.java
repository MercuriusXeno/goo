package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * The {@link StepHost} over the point a scan's wave reaches: the world around
 * it, acted on the tick the wave arrives, with no target, no thrower and no
 * driver of its own. Deep's Click sounds and vibrates here, so nothing it sets
 * off traces back to the player who clicked
 * (decisions click-is-a-hit-scan-drawn-as-a-delayed-wave,
 * click-vibration-carries-no-source-entity).
 *
 * @param level the server level
 * @param point where the wave is this tick
 */
public record WaveHost(ServerLevel level, Vec3 point) implements AnchoredWorldHost, VibrationHost {

    private static final String LOG_UNKNOWN_EVENT = "Vibrate step names {}, which no registry holds";

    @Override
    public HostKind kind() {
        return HostKind.WAVE;
    }

    @Override
    public OptionalDouble read(String name) {
        return OptionalDouble.empty();
    }

    @Override
    public BlockPos position() {
        return BlockPos.containing(point);
    }

    @Override
    public Vec3 anchor() {
        return point;
    }

    @Override
    public Direction.Axis burstAxis() {
        return Direction.Axis.Y;
    }

    @Override
    public void vibrate(Identifier event) {
        Optional<Holder.Reference<GameEvent>> holder = BuiltInRegistries.GAME_EVENT.get(event);
        if (holder.isEmpty()) {
            Goo.LOGGER.warn(LOG_UNKNOWN_EVENT, event);
            return;
        }
        // click-vibration-carries-no-source-entity: no thrower for a warden to grow angry at or a shrieker to answer
        level.gameEvent(holder.get(), point, GameEvent.Context.of((Entity) null));
    }
}
