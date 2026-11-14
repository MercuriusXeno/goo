package com.mercuriusxeno.goo.ability.typhoon;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ShulkerBullet;
import net.minecraft.world.phys.Vec3;

/**
 * Shulk's release: the held blob spews a vanilla shulker bullet at the mob
 * the press took, homing on it and levitating it on the hit, a shulker's
 * own attack with its own sound.
 * decision charm-like-abilities-lock-the-highlighted-mob-on-right-click
 * operator: "It's gotta be an identical function, for good theming."
 */
public final class ShulkerSpew {

    private static final float SHOOT_VOLUME = 2f;
    private static final float SHOOT_PITCH_BASE = 1f;
    private static final float SHOOT_PITCH_SPREAD = 0.2f;

    private ShulkerSpew() {
    }

    /**
     * Spews one shulker bullet from the blob at the taken mob.
     *
     * @param caster the casting player, the bullet's owner
     * @param taken  the mob the press took, which the bullet homes on
     * @param blob   where the held blob stands, which the bullet leaves from
     * @return the bullet spewed
     */
    public static ShulkerBullet spew(ServerPlayer caster, Entity taken, Vec3 blob) {
        ServerLevel level = caster.level();
        // A shulker passes the axis of the face it clings to, which its bullet never first flies along;
        // a caster clings to none, so the bullet leaves level with the ground.
        ShulkerBullet bullet = new ShulkerBullet(level, caster, taken, Direction.Axis.Y);
        bullet.snapTo(blob.x, blob.y, blob.z, bullet.getYRot(), bullet.getXRot());
        level.addFreshEntity(bullet);
        level.playSound(null, blob.x, blob.y, blob.z, SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS, SHOOT_VOLUME,
                SHOOT_PITCH_BASE + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * SHOOT_PITCH_SPREAD);
        return bullet;
    }
}
