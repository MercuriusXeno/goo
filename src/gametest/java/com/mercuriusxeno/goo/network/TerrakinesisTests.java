package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mercuriusxeno.goo.ability.program.TerrakinesisStep;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.entity.BlockHunk;
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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Gametests for rock's Terrakinesis, driven through the channel's stream
 * tick and the throw a left click sends: holding it on a slab of stone lifts
 * the struck block and its stone neighbors out of the ground as one hunk,
 * charging their rock goo value, and a throw hucks the hunk into a
 * cobblestone wall, which it crushes before it breaks apart into its own
 * stone as items. The tear names a sound the registry holds.
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 * decision terrakinesis-tears-free-with-a-stone-tear-and-seam-dust
 */
@RegistersGameTests
public final class TerrakinesisTests {
    private static final String LIFTS_A_HUNK = "terrakinesis_lifts_a_hunk_of_stone";
    private static final String HUCKS_THROUGH_A_WALL = "terrakinesis_hucks_a_hunk_through_a_wall";
    private static final String RELEASE_CANCELS = "terrakinesis_release_before_the_tear_lifts_nothing";

    private static final Identifier ROCK_TERRAKINESIS = Identifier.parse("goo:rock_terrakinesis");
    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** The slab of stone: one block deep along x, two high and three wide, the look striking its middle top. */
    private static final int SLAB_X = 2;
    private static final int SLAB_LOW_Y = 1;
    private static final int SLAB_HIGH_Y = 2;
    private static final int SLAB_LOW_Z = 1;
    private static final int SLAB_HIGH_Z = 3;
    /** The cobblestone wall the hunk is hucked into, across the bay's far side. */
    private static final int WALL_X = 5;
    private static final int WALL_LOW_Y = 1;
    private static final int WALL_HIGH_Y = 3;
    private static final BlockPos WALL_MIDDLE = new BlockPos(WALL_X, 2, 2);
    /** Yaw looking east, along +x. */
    private static final float FACING_EAST = -90f;
    private static final float LEVEL = 0f;
    /** Through the wind-up, then a few ticks more for the hunk to reach the hold. */
    private static final int HOLD_TICKS = TerrakinesisStep.WINDUP_TICKS + 4;
    /** Half the wind-up, then let go. */
    private static final int SHORT_HOLD_TICKS = TerrakinesisStep.WINDUP_TICKS / 2;
    /** Ticks after letting go past any hold's grace, by which a lift would have stood. */
    private static final int AFTER_RELEASE_TICKS = TerrakinesisStep.WINDUP_TICKS + 4;
    private static final int FLIGHT_TICKS = 10;
    private static final int CASTER_GOO = 100 * GooStacks.THOUSAND;
    private static final double REACH = 6;
    /** How far past the bay the dropped items may scatter and still be counted. */
    private static final double SCATTER = 3;
    private static final String ABILITY_REQUIRED = "goo:rock_terrakinesis must be loaded";
    private static final String CELL_STANDS = "The lifted cell at %s should stand empty, holds %s";
    private static final String NO_HUNK = "One hunk should carry the %d stone blocks, found %s";
    private static final String CHARGED_WRONG = "The lift should spend %d mB of rock goo, spent %d";
    private static final String WALL_STANDS = "The wall at %s should be crushed, holds %s";
    private static final String HUNK_STANDS = "The hunk should break apart on impact";
    private static final String DROPS_WRONG = "The hunk should drop its %d stone as items, dropped %d";
    private static final String TORE_EARLY = "No hunk should stand before the wind-up completes, found %d";
    private static final String CELL_LIFTED = "The cell at %s should still hold stone after an early release, holds %s";
    private static final String SPENT_ON_RELEASE = "An early release should spend no rock goo, spent %d";

    private TerrakinesisTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, LIFTS_A_HUNK, TerrakinesisTests::liftsAHunkOfStone);
        GooTestFunctions.registerTest(registrar, HUCKS_THROUGH_A_WALL, TerrakinesisTests::hucksAHunkThroughAWall);
        GooTestFunctions.registerTest(registrar, RELEASE_CANCELS, TerrakinesisTests::releaseBeforeTheTearLiftsNothing);
    }

    /**
     * Holding Terrakinesis on the slab through its wind-up tears no hunk a
     * tick before the wind-up completes, then empties the slab's cells into
     * one hunk of stone and spends the slab's rock goo value.
     *
     * @param helper the gametest helper
     */
    public static void liftsAHunkOfStone(GameTestHelper helper) {
        List<BlockPos> slab = placeSlab(helper);
        ServerPlayer player = caster(helper);
        // Each hold tick and its check share one callback: same-tick callbacks run in no set order.
        for (int held = 1; held <= HOLD_TICKS; held++) {
            int holdTick = held;
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(player, holdTick(player));
                if (holdTick == TerrakinesisStep.WINDUP_TICKS - 1) {
                    helper.assertTrue(hunksIn(helper).isEmpty(), String.format(TORE_EARLY, hunksIn(helper).size()));
                } else if (holdTick == TerrakinesisStep.WINDUP_TICKS) {
                    helper.assertTrue(hunksIn(helper).size() == 1, "One hunk should tear free on the tick the"
                            + " wind-up completes, found " + hunksIn(helper).size());
                }
            });
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            for (BlockPos cell : slab) {
                helper.assertTrue(helper.getBlockState(cell).isAir(),
                        String.format(CELL_STANDS, cell, helper.getBlockState(cell)));
            }
            List<BlockHunk> hunks = hunksIn(helper);
            helper.assertTrue(hunks.size() == 1 && hunks.getFirst().blocks().size() == slab.size()
                    && hunks.getFirst().blocks().stream().allMatch(block -> block.state().is(Blocks.STONE)),
                    String.format(NO_HUNK, slab.size(), hunks.stream().map(hunk -> hunk.blocks().size()).toList()));
            helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(TerrakinesisStep.TEAR.sound()),
                    "The tear's sound " + TerrakinesisStep.TEAR.sound() + " should stand in the sound registry");
            int expected = stoneRockValue(helper).exact(GooTypes.ROCK).times(slab.size()).ceiling();
            int spent = CASTER_GOO - GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.ROCK, 0);
            helper.assertTrue(spent == expected, String.format(CHARGED_WRONG, expected, spent));
            leave(helper, player);
        });
    }

    /**
     * A hunk hucked along the look into a cobblestone wall crushes the wall
     * where it strikes, breaks apart and drops every stone block it carried.
     *
     * @param helper the gametest helper
     */
    public static void hucksAHunkThroughAWall(GameTestHelper helper) {
        List<BlockPos> slab = placeSlab(helper);
        for (int y = WALL_LOW_Y; y <= WALL_HIGH_Y; y++) {
            for (int z = SLAB_LOW_Z; z <= SLAB_HIGH_Z; z++) {
                helper.setBlock(new BlockPos(WALL_X, y, z), Blocks.COBBLESTONE);
            }
        }
        ServerPlayer player = caster(helper);
        holdFor(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> helper.assertTrue(GrabEvents.throwHeld(player),
                "The player should hold the hunk to huck it"));
        helper.runAfterDelay(HOLD_TICKS + 1 + FLIGHT_TICKS, () -> {
            helper.assertTrue(helper.getBlockState(WALL_MIDDLE).isAir(),
                    String.format(WALL_STANDS, WALL_MIDDLE, helper.getBlockState(WALL_MIDDLE)));
            helper.assertTrue(hunksIn(helper).isEmpty(), HUNK_STANDS);
            int dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, bay(helper)).stream()
                    .filter(item -> item.getItem().is(Items.STONE)).mapToInt(item -> item.getItem().getCount()).sum();
            helper.assertTrue(dropped == slab.size(), String.format(DROPS_WRONG, slab.size(), dropped));
            leave(helper, player);
        });
    }

    /**
     * Letting go of Terrakinesis halfway through its wind-up leaves the slab
     * standing, raises no hunk and spends no rock goo.
     *
     * @param helper the gametest helper
     */
    public static void releaseBeforeTheTearLiftsNothing(GameTestHelper helper) {
        List<BlockPos> slab = placeSlab(helper);
        ServerPlayer player = caster(helper);
        holdFor(helper, player, SHORT_HOLD_TICKS);
        helper.runAfterDelay(SHORT_HOLD_TICKS + AFTER_RELEASE_TICKS, () -> {
            for (BlockPos cell : slab) {
                helper.assertTrue(helper.getBlockState(cell).is(Blocks.STONE),
                        String.format(CELL_LIFTED, cell, helper.getBlockState(cell)));
            }
            helper.assertTrue(hunksIn(helper).isEmpty(), String.format(TORE_EARLY, hunksIn(helper).size()));
            int spent = CASTER_GOO - GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.ROCK, 0);
            helper.assertTrue(spent == 0, String.format(SPENT_ON_RELEASE, spent));
            leave(helper, player);
        });
    }

    private static List<BlockPos> placeSlab(GameTestHelper helper) {
        List<BlockPos> slab = new ArrayList<>();
        for (int y = SLAB_LOW_Y; y <= SLAB_HIGH_Y; y++) {
            for (int z = SLAB_LOW_Z; z <= SLAB_HIGH_Z; z++) {
                BlockPos cell = new BlockPos(SLAB_X, y, z);
                helper.setBlock(cell, Blocks.STONE);
                slab.add(cell);
            }
        }
        return slab;
    }

    private static void holdFor(GameTestHelper helper, ServerPlayer player, int ticks) {
        for (int held = 1; held <= ticks; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, holdTick(player)));
        }
    }

    private static GooStreamPayload holdTick(ServerPlayer player) {
        Vec3 eye = player.getEyePosition();
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.ROCK), ROCK_TERRAKINESIS.toString(),
                eye, eye.add(player.getLookAngle().scale(REACH)));
    }

    private static AABB bay(GameTestHelper helper) {
        return new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(WALL_X, WALL_HIGH_Y, SLAB_HIGH_Z)
                .inflate(SCATTER);
    }

    private static List<BlockHunk> hunksIn(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(BlockHunk.class, bay(helper));
    }

    private static GooValue stoneRockValue(GameTestHelper helper) {
        GooValue value = GooValues.of(helper.getLevel()).lookup(BuiltInRegistries.ITEM.getKey(Items.STONE));
        helper.assertTrue(value != null, "Stone should carry a rock goo value");
        return value;
    }

    private static void leave(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer caster(GameTestHelper helper) {
        AbilityDefinition terrakinesis = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_TERRAKINESIS);
        helper.assertTrue(terrakinesis != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setYHeadRot(FACING_EAST);
        player.setXRot(LEVEL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.ROCK, CASTER_GOO));
        KnownRecipes.teachRequires(player, terrakinesis);
        return player;
    }
}
