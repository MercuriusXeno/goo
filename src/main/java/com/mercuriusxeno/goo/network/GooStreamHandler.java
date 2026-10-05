package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.program.EntityHost;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.SimpleParticles;
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
import java.util.List;

/**
 * Server side of a stream delivery: each tick the glove's use stays down,
 * one share of the ability's cost drains and its programs run on every
 * living entity inside the cone; a hold stops when the use releases or the
 * goo runs out (decision stream-delivery-held-cone).
 */
public final class GooStreamHandler {

    private static final String LOG_PROGRAM_REFUSED = "Ability {} refused on the streamed entity: {}";
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
     * Runs one tick of a held stream: a stream ability of the type, held in a
     * glove, drains its share for this tick of the hold and strikes its cone.
     *
     * @param player  the streaming player
     * @param payload the stream tick
     */
    public static void streamTick(ServerPlayer player, GooStreamPayload payload) {
        ResourceKey<GooTypeDefinition> gooType = GooTypes.known(payload.gooTypeId());
        if (gooType == null || !GooThrowHandler.validateGlove(player)) {
            return;
        }
        AbilityDefinition ability = GooThrowHandler.thrownAbility(player.level(), payload.abilityId(), gooType);
        if (ability != null && ability.delivery().kind() == DeliveryKind.STREAM
                && drainShare(player, gooType, ability)) {
            strikeCone(player, payload.origin(), ability);
        }
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
     * Sprays the cone from the glove hand along the player's look and runs the
     * ability on every living entity inside it.
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
        for (LivingEntity living : livingInCone(level, player, apex, axis, delivery)) {
            runProgram(level, player, living, ability);
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
     * Runs the ability's programs on one streamed entity, logging a program
     * the entity host refuses.
     *
     * @param level   the server level
     * @param player  the streaming player
     * @param living  the streamed entity
     * @param ability the stream ability
     */
    private static void runProgram(ServerLevel level, ServerPlayer player, LivingEntity living,
                                   AbilityDefinition ability) {
        try {
            ProgramBehavior.forHost(ability.behaviors(), HostKind.ENTITY).tick(new EntityHost(level, living, player));
        } catch (ProgramLoadException e) {
            Goo.LOGGER.error(LOG_PROGRAM_REFUSED, ability.id(), e.getMessage());
        }
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
        SimpleParticles.resolve(delivery.particle()).ifPresent(particle -> {
            for (int i = 1; i <= PARTICLES_PER_TICK; i++) {
                Vec3 at = apex.add(axis.scale(delivery.range() * i / PARTICLES_PER_TICK));
                level.sendParticles(particle, at.x, at.y, at.z, 1, PARTICLE_SPREAD, PARTICLE_SPREAD,
                        PARTICLE_SPREAD, PARTICLE_SPEED);
            }
        });
    }
}
