package com.mercuriusxeno.goo.entity;

import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * What a rising Spire hunk sheds on the client each tick it climbs: chips of
 * its own stone spilling off its sides above the ground it breaks out of, and
 * a low rumble every few ticks, so the stone grinds its way up the whole rise.
 * decision spire-entity-izes-its-blocks-and-raises-them-smoothly
 */
final class SpireRumble {

    /** Chips the pillar spills each tick it climbs. */
    private static final int CHIPS_PER_TICK = 4;
    /** How fast a chip leaves the pillar's side, in blocks a tick. */
    private static final double CHIP_SPEED = 0.06;
    /** Ticks between the rumble's beats. */
    private static final int RUMBLE_EVERY = 2;
    private static final float RUMBLE_VOLUME = 1f;
    private static final float RUMBLE_PITCH = 0.5f;
    /** A hair out from the side, so a chip starts in the air. */
    private static final double OFF_THE_SIDE = 0.05;
    private static final float HALF_F = 0.5f;

    private SpireRumble() {
    }

    /**
     * Spills this tick's chips and, on a beat, plays the rumble.
     *
     * @param hunk    the climbing hunk, on the client
     * @param elapsed ticks since the rise began
     */
    static void play(BlockHunk hunk, long elapsed) {
        Level level = hunk.level();
        if (hunk.blocks().isEmpty()) {
            return;
        }
        RandomSource random = level.getRandom();
        AABB box = hunk.getBoundingBox();
        double ground = hunk.groundTop();
        if (box.maxY <= ground) {
            return;
        }
        BlockParticleOption chip = new BlockParticleOption(ParticleTypes.BLOCK, hunk.blocks().getFirst().state());
        for (int index = 0; index < CHIPS_PER_TICK; index++) {
            Vec3 at = onASide(box, ground, random);
            Vec3 out = at.subtract(box.getCenter()).multiply(1, 0, 1).normalize().scale(CHIP_SPEED);
            level.addParticle(chip, at.x, at.y, at.z, out.x, 0, out.z);
        }
        if (elapsed % RUMBLE_EVERY == 0) {
            rumble(level, box.getCenter(), ground, random);
        }
    }

    private static void rumble(Level level, Vec3 middle, double ground, RandomSource random) {
        float pitch = RUMBLE_PITCH + random.nextFloat() * RUMBLE_PITCH * HALF_F;
        level.playLocalSound(middle.x, ground, middle.z, SoundEvents.GRAVEL_BREAK, SoundSource.BLOCKS,
                RUMBLE_VOLUME, pitch, false);
        // Deepslate's heavier break under the gravel, so the rumble carries.
        level.playLocalSound(middle.x, ground, middle.z, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS,
                RUMBLE_VOLUME, pitch, false);
    }

    /**
     * A random point just off one of the pillar's four sides, above the ground.
     *
     * @param box    the pillar's box
     * @param ground the ground's height
     * @param random the random
     * @return the point
     */
    private static Vec3 onASide(AABB box, double ground, RandomSource random) {
        double y = ground + random.nextDouble() * (box.maxY - ground);
        double along = random.nextDouble();
        return switch (Direction.Plane.HORIZONTAL.getRandomDirection(random)) {
            case WEST -> new Vec3(box.minX - OFF_THE_SIDE, y, box.minZ + along * box.getZsize());
            case EAST -> new Vec3(box.maxX + OFF_THE_SIDE, y, box.minZ + along * box.getZsize());
            case NORTH -> new Vec3(box.minX + along * box.getXsize(), y, box.minZ - OFF_THE_SIDE);
            default -> new Vec3(box.minX + along * box.getXsize(), y, box.maxZ + OFF_THE_SIDE);
        };
    }
}
