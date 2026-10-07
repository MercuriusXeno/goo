package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The worldspace vibration while a bore runs: each frame the camera's
 * pitch, yaw and roll take a small offset from a sum of fast sines, so the
 * world trembles as if an earthquake drove the bore.
 * decision bore-vortex-with-a-worldspace-shake
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class CameraShake {

    /** The largest the offset on any one axis reaches, in degrees. */
    static final float AMPLITUDE_DEGREES = 0.6f;
    /** Pitch's two sine rates, in radians per tick; unrelated rates keep the shake from repeating. */
    private static final float PITCH_FAST = 2.9f;
    private static final float PITCH_SLOW = 1.7f;
    private static final float YAW_FAST = 3.3f;
    private static final float YAW_SLOW = 2.1f;
    private static final float ROLL_FAST = 2.5f;
    private static final float ROLL_SLOW = 1.3f;
    /** Each of an axis's two sines carries half its amplitude, so their sum stays within it. */
    private static final float HALF = 0.5f;

    /**
     * The camera's offset at one moment.
     *
     * @param pitch the pitch offset in degrees
     * @param yaw   the yaw offset in degrees
     * @param roll  the roll offset in degrees
     */
    record Offset(float pitch, float yaw, float roll) {
    }

    private CameraShake() {
    }

    /**
     * Offsets the camera's angles while a bore runs.
     *
     * @param event the camera angle event
     */
    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || BoreVortex.runningBore(player) == null) {
            return;
        }
        Offset offset = offsetAt((float) (mc.level.getGameTime() + event.getPartialTick()));
        event.setPitch(event.getPitch() + offset.pitch());
        event.setYaw(event.getYaw() + offset.yaw());
        event.setRoll(event.getRoll() + offset.roll());
    }

    /**
     * The shake's offset at a moment.
     *
     * @param time the game time including the partial tick
     * @return the offset, each axis within the amplitude
     */
    static Offset offsetAt(float time) {
        return new Offset(axis(time, PITCH_FAST, PITCH_SLOW), axis(time, YAW_FAST, YAW_SLOW),
                axis(time, ROLL_FAST, ROLL_SLOW));
    }

    private static float axis(float time, float fast, float slow) {
        return AMPLITUDE_DEGREES * HALF * (float) (Math.sin(time * fast) + Math.sin(time * slow));
    }
}
