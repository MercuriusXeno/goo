package com.mercuriusxeno.goo.ability.root;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.CellSurface;
import com.mercuriusxeno.goo.block.ability.VinesCellBlock;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jspecify.annotations.Nullable;

/**
 * Runs the vines Vines goo holds a living thing with: a cell it crosses
 * gives it vines, each tick the vines slow it by their count and harm it
 * not at all, fire damage to a thing that is not fire immune is multiplied
 * while they hold, any damage it takes ends every vine and eats a layer of
 * the cell that last gave it vines, and they release when their time runs
 * out, falling away before they go. A thing under Barkskin takes no vines,
 * and its held vines end as Barkskin begins.
 * vines-is-sprayed-slime-that-entangles-what-tries-to-cross
 * vines-root-is-volume-based-one-to-five-vines-by-layers
 * vines-roots-last-three-seconds-plus-one-per-stack-and-each-vine-refreshes
 * vines-deal-no-thorn-damage-and-attacking-the-mob-frees-it
 * entangled-mob-taking-damage-eats-a-layer-of-vines-goo
 * barkskin-makes-you-immune-to-vines
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class RootEvents {

    /** The id of the speed modifier the vines hold a thing with. */
    public static final Identifier SLOW_ID = Identifier.fromNamespaceAndPath(Goo.MODID, "vines_slow");

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
     * gives it vines by its layers, with the rustle of vines taking hold; a
     * thing under Barkskin, and the player who placed the goo, take none.
     * barkskin-makes-you-immune-to-vines
     * operator ruling 2026-11-17: the caster is spared their own goo
     *
     * @param entity the thing touching the cell
     * @param touch  what the cell gives
     * @param placer the player who placed the goo, or null for none
     */
    public static void entangle(Entity entity, Rooted.Touch touch, @Nullable Entity placer) {
        if (!(entity instanceof LivingEntity living) || living == placer || !takesVines(living, touch)) {
            return;
        }
        Rooted standing = living.getData(GooAttachments.ROOTED);
        Rooted after = standing.touched(touch, living.position(), living.level().getGameTime());
        if (after.equals(standing)) {
            return;
        }
        living.setData(GooAttachments.ROOTED, after);
        rustle(living, SoundEvents.VINE_PLACE, LATCH_VOLUME, LATCH_PITCH);
    }

    /**
     * Whether a living thing touching a Vines cell takes its vines: on the
     * server, its center over the cell, and Barkskin not standing on it.
     * barkskin-makes-you-immune-to-vines
     *
     * @param living the thing touching the cell
     * @param touch  what the cell gives
     * @return true where the cell gives it vines
     */
    private static boolean takesVines(LivingEntity living, Rooted.Touch touch) {
        return !living.level().isClientSide() && over(touch.cell(), living.position())
                && !VineImmunity.spares(living);
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
     * Slows each held thing, and lets go of one whose hold has run out or on
     * whom Barkskin has begun.
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
        if (!rooted.holds(now) || VineImmunity.spares(living)) {
            letGo(living, rooted, now);
        }
    }

    /**
     * Releases vines still held, and lets released vines go once they have
     * fallen away and the thing has left the cell that last gave it vines,
     * so it is not rooted again where it stands.
     *
     * @param living the thing the vines stand on
     * @param rooted its vines
     * @param now    the game time
     */
    private static void letGo(LivingEntity living, Rooted rooted, long now) {
        if (rooted.vines() > 0) {
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
        if (rooted.holds(living.level().getGameTime())) {
            event.setAmount(RootStrike.of(rooted.vines(), event.getAmount(), feedsFire(living, event.getSource()),
                    rooted.fireFactor(), 0).damage());
        }
    }

    /**
     * Ends every vine on a held thing that took damage, the thing taking the
     * damage, and eats a layer of the Vines cell that last gave it vines
     * where that cell still stands.
     * vines-deal-no-thorn-damage-and-attacking-the-mob-frees-it
     * entangled-mob-taking-damage-eats-a-layer-of-vines-goo
     *
     * @param event the damage event, after the damage landed
     */
    @SubscribeEvent
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity living = rootedOnServer(event.getEntity());
        if (living == null || !(living.level() instanceof ServerLevel level)) {
            return;
        }
        Rooted rooted = living.getData(GooAttachments.ROOTED);
        long now = level.getGameTime();
        if (!rooted.holds(now)) {
            return;
        }
        int layers = vinesLayersAt(level, rooted.lastCell());
        RootStrike strike = RootStrike.of(rooted.vines(), event.getHealthDamage(), false, rooted.fireFactor(), layers);
        if (strike.vinesLeft() == 0) {
            release(living, rooted.releasedAt(now));
            leaveLayers(level, rooted.lastCell(), layers, strike.layersKept());
        }
    }

    /**
     * The layers a Vines cell holds.
     *
     * @param level the level
     * @param cell  the cell
     * @return its layers, none where no Vines cell stands there
     */
    private static int vinesLayersAt(ServerLevel level, BlockPos cell) {
        BlockState standing = level.getBlockState(cell);
        return VolumeCellBlock.hasSurface(standing.getBlock(), CellSurface.ENTANGLE)
                ? standing.getValue(VolumeCellBlock.LAYERS) : 0;
    }

    /**
     * Leaves a Vines cell holding the layers it keeps; one keeping none leaves
     * its goologged vines hanging alone, or air where it bore none.
     * vines-goo-on-walls-places-goologged-vines-and-depletes
     *
     * @param level  the level
     * @param cell   the cell
     * @param layers the layers it holds, none where no Vines cell stands there
     * @param kept   the layers it keeps
     */
    private static void leaveLayers(ServerLevel level, BlockPos cell, int layers, int kept) {
        if (layers == 0 || kept == layers) {
            return;
        }
        BlockState standing = level.getBlockState(cell);
        BlockState left = kept > 0 ? standing.setValue(VolumeCellBlock.LAYERS, kept) : VinesCellBlock.spent(standing);
        level.setBlock(cell, left, Block.UPDATE_ALL);
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
