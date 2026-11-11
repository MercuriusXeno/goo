package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.particle.GloveSwing;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Where a glove stream's caster holds the glove and looks, as this client
 * sees the caster: the local player's glove as drawn, or another caster's
 * eye, the glove's own pivot as the look turns
 * (decision glove-stream-motes-swing-with-the-glove-at-launch).
 */
public final class CasterPoses {

    private CasterPoses() {
    }

    /**
     * The caster's pose this tick, read by id.
     *
     * @param casterId the caster's entity id
     * @return the pose, or null where this client holds no such entity
     */
    public static GloveSwing.@Nullable CasterPose poseOf(int casterId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(casterId) instanceof Entity caster)) {
            return null;
        }
        Vec3 glove = caster == mc.player ? GloveAim.handPosition(mc.gameRenderer.getMainCamera())
                : caster.getEyePosition();
        return new GloveSwing.CasterPose(glove, caster.getLookAngle());
    }

    /**
     * Where the caster's glove stands for a launch: the local player's glove
     * as drawn this frame, or the glove hand another caster's stream left from.
     *
     * @param casterId  the caster's entity id
     * @param sentGlove the glove hand the server sent
     * @return where the glove stands
     */
    public static Vec3 gloveOf(int casterId, Vec3 sentGlove) {
        Minecraft mc = Minecraft.getInstance();
        Entity local = mc.player;
        if (local != null && local.getId() == casterId) {
            return GloveAim.handPosition(mc.gameRenderer.getMainCamera());
        }
        return sentGlove;
    }
}
