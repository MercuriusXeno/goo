package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.LeechGooStep;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Leech goo's slow, the way soul sand slows what walks on it: each tick a
 * caught thing's horizontal motion is scaled by the speed factor its laying
 * ability names, its vertical motion and its fall distance left alone, so
 * momentum carries through the goo and a fall onto it harms as on any block.
 * leech-goo-slows-the-way-soul-sand-does
 */
public final class LeechSlow {

    private LeechSlow() {
    }

    /**
     * A motion slowed by a speed factor: its horizontal parts scaled, its
     * vertical part as it was.
     *
     * @param motion      the motion
     * @param speedFactor the share of its horizontal motion a caught thing keeps
     * @return the slowed motion
     */
    static Vec3 slowed(Vec3 motion, double speedFactor) {
        return motion.multiply(speedFactor, 1.0, speedFactor);
    }

    /**
     * Slows a thing a Leech cell caught by the speed factor its laying
     * ability's leech goo step names, once a tick however many of the goo's
     * hooks catch it; on both sides, since the client moves its own player.
     *
     * @param level  the level the cell stands in
     * @param entity the thing caught
     * @param leech  the cell's laying ability's leech goo step
     */
    static void slowOnce(Level level, Entity entity, LeechGooStep leech) {
        long now = level.getGameTime();
        if (entity.getData(GooAttachments.LEECH_SLOWED_AT) != now) {
            entity.setData(GooAttachments.LEECH_SLOWED_AT, now);
            entity.setDeltaMovement(slowed(entity.getDeltaMovement(), leech.speedFactor()));
        }
    }
}
