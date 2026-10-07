package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ExplosionParticleInfo;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.protocol.game.ClientboundExplodePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Util;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * An explosion marching Goo's rays ({@link ExplosionMarch}) in place of
 * vanilla's, standing on {@link ServerExplosion} so blocks' explosion hooks,
 * the explosion damage source and NeoForge's explosion events still see an
 * explosion. Entities within the max reach take vanilla's damage and
 * knockback, item entities aside, and every broken block drops whole at its
 * own position.
 * goo-ray-diminishes-block-resistance
 * preview-sphere-is-max-reach
 */
public final class GooExplosion extends ServerExplosion {

    private static final ExplosionDamageCalculator DAMAGE_CALCULATOR = new ExplosionDamageCalculator();
    private static final double PACKET_RANGE_SQR = 4096.0;
    private static final float POOF_SCALING = 0.5F;
    private static final float SMOKE_SCALING = 1.0F;
    private static final float PARTICLE_SPEED = 1.0F;
    private static final float OUTSIDE_WORLD_RESISTANCE = Float.MAX_VALUE;

    private GooExplosion(ServerLevel level, Vec3 center, float power, BlockInteraction blockInteraction) {
        super(level, null, null, DAMAGE_CALCULATOR, center, power, false, blockInteraction);
    }

    /**
     * How an explosion shows and sounds to the players near it.
     *
     * @param smallParticle  the particle a small explosion shows
     * @param largeParticle  the particle a large explosion shows
     * @param blockParticles the particles thrown off broken blocks
     * @param sound          the boom
     */
    public record Look(ParticleOptions smallParticle, ParticleOptions largeParticle,
                       WeightedList<ExplosionParticleInfo> blockParticles, Holder<SoundEvent> sound) {

        /**
         * Vanilla's look, the one level.explode gives by default.
         *
         * @return vanilla's particles and boom
         */
        public static Look vanilla() {
            return new Look(ParticleTypes.EXPLOSION, ParticleTypes.EXPLOSION_EMITTER,
                    WeightedList.<ExplosionParticleInfo>builder()
                            .add(new ExplosionParticleInfo(ParticleTypes.POOF, POOF_SCALING, PARTICLE_SPEED))
                            .add(new ExplosionParticleInfo(ParticleTypes.SMOKE, SMOKE_SCALING, PARTICLE_SPEED))
                            .build(),
                    SoundEvents.GENERIC_EXPLODE);
        }

        /**
         * No particles and vanilla's boom, for an explosion its goo type
         * draws (decision elemental-explosion-per-type).
         *
         * @param silent the particle that draws nothing
         * @return the silent look
         */
        public static Look silent(ParticleOptions silent) {
            return new Look(silent, silent, WeightedList.of(), SoundEvents.GENERIC_EXPLODE);
        }
    }

    /**
     * Detonates a Goo explosion and tells the players near it, as
     * ServerLevel.explode does for vanilla's.
     *
     * @param level  the server level
     * @param center the explosion center
     * @param power  the power every ray starts with
     * @param mode   how blocks are treated
     * @param look   how the explosion shows and sounds
     */
    public static void detonate(ServerLevel level, Vec3 center, float power, ExplosionMode mode, Look look) {
        GooExplosion explosion = new GooExplosion(level, center, power, blockInteraction(mode));
        if (EventHooks.onExplosionStart(level, explosion)) {
            return;
        }
        int blockCount = explosion.explode();
        ParticleOptions particle = explosion.isSmall() ? look.smallParticle() : look.largeParticle();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(center) < PACKET_RANGE_SQR) {
                Optional<Vec3> knockback = Optional.ofNullable(explosion.getHitPlayers().get(player));
                player.connection.send(new ClientboundExplodePacket(center, power, blockCount, knockback,
                        particle, look.sound(), look.blockParticles()));
            }
        }
    }

    /**
     * Breaks blocks without vanilla's drop decay, so no loot table rolls its
     * survives_explosion condition and every block drops whole.
     * explosion-drops-whole-and-spares-items
     *
     * @param mode how the explode step treats blocks
     * @return destroy for a block-breaking explosion, keep otherwise
     */
    private static BlockInteraction blockInteraction(ExplosionMode mode) {
        return mode == ExplosionMode.TNT ? BlockInteraction.DESTROY : BlockInteraction.KEEP;
    }

    @Override
    public int explode() {
        level().gameEvent(null, GameEvent.EXPLODE, center());
        List<BlockPos> marked = new ArrayList<>(ExplosionMarch.markedCells(center(), radius(), this::resistanceAt));
        hurtEntitiesWithinReach(marked);
        if (getBlockInteraction() != BlockInteraction.KEEP) {
            breakBlocks(marked);
        }
        return marked.size();
    }

    /**
     * Reads a cell's resistance the way vanilla's damage calculator does;
     * a cell outside the world holds every ray.
     *
     * @param pos the cell
     * @return the resistance, empty for air
     */
    private Optional<Float> resistanceAt(BlockPos pos) {
        ServerLevel level = level();
        if (!level.isInWorldBounds(pos)) {
            return Optional.of(OUTSIDE_WORLD_RESISTANCE);
        }
        BlockState state = level.getBlockState(pos);
        return DAMAGE_CALCULATOR.getBlockExplosionResistance(this, level, pos, state, level.getFluidState(pos));
    }

    private void breakBlocks(List<BlockPos> marked) {
        ServerLevel level = level();
        Util.shuffle(marked, level.getRandom());
        for (BlockPos pos : marked) {
            level.getBlockState(pos).onExplosionHit(level, pos, this,
                    (stack, at) -> Block.popResource(level, at, stack));
        }
    }

    private void hurtEntitiesWithinReach(List<BlockPos> marked) {
        ServerLevel level = level();
        Vec3 center = center();
        double reach = ExplosionMarch.maxReach(radius());
        if (reach <= 0) {
            return;
        }
        AABB bounds = new AABB(center, center).inflate(reach + 1.0);
        // explosion-drops-whole-and-spares-items
        List<Entity> entities = level.getEntities((Entity) null, bounds, entity -> !(entity instanceof ItemEntity));
        EventHooks.onExplosionDetonate(level, this, entities, marked);
        for (Entity entity : entities) {
            double distance = Math.sqrt(entity.distanceToSqr(center)) / reach;
            if (!entity.ignoreExplosion(this) && distance <= 1.0) {
                hurtEntity(entity, distance, marked);
            }
        }
    }

    private void hurtEntity(Entity entity, double distance, List<BlockPos> marked) {
        Vec3 center = center();
        boolean damages = DAMAGE_CALCULATOR.shouldDamageEntity(this, entity);
        float knockbackMultiplier = DAMAGE_CALCULATOR.getKnockbackMultiplier(entity);
        float exposure = !damages && knockbackMultiplier == 0.0F ? 0.0F : getSeenPercent(center, entity);
        if (damages) {
            entity.hurtServer(level(), getDamageSource(), DAMAGE_CALCULATOR.getEntityDamageAmount(this, entity, exposure));
        }
        double knockbackPower = (1.0 - distance) * exposure * knockbackMultiplier * (1.0 - knockbackResistance(entity));
        Vec3 knockback = EventHooks.getExplosionKnockback(level(), this, entity,
                knockbackDirection(entity).scale(knockbackPower), marked);
        entity.push(knockback);
        recordKnockback(entity, knockback);
        entity.onExplosionHit(null);
    }

    private Vec3 knockbackDirection(Entity entity) {
        Vec3 origin = entity instanceof PrimedTnt ? entity.position() : entity.getEyePosition();
        return origin.subtract(center()).normalize();
    }

    private static double knockbackResistance(Entity entity) {
        return entity instanceof LivingEntity living
                ? living.getAttributeValue(Attributes.EXPLOSION_KNOCKBACK_RESISTANCE)
                : 0.0;
    }

    /**
     * Hands a redirectable projectile to the explosion's owner, and keeps a
     * player's knockback for the packet that pushes them client side.
     *
     * @param entity    the entity knocked back
     * @param knockback the push it took
     */
    private void recordKnockback(Entity entity, Vec3 knockback) {
        if (entity.is(EntityTypeTags.REDIRECTABLE_PROJECTILE) && entity instanceof Projectile projectile) {
            projectile.setOwner(getDamageSource().getEntity());
        } else if (entity instanceof Player player && isPushable(player)) {
            getHitPlayers().put(player, knockback);
        }
    }

    private static boolean isPushable(Player player) {
        return !player.isSpectator() && (!player.isCreative() || !player.getAbilities().flying);
    }
}
