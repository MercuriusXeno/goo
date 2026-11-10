package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.deep.SculkerSwim;
import com.mercuriusxeno.goo.ability.program.ScanVisualStep;
import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.throwing.ThrowArc;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
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
import java.util.ArrayList;
import java.util.List;

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
     * @param origin  the glove hand the client drew from, which the drawn wave leaves
     * @param gooType the ability's goo type
     * @param ability the scan ability
     */
    static void scan(ServerPlayer player, Vec3 origin, ResourceKey<GooTypeDefinition> gooType,
                     AbilityDefinition ability) {
        ServerLevel level = player.level();
        if (!AbilityCharges.charge(player, gooType, priceFor(player, ability))) {
            if (Goo.LOGGER.isDebugEnabled()) {
                Goo.LOGGER.debug(LOG_NO_GOO, GooTypes.id(gooType));
            }
            return;
        }
        GooThrowHandler.consumeReagents(player, ability.id().toString(), gooType);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 landing = landingAlong(player, eye, look, ability.delivery().range());
        ScanWaves waves = GooServerState.of(level.getServer()).scanWaves();
        int now = level.getServer().getTickCount();
        List<ScanWaves.Wave> sent = wavesFor(submerged(player), level, eye, look, landing, ability, now);
        sent.forEach(waves::schedule);
        Vec3 hand = ThrowArc.clampToReach(eye, origin, ThrowArc.HAND_REACH * player.getScale());
        ScanVisualStep.in(ability.behaviors()).ifPresent(visual -> EntityVisuals.sendToWatchers(player,
                drawnWave(hand, sent, now, visual)));
    }

    /**
     * The wave every watching client draws: from the glove hand to the last
     * point the scan reaches, over the ticks the server waits to get there.
     * click-is-a-hit-scan-drawn-as-a-delayed-wave
     *
     * @param hand   the glove hand
     * @param sent   the waves the scan scheduled, the last reaching farthest
     * @param now    the server tick the use fired on
     * @param visual the visual the ability's choreography names
     * @return the payload
     */
    static ScanWavePayload drawnWave(Vec3 hand, List<ScanWaves.Wave> sent, int now, ScanVisualStep visual) {
        ScanWaves.Wave last = sent.getLast();
        return new ScanWavePayload(hand, last.landing(), last.arrivalTick() - now, visual.visual(), visual.fade());
    }

    /**
     * Whether a thrower clicks from inside the ground: diving under Sculker
     * with its eyes in a block that stops movement.
     * submerged-click-ripples-through-the-ground-to-its-range
     *
     * @param player the throwing player
     * @return true for a submerged thrower
     */
    static boolean submerged(ServerPlayer player) {
        BlockPos eyeCell = BlockPos.containing(player.getEyePosition());
        return SculkerSwim.passesThrough(player)
                && !player.level().getBlockState(eyeCell).getCollisionShape(player.level(), eyeCell).isEmpty();
    }

    /**
     * The waves a scan sends: from inside the ground, one each tick at the
     * point the wave has reached along the look, through solid and open space
     * alike, out to the range; from the surface, one to its landing.
     * submerged-click-ripples-through-the-ground-to-its-range
     *
     * @param submerged   whether the thrower clicks from inside the ground
     * @param level       the level thrown in
     * @param eye         the thrower's eye
     * @param look        the thrower's unit look
     * @param landing     where a surfaced scan lands
     * @param ability     the scan ability
     * @param currentTick the server tick the use fired on
     * @return the waves to schedule
     */
    static List<ScanWaves.Wave> wavesFor(boolean submerged, ServerLevel level, Vec3 eye, Vec3 look, Vec3 landing,
                                         AbilityDefinition ability, int currentTick) {
        if (!submerged) {
            return List.of(ScanWaves.thrown(level, eye, landing, ability.delivery(), ability.id(),
                    ability.behaviors(), currentTick));
        }
        double range = ability.delivery().range();
        double speed = ability.delivery().blocksPerTick();
        int ticks = (int) Math.ceil(range / speed);
        List<ScanWaves.Wave> ripple = new ArrayList<>(ticks);
        for (int tick = 1; tick <= ticks; tick++) {
            Vec3 reached = eye.add(look.scale(Math.min(speed * tick, range)));
            ripple.add(new ScanWaves.Wave(currentTick + tick, level, reached, ability.id(), ability.behaviors()));
        }
        return ripple;
    }

    /**
     * What a scan costs its thrower: nothing while the self ability it names
     * stands on them, its cost otherwise.
     * click-costs-one-mb-and-nothing-under-sculker
     *
     * @param player  the throwing player
     * @param ability the scan ability
     * @return the exact cost in mB
     */
    static GooFraction priceFor(ServerPlayer player, AbilityDefinition ability) {
        return ability.costFor(player.getData(GooAttachments.HELD_EFFECTS)::holds, GooValues.of(player.level()));
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
