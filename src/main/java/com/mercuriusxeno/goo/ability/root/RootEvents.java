package com.mercuriusxeno.goo.ability.root;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.LingeringAppliers;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Runs the vines Vines goo holds a living thing with: a cell it crosses
 * gives it vines, each tick the vines slow it by their count and thorn it
 * for moving, fire damage to a thing that is not fire immune is multiplied
 * while they hold, and they release when their time runs out, falling away
 * before they go.
 * vines-is-sprayed-slime-that-entangles-what-tries-to-cross
 * vines-root-is-volume-based-one-to-five-vines-by-layers
 * vines-roots-last-three-seconds-plus-one-per-stack-and-each-vine-refreshes
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class RootEvents {

    /** The id the vines' thorns remember the player who placed the goo under, among a thing's lingering appliers. */
    static final Identifier THORNS = Identifier.fromNamespaceAndPath(Goo.MODID, "root_thorns");

    /** The id of the speed modifier the vines hold a thing with. */
    static final Identifier SLOW_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "vines_slow");

    /** How far across the ground a held thing moves in a tick before the vines count it moving. */
    static final double MOVED_BLOCKS = 0.01;

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
     * Entangles a living thing whose center crosses a Vines cell: the cell
     * gives it vines by its layers, with the rustle of vines taking hold,
     * and the player who placed the goo is remembered, whom the vines' later
     * thorns name.
     * standing-goo-thing-credits-whoever-placed-it
     *
     * @param entity the thing touching the cell
     * @param touch  what the cell gives
     * @param placer the player who placed the goo, or null for none
     */
    public static void entangle(Entity entity, Rooted.Touch touch, @Nullable Entity placer) {
        if (entity.level().isClientSide() || !(entity instanceof LivingEntity living)
                || !over(touch.cell(), living.position())) {
            return;
        }
        Rooted standing = living.getData(GooAttachments.ROOTED);
        Rooted after = standing.touched(touch, living.position(), living.level().getGameTime());
        if (after.equals(standing)) {
            return;
        }
        living.setData(GooAttachments.ROOTED, after);
        LingeringAppliers.remember(living, THORNS, placer);
        rustle(living, SoundEvents.VINE_PLACE, LATCH_VOLUME, LATCH_PITCH);
    }

    /**
     * Whether a thing standing at a point stands over a cell, its center
     * inside the cell's column: what crossing the goo is.
     *
     * @param cell     the cell
     * @param position where the thing stands
     * @return true where the point lies over the cell
     */
    static boolean over(BlockPos cell, Vec3 position) {
        return (int) Math.floor(position.x) == cell.getX() && (int) Math.floor(position.z) == cell.getZ();
    }

    /**
     * Slows and thorns each held thing, releases one whose hold has run
     * out, and lets the vines go once they have fallen away and the thing
     * has left the cell that last gave it vines.
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
        slowBy(living, rooted.speedAt(now));
        if (rooted.holds(now)) {
            thornIfMoved(living, rooted);
        } else if (rooted.vines() > 0) {
            release(living, rooted.releasedAt(now));
        } else if (rooted.fadedBy(now) && !over(rooted.lastCell(), living.position())) {
            living.removeData(GooAttachments.ROOTED);
        }
    }

    /**
     * Multiplies fire damage to a held thing that is not fire immune.
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
     * Whether damage is fire the vines feed: fire damage to a thing that is
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
     * Holds a thing at the share of its speed the vines leave it, through a
     * speed modifier its client reads too; a whole share takes the modifier off.
     *
     * @param living the held entity
     * @param speed  the share of its speed it keeps
     */
    private static void slowBy(LivingEntity living, double speed) {
        AttributeInstance movement = living.getAttribute(Attributes.MOVEMENT_SPEED);
        if (movement == null) {
            return;
        }
        if (speed >= 1.0) {
            movement.removeModifier(SLOW_ID);
        } else {
            movement.addOrUpdateTransientModifier(new AttributeModifier(SLOW_ID, speed - 1.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /**
     * Thorns a held thing that moved across the ground this tick, naming the
     * player who placed the goo.
     * standing-goo-thing-credits-whoever-placed-it
     *
     * @param living the held entity
     * @param rooted its vines
     */
    private static void thornIfMoved(LivingEntity living, Rooted rooted) {
        double moved = Math.hypot(living.getX() - living.xo, living.getZ() - living.zo);
        if (moved > MOVED_BLOCKS && living.level() instanceof ServerLevel level) {
            living.hurtServer(level, LingeringAppliers.credit(level, level.damageSources().sweetBerryBush(), living,
                    THORNS), rooted.thorns());
        }
    }

    /**
     * Releases a held thing: the vines snap with a burst of torn vine and
     * start to fall away.
     *
     * @param living   the held entity
     * @param released the vines as they let go
     */
    private static void release(LivingEntity living, Rooted released) {
        living.setData(GooAttachments.ROOTED, released);
        slowBy(living, 1.0);
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
