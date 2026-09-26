package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The {@link StepHost} over the living entity a thrown blob struck: it
 * hands the target to the effect steps, which act on it (decision
 * step-tick-holds-effect); reads answer its health and its distance
 * from the thrower, and world actions anchor at the target. A blob lands
 * in one tick and nothing ticks an entity afterwards, so this host has no
 * {@link HostCapability#TICKING} and a program with a waiting step refuses
 * at load (decision host-agnostic-runtime).
 *
 * @param level   the server level
 * @param target  the struck entity
 * @param thrower the entity that threw the blob, or null when unknown
 */
public record EntityHost(ServerLevel level, LivingEntity target, @Nullable Entity thrower)
        implements TargetHost, ExplodeHost, EntityScanHost {

    private static final double BODY_CENTER = 0.5;

    @Override
    public HostKind kind() {
        return HostKind.ENTITY;
    }

    @Override
    public LivingEntity target() {
        return target;
    }

    @Override
    public @Nullable Entity thrower() {
        return thrower;
    }

    @Override
    public OptionalDouble read(String name) {
        return switch (name) {
            case HostVariables.HEALTH -> OptionalDouble.of(target.getHealth());
            case HostVariables.MAX_HEALTH -> OptionalDouble.of(target.getMaxHealth());
            case HostVariables.DISTANCE -> OptionalDouble.of(distanceFromThrower());
            case HostVariables.UNDEAD -> flag(target.isInvertedHealAndHarm());
            case HostVariables.SPRINTING -> flag(isSprintingPlayer());
            default -> readCounter(name);
        };
    }

    /**
     * Reads a counter the target keeps, named in an expression by its id;
     * a name that is no counter id is unbound.
     *
     * @param name the variable name
     * @return the counter's value, or empty for a name that is no counter id
     */
    private OptionalDouble readCounter(String name) {
        if (!HostVariables.isCounter(name)) {
            return OptionalDouble.empty();
        }
        Identifier id = Identifier.tryParse(name);
        return id == null ? OptionalDouble.empty() : OptionalDouble.of(counters().read(id));
    }

    /**
     * Returns the counters the target keeps, empty for a target never counted.
     *
     * @return the counters
     */
    public EntityCounters counters() {
        return target.getData(GooAttachments.ENTITY_COUNTERS);
    }

    /**
     * Reads a target predicate as the variable value an expression weighs by.
     *
     * @param holds whether the predicate holds
     * @return one when it holds, zero otherwise
     */
    private static OptionalDouble flag(boolean holds) {
        return OptionalDouble.of(holds ? 1 : 0);
    }

    /**
     * Tests whether the target is a player sprinting; only a player's
     * sprint doubles the crystal cloud's shred rate.
     *
     * @return true for a sprinting player
     */
    private boolean isSprintingPlayer() {
        return target instanceof Player player && player.isSprinting();
    }

    /**
     * Measures the target's distance from the thrower.
     *
     * @return the distance in blocks, zero with no thrower
     */
    private double distanceFromThrower() {
        return thrower == null ? 0 : target.position().distanceTo(thrower.position());
    }

    @Override
    public BlockPos position() {
        return target.blockPosition();
    }


    @Override
    public void explode(float power, ExplosionMode mode) {
        Level.ExplosionInteraction interaction = mode == ExplosionMode.TNT
                ? Level.ExplosionInteraction.TNT
                : Level.ExplosionInteraction.NONE;
        level.explode(null, target.getX(), target.getY(), target.getZ(), power, interaction);
    }

    @Override
    public boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters) {
        return EntityScan.anyEntityWithin(level, target.position(), shape, radius, filters, target);
    }

    @Override
    public void forEachEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters,
                                    Consumer<TargetHost> body) {
        EntityScan.forEachLivingWithin(level, target.position(), shape, radius, filters, target,
                living -> body.accept(new EntityHost(level, living, thrower)));
    }

    @Override
    public void forEntity(int entityId, Consumer<TargetHost> body) {
        if (level.getEntity(entityId) instanceof LivingEntity living && living.isAlive()) {
            body.accept(new EntityHost(level, living, thrower));
        }
    }


    @Override
    public void pullEntitiesWithin(double radius, double speed) {
        EntityPull.pullWithin(level, target.position(), radius, speed, target);
    }


    @Override
    public void spawnParticles(ParticleBurst burst) {
        SimpleParticles.resolve(burst.particle()).ifPresent(particle -> level.sendParticles(particle,
                target.getX(), target.getY(BODY_CENTER) + burst.lift(), target.getZ(),
                burst.count(), burst.spreadAcross(), burst.spreadAlong(), burst.spreadAcross(), burst.speed()));
    }

    @Override
    public void playSound(SoundCue cue) {
        SoundPlays.play(level, new Vec3(target.getX(), target.getY(BODY_CENTER), target.getZ()), cue);
    }


}
