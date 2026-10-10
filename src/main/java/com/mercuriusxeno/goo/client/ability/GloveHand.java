package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.throwing.GloveAim;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Where a caster's glove hand stands in the world, the point a held glow
 * ability leaves from: the goo in the local player's glove in first person,
 * else the caster's main hand, out from the eye by its look.
 * decisions sunbeam-splits-at-the-prism-with-a-glisten, radiant-wisps-where-light-is-low
 */
public final class GloveHand {

    /** Where another player's glove hand sits, from the eye: below it, out to the side and ahead. */
    private static final double HAND_BELOW_EYE = 0.45;
    private static final double HAND_TO_THE_SIDE = 0.35;
    private static final double HAND_AHEAD = 0.4;
    /** Which way from the look the main hand sits: to the right, or mirrored for a left-handed player. */
    private static final double RIGHT_HANDED = 1;
    private static final double LEFT_HANDED = -1;

    private GloveHand() {
    }

    /**
     * The glove hand's world point.
     *
     * @param mc          the client
     * @param caster      the casting player
     * @param partialTick the frame's partial tick
     * @return the world point
     */
    public static Vec3 of(Minecraft mc, Entity caster, float partialTick) {
        if (caster == mc.player && mc.options.getCameraType().isFirstPerson()) {
            return GloveAim.handPosition(mc.gameRenderer.getMainCamera());
        }
        Vec3 look = caster.getViewVector(partialTick);
        Vec3 side = look.cross(new Vec3(0, 1, 0)).normalize();
        double handedness = caster instanceof Player player && player.getMainArm() == HumanoidArm.LEFT
                ? LEFT_HANDED : RIGHT_HANDED;
        return caster.getEyePosition(partialTick).subtract(0, HAND_BELOW_EYE, 0)
                .add(side.scale(HAND_TO_THE_SIDE * handedness)).add(look.scale(HAND_AHEAD));
    }
}
