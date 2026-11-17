package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.crystal.ThrownKnife;
import com.mercuriusxeno.goo.item.GooCasters;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.DripArc;
import com.mercuriusxeno.goo.throwing.ProjectileFlight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * Aims a gametest caster's held throw at a point: turns the caster toward it
 * and pitches the look so the throw the hold takes next, leaving its slot at
 * the crosshair's point and flown as the server flies it under gravity, crosses the point.
 * decision knives-and-javelins-drop-like-an-arrow
 */
final class ThrowAim {

    private static final float LOOK_UP_MOST = -45f;
    private static final float LOOK_DOWN_MOST = 60f;
    private static final int AIM_HALVINGS = 40;
    private static final int MOST_AIM_TICKS = 40;
    /** Turns of the look toward the target from the slot the throw leaves from, each closing the gap. */
    private static final int AIM_TURNS = 6;
    private static final CoatRay.BlockClip OPEN_AIR = (from, to) -> Optional.empty();
    /** A knife's speed, which a javelin's tops (crystal_knives.json, metal_dart.json). */
    private static final double KNIFE_SPEED = 1.5;

    // The stack a throw leaves from: the count held, whether it holds javelins, and the glove's side.
    private record Stack(int held, boolean javelins, boolean gloveRight) {
    }

    private ThrowAim() {
    }

    /**
     * Turns and pitches the caster so its next throw crosses a point.
     *
     * @param caster the caster
     * @param target the point the throw crosses
     * @param held   the count the hold holds as it throws, which sets the slot the throw leaves from
     * @param speed  the throw's speed, blocks a tick
     */
    static void aimAt(ServerPlayer caster, Vec3 target, int held, double speed) {
        Stack stack = new Stack(held, speed > KNIFE_SPEED, GooCasters.casterOnTheRight(caster));
        float yaw = yawToward(caster.getEyePosition(), target);
        float pitch = 0f;
        // the throw heads from its slot beside the eye at a point far straight ahead, so the look turns until that
        // path runs over the target, each turn closing the gap
        for (int turn = 0; turn < AIM_TURNS; turn++) {
            pitch = pitchThrough(caster, yaw, stack, speed, target);
            yaw += yawToward(launchOf(caster, pitch, yaw, stack), target) - headingYawOf(caster, pitch, yaw, stack);
        }
        pitch = pitchThrough(caster, yaw, stack, speed, target);
        look(caster, pitch, yaw);
    }

    // Where the throw leaves at a look: the point its slot draws from.
    private static Vec3 launchOf(ServerPlayer caster, float pitch, float yaw, Stack stack) {
        look(caster, pitch, yaw);
        Vec3 slot = ThrownKnife.launchPointOf(caster.getEyePosition(), pitch, yaw, stack.gloveRight(), stack.held(),
                stack.javelins());
        return ThrownKnife.drawnFromOf(slot, ThrownKnife.headingFrom(slot, ThrownKnife.aimPointOf(caster, 1f)),
                stack.javelins(), 1f);
    }

    // The yaw the throw heads along across the ground at a look.
    private static float headingYawOf(ServerPlayer caster, float pitch, float yaw, Stack stack) {
        Vec3 launch = launchOf(caster, pitch, yaw, stack);
        return yawToward(launch, ThrownKnife.aimPointOf(caster, 1f));
    }

    private static void look(ServerPlayer caster, float pitch, float yaw) {
        caster.setYRot(yaw);
        caster.yRotO = yaw;
        caster.setYHeadRot(yaw);
        caster.yHeadRotO = yaw;
        caster.setXRot(pitch);
        caster.xRotO = pitch;
    }

    // The yaw whose look runs across the ground from a point toward the target.
    private static float yawToward(Vec3 from, Vec3 target) {
        return (float) Math.toDegrees(Math.atan2(-(target.x - from.x), target.z - from.z));
    }

    // The pitch whose throw at a yaw crosses the target's height above the target.
    private static float pitchThrough(ServerPlayer caster, float yaw, Stack stack, double speed, Vec3 target) {
        float up = LOOK_UP_MOST;
        float down = LOOK_DOWN_MOST;
        for (int halving = 0; halving < AIM_HALVINGS; halving++) {
            float pitch = (up + down) / 2;
            if (crossingHeight(caster, pitch, yaw, stack, speed, target) > target.y) {
                up = pitch;
            } else {
                down = pitch;
            }
        }
        return (up + down) / 2;
    }

    // The height a throw at a look crosses the target's distance across the ground from its launch at.
    private static double crossingHeight(ServerPlayer caster, float pitch, float yaw, Stack stack, double speed,
                                         Vec3 target) {
        look(caster, pitch, yaw);
        Vec3 slot = ThrownKnife.launchPointOf(caster.getEyePosition(), pitch, yaw, stack.gloveRight(), stack.held(),
                stack.javelins());
        Vec3 crosshair = ThrownKnife.aimPointOf(caster, 1f);
        Vec3 position = ThrownKnife.drawnFromOf(slot, ThrownKnife.headingFrom(slot, crosshair), stack.javelins(), 1f);
        Vec3 across = new Vec3(target.x - position.x, 0, target.z - position.z);
        Vec3 ground = across.normalize();
        double reach = across.length();
        Vec3 launch = position;
        Vec3 velocity = ThrownKnife.launchVelocityToward(position, crosshair, speed);
        for (int tick = 0; tick < MOST_AIM_TICKS; tick++) {
            DripArc.Step step = ProjectileFlight.step(position, velocity, ProjectileFlight.ARROW, OPEN_AIR,
                    DripArc.NO_LIVING, point -> true);
            double from = position.subtract(launch).dot(ground);
            double to = step.position().subtract(launch).dot(ground);
            if (to >= reach) {
                double share = (reach - from) / (to - from);
                return position.y + (step.position().y - position.y) * share;
            }
            position = step.position();
            velocity = step.velocity();
        }
        return position.y;
    }
}
