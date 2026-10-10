package com.mercuriusxeno.goo.ability.hex;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;
import java.util.List;
import java.util.Optional;

/**
 * One agitator attempt: a random column within the radius, its highest
 * floor within the radius, and a monster the biome spawns that vanilla's
 * spawn rules would let stand there, darkness included; the attempt spawns
 * it there, or fails with nothing standing.
 * agitator-prism-quickens-until-a-spawn
 */
public final class MonsterStirring {

    /** A whole turn, in degrees, over which the monster's facing is drawn. */
    private static final float FULL_TURN_DEGREES = 360f;
    /** A radius reaches this many cells across the center, both sides of it. */
    private static final int BOTH_SIDES = 2;

    private MonsterStirring() {
    }

    /**
     * Attempts one monster spawn around a center.
     *
     * @param level  the level
     * @param center the agitator's cell
     * @param radius how far from the center, on each axis, the attempt may place a monster
     * @return true when a monster spawned
     */
    public static boolean attempt(ServerLevel level, BlockPos center, int radius) {
        RandomSource random = level.getRandom();
        Optional<BlockPos> floor = floorIn(level, center.offset(spread(random, radius), radius, spread(random, radius)),
                radius * BOTH_SIDES);
        if (floor.isEmpty()) {
            return false;
        }
        BlockPos cell = floor.get();
        List<EntityType<?>> monsters = NaturalSpawns.pool(level.getBiome(cell).value().getMobSettings()
                .getMobs(MobCategory.MONSTER).unwrap().stream().map(weighted -> weighted.value().type()),
                type -> allowedAt(level, cell, type, random));
        if (monsters.isEmpty()) {
            return false;
        }
        return spawn(level, cell, monsters.get(random.nextInt(monsters.size()))).isPresent();
    }

    /**
     * The highest open cell standing on a solid block, scanning down a column.
     *
     * @param level the level
     * @param top   the column's top cell
     * @param depth how many cells below the top the scan reaches
     * @return the cell, or empty where the column holds no floor
     */
    private static Optional<BlockPos> floorIn(ServerLevel level, BlockPos top, int depth) {
        for (int down = 0; down <= depth; down++) {
            BlockPos cell = top.below(down);
            if (level.isEmptyBlock(cell) && level.getBlockState(cell.below()).isFaceSturdy(level, cell.below(),
                    Direction.UP)) {
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }

    private static int spread(RandomSource random, int radius) {
        return random.nextInt(radius * BOTH_SIDES + 1) - radius;
    }

    private static boolean allowedAt(ServerLevel level, BlockPos cell, EntityType<?> type, RandomSource random) {
        Vec3 feet = Vec3.atBottomCenterOf(cell);
        return SpawnPlacements.isSpawnPositionOk(type, level, cell)
                && SpawnPlacements.checkSpawnRules(type, level, EntitySpawnReason.NATURAL, cell, random)
                && level.noCollision(type.getSpawnAABB(feet.x, feet.y, feet.z));
    }

    private static Optional<Entity> spawn(ServerLevel level, BlockPos cell, EntityType<?> type) {
        Entity entity = type.create(level, EntitySpawnReason.NATURAL);
        if (entity == null) {
            return Optional.empty();
        }
        Vec3 feet = Vec3.atBottomCenterOf(cell);
        entity.snapTo(feet.x, feet.y, feet.z, level.getRandom().nextFloat() * FULL_TURN_DEGREES, 0f);
        if (entity instanceof Mob mob) {
            EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(cell), EntitySpawnReason.NATURAL,
                    null);
        }
        return level.addFreshEntity(entity) ? Optional.of(entity) : Optional.empty();
    }
}
