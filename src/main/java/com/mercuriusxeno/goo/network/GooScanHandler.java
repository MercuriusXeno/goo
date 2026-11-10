package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.deep.SculkerSwim;
import com.mercuriusxeno.goo.ability.program.ScanVisualStep;
import com.mercuriusxeno.goo.ability.program.SoundStep;
import com.mercuriusxeno.goo.ability.program.Step;
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
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends a scan ability: it aims as every free-aim ability does, at the point
 * the glove's reticule marks, and lands there the tick the use fires, capped
 * onto the delivery's range; the wave that runs the ability's program there
 * arrives after the delay its speed gives the distance. No goo leaves the
 * hand and nothing plays at the thrower
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
     * Prices, pays and sends a scan from the player's eye to the point the
     * glove aimed, as every free-aim ability lands at its reticule, capped
     * onto the delivery's range along the same line.
     *
     * @param player  the throwing player
     * @param payload the throw: the glove hand and the aimed point
     * @param gooType the ability's goo type
     * @param ability the scan ability
     */
    static void scan(ServerPlayer player, GooThrowPayload payload, ResourceKey<GooTypeDefinition> gooType,
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
        boolean submerged = submerged(player);
        Vec3 along = rippleLine(submerged, eye, payload.targetPoint(), player.getLookAngle());
        Vec3 landing = landingAt(eye, payload.targetPoint(), ability.delivery().range());
        ScanWaves waves = GooServerState.of(level.getServer()).scanWaves();
        int now = level.getServer().getTickCount();
        List<ScanWaves.Wave> sent = wavesFor(submerged, level, eye, along, landing, ability, now);
        sent.forEach(waves::schedule);
        Vec3 hand = ThrowArc.clampToReach(eye, payload.origin(), ThrowArc.HAND_REACH * player.getScale());
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
        return new ScanWavePayload(hand, last.landing(), last.arrivalTick() - now, visual.visual(), visual.trail());
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
     * point the wave has reached along the aimed line, through solid and open
     * space alike, out to the range, each vibrating there and only the last
     * sounding; from the surface, one to its landing.
     * submerged-click-ripples-through-the-ground-to-its-range
     *
     * @param submerged   whether the thrower clicks from inside the ground
     * @param level       the level thrown in
     * @param eye         the thrower's eye
     * @param look        the unit line from the eye toward the aimed point
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
        // the operator ruled a click every tick excessive: on the way the click sounds only as the trailing echoes
        List<Step> silent = ability.behaviors().stream().filter(step -> !(step instanceof SoundStep)).toList();
        List<ScanWaves.Wave> ripple = new ArrayList<>(ticks);
        for (int tick = 1; tick <= ticks; tick++) {
            Vec3 reached = eye.add(look.scale(Math.min(speed * tick, range)));
            List<Step> steps = tick == ticks ? ability.behaviors() : silent;
            ripple.add(new ScanWaves.Wave(currentTick + tick, level, reached, ability.id(), steps));
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
     * The line a scan runs along: from inside the ground, the way the diver
     * faces, since the glove's aim from inside a block meets that block at the
     * camera and names no direction; from the surface, toward the aimed point.
     * submerged-click-ripples-through-the-ground-to-its-range
     *
     * @param submerged whether the thrower clicks from inside the ground
     * @param eye       the thrower's eye
     * @param aimed     the point the glove aimed at
     * @param look      the thrower's unit look
     * @return the unit line
     */
    static Vec3 rippleLine(boolean submerged, Vec3 eye, Vec3 aimed, Vec3 look) {
        Vec3 line = aimed.subtract(eye);
        return submerged || line.lengthSqr() == 0 ? look : line.normalize();
    }

    /**
     * Where a scan lands for the point the glove aimed: that point, stood back
     * off the face or the body it sits on toward the eye, when it lies within
     * the range; the range's end along the same line when it lies past it.
     *
     * @param eye   the thrower's eye
     * @param aimed the point the glove's reticule aimed at
     * @param range the scan's reach, in blocks
     * @return the landing
     */
    static Vec3 landingAt(Vec3 eye, Vec3 aimed, double range) {
        Vec3 capped = GooThrowHandler.capToRange(eye, aimed, range);
        Vec3 line = aimed.subtract(eye);
        if (!capped.equals(aimed) || line.lengthSqr() == 0) {
            return capped;
        }
        return aimed.subtract(line.normalize().scale(STAND_OFF));
    }
}
