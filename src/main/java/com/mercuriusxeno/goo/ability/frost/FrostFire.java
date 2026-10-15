package com.mercuriusxeno.goo.ability.frost;

import com.mercuriusxeno.goo.ability.program.SimpleParticles;
import com.mercuriusxeno.goo.ability.program.SoundCue;
import com.mercuriusxeno.goo.ability.program.SoundKind;
import com.mercuriusxeno.goo.ability.program.SoundPlays;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Frost leeching the heat from a sphere: every fire block whose center stands
 * within it goes out with the vanilla extinguish hiss and a puff of smoke, as
 * water on fire reads, and every burning entity within it stops burning.
 * frost-reach-extinguishes-fire-and-burning-mobs
 */
public final class FrostFire {

    /** The hiss a fire block makes going out, vanilla's extinguish at its own volume and pitch. */
    static final SoundCue HISS = new SoundCue(Identifier.withDefaultNamespace("block.fire.extinguish"),
            SoundKind.BLOCKS, 0.5f, 2.6f);
    /** The smoke a fire block puffs going out. */
    static final Identifier SMOKE = Identifier.withDefaultNamespace("smoke");
    static final int SMOKE_COUNT = 8;
    private static final double SMOKE_SPREAD = 0.25;
    private static final double SMOKE_SPEED = 0.01;

    private FrostFire() {
    }

    /**
     * Puts out every fire block whose center stands within a sphere, and the
     * fire on every burning entity within it.
     *
     * @param level  the level
     * @param center the sphere's center
     * @param radius the sphere's radius in blocks
     */
    public static void putOutWithin(ServerLevel level, Vec3 center, double radius) {
        BlockPos from = BlockPos.containing(center.subtract(radius, radius, radius));
        BlockPos to = BlockPos.containing(center.add(radius, radius, radius));
        double reachSquared = radius * radius;
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (Vec3.atCenterOf(pos).distanceToSqr(center) <= reachSquared && burns(level.getBlockState(pos))) {
                putOut(level, pos.immutable());
            }
        }
        AABB box = new AABB(center.subtract(radius, radius, radius), center.add(radius, radius, radius));
        for (Entity entity : level.getEntities((Entity) null, box, Entity::isOnFire)) {
            if (entity.distanceToSqr(center) <= reachSquared) {
                entity.clearFire();
            }
        }
    }

    /**
     * Whether a block is fire frost puts out.
     *
     * @param state the block
     * @return true for any block tagged fire
     */
    public static boolean burns(BlockState state) {
        return state.is(BlockTags.FIRE);
    }

    /**
     * Puts out one fire block: it becomes air, hisses and puffs smoke for
     * every player near it.
     *
     * @param level the level
     * @param pos   the fire block
     */
    public static void putOut(ServerLevel level, BlockPos pos) {
        level.removeBlock(pos, false);
        Vec3 at = Vec3.atCenterOf(pos);
        SoundPlays.play(level, at, HISS);
        SimpleParticles.resolve(SMOKE).ifPresent(smoke -> level.sendParticles(smoke, at.x(), at.y(), at.z(),
                SMOKE_COUNT, SMOKE_SPREAD, SMOKE_SPREAD, SMOKE_SPREAD, SMOKE_SPEED));
    }
}
