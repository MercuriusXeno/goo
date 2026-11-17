package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.item.GooCasters;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The point a caster's held stack, Knives' pyramid or Dart's triangle,
 * centers over this frame: over the palm the hand's pass draws for the local
 * player in first person, the lifted blob's place while it stands, and over
 * the body's hand for a caster drawn in third person, so the pile stands over
 * the hand however it is seen.
 * decision knives-hover-above-the-blob-cube-over-the-hand
 * decision dart-javelins-hover-over-the-shrinking-blob-cube
 */
final class StackAnchor {

    private StackAnchor() {
    }

    /**
     * The center of the cube a caster's stack stands over this frame.
     *
     * @param caster  the caster
     * @param partial the frame's partial tick
     * @return the cube's center
     */
    static Vec3 cubeOf(Player caster, float partial) {
        Minecraft mc = Minecraft.getInstance();
        if (caster == mc.player && mc.options.getCameraType().isFirstPerson()) {
            Vec3 overThePalm = SpraySource.of(caster.getId(), caster.getEyePosition(partial), partial);
            return LiftedPalmBlob.blobOf(caster.getId(), partial).map(LiftedPalmBlob.Blob::place).orElse(overThePalm);
        }
        return KnivesCharge.handAt(caster, GooCasters.casterOnTheRight(caster), partial)
                .add(0, SpraySource.RISE_ABOVE_THE_PALM, 0);
    }
}
