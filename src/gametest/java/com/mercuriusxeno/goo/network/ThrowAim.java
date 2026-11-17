package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.crystal.ThrownKnife;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.DripArc;
import com.mercuriusxeno.goo.throwing.ProjectileFlight;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * Aims a gametest caster's held throw at a point: turns the caster toward it
 * and pitches the look so the throw the hold takes next, flown as the server
 * flies it under gravity, crosses the point.
 * decision knives-and-javelins-drop-like-an-arrow
 */
final class ThrowAim {

    private static final float LOOK_UP_MOST = -45f;
    private static final float LOOK_DOWN_MOST = 60f;
    private static final int AIM_HALVINGS = 40;
    private static final int MOST_AIM_TICKS = 40;
    private static final CoatRay.BlockClip OPEN_AIR = (from, to) -> Optional.empty();

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
        Vec3 eye = caster.getEyePosition();
        Vec3 across = new Vec3(target.x - eye.x, 0, target.z - eye.z);
        float yaw = (float) Math.toDegrees(Math.atan2(-across.x, across.z));
        float up = LOOK_UP_MOST;
        float down = LOOK_DOWN_MOST;
        for (int halving = 0; halving < AIM_HALVINGS; halving++) {
            float pitch = (up + down) / 2;
            if (crossingHeight(eye, pitch, yaw, held, speed, across) > target.y) {
                up = pitch;
            } else {
                down = pitch;
            }
        }
        float pitch = (up + down) / 2;
        caster.setYRot(yaw);
        caster.yRotO = yaw;
        caster.setYHeadRot(yaw);
        caster.yHeadRotO = yaw;
        caster.setXRot(pitch);
        caster.xRotO = pitch;
    }

    // The height a throw at a look crosses the target's distance across the ground at.
    private static double crossingHeight(Vec3 eye, float pitch, float yaw, int held, double speed, Vec3 across) {
        Vec3 look = Vec3.directionFromRotation(pitch, yaw);
        Vec3 ground = across.normalize();
        double reach = across.length();
        Vec3 position = ThrownKnife.launchPointOf(eye, look, held);
        Vec3 velocity = ProjectileFlight.launch(look, speed);
        for (int tick = 0; tick < MOST_AIM_TICKS; tick++) {
            DripArc.Step step = ProjectileFlight.step(position, velocity, ProjectileFlight.ARROW, OPEN_AIR,
                    DripArc.NO_LIVING, point -> true);
            double from = position.subtract(eye).dot(ground);
            double to = step.position().subtract(eye).dot(ground);
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
