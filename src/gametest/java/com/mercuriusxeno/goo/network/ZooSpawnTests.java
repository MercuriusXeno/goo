package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Gametests for Zoo's Spawn and Shape: a blob on a slime consumes it and
 * a mob of the biome, hostile or peaceful, stands where it stood
 * (decision spawn-hostile-shape-peaceful-from-a-slime).
 */
public final class ZooSpawnTests {

    private static final BlockPos SLIME_POS = new BlockPos(2, 1, 2);
    private static final Identifier ZOO_SPAWN = Identifier.fromNamespaceAndPath(Goo.MODID, "zoo_spawn");
    private static final Identifier ZOO_SHAPE = Identifier.fromNamespaceAndPath(Goo.MODID, "zoo_shape");
    /** How far from the slime's cell the born mob is looked for. */
    private static final double SEARCH_REACH = 1.5;

    private static final String ABILITY_REQUIRED = "Ability registry must hold %s";
    private static final String SLIME_REMAINS = "The struck slime should be consumed";
    private static final String NO_MOB_BORN = "A %s mob the biome spawns should stand where the slime stood, found %s";

    private ZooSpawnTests() {
    }

    /**
     * A Spawn blob on a slime consumes it and a monster the biome spawns
     * naturally stands in its place.
     *
     * @param helper the gametest helper
     */
    public static void spawnTurnsASlimeHostile(GameTestHelper helper) {
        transmutes(helper, ZOO_SPAWN, category -> category == MobCategory.MONSTER, "hostile");
    }

    /**
     * A Shape blob on a slime consumes it and a peaceful mob the biome
     * spawns naturally stands in its place.
     *
     * @param helper the gametest helper
     */
    public static void shapeTurnsASlimePeaceful(GameTestHelper helper) {
        transmutes(helper, ZOO_SHAPE, MobCategory::isFriendly, "peaceful");
    }

    private static void transmutes(GameTestHelper helper, Identifier abilityId, Predicate<MobCategory> categories,
                                   String temper) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(abilityId);
        helper.assertTrue(ability != null, String.format(ABILITY_REQUIRED, abilityId));
        helper.setBlock(SLIME_POS.below(), Blocks.STONE);
        Slime slime = helper.spawnWithNoFreeWill(EntityType.SLIME, SLIME_POS);
        slime.setSize(1, true);
        BlockPos cell = slime.blockPosition();
        Set<EntityType<?>> natural = naturalSpawnsAt(helper, cell, categories);

        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, ability.gooType(),
                slime.getId(), cell, Direction.UP, abilityId.toString()));

        helper.assertTrue(slime.isRemoved(), SLIME_REMAINS);
        List<LivingEntity> born = helper.getLevel().getEntitiesOfClass(LivingEntity.class,
                new AABB(cell).inflate(SEARCH_REACH), living -> !(living instanceof Player) && living != slime
                        && living.tickCount == 0);
        helper.assertTrue(born.stream().anyMatch(living -> natural.contains(living.getType())
                        && categories.test(living.getType().getCategory())),
                String.format(NO_MOB_BORN, temper, born));
        helper.succeed();
    }

    private static Set<EntityType<?>> naturalSpawnsAt(GameTestHelper helper, BlockPos cell,
                                                      Predicate<MobCategory> categories) {
        MobSpawnSettings settings = helper.getLevel().getBiome(cell).value().getMobSettings();
        return Arrays.stream(MobCategory.values()).filter(categories)
                .flatMap(category -> settings.getMobs(category).unwrap().stream())
                .map(weighted -> weighted.value().type())
                .collect(Collectors.toSet());
    }
}
