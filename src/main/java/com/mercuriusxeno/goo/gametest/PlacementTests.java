package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.world.EffectBlockPlacement;
import com.mercuriusxeno.goo.ability.world.WorldEffects;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlock;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Blocks;
import java.util.List;

/**
 * Gametests for EffectBlockPlacement via the WorldEffects dispatch.
 * Covers the blob-impact placement path: WorldEffects -> *Effect ->
 * EffectBlockPlacement -> ChainPlacementRules -> chain marker creation.
 */
public final class PlacementTests {

    private static final BlockPos WALL_POS = new BlockPos(1, 1, 1);
    private static final BlockPos AIR_POS = new BlockPos(1, 1, 2);
    /** The soil under the tall grass an ability blob strikes. */
    private static final BlockPos GRASS_SOIL_POS = new BlockPos(3, 1, 3);
    /** The grass an ability blob strikes from above. */
    private static final BlockPos GRASS_POS = GRASS_SOIL_POS.above();
    private static final String BLAZE_TUNNEL = "goo:blaze_tunnel";
    private static final String ROCK_TUNNEL = "goo:rock_tunnel";
    private static final String ABILITIES_REQUIRED = "Ability registry must be loaded";
    private static final String OTHER_ABILITY_STACKED = "A blob of another ability stacked onto the marker";
    private static final String OTHER_ABILITY_REPLACED = "A blob of another ability replaced the marker";
    /** The stack count after a second blob of the same ability. */
    private static final int TWO_STACKS = 2;
    private static final String SAME_ABILITY_NOT_STACKED = "A second blob of the same ability did not stack";

    private PlacementTests() {}

    /**
     * Hitting a stone block with blaze goo places a chain marker in the
     * adjacent air block. Exercises the full placement dispatch path.
     *
     * @param helper the gametest helper
     */
    public static void blazePlacesMarker(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        WorldEffects.apply(helper.getLevel(), helper.absolutePos(WALL_POS),
            GooTypes.BLAZE, Direction.SOUTH);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        helper.succeed();
    }

    /**
     * Hitting a stone block with rock goo places a chain marker.
     *
     * @param helper the gametest helper
     */
    public static void rockPlacesMarker(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        WorldEffects.apply(helper.getLevel(), helper.absolutePos(WALL_POS),
            GooTypes.ROCK, Direction.SOUTH);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        helper.succeed();
    }

    /**
     * Hitting a stone block with frost goo places a chain marker.
     *
     * @param helper the gametest helper
     */
    public static void frostPlacesMarker(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        WorldEffects.apply(helper.getLevel(), helper.absolutePos(WALL_POS),
            GooTypes.FROST, Direction.SOUTH);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        helper.succeed();
    }

    /**
     * Hitting the same position twice with blaze stacks the existing marker
     * instead of placing a second one. Exercises the STACK decision path.
     *
     * @param helper the gametest helper
     */
    public static void doubleHitStacks(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        BlockPos abs = helper.absolutePos(WALL_POS);
        WorldEffects.apply(helper.getLevel(), abs, GooTypes.BLAZE, Direction.SOUTH);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        WorldEffects.apply(helper.getLevel(), abs, GooTypes.BLAZE, Direction.SOUTH);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        helper.succeed();
    }

    /**
     * A sideways-placed marker must survive a neighbor change during
     * its fuse phase. The pre-fix bug computed the support direction
     * as {@code placedFace} instead of {@code placedFace.getOpposite()},
     * so any neighbor update on a side-attached marker triggered a
     * false-positive "no support" detection that scheduled a fall and
     * removed the marker before its effect fired.
     *
     * @param helper the gametest helper
     */
    public static void sidewaysMarkerSurvivesNeighborChange(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        WorldEffects.apply(helper.getLevel(), helper.absolutePos(WALL_POS),
            GooTypes.ROCK, Direction.SOUTH);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        BlockPos neighbor = AIR_POS.south();
        helper.setBlock(neighbor, Blocks.STONE);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        helper.succeed();
    }

    /**
     * Throws one ability blob at a block face through the ability placement
     * path.
     *
     * @param helper    the gametest helper
     * @param hit       the struck block, relative
     * @param face      the struck face
     * @param type      the goo type
     * @param abilityId the ability the blob names
     */
    private static void throwAbility(GameTestHelper helper, BlockPos hit, Direction face,
                                     ResourceKey<GooTypeDefinition> type, String abilityId) {
        AbilityDefinition ability = AbilityRegistry.getAbility(Identifier.parse(abilityId));
        helper.assertTrue(ability != null, ABILITIES_REQUIRED);
        EffectBlockPlacement.placeOrStackAbility(helper.getLevel(), helper.absolutePos(hit), type, face, ability);
    }

    /**
     * An ability blob striking tall grass from above places its marker in
     * the grass block's place, not on top of it.
     *
     * @param helper the gametest helper
     */
    public static void abilityTakesReplaceableHitBlock(GameTestHelper helper) {
        helper.setBlock(GRASS_SOIL_POS, Blocks.GRASS_BLOCK);
        helper.setBlock(GRASS_POS, Blocks.SHORT_GRASS);
        throwAbility(helper, GRASS_POS, Direction.UP, GooTypes.BLAZE, BLAZE_TUNNEL);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), GRASS_POS);
        helper.assertBlockPresent(Blocks.AIR, GRASS_POS.above());
        helper.succeed();
    }

    /**
     * An ability blob striking stone behind a water source places its
     * marker waterlogged in the water.
     *
     * @param helper the gametest helper
     */
    public static void abilityWaterlogsInWater(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        helper.setBlock(AIR_POS, Blocks.WATER);
        throwAbility(helper, WALL_POS, Direction.SOUTH, GooTypes.BLAZE, BLAZE_TUNNEL);
        helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        helper.assertBlockProperty(AIR_POS, ChainMarkerBlock.WATERLOGGED, true);
        helper.succeed();
    }

    /**
     * An ability blob striking stone behind lava places no marker.
     *
     * @param helper the gametest helper
     */
    public static void abilityRefusesLava(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        helper.setBlock(AIR_POS, Blocks.LAVA);
        throwAbility(helper, WALL_POS, Direction.SOUTH, GooTypes.BLAZE, BLAZE_TUNNEL);
        helper.assertBlockPresent(Blocks.LAVA, AIR_POS);
        helper.succeed();
    }

    /**
     * A second blob of the same ability stacks onto the first marker; a
     * blob of another ability on the same face leaves it as it stood.
     *
     * @param helper the gametest helper
     */
    public static void abilityStacksOnlyOntoSameAbility(GameTestHelper helper) {
        helper.setBlock(WALL_POS, Blocks.STONE);
        throwAbility(helper, WALL_POS, Direction.SOUTH, GooTypes.ROCK, ROCK_TUNNEL);
        throwAbility(helper, WALL_POS, Direction.SOUTH, GooTypes.BLAZE, BLAZE_TUNNEL);
        ChainMarkerBlockEntity marker = helper.getBlockEntity(AIR_POS, ChainMarkerBlockEntity.class);
        helper.assertTrue(marker.getStackCount() == 1, OTHER_ABILITY_STACKED);
        helper.assertTrue(ROCK_TUNNEL.equals(marker.getAbilityId()), OTHER_ABILITY_REPLACED);
        throwAbility(helper, WALL_POS, Direction.SOUTH, GooTypes.ROCK, ROCK_TUNNEL);
        helper.assertTrue(marker.getStackCount() == TWO_STACKS, SAME_ABILITY_NOT_STACKED);
        helper.succeed();
    }

    /**
     * Crystal, metal, nether, unstable, glow all place markers via the
     * same path. A single combined test verifies they all succeed.
     *
     * @param helper the gametest helper
     */
    public static void otherTypesPlaceMarker(GameTestHelper helper) {
        List<ResourceKey<GooTypeDefinition>> types = List.of(
            GooTypes.CRYSTAL, GooTypes.METAL, GooTypes.NETHER,
            GooTypes.UNSTABLE, GooTypes.GLOW);
        for (ResourceKey<GooTypeDefinition> type : types) {
            helper.setBlock(WALL_POS, Blocks.STONE);
            helper.setBlock(AIR_POS, Blocks.AIR);
            WorldEffects.apply(helper.getLevel(), helper.absolutePos(WALL_POS),
                type, Direction.SOUTH);
            helper.assertBlockPresent(GooBlocks.CHAIN_MARKER.get(), AIR_POS);
        }
        helper.succeed();
    }
}
