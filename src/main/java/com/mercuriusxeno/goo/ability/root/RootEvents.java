package com.mercuriusxeno.goo.ability.root;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Runs the vines rooting a mob: each tick they stop its pathing, and a mob
 * that has moved off the root is thorned and dragged back onto it, never
 * standing past the leash; fire damage to a
 * mob that is not fire immune is multiplied while they hold, every other
 * hit spends one of theirs, and they release at their last hit or when
 * their hold runs out, falling away before they go.
 * vines-unpack-root-and-thorn
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class RootEvents {

    /** How far from the root, across the ground, a rooted mob strains before the vines pull it back. */
    public static final double LEASH_BLOCKS = 1.0;

    /** How far off the root a mob stands before the vines count it moving, thorn it and drag it back. */
    public static final double SLACK_BLOCKS = 0.25;

    /** The share of the way back onto the root the vines drag a mob each tick it stands off it. */
    static final double PULL_SHARE = 0.3;

    private static final float LATCH_VOLUME = 1.0f;
    private static final float LATCH_PITCH = 0.8f;
    private static final float SNAP_VOLUME = 1.0f;
    private static final float SNAP_PITCH = 1.2f;
    private static final int SNAP_PARTICLES = 24;
    private static final double SNAP_SPREAD = 0.35;
    private static final double SNAP_SPEED = 0.1;
    private static final double BODY_CENTER = 0.5;

    private RootEvents() {
    }

    /**
     * Latches a throw of vines onto a living entity, with the rustle of
     * vines taking hold.
     *
     * @param target the entity the vines root
     * @param thrown the throw's strength
     */
    public static void latch(LivingEntity target, Rooted.Throw thrown) {
        long now = target.level().getGameTime();
        Rooted standing = target.getData(GooAttachments.ROOTED);
        target.setData(GooAttachments.ROOTED, standing.latch(thrown, target.position(), now));
        rustle(target, SoundEvents.VINE_PLACE, LATCH_VOLUME, LATCH_PITCH);
    }

    /**
     * Holds each rooted mob at its root, releases one whose hold has run
     * out, and lets the vines go once they have fallen away.
     *
     * @param event the entity tick event
     */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        LivingEntity living = rootedOnServer(event.getEntity());
        if (living == null) {
            return;
        }
        Rooted rooted = living.getData(GooAttachments.ROOTED);
        long now = living.level().getGameTime();
        if (rooted.holds(now)) {
            holdAtRoot(living, rooted);
        } else if (rooted.hitsLeft() > 0) {
            release(living, rooted.releasedAt(now));
        } else if (rooted.fadedBy(now)) {
            living.removeData(GooAttachments.ROOTED);
        }
    }

    /**
     * Multiplies fire damage to a rooted mob that is not fire immune.
     *
     * @param event the incoming damage event
     */
    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity living = rootedOnServer(event.getEntity());
        if (living == null) {
            return;
        }
        Rooted rooted = living.getData(GooAttachments.ROOTED);
        if (rooted.holds(living.level().getGameTime()) && feedsFire(living, event.getSource())) {
            event.setAmount(event.getAmount() * rooted.fireFactor());
        }
    }

    /**
     * Spends one of the vines' hits for each hit a rooted mob takes, past
     * their own thorns; the last one releases it.
     *
     * @param event the damage event, after the damage landed
     */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity living = rootedOnServer(event.getEntity());
        if (living == null || event.getNewDamage() <= 0f || isThorn(event.getSource())) {
            return;
        }
        Rooted rooted = living.getData(GooAttachments.ROOTED);
        long now = living.level().getGameTime();
        if (!rooted.holds(now)) {
            return;
        }
        Rooted after = rooted.struck(now);
        if (after.holds(now)) {
            living.setData(GooAttachments.ROOTED, after);
        } else {
            release(living, after);
        }
    }

    /**
     * The living entity vines stand on, on the server, where the vines are run.
     *
     * @param entity the entity
     * @return the entity as a living one, or null on the client or where no vines stand
     */
    private static @Nullable LivingEntity rootedOnServer(Entity entity) {
        if (entity.level().isClientSide() || !(entity instanceof LivingEntity living)) {
            return null;
        }
        return living.hasData(GooAttachments.ROOTED) ? living : null;
    }

    /**
     * Whether damage is fire the vines feed: fire damage to a mob that is
     * not fire immune.
     *
     * @param living the damaged entity
     * @param source the damage source
     * @return true when the vines multiply the damage
     */
    static boolean feedsFire(LivingEntity living, DamageSource source) {
        return source.is(DamageTypeTags.IS_FIRE) && !living.fireImmune();
    }

    /**
     * Whether damage is the vines' own thorns, which spend none of their hits.
     *
     * @param source the damage source
     * @return true for the thorn damage the vines deal
     */
    static boolean isThorn(DamageSource source) {
        return source.is(DamageTypes.SWEET_BERRY_BUSH);
    }

    /**
     * Holds a rooted mob at its root: its pathing stops, a mob that has
     * moved off the root is thorned and dragged a share of the way back onto
     * it, and none stands past the leash's edge.
     *
     * @param living the rooted entity
     * @param rooted its vines
     */
    private static void holdAtRoot(LivingEntity living, Rooted rooted) {
        if (living instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        Vec3 pull = springPull(rooted.anchor(), living.position());
        if (pull == null) {
            return;
        }
        Vec3 motion = living.getDeltaMovement();
        living.setDeltaMovement(0, Math.min(0, motion.y), 0);
        living.move(MoverType.SELF, pull);
        Vec3 leashed = pulledToLeash(rooted.anchor(), living.position());
        if (leashed != null) {
            living.setPos(leashed);
        }
        living.hurtMarked = true;
        if (living.level() instanceof ServerLevel level) {
            living.hurtServer(level, level.damageSources().sweetBerryBush(), rooted.thorns());
        }
    }

    /**
     * How far the vines drag a mob back toward the root this tick: none
     * while it stands on the root, and a share of the way across the ground
     * once it has moved off it.
     *
     * @param anchor   the root
     * @param position where the mob stands
     * @return the drag across the ground, or null for a mob standing on the root
     */
    static @Nullable Vec3 springPull(Vec3 anchor, Vec3 position) {
        double dx = anchor.x - position.x;
        double dz = anchor.z - position.z;
        if (Math.sqrt(dx * dx + dz * dz) <= SLACK_BLOCKS) {
            return null;
        }
        return new Vec3(dx * PULL_SHARE, 0, dz * PULL_SHARE);
    }

    /**
     * Where the vines pull a mob standing past the leash: back toward the
     * root across the ground to the leash's edge, at the height it stands.
     *
     * @param anchor   the root
     * @param position where the mob stands
     * @return the point on the leash's edge, or null for a mob within the leash
     */
    static @Nullable Vec3 pulledToLeash(Vec3 anchor, Vec3 position) {
        double dx = position.x - anchor.x;
        double dz = position.z - anchor.z;
        double across = Math.sqrt(dx * dx + dz * dz);
        if (across <= LEASH_BLOCKS) {
            return null;
        }
        double scale = LEASH_BLOCKS / across;
        return new Vec3(anchor.x + dx * scale, position.y, anchor.z + dz * scale);
    }

    /**
     * Releases a rooted mob: the vines snap with a burst of torn vine and
     * start to fall away.
     *
     * @param living   the rooted entity
     * @param released the vines as they let go
     */
    private static void release(LivingEntity living, Rooted released) {
        living.setData(GooAttachments.ROOTED, released);
        rustle(living, SoundEvents.VINE_BREAK, SNAP_VOLUME, SNAP_PITCH);
        if (living.level() instanceof ServerLevel level) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.VINE.defaultBlockState()),
                    living.getX(), living.getY(BODY_CENTER), living.getZ(), SNAP_PARTICLES,
                    SNAP_SPREAD, SNAP_SPREAD, SNAP_SPREAD, SNAP_SPEED);
        }
    }

    private static void rustle(LivingEntity living, SoundEvent sound, float volume, float pitch) {
        living.level().playSound(null, living.getX(), living.getY(), living.getZ(), sound, SoundSource.HOSTILE,
                volume, pitch);
    }
}
