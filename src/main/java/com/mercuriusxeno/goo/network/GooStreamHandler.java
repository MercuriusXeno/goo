package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityTags;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.HealReport;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
import com.mercuriusxeno.goo.ability.program.ChargedMultipliers;
import com.mercuriusxeno.goo.ability.program.ChargedStep;
import com.mercuriusxeno.goo.ability.program.EntityHost;
import com.mercuriusxeno.goo.ability.program.HostCapability;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.PlayerHost;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.StepHost;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
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
    /** Particles sprayed along the cone each tick. */
    private static final int PARTICLES_PER_TICK = 6;
    /** Spread of each particle around its point on the axis, in blocks. */
    private static final double PARTICLE_SPREAD = 0.15;
    /** Speed of each particle, in blocks per tick. */
    private static final double PARTICLE_SPEED = 0.05;
    /** The widest a charged cone opens, a half turn. */
    private static final double MAX_CONE_DEGREES = 180.0;
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
        if (ability == null || !drainShare(player, gooType, ability)) {
            return;
        }
        if (HeldRoute.channelsOnSelf(ability.delivery(), ability.badge())) {
            channelOnPlayer(player, new ChannelAim(payload.aimPoint(), payload.plane()), ability);
        } else {
            strikeCone(player, payload.origin(), ability);
        }
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
     * @return false when the player cannot pay the share, which stops the stream
     */
    private static boolean drainShare(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType,
                                      AbilityDefinition ability) {
        MinecraftServer server = player.level().getServer();
        int held = GooServerState.of(server).streamHolds().advance(player.getUUID(), server.getTickCount());
        int share = StreamHolds.shareAt(ability.cost(), ability.delivery().ticksPerCharge(), held);
        if (!GooSourceScanner.hasEnough(player, gooType, share)) {
            return false;
        }
        GooSourceScanner.deplete(player, gooType, share);
        return true;
    }

    /**
     * Sprays the cone from the glove hand along the player's look, runs the
     * block pass on the player and the entity pass on every living entity
     * inside the cone.
     *
     * @param player  the streaming player
     * @param origin  the glove hand the client sent
     * @param ability the stream ability
     */
    private static void strikeCone(ServerPlayer player, Vec3 origin, AbilityDefinition ability) {
        ServerLevel level = player.level();
        ChargedMultipliers charged = chargedFor(player, ability);
        Delivery delivery = chargedDelivery(ability.delivery(), charged);
        Vec3 apex = ThrowArc.clampToReach(player.getEyePosition(), origin, ThrowArc.HAND_REACH * player.getScale());
        Vec3 axis = player.getLookAngle();
        CastOn cast = new CastOn(level, player, ability.id(), charged);
        List<Step> entitySteps = channelSteps(ability.behaviors(), false);
        List<Integer> healed = new ArrayList<>();
        if (delivery.range() > 0) {
            // reserve-hearts-sit-behind-the-bar: a stream reaching nothing runs only on its caster
            sprayParticles(level, apex, axis, delivery);
            runBlockPass(cast, axis, delivery, ability.behaviors());
            for (LivingEntity living : livingInCone(level, player, apex, axis, delivery)) {
                HEALS.runNoting(living, healed, () -> runSteps(new EntityHost(level, living, player), HostKind.ENTITY,
                        entitySteps, cast));
            }
        }
        if (ability.hasTag(AbilityTags.SELF)) {
            // vitality-waves-regenerate-and-court
            HEALS.runNoting(player, healed,
                    () -> runSteps(new PlayerHost(level, player), HostKind.PLAYER, entitySteps, cast));
        }
        if (!healed.isEmpty()) {
            // vitality-waves-regenerate-and-court: the client homes goo to each healed thing and stars it
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
     * Runs a stream's block pass once this tick on the player, aimed at the
     * end of the reach along the look, where the stream's program holds steps
     * needing the channel
     * (decisions bore-vortex-with-a-worldspace-shake, petrify-stone-encasement-and-calcify-map).
     *
     * @param cast      the tick's cast
     * @param axis      the look
     * @param delivery  the stream's delivery this tick
     * @param behaviors the stream's top-level steps
     */
    private static void runBlockPass(CastOn cast, Vec3 axis, Delivery delivery, List<Step> behaviors) {
        List<Step> blockSteps = channelSteps(behaviors, true);
        if (blockSteps.isEmpty()) {
            return;
        }
        ServerPlayer player = cast.player();
        ChannelAim aim = new ChannelAim(player.getEyePosition().add(axis.scale(delivery.range())), null,
                delivery.coneDegrees());
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
        return behaviors.stream()
                .filter(step -> worksTheCone(step) == channel)
                .toList();
    }

    /**
     * Whether a step runs in the block pass: it needs the channel, or works
     * the cone's blocks.
     *
     * @param step the step
     * @return true for a block pass step
     */
    private static boolean worksTheCone(Step step) {
        return step.requires().contains(HostCapability.CHANNEL);
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
     * Sprays the delivery's particle at even steps along the cone's axis.
     *
     * @param level    the server level
     * @param apex     the cone's apex
     * @param axis     the cone's axis
     * @param delivery the stream delivery
     */
    private static void sprayParticles(ServerLevel level, Vec3 apex, Vec3 axis, Delivery delivery) {
        delivery.particle().flatMap(SimpleParticles::resolve).ifPresent(particle -> {
            for (int i = 1; i <= PARTICLES_PER_TICK; i++) {
                Vec3 at = apex.add(axis.scale(delivery.range() * i / PARTICLES_PER_TICK));
                level.sendParticles(particle, at.x, at.y, at.z, 1, PARTICLE_SPREAD, PARTICLE_SPREAD,
                        PARTICLE_SPREAD, PARTICLE_SPEED);
            }
        });
    }
}
