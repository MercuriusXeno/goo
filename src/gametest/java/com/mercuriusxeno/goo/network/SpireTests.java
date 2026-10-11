package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.SpireLift;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
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
import java.util.List;

/**
 * Gametest for rock's Spire, submitted the way the second right click
 * submits it: a 3x1 footprint at rise 4 stands a 3x1x4 wall of the ground's
 * own blocks in their order, refills the column it left with the mundane
 * block of each cell's depth, and spends the rock goo value of that refill.
 * decision spire-rips-walls-and-platforms
 */
public final class SpireTests {

    private static final String ROCK_SPIRE = "goo:rock_spire";
    /**
     * The footprint's ground row, three cells along X at the bay's middle
     * depth, one above the floor so the risen wall tops out inside the
     * six-block bay; the column it lifts reaches under the floor.
     */
    private static final BlockPos CORNER = new BlockPos(1, 1, 2);
    private static final BlockPos OPPOSITE = new BlockPos(3, 1, 2);
    private static final int RISE = 4;
    /** The ground column under each footprint cell, top first, which the wall stands in this order. */
    private static final List<Block> GROUND = List.of(Blocks.DIRT, Blocks.COBBLESTONE, Blocks.ANDESITE, Blocks.STONE);
    private static final BlockPos CASTER_POS = new BlockPos(0, 1, 5);
    private static final int CASTER_GOO = 100 * GooStacks.THOUSAND;
    private static final String ABILITY_REQUIRED = "goo:rock_spire must be loaded";
    private static final String WALL_WRONG = "The wall at %s should hold %s, holds %s";
    private static final String REFILL_WRONG = "The lifted ground at %s should refill with %s, holds %s";
    private static final String UNPRICED = "Stone and deepslate should carry a rock goo value for Spire to spend";
    private static final String CHARGED_WRONG = "The lift should spend %d mB of rock goo, spent %d";

    private SpireTests() {
    }

    /**
     * A submitted 3x1 footprint at rise 4 stands a 3x1x4 wall of the ground's
     * own blocks, refills the gap under it and spends the refill's goo value.
     *
     * @param helper the gametest helper
     */
    public static void spireStandsAWall(GameTestHelper helper) {
        for (int x = CORNER.getX(); x <= OPPOSITE.getX(); x++) {
            for (int depth = 0; depth < GROUND.size(); depth++) {
                helper.setBlock(new BlockPos(x, CORNER.getY() - depth, CORNER.getZ()), GROUND.get(depth));
            }
        }
        int spent = submit(helper);
        int expected = 0;
        for (int x = CORNER.getX(); x <= OPPOSITE.getX(); x++) {
            for (int depth = 0; depth < GROUND.size(); depth++) {
                BlockPos risen = new BlockPos(x, CORNER.getY() + RISE - depth, CORNER.getZ());
                assertHolds(helper, risen, GROUND.get(depth).defaultBlockState(), WALL_WRONG);
                BlockPos refilled = new BlockPos(x, CORNER.getY() - depth, CORNER.getZ());
                BlockState refill = SpireLift.refillAt(helper.absolutePos(refilled).getY());
                assertHolds(helper, refilled, refill, REFILL_WRONG);
                expected += rockValue(helper, refill);
            }
        }
        helper.assertTrue(expected > 0, UNPRICED);
        helper.assertTrue(spent == expected, String.format(CHARGED_WRONG, expected, spent));
        helper.succeed();
    }

    /**
     * Submits the footprint as a caster holding a glove and rock goo.
     *
     * @param helper the gametest helper
     * @return the mB of rock goo the lift spent
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
        helper.assertTrue(actual.is(expected.getBlock()), String.format(message, pos, expected, actual));
    }

    private static int rockValue(GameTestHelper helper, BlockState refill) {
        GooValue value = GooValues.of(helper.getLevel())
                .lookup(BuiltInRegistries.ITEM.getKey(refill.getBlock().asItem()));
        return value == null ? 0 : value.get(GooTypes.ROCK);
    }

    private static AbilityDefinition spire(GameTestHelper helper) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(ROCK_SPIRE));
        helper.assertTrue(ability != null, ABILITY_REQUIRED);
        return ability;
    }
}
