package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.BlockEffect;
import com.mercuriusxeno.goo.ability.LayerAudio;
import com.mercuriusxeno.goo.ability.LayerVisuals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.function.Consumer;

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
    private static final String LOG_UNKNOWN_ITEM = "Drop step names item {}, which no registry holds";
    private static final float PERCENT = 100;
    private static final double BODY_CENTER = 0.5;
    private static final double HALF = 0.5;

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
            case HostVariables.UNDEAD -> flag(target.isInvertedHealAndHarm());
            case HostVariables.SPRINTING -> flag(isSprintingPlayer());
            default -> OptionalDouble.empty();
        };
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
        return EntityScan.anyEntityWithin(level, target.position(), shape, radius, filters, target);
    }

    @Override
    public void forEachEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters,
                                    Consumer<StepHost> body) {
        EntityScan.forEachLivingWithin(level, target.position(), shape, radius, filters, target,
                living -> body.accept(new EntityHost(level, living, thrower)));
    }

    @Override
    public void forEntity(int entityId, Consumer<StepHost> body) {
        if (level.getEntity(entityId) instanceof LivingEntity living && living.isAlive()) {
            body.accept(new EntityHost(level, living, thrower));
        }
    }

    @Override
    public int targetId() {
        return target.getId();
    }

    @Override
    public Vec3 targetCenter() {
        return target.getBoundingBox().getCenter();
    }

    @Override
    public FieldEffectState fieldEffect() {
        throw HostCapability.FIELD_EFFECT.refusedBy(kind());
    }

    @Override
    public PhasedState phased() {
        throw HostCapability.PHASED.refusedBy(kind());
    }

    @Override
    public void pullEntitiesWithin(double radius, double speed) {
        EntityPull.pullWithin(level, target.position(), radius, speed, target);
    }

    @Override
    public void consumeValuedBlocks(int radius) {
        throw HostCapability.CONSUMED_GOO.refusedBy(kind());
    }

    @Override
    public void dropConsumedGoo() {
        throw HostCapability.CONSUMED_GOO.refusedBy(kind());
    }

    @Override
    public void damageTarget(float amount, DamageKind source, boolean knockback) {
        target.hurtServer(level, damageSource(source), amount);
        if (!knockback) {
            target.hurtMarked = false;
        }
    }

    @Override
    public void setTargetHurtCooldown(int ticks) {
        target.invulnerableTime = ticks;
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
        return EntityScan.passes(target, filters, target);
    }

    @Override
    public void setTargetHealthFraction(float fraction) {
        target.setHealth(target.getHealth() * fraction);
    }

    @Override
    public void addTargetFreezeTicks(int ticks) {
        target.setTicksFrozen(target.getTicksFrozen() + ticks);
    }

    @Override
    public void setTargetAi(boolean enabled) {
        if (target instanceof Mob mob) {
            mob.setNoAi(!enabled);
        }
    }

    @Override
    public void setTargetInvulnerable(boolean enabled) {
        target.setInvulnerable(enabled);
    }

    @Override
    public void cloneTarget(float chancePercent) {
        if (level.getRandom().nextFloat() * PERCENT < chancePercent) {
            spawnClone();
        }
    }

    @Override
    public void dropItemAtTarget(Identifier item, int count) {
        Optional<Holder.Reference<Item>> holder = BuiltInRegistries.ITEM.get(item);
        if (holder.isEmpty()) {
            Goo.LOGGER.warn(LOG_UNKNOWN_ITEM, item);
            return;
        }
        target.spawnAtLocation(level, new ItemStack(holder.get(), count));
    }

    @Override
    public void igniteTarget(int seconds) {
        target.igniteForSeconds(seconds);
    }

    @Override
    public void spawnParticles(FxAnchor at, ParticleBurst burst) {
        SimpleParticles.resolve(burst.particle()).ifPresent(particle -> level.sendParticles(particle,
                target.getX(), target.getY(BODY_CENTER) + burst.lift(), target.getZ(),
                burst.count(), burst.spreadAcross(), burst.spreadAlong(), burst.spreadAcross(), burst.speed()));
    }

    @Override
    public void playSound(FxAnchor at, SoundCue cue) {
        SoundPlays.play(level, new Vec3(target.getX(), target.getY(BODY_CENTER), target.getZ()), cue);
    }

    @Override
    public void teleportTarget(TeleportMode mode, double range) {
        Vec3 jump = switch (mode) {
            case RANDOM_OFFSET -> randomOffset(range);
            case TOWARD_THROWER -> towardThrower(range);
            case AWAY_FROM_THROWER -> towardThrower(-range);
        };
        target.teleportTo(target.getX() + jump.x(), target.getY(), target.getZ() + jump.z());
    }

    /**
     * Rolls a level jump of up to half the range either way on each axis.
     *
     * @param range the full width of the roll
     * @return the jump
     */
    private Vec3 randomOffset(double range) {
        RandomSource random = level.getRandom();
        return new Vec3((random.nextDouble() - HALF) * range, 0, (random.nextDouble() - HALF) * range);
    }

    /**
     * Measures a level jump of the range along the line from the target
     * to the thrower; a negative range jumps away.
     *
     * @param range the jump length, negative to jump away
     * @return the jump, zero with no thrower or a thrower at the target
     */
    private Vec3 towardThrower(double range) {
        if (thrower == null) {
            return Vec3.ZERO;
        }
        Vec3 line = new Vec3(thrower.getX() - target.getX(), 0, thrower.getZ() - target.getZ());
        return line.lengthSqr() == 0 ? Vec3.ZERO : line.normalize().scale(range);
    }

    /**
     * Spawns a fresh entity of the target's type a gaussian step away on
     * each horizontal axis.
     */
    private void spawnClone() {
        Entity clone = target.getType().create(level, EntitySpawnReason.MOB_SUMMONED);
        if (clone == null) {
            return;
        }
        RandomSource random = level.getRandom();
        clone.setPos(target.getX() + random.nextGaussian(), target.getY(), target.getZ() + random.nextGaussian());
        level.addFreshEntity(clone);
    }

    @Override
    public void placeBlock(Identifier block, Map<String, String> state) {
        throw HostCapability.PLACE_BLOCK.refusedBy(kind());
    }

    @Override
    public boolean applyBlockEffect(BlockEffect effect, BlockPos cell) {
        throw HostCapability.LAYER_WALK.refusedBy(kind());
    }

    @Override
    public void previewLayer(LayerVisuals visuals, int layer) {
        throw HostCapability.LAYER_WALK.refusedBy(kind());
    }

    @Override
    public void strikeLayerFx(LayerVisuals visuals, LayerAudio audio, int layer, int destroyed) {
        throw HostCapability.LAYER_WALK.refusedBy(kind());
    }

    @Override
    public void reportMinedLayers(int layers) {
        throw HostCapability.LAYER_WALK.refusedBy(kind());
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
