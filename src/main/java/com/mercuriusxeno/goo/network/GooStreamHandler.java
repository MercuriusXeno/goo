package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.HealReport;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.ability.StreamSound;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.ability.program.ChannelHost;
import com.mercuriusxeno.goo.ability.program.ChargedMultipliers;
import com.mercuriusxeno.goo.ability.program.ChargedStep;
import com.mercuriusxeno.goo.ability.program.EntityHost;
import com.mercuriusxeno.goo.ability.program.FloorReach;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.PlayerHost;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.ability.program.SoundKind;
import com.mercuriusxeno.goo.ability.program.SoundPlays;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.StepHost;
import com.mercuriusxeno.goo.ability.spray.SprayPrograms;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.throwing.StreamCone;
import com.mercuriusxeno.goo.throwing.ThrowArc;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Server side of a stream delivery: each tick the glove's use stays down,
 * one share of the ability's cost drains and its programs run on every
 * living entity inside the cone; a hold stops when the use releases or the
 * goo runs out (decision stream-delivery-held-cone). A channel, a self
 * ability wearing the channeled badge, drains the same share and runs its
 * programs on the player, carrying the tick's aim
 * (decision flatten-disc-cursor-breaks-above-the-plane). A stream whose
 * program needs the channel runs a block pass instead of striking entities:
 * its programs run on the player, aimed at the end of its reach along the
 * look (decision bore-vortex-with-a-worldspace-shake).
 */
public final class GooStreamHandler {

    private static final String LOG_PROGRAM_REFUSED = "Ability {} refused on its held pass's host: {}";
    /** Log: one held tick reached the server, so a hold that does nothing shows where it stops. */
    private static final String LOG_HELD_TICK = "Held tick of {} for {}: hold tick {}";
    /** Particles sprayed along the cone each tick. */
    private static final int PARTICLES_PER_TICK = 6;
    /** The widest a charged cone opens, a half turn. */
    private static final double MAX_CONE_DEGREES = 180.0;
    /**
     * Launch speed per block of the stream's range: a mote slowing by a tenth
     * each tick carries ten times its launch speed, so it reaches the cone's end.
     */
    private static final double LAUNCH_SPEED_PER_BLOCK = 0.1;
    /** Below this squared gap the crosshair sits on the hand and the look aims instead. */
    private static final double MIN_AIM_LENGTH_SQUARED = 1e-6;
    /** Rays the spray casts each tick to find the floors it lands on. */
    private static final int FLOOR_RAYS_PER_TICK = 12;
    /** A particle sent with a count of zero flies along the vector it is handed. */
    private static final int ALONG_THE_VECTOR = 0;
    /** Reads which living things a tick's program healed. */
    private static final HealReport<LivingEntity> HEALS =
            new HealReport<>(LivingEntity::getHealth, LivingEntity::getId);

    private GooStreamHandler() {
    }

    /**
     * Handles a stream tick on the server thread.
     *
     * @param payload the stream tick
     * @param context the network context
     */
    public static void handle(GooStreamPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                streamTick(player, payload);
            }
        });
    }

    /**
     * Runs one tick of a held ability: a stream or channel of the type, held
     * in a glove, drains its share for this tick of the hold, then a stream
     * strikes its cone and a channel runs on the player.
     *
     * @param player  the streaming player
     * @param payload the stream tick
     */
    public static void streamTick(ServerPlayer player, GooStreamPayload payload) {
        ResourceKey<GooTypeDefinition> gooType = GooTypes.known(payload.gooTypeId());
        if (gooType == null || !GooThrowHandler.validateGlove(player)) {
            return;
        }
        AbilityDefinition ability = heldAbility(player, payload, gooType);
        int held = ability == null ? 0 : drainShare(player, gooType, ability);
        Goo.LOGGER.debug(LOG_HELD_TICK, payload.abilityId(), player.getName().getString(), held);
        if (held == 0) {
            return;
        }
        if (HeldRoute.channelsOnSelf(ability.delivery(), ability.badge())) {
            channelOnPlayer(player, new ChannelAim(payload.aimPoint(), payload.plane()), ability);
        } else {
            strikeCone(player, payload.origin(), ability, held);
        }
        // mycosis-spore-stream-buds-and-poisons
        ability.delivery().sound().filter(sound -> sound.playsOn(held))
                .ifPresent(sound -> playStreamSound(player, sound));
    }

    /**
     * Plays one beat of the held ability's sound at the player, its pitch strayed a little.
     *
     * @param player the streaming player
     * @param sound  the stream's sound
     */
    private static void playStreamSound(ServerPlayer player, StreamSound sound) {
        float pitch = sound.pitchFor(player.getRandom().nextFloat());
        // decay-gnats-degrade-each-block-once: a looped sound's holder hears its own fading loop instead
        SoundPlays.playExcept(player.level(), sound.loop() ? player : null, player.getEyePosition(),
                new SoundCue(sound.sound(), SoundKind.PLAYERS, sound.volume(), pitch));
    }

    /**
     * The ability the payload names, where the player may use it and it runs while held.
     *
     * @param player  the holding player
     * @param payload the held tick
     * @param gooType the ability's goo type
     * @return the ability, or null for one the player cannot use or one that does not run while held
     */
    private static @Nullable AbilityDefinition heldAbility(ServerPlayer player, GooStreamPayload payload,
                                                            ResourceKey<GooTypeDefinition> gooType) {
        AbilityDefinition ability = GooThrowHandler.usableAbility(player, payload.abilityId(), gooType);
        return ability != null && HeldRoute.runsWhileHeld(ability.delivery(), ability.badge()) ? ability : null;
    }

    /**
     * Runs a channel's programs on the player for this tick of the hold,
     * logging a program the player host refuses.
     *
     * @param player  the channeling player
     * @param aim     the hold's aim this tick
     * @param ability the channel ability
     */
    private static void channelOnPlayer(ServerPlayer player, ChannelAim aim, AbilityDefinition ability) {
        runSteps(PlayerHost.channeling(player.level(), player, aim), HostKind.PLAYER, ability.behaviors(),
                new CastOn(player.level(), player, ability.id(), chargedFor(player, ability)));
    }

    /**
     * Drains this tick's share of the ability's cost.
     *
     * @param player  the streaming player
     * @param gooType the ability's goo type
     * @param ability the stream ability
     * @return the hold's tick count, or 0 when the tick runs nothing: a second stream tick in one
     *         server tick, or a share the player cannot pay, which stops the stream
     */
    private static int drainShare(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
                                      AbilityDefinition ability) {
        MinecraftServer server = player.level().getServer();
        int held = GooServerState.of(server).streamHolds().advance(player.getUUID(), server.getTickCount());
        if (held == 0) {
            return 0;
        }
        int share = StreamHolds.shareAt(ability.cost(), ability.delivery().ticksPerCharge(), held);
        if (!GooSourceScanner.hasEnough(player, gooType, share)) {
            return 0;
        }
        GooSourceScanner.deplete(player, gooType, share);
        return held;
    }

    /**
     * Sprays the cone from the glove hand along the player's look, runs the
     * block pass on the player and the entity pass on every living entity
     * inside the cone.
     *
     * @param player  the streaming player
     * @param origin  the glove hand the client sent
     * @param ability the stream ability
     * @param held    the hold's tick count, 1 on its first tick
     */
    private static void strikeCone(ServerPlayer player, Vec3 origin, AbilityDefinition ability, int held) {
        ServerLevel level = player.level();
        ChargedMultipliers charged = chargedFor(player, ability);
        Delivery delivery = chargedDelivery(ability.delivery(), charged);
        Vec3 apex = ThrowArc.clampToReach(player.getEyePosition(), origin, ThrowArc.HAND_REACH * player.getScale());
        Vec3 axis = player.getLookAngle();
        CastOn cast = new CastOn(level, player, ability.id(), charged);
        // decay-gnats-degrade-each-block-once: what the stream strikes follows the look; only its motes
        // fly from the hand onto the point the crosshair lands on
        Vec3 target = crosshairTarget(player, delivery.range());
        List<Step> entitySteps = channelSteps(ability.behaviors(), false);
        List<Integer> healed = new ArrayList<>();
        if (delivery.range() > 0) {
            // reserve-hearts-sit-behind-the-bar: a stream reaching nothing runs only on its caster
            sprayParticles(level, apex, aimFrom(apex, target, axis), delivery,
                    Math.min(delivery.range(), apex.distanceTo(target)));
            for (LivingEntity living : runPasses(cast, apex, axis, ability, delivery, held)) {
                HEALS.runNoting(living, healed, () -> runSteps(new EntityHost(level, living, player), HostKind.ENTITY,
                        entitySteps, cast));
            }
            sprayFloors(player, apex, axis, ability);
        }
        strikeCaster(cast, ability, apex, entitySteps, healed);
    }

    /**
     * Runs the entity pass on the caster of a stream tagged self, then tells
     * the watchers who the tick healed (decision vitality-waves-regenerate-and-court).
     *
     * @param cast        the tick's cast
     * @param ability     the stream ability
     * @param apex        the cone's apex
     * @param entitySteps the entity pass's steps
     * @param healed      the living the tick healed so far, which the caster joins
     */
    private static void strikeCaster(CastOn cast, AbilityDefinition ability, Vec3 apex, List<Step> entitySteps,
                                     List<Integer> healed) {
        ServerPlayer player = cast.player();
        if (ability.hasTag(AbilityTags.SELF)) {
            HEALS.runNoting(player, healed,
                    () -> runSteps(new PlayerHost(cast.level(), player), HostKind.PLAYER, entitySteps, cast));
        }
        if (!healed.isEmpty()) {
            // the client homes goo to each healed thing and stars it
            EntityVisuals.sendToWatchers(player, new StreamHealedPayload(player.getId(), apex, healed));
        }
    }

    /**
     * One tick's cast of a stream or channel: where it runs, who holds it,
     * which ability, and the Charged multipliers it carries.
     *
     * @param level     the server level
     * @param player    the holding player
     * @param abilityId the ability's id, for a refusal's log
     * @param charged   the cast's Charged multipliers, all 1 for an uncharged player
     */
    private record CastOn(ServerLevel level, ServerPlayer player, Identifier abilityId, ChargedMultipliers charged) {
    }

    /**
     * The Charged multipliers a player's hold of an ability carries: the
     * ability's own while the player stands charged, else none
     * (decision charged-scales-channel-params-by-json).
     *
     * @param player  the holding player
     * @param ability the held ability
     * @return the multipliers
     */
    private static ChargedMultipliers chargedFor(ServerPlayer player, AbilityDefinition ability) {
        return ChargedStep.isCharged(player) ? ability.charged() : ChargedMultipliers.NONE;
    }

    /**
     * The stream's delivery under Charged: its range and cone scaled by the
     * area multiplier, the cone kept within a half turn
     * (decision charged-scales-channel-params-by-json).
     *
     * @param delivery the ability's delivery
     * @param charged  the cast's Charged multipliers
     * @return the delivery the cone sprays
     */
    static Delivery chargedDelivery(Delivery delivery, ChargedMultipliers charged) {
        if (charged.area() == 1.0) {
            return delivery;
        }
        return delivery.withCone(delivery.range() * charged.area(),
                Math.min(MAX_CONE_DEGREES, delivery.coneDegrees() * charged.area()));
    }

    /**
     * Where the crosshair lands within a stream's reach: the face of the first
     * block the player's look meets, or the end of the reach along the look.
     *
     * @param player the streaming player
     * @param range  the stream's reach in blocks
     * @return the point
     */
    private static Vec3 crosshairTarget(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getLookAngle().scale(range));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : end;
    }

    /**
     * The direction from the cone's apex to the crosshair's point, so a stream
     * opening from the hand still meets the crosshair rather than running
     * beside its line.
     *
     * @param apex     the cone's apex, the glove hand
     * @param target   where the crosshair lands
     * @param fallback the look, for a target on the apex itself
     * @return the unit axis
     */
    static Vec3 aimFrom(Vec3 apex, Vec3 target, Vec3 fallback) {
        Vec3 line = target.subtract(apex);
        return line.lengthSqr() < MIN_AIM_LENGTH_SQUARED ? fallback : line.normalize();
    }

    /**
     * Runs the stream's {@code on_blocks} steps on the floors its rays land on
     * (decision mycosis-spore-stream-buds-and-poisons).
     *
     * @param player  the streaming player
     * @param apex    the cone's apex
     * @param axis    the cone's axis
     * @param ability the stream ability
     */
    private static void sprayFloors(ServerPlayer player, Vec3 apex, Vec3 axis, AbilityDefinition ability) {
        if (ability.onBlocks().isEmpty()) {
            return;
        }
        ServerLevel level = player.level();
        Delivery delivery = ability.delivery();
        SprayPrograms.runOnFloors(level, FloorReach.struckInCone(level, player, apex, axis, delivery.range(),
                delivery.coneDegrees(), FLOOR_RAYS_PER_TICK, level.getRandom()), apex, ability);
    }

    /**
     * Runs a stream's block pass once this tick on the player, aimed at the
     * end of the reach along the look, where the stream's program holds steps
     * needing the channel
     * (decisions bore-vortex-with-a-worldspace-shake, petrify-stone-encasement-and-calcify-map).
     *
     * @param cast     the tick's cast
     * @param axis     the look
     * @param delivery the stream's delivery this tick
     * @param ability  the stream ability
     * @param held     the hold's tick count, which a wave front grows by
     * @param reaching whether the pass reaches new blocks, false while a mob-first stream bites
     */
    private static void runBlockPass(CastOn cast, Vec3 axis, Delivery delivery, AbilityDefinition ability, int held,
                                     boolean reaching) {
        List<Step> blockSteps = channelSteps(ability.behaviors(), true);
        if (blockSteps.isEmpty()) {
            return;
        }
        ServerPlayer player = cast.player();
        ChannelAim aim = new ChannelAim(player.getEyePosition().add(axis.scale(delivery.range())), null,
                delivery.coneDegrees(), held, reaching);
        runSteps(PlayerHost.channeling(player.level(), player, aim), HostKind.PLAYER, blockSteps, cast);
    }

    /**
     * A stream program's top-level steps split by the pass they run in: those
     * needing the channel or working the cone's blocks run once a tick on the
     * player over the cone's blocks (decisions bore-vortex-with-a-worldspace-shake,
     * petrify-stone-encasement-and-calcify-map),
     * and the rest run on every entity in the cone, and on the caster of a
     * stream tagged self.
     *
     * @param behaviors the stream's top-level steps
     * @param channel   true for the block pass's steps, false for the entity pass's
     * @return the steps of that pass, in program order
     */
    static List<Step> channelSteps(List<Step> behaviors, boolean channel) {
        return ChannelHost.passSteps(behaviors, channel);
    }

    /**
     * Runs a pass's steps on its host with the cast's Charged multipliers,
     * logging a program the host refuses.
     *
     * @param host  the pass's host
     * @param kind  the host's kind
     * @param steps the pass's steps
     * @param cast  the tick's cast
     */
    private static void runSteps(StepHost host, HostKind kind, List<Step> steps, CastOn cast) {
        try {
            ProgramBehavior.forHost(steps, kind).tick(host, cast.charged());
        } catch (ProgramLoadException e) {
            Goo.LOGGER.error(LOG_PROGRAM_REFUSED, cast.abilityId(), e.getMessage());
        }
    }

    /**
     * The living entities inside the cone, the streaming player aside.
     *
     * @param level    the server level
     * @param player   the streaming player
     * @param apex     the cone's apex
     * @param axis     the cone's axis
     * @param delivery the stream delivery
     * @return the entities the stream reaches
     */
    private static List<LivingEntity> livingInCone(ServerLevel level, ServerPlayer player, Vec3 apex, Vec3 axis,
                                                   Delivery delivery) {
        AABB reach = new AABB(apex, apex).inflate(delivery.range());
        return level.getEntitiesOfClass(LivingEntity.class, reach, living -> living != player && living.isAlive()
                && StreamCone.contains(apex, axis, delivery.range(), delivery.coneDegrees(),
                        living.getBoundingBox().getCenter()));
    }

    /**
     * Runs the stream's block pass this tick and answers the living its
     * entity pass strikes: every living thing in the cone, beside the block
     * pass; or, for a mob-first stream, the nearest mob, while the block pass
     * reaches no new block but keeps working the ones already reached, and
     * the block pass alone when no mob stands in the cone.
     *
     * @param cast     the tick's cast
     * @param apex     the cone's apex
     * @param axis     the cone's axis
     * @param ability  the stream ability
     * @param delivery the stream's delivery this tick, under Charged
     * @param held     the hold's tick count, which a wave front grows by
     * @return the living the entity pass strikes this tick
     */
    private static List<LivingEntity> runPasses(CastOn cast, Vec3 apex, Vec3 axis, AbilityDefinition ability,
                                                Delivery delivery, int held) {
        List<LivingEntity> inCone = livingInCone(cast.level(), cast.player(), apex, axis, delivery);
        // decay-gnats-degrade-each-block-once: a mob in the cone takes the swarm, else the blocks do;
        // the operator's ruling: blocks already painted keep stepping while the swarm bites
        boolean mobFirst = ability.hasTag(AbilityTags.MOB_FIRST);
        List<LivingEntity> struck = mobFirst ? nearestMob(inCone, apex) : inCone;
        runBlockPass(cast, axis, delivery, ability, held, !mobFirst || struck.isEmpty());
        return struck;
    }

    /**
     * The mob nearest the cone's apex among the living a stream reaches, the
     * one a mob-first stream strikes (decision decay-gnats-degrade-each-block-once).
     *
     * @param living the living entities in the cone
     * @param apex   the cone's apex
     * @return the nearest mob alone, or none when no mob stands in the cone
     */
    static List<LivingEntity> nearestMob(List<LivingEntity> living, Vec3 apex) {
        return living.stream().filter(Mob.class::isInstance)
                .min(Comparator.comparingDouble(entity -> entity.distanceToSqr(apex)))
                .map(List::of).orElse(List.of());
    }

    /**
     * Launches the delivery's particle from the glove, each mote flying out
     * along its own heading inside the cone and coming to rest as far off as
     * the crosshair lands (decisions mycosis-spore-stream-buds-and-poisons,
     * decay-gnats-degrade-each-block-once).
     *
     * @param level    the server level
     * @param apex     the cone's apex
     * @param axis     the cone's axis
     * @param delivery the stream delivery
     * @param reach    how far the motes fly, in blocks
     */
    private static void sprayParticles(ServerLevel level, Vec3 apex, Vec3 axis, Delivery delivery, double reach) {
        RandomSource random = level.getRandom();
        double speed = reach * LAUNCH_SPEED_PER_BLOCK;
        delivery.particle().flatMap(SimpleParticles::resolve).ifPresent(particle -> {
            for (int i = 0; i < PARTICLES_PER_TICK; i++) {
                Vec3 heading = StreamCone.launchDirection(axis, delivery.coneDegrees(), random.nextDouble(),
                        random.nextDouble());
                level.sendParticles(particle, apex.x, apex.y, apex.z, ALONG_THE_VECTOR, heading.x, heading.y,
                        heading.z, speed);
            }
        });
    }
}
