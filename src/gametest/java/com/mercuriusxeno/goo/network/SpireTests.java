package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Gametest for rock's Spire, submitted the way the second right click
 * submits it: a 3x1 footprint at rise 3 over ground of dirt, stone and
 * deepslate grows a 3x1x3 wall of stone above it, leaves every ground block
 * as it stood, and spends stone's rock goo value per stone block grown.
 * decision spire-grows-stone-out-of-the-top
 */
@RegistersGameTests
public final class SpireTests {
    private static final String SPIRE_GROWS_STONE = "spire_grows_stone_above_the_footprint";

    private static final String ROCK_SPIRE = "goo:rock_spire";
    /** The footprint's ground row, three cells along X at the bay's middle depth, one above the floor. */
    private static final BlockPos CORNER = new BlockPos(1, 1, 2);
    private static final BlockPos OPPOSITE = new BlockPos(3, 1, 2);
    private static final int RISE = 3;
    /** The footprint's ground cells along X, each a different block. */
    private static final List<Block> GROUND = List.of(Blocks.DIRT, Blocks.STONE, Blocks.DEEPSLATE);
    /** How deep under the footprint the test checks the ground stands unchanged. */
    private static final int GROUND_DEPTH_CHECKED = 2;
    private static final BlockPos CASTER_POS = new BlockPos(0, 1, 5);
    private static final int CASTER_GOO = 100 * GooStacks.THOUSAND;
    private static final String ABILITY_REQUIRED = "goo:rock_spire must be loaded";
    private static final String STONE_MISSING = "The cell at %s should hold %s, holds %s";
    private static final String GROUND_MOVED = "The ground at %s should still hold %s, holds %s";
    private static final String UNPRICED = "Stone should carry a rock goo value for Spire to spend";
    private static final String CHARGED_WRONG = "The growth should spend %d mB of rock goo, spent %d";

    private SpireTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SPIRE_GROWS_STONE, SpireTests::spireGrowsStoneAboveTheFootprint);
    }

    /**
     * A submitted 3x1 footprint at rise 3 over mixed ground grows stone in the
     * three cells above each footprint cell and leaves the ground as it stood.
     *
     * @param helper the gametest helper
     */
    public static void spireGrowsStoneAboveTheFootprint(GameTestHelper helper) {
        for (int x = CORNER.getX(); x <= OPPOSITE.getX(); x++) {
            helper.setBlock(new BlockPos(x, CORNER.getY(), CORNER.getZ()), GROUND.get(x - CORNER.getX()));
        }
        Map<BlockPos, BlockState> groundBefore = new HashMap<>();
        for (int x = CORNER.getX(); x <= OPPOSITE.getX(); x++) {
            for (int depth = 0; depth < GROUND_DEPTH_CHECKED; depth++) {
                BlockPos ground = new BlockPos(x, CORNER.getY() - depth, CORNER.getZ());
                groundBefore.put(ground, helper.getBlockState(ground));
            }
        }
        int spent = submit(helper);
        for (int x = CORNER.getX(); x <= OPPOSITE.getX(); x++) {
            for (int height = 1; height <= RISE; height++) {
                assertHolds(helper, new BlockPos(x, CORNER.getY() + height, CORNER.getZ()),
                        Blocks.STONE.defaultBlockState(), STONE_MISSING);
            }
        }
        groundBefore.forEach((ground, before) -> assertHolds(helper, ground, before, GROUND_MOVED));
        int stoneValue = stoneRockValue(helper);
        helper.assertTrue(stoneValue > 0, UNPRICED);
        int expected = stoneValue * GROUND.size() * RISE;
        helper.assertTrue(spent == expected, String.format(CHARGED_WRONG, expected, spent));
        helper.succeed();
    }

    /**
     * Submits the footprint as a caster holding a glove, the reagents and rock goo.
     *
     * @param helper the gametest helper
     * @return the mB of rock goo the growth spent
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static int submit(GameTestHelper helper) {
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(CASTER_POS));
        caster.setPos(stand.x, stand.y, stand.z);
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.ROCK, CASTER_GOO));
        KnownRecipes.teachRequires(caster, spire(helper));
        GooSpireHandler.cast(caster, new GooSpirePayload(GooTypes.id(GooTypes.ROCK), ROCK_SPIRE,
                helper.absolutePos(CORNER), helper.absolutePos(OPPOSITE), RISE));
        int spent = CASTER_GOO - GooSourceScanner.aggregateAvailable(caster).getOrDefault(GooTypes.ROCK, 0);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        return spent;
    }

    private static void assertHolds(GameTestHelper helper, BlockPos pos, BlockState expected, String message) {
        BlockState actual = helper.getBlockState(pos);
        helper.assertTrue(actual.equals(expected), String.format(message, pos, expected, actual));
    }

    private static int stoneRockValue(GameTestHelper helper) {
        GooValue value = GooValues.of(helper.getLevel()).lookup(BuiltInRegistries.ITEM.getKey(Blocks.STONE.asItem()));
        return value == null ? 0 : value.fabricateCeiling(GooTypes.ROCK);
    }

    private static AbilityDefinition spire(GameTestHelper helper) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(ROCK_SPIRE));
        helper.assertTrue(ability != null, ABILITY_REQUIRED);
        return ability;
    }
}
