package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.ExitTilt;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The first-person camera of a player come out of a linked pair tilted:
 * the player's own look turned by the tilt its body still holds, as yaw,
 * pitch and roll for the camera alone, so the view comes out turned the way
 * the body is and levels with it; the player's stored yaw and pitch, which
 * aim and the server read, stand as the landing set them.
 * bodies-emerge-along-their-travel-and-ease-upright
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CameraTilt {

    private CameraTilt() {
    }

    /**
     * The camera's angles in degrees, as the camera reads them.
     *
     * @param yaw   its yaw
     * @param pitch its pitch
     * @param roll  its roll
     */
    public record Angles(float yaw, float pitch, float roll) {
    }

    /**
     * A look turned by the tilt a body still holds.
     *
     * @param yaw   the look's yaw, in degrees
     * @param pitch the look's pitch, in degrees
     * @param tilt  the tilt left
     * @return the camera's angles
     */
    public static Angles turnedBy(float yaw, float pitch, ExitTilt tilt) {
        if (!tilt.tilts()) {
            return new Angles(yaw, pitch, 0F);
        }
        Quaternionf turned = new Quaternionf().rotationAxis((float) Math.toRadians(tilt.degrees()),
                (float) tilt.axis().x(), (float) tilt.axis().y(), (float) tilt.axis().z())
                .mul(new Quaternionf().rotationYXZ(HALF_TURN - (float) Math.toRadians(yaw),
                        -(float) Math.toRadians(pitch), 0F));
        // Read back as the camera builds its rotation: rotationYXZ(half turn - yaw, -pitch, -roll).
        Vector3f euler = turned.getEulerAnglesYXZ(new Vector3f());
        return new Angles((float) Math.toDegrees(HALF_TURN - euler.y()), -(float) Math.toDegrees(euler.x()),
                -(float) Math.toDegrees(euler.z()));
    }

    /**
     * Turns the first-person camera of a player still holding an exit tilt.
     *
     * @param event the camera's angles for this frame
     */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || event.getCamera().isDetached()) {
            return;
        }
        turnCamera(event, mc.player, mc.level.getGameTime() + event.getPartialTick());
    }

    /**
     * Turns the camera's angles for this frame by the exit tilt a player
     * still holds, writing the event's angles alone, so the player's own yaw
     * and pitch stay as the landing set them.
     * bodies-emerge-along-their-travel-and-ease-upright
     *
     * @param event  the camera's angles for this frame
     * @param player the player the camera stands in
     * @param now    the game time, with the frame's share of a tick
     */
    static void turnCamera(ViewportEvent.ComputeCameraAngles event, Entity player, double now) {
        ClientExitTilts.tiltOf(player.getId(), now).ifPresent(tilt -> {
            Angles turned = turnedBy(event.getYaw(), event.getPitch(), tilt);
            event.setYaw(turned.yaw());
            event.setPitch(turned.pitch());
            event.setRoll(event.getRoll() + turned.roll());
        });
    }

    private static final float HALF_TURN = (float) Math.PI;
}
