package com.mercuriusxeno.goo.ability.hex;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A natural pool: every mob type the biome at a cell spawns naturally, of
 * the categories asked, that fits the cell, light level ignored; one comes
 * up at random, each type as likely as any other.
 * spawn-hostile-shape-peaceful-from-a-slime
 */
public final class NaturalSpawns {

    private NaturalSpawns() {
    }

    /**
     * The types a pool draws from: each listed type once, in listing order,
     * those that fit the cell.
     *
     * @param listed the types the biome lists, repeats among them
     * @param fits   whether a type fits the cell
     * @param <T>    the type's type
     * @return the distinct fitting types
     */
    public static <T> List<T> pool(Stream<T> listed, Predicate<T> fits) {
        return listed.distinct().filter(fits).toList();
    }

    /**
     * Draws a type the biome at the cell spawns naturally in one of the
     * categories asked, that the caller allows and that fits the cell:
     * placed as its kind spawns (on ground, in water) with room for its
     * body, day or night.
     *
     * @param level      the level
     * @param cell       the cell the mob would stand in
     * @param random     the draw
     * @param categories the categories the draw takes from
     * @param allowed    the types the caller allows
     * @return the type, or empty where nothing the biome spawns fits
     */
    public static Optional<EntityType<?>> drawAt(ServerLevel level, BlockPos cell, RandomSource random,
                                                 Predicate<MobCategory> categories,
                                                 Predicate<EntityType<?>> allowed) {
        MobSpawnSettings settings = level.getBiome(cell).value().getMobSettings();
        Stream<EntityType<?>> listed = Arrays.stream(MobCategory.values()).filter(categories)
                .flatMap(category -> settings.getMobs(category).unwrap().stream())
                .map(weighted -> weighted.value().type());
        List<EntityType<?>> pool = pool(listed, type -> allowed.test(type) && fits(level, cell, type));
        return pool.isEmpty() ? Optional.empty() : Optional.of(pool.get(random.nextInt(pool.size())));
    }

    private static boolean fits(ServerLevel level, BlockPos cell, EntityType<?> type) {
        Vec3 feet = Vec3.atBottomCenterOf(cell);
        return SpawnPlacements.isSpawnPositionOk(type, level, cell)
                && level.noCollision(type.getSpawnAABB(feet.x, feet.y, feet.z));
    }
}
