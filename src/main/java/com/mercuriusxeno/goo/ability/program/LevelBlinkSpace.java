package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * A level as the blinking entity reads it: a box fits where the entity
 * collides with nothing, and a line stops on the first block it would
 * collide with, fluids passed through.
 * Decision blink-lands-safely-costed-by-distance.
 *
 * @param level    the level the entity blinks in
 * @param blinker  the blinking entity, whose own box never blocks it
 */
public record LevelBlinkSpace(Level level, Entity blinker) implements BlinkSpace {

    @Override
    public boolean fits(AABB box) {
        return level.noCollision(blinker, box);
    }

    @Override
    public Optional<FaceHit> firstFace(Vec3 from, Vec3 to) {
        BlockHitResult hit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, blinker));
        return hit != null && hit.getType() == HitResult.Type.BLOCK
                ? Optional.of(new FaceHit(hit.getLocation(), hit.getBlockPos(), hit.getDirection()))
                : Optional.empty();
    }
}
