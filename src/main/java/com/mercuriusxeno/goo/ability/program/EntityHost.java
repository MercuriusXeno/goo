package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/**
 * The {@link StepHost} over the living entity a thrown blob struck: the
 * effect steps act on the target, reads answer its health and its distance
 * from the thrower, and world actions anchor at the target. A blob lands
 * in one tick and nothing ticks an entity afterwards, so this host has no
 * {@link HostCapability#TICKING} and a program with a waiting step refuses
 * at load (decision host-agnostic-runtime).
 *
 * @param level   the server level
 * @param target  the struck entity
 * @param thrower the entity that threw the blob, or null when unknown
 */
public record EntityHost(ServerLevel level, LivingEntity target, @Nullable Entity thrower) implements StepHost {

    private static final String LOG_UNKNOWN_EFFECT = "Potion step names status effect {}, which no registry holds";

    @Override
    public HostKind kind() {
        return HostKind.ENTITY;
    }

    @Override
    public OptionalDouble read(String name) {
        return switch (name) {
            case HostVariables.HEALTH -> OptionalDouble.of(target.getHealth());
            case HostVariables.MAX_HEALTH -> OptionalDouble.of(target.getMaxHealth());
            case HostVariables.DISTANCE -> OptionalDouble.of(distanceFromThrower());
            default -> OptionalDouble.empty();
        };
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
    public Direction placedFace() {
        throw HostCapability.PLACED_FACE.refusedBy(kind());
    }

    @Override
    public int stackCount() {
        throw HostCapability.STACKS.refusedBy(kind());
    }

    @Override
    public void decrementStack() {
        throw HostCapability.STACKS.refusedBy(kind());
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
        Vec3 center = target.position();
        return EntityScan.anyEntityWithin(level, center, shape, radius, filters);
    }

    @Override
    public void damageTarget(float amount, DamageKind source) {
        target.hurtServer(level, damageSource(source), amount);
    }

    @Override
    public void applyPotion(Identifier effect, int duration, int amplifier, boolean visible) {
        Optional<Holder.Reference<MobEffect>> holder = BuiltInRegistries.MOB_EFFECT.get(effect);
        if (holder.isEmpty()) {
            Goo.LOGGER.warn(LOG_UNKNOWN_EFFECT, effect);
            return;
        }
        target.addEffect(new MobEffectInstance(holder.get(), duration, amplifier, false, visible));
    }

    @Override
    public boolean targetPasses(Set<EntityFilter> filters) {
        return EntityScan.passes(target, filters);
    }

    @Override
    public void setTargetHealthFraction(float fraction) {
        target.setHealth(target.getHealth() * fraction);
    }

    /**
     * Maps a damage kind to the level's damage source.
     *
     * @param kind the kind the step named
     * @return the damage source
     */
    private DamageSource damageSource(DamageKind kind) {
        DamageSources sources = target.damageSources();
        return switch (kind) {
            case MAGIC -> sources.magic();
            case FREEZE -> sources.freeze();
            case STALAGMITE -> sources.stalagmite();
            case CACTUS -> sources.cactus();
        };
    }
}
