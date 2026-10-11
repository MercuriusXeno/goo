package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A one-tick host whose world actions anchor at one point of a block: the
 * entity scans, the pull, the particles and the sound all reach out from
 * that anchor, so a tap's drip and a blob's landing share them.
 */
public interface AnchoredWorldHost extends EntityScanHost {

    /**
     * Returns the server level the host acts in.
     *
     * @return the level
     */
    ServerLevel level();

    /**
     * Returns the point the host's world actions reach out from.
     *
     * @return the anchor
     */
    Vec3 anchor();

    /**
     * Returns the axis a particle burst spreads across.
     *
     * @return the burst axis
     */
    Direction.Axis burstAxis();

    @Override
    default boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters) {
        return EntityScan.anyEntityWithin(level(), anchor(), shape, radius, filters, null);
    }

    @Override
    default void forEachEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters,
                                     Consumer<TargetHost> body) {
        BlockAnchoredActions.forEachEntityWithin(level(), anchor(), shape, radius, filters, body);
    }

    @Override
    default void forEntity(int entityId, Consumer<TargetHost> body) {
        BlockAnchoredActions.forEntity(level(), entityId, body);
    }

    @Override
    default void pullEntitiesWithin(double radius, double speed) {
        EntityPull.pullWithin(level(), anchor(), radius, speed, null);
    }

    @Override
    default void liftEntitiesInColumn(double radius, double height, double speed) {
        EntityLift.liftInColumn(level(), anchor(), radius, height, speed);
    }

    @Override
    default void rideShaftAbove(int cap, double rise, double sink) {
        EntityLift.rideShaft(level(), BlockPos.containing(anchor()).above(), cap, rise, sink);
    }

    @Override
    default void spawnParticles(ParticleBurst burst) {
        BlockAnchoredActions.sendBurst(level(), anchor(), burstAxis(), burst);
    }

    @Override
    default void playSound(SoundCue cue) {
        SoundPlays.play(level(), anchor(), cue);
    }
}
