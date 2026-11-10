package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Sends a scan ability: the server reads the landing along the thrower's look
 * the tick the use fires, the first block face or entity within the
 * delivery's range, and queues the wave that runs the ability's program there
 * once it arrives. Nothing leaves the hand and nothing plays at the thrower
 * (decision click-is-a-hit-scan-drawn-as-a-delayed-wave).
 */
public final class GooScanHandler {

    /**
     * How far a landing stands back from the face or the body it struck, along
     * the look, so it sounds in the open cell before a wall rather than inside it.
     */
    static final double STAND_OFF = 0.05;
    /** Log: a scan the player cannot pay for, refused. */
    private static final String LOG_NO_GOO = "Scan rejected: insufficient {} goo";

    private GooScanHandler() {
    }

    /**
     * Prices, pays and sends a scan from the player's eye along the look.
     *
     * @param player  the throwing player
     * @param gooType the ability's goo type
     * @param ability the scan ability
     */
    static void scan(ServerPlayer player, ResourceKey<GooTypeDefinition> gooType, AbilityDefinition ability) {
        ServerLevel level = player.level();
        if (!AbilityCharges.charge(player, gooType, ability.cost().exactIn(GooValues.of(level)))) {
            if (Goo.LOGGER.isDebugEnabled()) {
                Goo.LOGGER.debug(LOG_NO_GOO, GooTypes.id(gooType));
            }
            return;
        }
        GooThrowHandler.consumeReagents(player, ability.id().toString(), gooType);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 landing = landingAlong(player, eye, look, ability.delivery().range());
        GooServerState.of(level.getServer()).scanWaves().schedule(ScanWaves.thrown(level, eye, landing,
                ability.delivery(), ability.id(), ability.behaviors(), level.getServer().getTickCount()));
    }

    /**
     * Reads where a look lands within a range: the first block face it meets,
     * or a body before that face.
     *
     * @param thrower the player looking, whom the look passes through
     * @param eye     the thrower's eye
     * @param look    the thrower's unit look
     * @param range   how far the look reads, in blocks
     * @return the landing
     */
    private static Vec3 landingAlong(ServerPlayer thrower, Vec3 eye, Vec3 look, double range) {
        Vec3 end = eye.add(look.scale(range));
        BlockHitResult block = thrower.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE, thrower));
        Vec3 stop = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : end;
        EntityHitResult body = ProjectileUtil.getEntityHitResult(thrower, eye, stop, new AABB(eye, stop).inflate(1),
                candidate -> stopsTheScan(candidate, thrower), eye.distanceToSqr(stop));
        return landing(look, block, body, end);
    }

    /**
     * Where a scan lands: the body struck, else the block face struck, else the
     * range's end, each stood back along the look off what it struck.
     *
     * @param look  the thrower's unit look
     * @param block what the look's clip met
     * @param body  the body the look met before that block, or null
     * @param end   the point the range ends at
     * @return the landing
     */
    static Vec3 landing(Vec3 look, BlockHitResult block, @Nullable EntityHitResult body, Vec3 end) {
        if (body != null) {
            return standOff(body.getLocation(), look);
        }
        return block.getType() == HitResult.Type.BLOCK ? standOff(block.getLocation(), look) : end;
    }

    private static Vec3 standOff(Vec3 struck, Vec3 look) {
        return struck.subtract(look.scale(STAND_OFF));
    }

    /**
     * Whether an entity may stop a scan: any entity but the one looking.
     *
     * @param candidate the entity the look crosses
     * @param thrower   the entity looking
     * @return true where the look lands on it
     */
    static boolean stopsTheScan(Entity candidate, Entity thrower) {
        return candidate != thrower && candidate.isPickable();
    }
}
