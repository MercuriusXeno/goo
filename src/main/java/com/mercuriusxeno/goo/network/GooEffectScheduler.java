package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.EntityHost;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.world.AbilityImpact;
import com.mercuriusxeno.goo.registry.GooSounds;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Schedules and applies delayed goo effects after goo flight completes.
 * Each server holds one, so its pending effects end with the server
 * (decision type-package-and-per-server-holders); it plays impact sounds on arrival.
 * Extracted from {@link GooThrowHandler} to keep per-class method counts manageable.
 */
public final class GooEffectScheduler {

    /**
     * Sound volume for throw event.
     */
    private static final float THROW_SOUND_VOLUME = 0.5f;
    /**
     * Base pitch for throw sound.
     */
    private static final float THROW_PITCH_BASE = 0.4f;
    /**
     * Pitch randomness range for throw sound.
     */
    private static final float THROW_PITCH_RANGE = 0.4f;
    /**
     * Minimum pitch offset for throw sound.
     */
    private static final float THROW_PITCH_OFFSET = 0.8f;
    /**
     * Sound volume for impact event.
     */
    private static final float IMPACT_SOUND_VOLUME = 1.0f;
    /**
     * Base pitch for impact sound.
     */
    private static final float IMPACT_PITCH_BASE = 0.9f;
    /**
     * Pitch randomness range for impact sound.
     */
    private static final float IMPACT_PITCH_RANGE = 0.2f;
    /**
     * Block center offset (half-block).
     */
    private static final double BLOCK_CENTER = 0.5;
    /**
     * Blocks an aim reaches when it is clipped against a struck mob's box,
     * past any throw's range.
     */
    private static final double AIM_REACH = 256.0;

    /**
     * Log: entity no longer exists at goo arrival.
     */
    private static final String LOG_ENTITY_GONE = "Goo arrived but entity {} no longer exists";
    /**
     * Log: a program entry the struck entity host refused at load.
     */
    private static final String LOG_PROGRAM_REFUSED = "Ability {} refused on the struck entity: {}";
    /**
     * Log: a block throw naming no ability, refused.
     */
    private static final String LOG_NO_ABILITY = "Block throw of goo type {} names no ability; nothing lands";

    /**
     * What a goo landing on a mob does to the world, named so the landing's
     * order runs without a live server.
     */
    interface MobLanding {

        /**
         * Tells the players tracking the struck mob of the hit.
         *
         * @param struck the struck mob
         * @param hit    the hit payload
         */
        void announceHit(LivingEntity struck, MobHitPayload hit);

        /**
         * Runs the ability the effect names on the struck mob.
         *
         * @param pe     the pending effect targeting the mob
         * @param struck the struck mob
         */
        void runProgram(PendingEffect pe, LivingEntity struck);
    }

    /**
     * A mob landing on the live server: the hit goes to every player
     * tracking the mob, and the ability runs on it.
     */
    private static final MobLanding LIVE_LANDING = new MobLanding() {
        @Override
        public void announceHit(LivingEntity struck, MobHitPayload hit) {
            EntityVisuals.sendToTrackers(struck, hit);
        }

        @Override
        public void runProgram(PendingEffect pe, LivingEntity struck) {
            runAbilityOn(pe, struck);
        }
    };

    /**
     * Pending effects waiting for their goo to arrive.
     */
    private final List<PendingEffect> pendingEffects = new ArrayList<>();

    /**
     * Plays the throw sound and queues a pending effect for goo arrival.
     *
     * @param player      the throwing player
     * @param payload     the throw payload data
     * @param gooType     the goo type being thrown
     * @param delivery    the delivery the throw flies by
     * @param travelTicks the number of ticks until arrival
     */
    void scheduleEffect(ServerPlayer player, GooThrowPayload payload,
                               ResourceKey<GooTypeDefinition> gooType, Delivery delivery, int travelTicks) {
        playThrowSound(player, delivery);
        enqueueArrival(player, payload, gooType, travelTicks);
    }

    /**
     * Plays the throw sound at the player's position: a beam fires the
     * laser sound, every other delivery the snowball throw
     * (decision delivery-block-in-ability-json).
     *
     * @param player   the throwing player
     * @param delivery the delivery the throw flies by
     */
    static void playThrowSound(ServerPlayer player, Delivery delivery) {
        ServerLevel level = player.level();
        SoundEvent sound = delivery.fliesStraight()
                ? GooSounds.GLOW_THROW.get()
                : SoundEvents.SNOWBALL_THROW;
        float pitch = THROW_PITCH_BASE / (level.getRandom().nextFloat() * THROW_PITCH_RANGE + THROW_PITCH_OFFSET);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                sound, SoundSource.PLAYERS, THROW_SOUND_VOLUME, pitch);
    }

    /**
     * Queues a pending effect for the goo's arrival tick.
     *
     * @param player      the throwing player
     * @param payload     the throw payload data
     * @param gooType     the goo type being thrown
     * @param travelTicks the number of ticks until arrival
     */
    void enqueueArrival(ServerPlayer player, GooThrowPayload payload,
                               ResourceKey<GooTypeDefinition> gooType, int travelTicks) {
        ServerLevel level = player.level();
        int arrivalTick = level.getServer().getTickCount() + travelTicks;
        Direction face = GooThrowHandler.directionFromOrdinal(payload.targetFace());
        enqueue(new PendingEffect(
                arrivalTick, level, player, gooType,
                payload.targetEntityId(), payload.targetPos(), face,
                payload.abilityId(), crosshairAim(player), resolvePoint(level, payload, gooType)));
    }

    /**
     * The point a throw resolves at: the aimed point for an ability aiming a
     * point, none for one favoring a mob or a block.
     * aim-point-follows-the-cursor
     *
     * @param level   the server level
     * @param payload the throw payload data
     * @param gooType the goo type thrown
     * @return the point, or null to resolve at the landing cell
     */
    private static @Nullable Vec3 resolvePoint(ServerLevel level, GooThrowPayload payload,
                                               ResourceKey<GooTypeDefinition> gooType) {
        AbilityDefinition ability = GooThrowHandler.thrownAbility(level, payload.abilityId(), gooType);
        return ability != null && ability.badge().aimsAPoint() ? payload.targetPoint() : null;
    }

    /**
     * The crosshair's line as the goo leaves the hand: the striker's eye and
     * look. The throw payload's origin is the glove hand, below and beside the
     * eye, so a ray from it along the look would strike low.
     *
     * @param striker the striking player
     * @return the aim
     */
    static Aim crosshairAim(ServerPlayer striker) {
        return new Aim(striker.getEyePosition(), striker.getViewVector(1f));
    }

    /**
     * Queues an effect to apply on its arrival tick.
     *
     * @param effect the pending effect
     */
    public void enqueue(PendingEffect effect) {
        pendingEffects.add(effect);
    }

    /**
     * Returns true if there are pending effects to process.
     *
     * @return true if the queue is non-empty
     */
    public boolean hasPending() {
        return !pendingEffects.isEmpty();
    }

    /**
     * Counts the pending effects, so a caller sharing the scheduler can read
     * what one act added.
     *
     * @return the number of effects waiting to arrive
     */
    int pendingCount() {
        return pendingEffects.size();
    }

    /**
     * Drops every pending effect, as a server stop does.
     */
    public void clear() {
        pendingEffects.clear();
    }

    /**
     * Applies and removes all effects whose goo have arrived.
     *
     * @param currentTick the current server tick
     */
    public void drainArrivedEffects(int currentTick) {
        drainArrivedEffects(currentTick, LIVE_LANDING);
    }

    /**
     * Applies and removes all effects whose goo have arrived, each mob
     * landing going through the landing given.
     *
     * @param currentTick the current server tick
     * @param landing     what a mob landing does to the world
     */
    void drainArrivedEffects(int currentTick, MobLanding landing) {
        List<PendingEffect> ready = new ArrayList<>();
        Iterator<PendingEffect> it = pendingEffects.iterator();
        while (it.hasNext()) {
            PendingEffect pe = it.next();
            if (currentTick >= pe.arrivalTick) {
                ready.add(pe);
                it.remove();
            }
        }
        for (PendingEffect pe : ready) {
            applyEffect(pe, landing);
        }
    }

    /**
     * Applies the goo effect at the target location or entity, with impact sound.
     *
     * @param pe the pending effect to apply
     */
    static void applyEffect(PendingEffect pe) {
        applyEffect(pe, LIVE_LANDING);
    }

    /**
     * Applies the goo effect at the target location or entity, a mob
     * landing going through the landing given.
     *
     * @param pe      the pending effect to apply
     * @param landing what a mob landing does to the world
     */
    static void applyEffect(PendingEffect pe, MobLanding landing) {
        if (pe.targetEntityId >= 0) {
            applyEntityEffect(pe, landing);
        } else {
            applyBlockEffect(pe);
        }
    }

    /**
     * Applies the goo effect to a living entity target with impact sound:
     * the tracking players hear of the hit, then the programs of the ability
     * the throw names run on the struck entity in the same call, and a throw
     * naming no ability does nothing past the sound and the hit
     * (decision no-throw-without-ability).
     * Decision visuals-play-beside-the-program.
     *
     * @param pe      the pending effect targeting an entity
     * @param landing what a mob landing does to the world
     */
    static void applyEntityEffect(PendingEffect pe, MobLanding landing) {
        Entity target = pe.level.getEntity(pe.targetEntityId);
        if (!(target instanceof LivingEntity living)) {
            Goo.LOGGER.debug(LOG_ENTITY_GONE, pe.targetEntityId);
            return;
        }
        playImpactSound(pe.level, living.getX(), living.getY(), living.getZ());
        Aim aim = aimOf(pe);
        if (splats(resolveAbility(pe.level, pe.abilityId))) {
            landing.announceHit(living, new MobHitPayload(living.getId(), GooTypes.id(pe.gooType),
                    aimedHitPoint(living.getBoundingBox(), aim), aim == null ? Vec3.ZERO : aim.direction()));
        }
        landing.runProgram(pe, living);
    }

    /**
     * Whether a blob of the ability splats goo on the mob it strikes; one
     * tagged no_splat draws its own hit (decision crush-blob-breaks-along-its-strike).
     *
     * @param ability the ability the effect names, or null for none
     * @return true unless the ability is tagged no_splat
     */
    static boolean splats(@Nullable AbilityDefinition ability) {
        return ability == null || !ability.hasTag(AbilityTags.NO_SPLAT);
    }

    /**
     * The aim a landing struck along: the crosshair's line, captured as a
     * throw leaves the hand or read this tick for a punch or touch landing at
     * once; none where no striker stands.
     *
     * @param pe the pending effect
     * @return the aim, or null
     */
    static @Nullable Aim aimOf(PendingEffect pe) {
        if (pe.aim() != null) {
            return pe.aim();
        }
        return pe.thrower == null ? null : crosshairAim(pe.thrower);
    }

    /**
     * The point the goo struck on a mob's box: where the aim enters it, or,
     * where the aim misses the box by the time the goo lands, where the line
     * from the aim's start to the box's center enters it. The client carries
     * the aim on from this point onto the mob's model.
     *
     * @param box the struck mob's bounding box
     * @param aim the aim struck along, or null
     * @return the hit point on the box
     */
    static Vec3 aimedHitPoint(AABB box, @Nullable Aim aim) {
        if (aim == null) {
            return box.getCenter();
        }
        Vec3 reach = aim.from().add(aim.direction().scale(AIM_REACH));
        return box.clip(aim.from(), reach).orElseGet(() -> hitPoint(box, aim.from()));
    }

    /**
     * The point the goo struck on a mob: where the line from the striker's
     * eye to the mob's center enters its box, or the box's center where no
     * striker stands or the striker stands inside the box.
     *
     * @param box        the struck mob's bounding box
     * @param struckFrom the striker's eye, or null
     * @return the hit point
     */
    static Vec3 hitPoint(AABB box, @Nullable Vec3 struckFrom) {
        Vec3 center = box.getCenter();
        if (struckFrom == null) {
            return center;
        }
        return box.clip(struckFrom, center).orElse(center);
    }

    /**
     * Runs the ability the effect names on the struck entity.
     *
     * @param pe     the pending effect targeting an entity
     * @param living the struck entity
     */
    private static void runAbilityOn(PendingEffect pe, LivingEntity living) {
        AbilityDefinition def = resolveAbility(pe.level, pe.abilityId);
        if (def != null) {
            runEntityProgram(pe, def, living);
        }
    }

    private static AbilityDefinition resolveAbility(ServerLevel level, String abilityId) {
        Identifier id = Identifier.tryParse(abilityId);
        if (id == null) {
            return null;
        }
        return AbilityRegistry.of(level).getAbility(id);
    }

    /**
     * Loads the ability's program for the struck entity host and runs its
     * one tick on an {@link EntityHost} (decision host-agnostic-runtime). A program the host cannot serve is refused at load, and
     * the refusal is logged with the ability, the step and the host.
     *
     * @param pe     the pending effect
     * @param def    the ability definition
     * @param living the target entity
     */
    private static void runEntityProgram(PendingEffect pe, AbilityDefinition def, LivingEntity living) {
        try {
            ProgramBehavior program = ProgramBehavior.forHost(def.behaviors(), HostKind.ENTITY);
            program.tick(new EntityHost(pe.level, living, pe.thrower));
        } catch (ProgramLoadException e) {
            Goo.LOGGER.error(LOG_PROGRAM_REFUSED, def.id(), e.getMessage());
        }
    }

    /**
     * Applies the goo effect to a block target with impact sound: the
     * ability the throw names lands on the block, and a throw naming no
     * ability does nothing past the sound (decision no-throw-without-ability).
     *
     * @param pe the pending effect targeting a block
     */
    static void applyBlockEffect(PendingEffect pe) {
        BlockPos pos = pe.targetPos;
        playImpactSound(pe.level, pos.getX() + BLOCK_CENTER, pos.getY() + BLOCK_CENTER, pos.getZ() + BLOCK_CENTER);
        if (pe.abilityId.isEmpty()) {
            Goo.LOGGER.warn(LOG_NO_ABILITY, GooTypes.id(pe.gooType));
            return;
        }
        applyAbilityBlockEffect(pe);
    }

    /**
     * Lands the goo through the ability it names, which runs its program
     * on the landing the tick it splats.
     *
     * @param pe the pending effect with ability id set
     */
    static void applyAbilityBlockEffect(PendingEffect pe) {
        Identifier id = Identifier.tryParse(pe.abilityId);
        if (id == null) {
            return;
        }
        AbilityDefinition def = AbilityRegistry.of(pe.level).getAbility(id);
        if (def == null) {
            return;
        }
        AbilityImpact.land(pe.level, pe.targetPos, pe.gooType, pe.targetFace, def, pe.point);
    }

    /**
     * Plays the slime-squish impact sound at the given coordinates.
     *
     * @param level the server level
     * @param x     the x coordinate
     * @param y     the y coordinate
     * @param z     the z coordinate
     */
    static void playImpactSound(ServerLevel level, double x, double y, double z) {
        float pitch = IMPACT_PITCH_BASE + level.getRandom().nextFloat() * IMPACT_PITCH_RANGE;
        level.playSound(null, x, y, z,
                SoundEvents.SLIME_SQUISH, SoundSource.PLAYERS, IMPACT_SOUND_VOLUME, pitch);
    }

    /**
     * A goo effect waiting for its goo to finish travelling.
     *
     * @param arrivalTick    the server tick the goo lands on
     * @param level          the level it lands in
     * @param thrower        the throwing player
     * @param gooType        the goo type thrown
     * @param targetEntityId the struck entity's id, or -1 for a block
     * @param targetPos      the struck block
     * @param targetFace     the struck face
     * @param abilityId      the ability the throw names, or empty
     * @param aim            the aim captured as the goo left the hand, or null to read the striker's on landing
     * @param point          the point an ability aiming a point resolves at, or null to resolve at the landing cell
     */
    public record PendingEffect(int arrivalTick, ServerLevel level,
                         ServerPlayer thrower, ResourceKey<GooTypeDefinition> gooType,
                         int targetEntityId, BlockPos targetPos,
                         Direction targetFace, String abilityId, @Nullable Aim aim, @Nullable Vec3 point) {

        /**
         * A pending effect resolving at its landing cell.
         *
         * @param arrivalTick    the server tick the goo lands on
         * @param level          the level it lands in
         * @param thrower        the throwing player
         * @param gooType        the goo type thrown
         * @param targetEntityId the struck entity's id, or -1 for a block
         * @param targetPos      the struck block
         * @param targetFace     the struck face
         * @param abilityId      the ability the throw names, or empty
         * @param aim            the aim captured as the goo left the hand, or null
         */
        public PendingEffect(int arrivalTick, ServerLevel level, ServerPlayer thrower,
                             ResourceKey<GooTypeDefinition> gooType, int targetEntityId, BlockPos targetPos,
                             Direction targetFace, String abilityId, @Nullable Aim aim) {
            this(arrivalTick, level, thrower, gooType, targetEntityId, targetPos, targetFace, abilityId, aim, null);
        }

        /**
         * A pending effect carrying no captured aim: one landing at once reads
         * its striker's aim the tick it lands.
         *
         * @param arrivalTick    the server tick the goo lands on
         * @param level          the level it lands in
         * @param thrower        the throwing player
         * @param gooType        the goo type thrown
         * @param targetEntityId the struck entity's id, or -1 for a block
         * @param targetPos      the struck block
         * @param targetFace     the struck face
         * @param abilityId      the ability the throw names, or empty
         */
        public PendingEffect(int arrivalTick, ServerLevel level, ServerPlayer thrower,
                             ResourceKey<GooTypeDefinition> gooType, int targetEntityId, BlockPos targetPos,
                             Direction targetFace, String abilityId) {
            this(arrivalTick, level, thrower, gooType, targetEntityId, targetPos, targetFace, abilityId, null, null);
        }
    }

    /**
     * The line a goo was aimed along.
     *
     * @param from      where the aim starts, the striker's eye
     * @param direction the aim's unit direction
     */
    public record Aim(Vec3 from, Vec3 direction) {
    }
}
