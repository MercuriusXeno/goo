package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.entity.EldritchMonstrosity;
import com.mercuriusxeno.goo.registry.GooDataMaps;
import com.mercuriusxeno.goo.registry.GooEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.random.Weighted;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.SpawnClusterSizeEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Spawns the eldritch in larger groups on lunar's dark side: each monstrosity group the biome spawns there, and the
 * cluster a spawn pass stops at, run the biome's dark-side factor times the light side's.
 * decision dark-side-is-an-endless-night-of-eldritch-rich-in-void-and-xeno
 */
@EventBusSubscriber(modid = Goo.MODID)
public final class LunarEldritchEvents {

    private LunarEldritchEvents() {
    }

    /**
     * Scales the monstrosity's group on the dark side.
     *
     * @param event the spawn list a natural spawn picks from
     */
    @SubscribeEvent
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.MONSTER || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = event.getPos();
        LunarEldritch.DarkSideGroups groups = darkSideGroupsAt(level, pos);
        if (groups == null) {
            return;
        }
        for (Weighted<MobSpawnSettings.SpawnerData> entry : List.copyOf(event.getSpawnerDataList())) {
            MobSpawnSettings.SpawnerData spawner = entry.value();
            if (spawner.type() == GooEntities.ELDRITCH_MONSTROSITY.get()) {
                event.removeSpawnerData(entry);
                event.addSpawnerData(new Weighted<>(new MobSpawnSettings.SpawnerData(spawner.type(), UniformInt.of(
                        LunarEldritch.volumeAt(pos.getX(), spawner.count().minInclusive(), groups.groupFactor()),
                        LunarEldritch.volumeAt(pos.getX(), spawner.count().maxInclusive(), groups.groupFactor()))),
                        entry.weight()));
            }
        }
    }

    /**
     * Scales the cluster a spawn pass stops at once a monstrosity spawns on the dark side.
     *
     * @param event the cluster size read for the mob just spawned
     */
    @SubscribeEvent
    public static void onSpawnClusterSize(SpawnClusterSizeEvent event) {
        if (event.getEntity() instanceof EldritchMonstrosity monstrosity
                && monstrosity.level() instanceof ServerLevel level) {
            LunarEldritch.DarkSideGroups groups = darkSideGroupsAt(level, monstrosity.blockPosition());
            if (groups != null) {
                event.setSize(LunarEldritch.volumeAt(monstrosity.getX(), event.getSize(), groups.groupFactor()));
            }
        }
    }

    private static LunarEldritch.@Nullable DarkSideGroups darkSideGroupsAt(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Lunar.LEVEL || !Lunar.isDarkSide(pos.getX())) {
            return null;
        }
        return level.getBiome(pos).getData(GooDataMaps.DARK_SIDE_ELDRITCH);
    }
}
