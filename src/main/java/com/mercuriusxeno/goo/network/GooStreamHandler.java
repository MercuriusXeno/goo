package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.program.FloorReach;
import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.ability.spray.SprayPrograms;
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
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.List;

/**
 * Server side of a stream delivery: each tick the glove's use stays down,
 * one share of the ability's cost drains, its programs run on every
 * living entity inside the cone and its {@code on_blocks} steps on every
 * floor the cone holds; a hold stops when the use releases or the goo runs
 * out (decisions stream-delivery-held-cone and mycosis-spore-stream-buds-and-poisons).
 */
public final class GooStreamHandler {

    /** Particles sprayed along the cone each tick. */
    private static final int PARTICLES_PER_TICK = 6;
    /**
     * Launch speed per block of the stream's range: a mote slowing by a tenth
     * each tick carries ten times its launch speed, so it reaches the cone's end.
     */
    private static final double LAUNCH_SPEED_PER_BLOCK = 0.1;
    /** Rays the spray casts each tick to find the floors it lands on. */
    private static final int FLOOR_RAYS_PER_TICK = 12;
    /** A particle sent with a count of zero flies along the vector it is handed. */
    private static final int ALONG_THE_VECTOR = 0;

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
        AbilityDefinition ability = GooThrowHandler.usableAbility(player, payload.abilityId(), gooType);
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
     * ability on every living entity and every floor inside it.
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
            SprayPrograms.runOnLiving(level, living, player, ability);
        }
        if (!ability.onBlocks().isEmpty()) {
            SprayPrograms.runOnFloors(level, FloorReach.struckInCone(level, player, apex, axis, delivery.range(),
                    delivery.coneDegrees(), FLOOR_RAYS_PER_TICK, level.getRandom()), apex, ability);
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
     * Launches the delivery's particle from the glove, each mote flying out
     * along its own heading inside the cone (decision mycosis-spore-stream-buds-and-poisons).
     *
     * @param level    the server level
     * @param apex     the cone's apex
     * @param axis     the cone's axis
     * @param delivery the stream delivery
     */
    private static void sprayParticles(ServerLevel level, Vec3 apex, Vec3 axis, Delivery delivery) {
        RandomSource random = level.getRandom();
        double speed = delivery.range() * LAUNCH_SPEED_PER_BLOCK;
        SimpleParticles.resolve(delivery.particle()).ifPresent(particle -> {
            for (int i = 0; i < PARTICLES_PER_TICK; i++) {
                Vec3 heading = StreamCone.launchDirection(axis, delivery.coneDegrees(), random.nextDouble(),
                        random.nextDouble());
                level.sendParticles(particle, apex.x, apex.y, apex.z, ALONG_THE_VECTOR, heading.x, heading.y,
                        heading.z, speed);
            }
        });
    }
}
