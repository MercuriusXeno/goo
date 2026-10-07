package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.HeldRoute;
import com.mercuriusxeno.goo.ability.program.ChannelAim;
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
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jspecify.annotations.Nullable;
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
            channelOnPlayer(player, new ChannelAim(payload.aimPoint(), payload.planeY()), ability);
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
        runSteps(PlayerHost.channeling(player.level(), player, aim), HostKind.PLAYER, ability.behaviors(), ability);
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
        Delivery delivery = ability.delivery();
        Vec3 apex = ThrowArc.clampToReach(player.getEyePosition(), origin, ThrowArc.HAND_REACH * player.getScale());
        Vec3 axis = player.getLookAngle();
        sprayParticles(level, apex, axis, delivery);
        List<Step> blockSteps = channelSteps(ability.behaviors(), true);
        if (!blockSteps.isEmpty()) {
            ChannelAim aim = new ChannelAim(player.getEyePosition().add(axis.scale(delivery.range())),
                    Double.NEGATIVE_INFINITY, delivery.coneDegrees());
            runSteps(PlayerHost.channeling(level, player, aim), HostKind.PLAYER, blockSteps, ability);
        }
        List<Step> entitySteps = channelSteps(ability.behaviors(), false);
        if (entitySteps.isEmpty()) {
            return;
        }
        for (LivingEntity living : livingInCone(level, player, apex, axis, delivery)) {
            runSteps(new EntityHost(level, living, player), HostKind.ENTITY, entitySteps, ability);
        }
    }

    /**
     * A stream program's top-level steps split by the pass they run in: those
     * needing the channel run once a tick on the player over the cone's blocks
     * (decisions bore-vortex-with-a-worldspace-shake,
     * petrify-stone-encasement-and-calcify-map), and the rest run on every
     * entity in the cone.
     *
     * @param behaviors the stream's top-level steps
     * @param channel   true for the block pass's steps, false for the entity pass's
     * @return the steps of that pass, in program order
     */
    static List<Step> channelSteps(List<Step> behaviors, boolean channel) {
        return behaviors.stream()
                .filter(step -> step.requires().contains(HostCapability.CHANNEL) == channel)
                .toList();
    }

    /**
     * Runs a pass's steps on its host, logging a program the host refuses.
     *
     * @param host    the pass's host
     * @param kind    the host's kind
     * @param steps   the pass's steps
     * @param ability the stream ability
     */
    private static void runSteps(StepHost host, HostKind kind, List<Step> steps, AbilityDefinition ability) {
        try {
            ProgramBehavior.forHost(steps, kind).tick(host);
        } catch (ProgramLoadException e) {
            Goo.LOGGER.error(LOG_PROGRAM_REFUSED, ability.id(), e.getMessage());
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
        if (Delivery.NO_PARTICLE.equals(delivery.particle())) {
            return;
        }
        SimpleParticles.resolve(delivery.particle()).ifPresent(particle -> {
            for (int i = 1; i <= PARTICLES_PER_TICK; i++) {
                Vec3 at = apex.add(axis.scale(delivery.range() * i / PARTICLES_PER_TICK));
                level.sendParticles(particle, at.x, at.y, at.z, 1, PARTICLE_SPREAD, PARTICLE_SPREAD,
                        PARTICLE_SPREAD, PARTICLE_SPEED);
            }
        });
    }
}
