package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The {@link StepHost} over the player invoking a self ability: the player
 * is the target, the thrower and the anchor at once, so a step reading the
 * thrower's look reads the player's own (decision self-delivery-runs-on-player).
 * Every read and world action runs as the struck entity host would run it
 * on the player, and like that host it has no driver for later ticks.
 *
 * A drunk brew runs its ability on this host too, carrying the brew's
 * duration (decision brew-grants-the-self-ability-for-an-hour).
 *
 * @param level         the server level
 * @param player        the invoking player
 * @param brewDuration  the drunk brew's duration in ticks, empty for a glove invocation
 */
public record PlayerHost(ServerLevel level, ServerPlayer player, OptionalInt brewDuration)
        implements TargetHost, ExplodeHost, EntityScanHost {

    /**
     * The host of a glove invocation, which carries no brew duration.
     *
     * @param level  the server level
     * @param player the invoking player
     */
    public PlayerHost(ServerLevel level, ServerPlayer player) {
        this(level, player, OptionalInt.empty());
    }

    @Override
    public HostKind kind() {
        return HostKind.PLAYER;
    }

    @Override
    public LivingEntity target() {
        return player;
    }

    @Override
    public Entity thrower() {
        return player;
    }

    /**
     * Sets the player moving and clears the fall it has built up, so a
     * propelled player is not killed by the landing.
     *
     * @param motion the velocity to set, in blocks per tick
     */
    @Override
    public void push(Vec3 motion) {
        TargetHost.super.push(motion);
        player.resetFallDistance();
    }

    /**
     * The struck entity host over the player, which every read and world action runs through.
     *
     * @return the entity host with the player as target and thrower
     */
    private EntityHost asEntity() {
        return new EntityHost(level, player, player);
    }

    @Override
    public OptionalDouble read(String name) {
        return asEntity().read(name);
    }

    @Override
    public BlockPos position() {
        return player.blockPosition();
    }

    @Override
    public void explode(float power, ExplosionMode mode) {
        asEntity().explode(power, mode);
    }

    @Override
    public boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters) {
        return asEntity().anyEntityWithin(shape, radius, filters);
    }

    @Override
    public void forEachEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters,
                                    Consumer<TargetHost> body) {
        asEntity().forEachEntityWithin(shape, radius, filters, body);
    }

    @Override
    public void forEntity(int entityId, Consumer<TargetHost> body) {
        asEntity().forEntity(entityId, body);
    }

    @Override
    public void pullEntitiesWithin(double radius, double speed) {
        asEntity().pullEntitiesWithin(radius, speed);
    }

    @Override
    public void spawnParticles(ParticleBurst burst) {
        asEntity().spawnParticles(burst);
    }

    @Override
    public void playSound(SoundCue cue) {
        asEntity().playSound(cue);
    }
}
