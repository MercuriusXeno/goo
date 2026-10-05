package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.EffectBlockPlacement;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlock;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import java.util.Map;

/**
 * Gametests for EffectBlockPlacement on the ability path: an ability goo
 * lands through ChainPlacementRules and stands or stacks its chain marker.
 */
public final class PlacementTests {

    private static final BlockPos WALL_POS = new BlockPos(1, 1, 1);
    private static final BlockPos AIR_POS = new BlockPos(1, 1, 2);
    /** The soil under the tall grass an ability goo strikes. */
    private static final BlockPos GRASS_SOIL_POS = new BlockPos(3, 1, 3);
    /** The grass an ability goo strikes from above. */
    private static final BlockPos GRASS_POS = GRASS_SOIL_POS.above();
    private static final String CRYSTAL_CLOUD = "goo:crystal_cloud";
    private static final String METAL_SPIKES = "goo:metal_spikes";
    private static final String NETHER_BLACK_HOLE = "goo:nether_black_hole";
    private static final String UNSTABLE_PROXIMITY_MINE = "goo:unstable_proximity_mine";
    private static final String ABILITIES_REQUIRED = "Ability registry must be loaded";
    private static final String OTHER_ABILITY_STACKED = "A goo of another ability stacked onto the marker";
    private static final String OTHER_ABILITY_REPLACED = "A goo of another ability replaced the marker";
    /** The stack count after a second goo of the same ability. */
    private static final int TWO_STACKS = 2;
    private static final String SAME_ABILITY_NOT_STACKED = "A second goo of the same ability did not stack";

    private PlacementTests() {}

    /**
     * Hitting the same position twice with crystal_cloud stacks the existing marker
     * instead of placing a second one. Exercises the STACK decision path.
     *
     * @param helper the gametest helper
     */
    public static void doubleHitStacks(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        throwAbility(helper, WALL_POS, Direction.SOUTH, GooTypes.CRYSTAL, CRYSTAL_CLOUD);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        throwAbility(helper, WALL_POS, Direction.SOUTH, GooTypes.CRYSTAL, CRYSTAL_CLOUD);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        helper.succeed();
    }

    /**
     * A sideways-placed marker must survive a neighbor change while its
     * program runs. The pre-fix bug computed the support direction
     * as {@code placedFace} instead of {@code placedFace.getOpposite()},
     * so any neighbor update on a side-attached marker triggered a
     * false-positive "no support" detection that scheduled a fall and
     * removed the marker before its effect fired.
     *
     * @param helper the gametest helper
     */
    public static void sidewaysMarkerSurvivesNeighborChange(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        throwAbility(helper, WALL_POS, Direction.SOUTH, GooTypes.METAL, METAL_SPIKES);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        BlockPos neighbor = AIR_POS.south();
        helper.setBlock(neighbor, Blocks.STONE);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        helper.succeed();
    }

    /**
     * Throws one ability goo at a block face through the ability placement
     * path.
     *
     * @param helper    the gametest helper
     * @param hit       the struck block, relative
     * @param face      the struck face
     * @param type      the goo type
     * @param abilityId the ability the goo names
     */
    private static void throwAbility(GameTestHelper helper, BlockPos hit, Direction face,
                                     ResourceKey<GooTypeDefinition> type, String abilityId) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(abilityId));
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        EffectBlockPlacement.placeOrStackAbility(helper.getLevel(), helper.absolutePos(hit), type, face, ability);
    }

    /**
     * An ability goo striking tall grass from above places its marker in
     * the grass block's place, not on top of it.
     *
     * @param helper the gametest helper
     */
    public static void abilityTakesReplaceableHitBlock(GameTestHelper helper) {
        helper.setBlock(GRASS_SOIL_POS, Blocks.GRASS_BLOCK);
        helper.setBlock(GRASS_POS, Blocks.SHORT_GRASS);
        throwAbility(helper, GRASS_POS, Direction.UP, GooTypes.CRYSTAL, CRYSTAL_CLOUD);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), GRASS_POS);
        helper.assertBlockPresent(Blocks.AIR, GRASS_POS.above());
        helper.succeed();
    }

    /**
     * An ability goo striking stone behind a water source places its
     * marker waterlogged in the water.
     *
     * @param helper the gametest helper
     */
    public static void abilityWaterlogsInWater(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        helper.setBlock(AIR_POS, Blocks.WATER);
        throwAbility(helper, WALL_POS, Direction.SOUTH, GooTypes.CRYSTAL, CRYSTAL_CLOUD);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        helper.assertBlockProperty(AIR_POS, ChainMarkerBlock.WATERLOGGED, true);
        helper.succeed();
    }

    /**
     * An ability goo striking stone behind lava places no marker.
     *
     * @param helper the gametest helper
     */
    public static void abilityRefusesLava(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        helper.setBlock(AIR_POS, Blocks.LAVA);
        throwAbility(helper, WALL_POS, Direction.SOUTH, GooTypes.CRYSTAL, CRYSTAL_CLOUD);
        helper.assertBlockPresent(Blocks.LAVA, AIR_POS);
        helper.succeed();
    }

    /**
     * A second goo of the same ability stacks onto the first marker; a
     * goo of another ability on the same face leaves it as it stood.
     *
     * @param helper the gametest helper
     */
    public static void abilityStacksOnlyOntoSameAbility(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        throwAbility(helper, WALL_POS, Direction.SOUTH, GooTypes.METAL, METAL_SPIKES);
        throwAbility(helper, WALL_POS, Direction.SOUTH, GooTypes.CRYSTAL, CRYSTAL_CLOUD);
        ChainMarkerBlockEntity marker = helper.getBlockEntity(AIR_POS, ChainMarkerBlockEntity.class);
        helper.assertTrue(marker.getStackCount() == 1, OTHER_ABILITY_STACKED);
        helper.assertTrue(METAL_SPIKES.equals(marker.getAbilityId()), OTHER_ABILITY_REPLACED);
        throwAbility(helper, WALL_POS, Direction.SOUTH, GooTypes.METAL, METAL_SPIKES);
        helper.assertTrue(marker.getStackCount() == TWO_STACKS, SAME_ABILITY_NOT_STACKED);
        helper.succeed();
    }

    /**
     * The crystal, metal, nether and unstable abilities that stand while
     * their programs run all place their markers through the same path.
     *
     * @param helper the gametest helper
     */
    public static void otherTypesPlaceMarker(GameTestHelper helper) {
        Map<ResourceKey<GooTypeDefinition>, String> abilities = Map.of(
            GooTypes.CRYSTAL, CRYSTAL_CLOUD, GooTypes.METAL, METAL_SPIKES,
            GooTypes.NETHER, NETHER_BLACK_HOLE, GooTypes.UNSTABLE, UNSTABLE_PROXIMITY_MINE);
        abilities.forEach((type, abilityId) -> {
            helper.setBlock(WALL_POS, Blocks.STONE);
            helper.setBlock(AIR_POS, Blocks.AIR);
            throwAbility(helper, WALL_POS, Direction.SOUTH, type, abilityId);
            helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        });
        helper.succeed();
    }
}
