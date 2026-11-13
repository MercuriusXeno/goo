package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mercuriusxeno.goo.ability.program.TerrakinesisStep;
import com.mercuriusxeno.goo.entity.BlockHunk;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
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
 * charging rock goo for the tear, and a throw hucks the hunk into a
 * cobblestone wall, which it crushes before it breaks apart into what its
 * blocks mine, cobblestone from stone. The tear names a sound the registry holds.
 * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
 * decision terrakinesis-tears-free-with-a-stone-tear-and-seam-dust
 */
@RegistersGameTests
public final class TerrakinesisTests {
    private static final String LIFTS_A_HUNK = "terrakinesis_lifts_a_hunk_of_stone";
    private static final String HUCKS_THROUGH_A_WALL = "terrakinesis_hucks_a_hunk_through_a_wall";
    private static final String DROPS_WHAT_IT_MINES = "terrakinesis_hunk_drops_what_its_blocks_mine";
    private static final String WAITS_AFTER_A_THROW = "terrakinesis_waits_after_a_throw";
    private static final String RELEASE_PLACES = "terrakinesis_release_places_the_chunk_on_the_grid";
    private static final String STAYS_IN = "terrakinesis_held_looking_down_stays_in_its_slot_until_pulled_up";
    private static final String SLOTS_BACK = "terrakinesis_carried_away_and_aimed_back_slots_into_its_hole";
    private static final String WIDE_SLOTS_BACK = "terrakinesis_wide_chunk_aimed_back_down_slots_into_its_hole";
    private static final String WIDE_STAYS_IN = "terrakinesis_wide_chunk_stays_in_its_slot_until_pulled_up";
    private static final String LOOKED_DOWN_INTO = "terrakinesis_wide_chunk_looked_down_into_its_hole_slots_in";
    private static final String HELD_INTO = "terrakinesis_held_chunk_eases_into_its_slot_while_held";
    private static final String FROM_IN_THE_HOLE = "terrakinesis_held_chunk_slots_in_from_inside_its_hole";
    private static final String RAISED_THEN_PULLED = "terrakinesis_held_up_then_pulled_down_over_its_hole_seats";
    /** Looking up, holding the chunk up clear of its hole. */
    private static final float HOLDING_UP_PITCH = -45f;
    /** Ticks the holder spends pulling the chunk up out of its slot before carrying it. */
    private static final int PULL_UP_TICKS = 10;
    /** Aiming down, from inside the hole: near straight down, and straight down. */
    private static final float[] AIM_DOWN_PITCHES = {80f, 90f};
    /** How near its slot a held chunk counts as seated in it, in blocks. */
    private static final double IN_THE_SLOT = 0.01;
    /** Looking steeply down into the hole from its edge. */
    private static final float INTO_THE_HOLE_PITCH = 75f;
    /** The near patch's west edge: two by two, two deep, one block of planks from the caster. */
    private static final int NEAR_PATCH_X = 2;
    /** The far patch's east edge: three by three, two blocks deep in the floor, its middle four blocks off. */
    private static final int FAR_STRIP_X = 5;
    /** Where the caster carries the chunk aside to, on the planks floor beside its hole, within the hold's reach. */
    private static final int ASIDE_X = 1;
    private static final int ASIDE_Z = 4;
    /** Ticks spent carrying the chunk aside, then aiming it back. */
    private static final int CARRY_TICKS = 20;
    /** The floor's strip of stone the caster looks down at: three cells along z, set in planks. */
    private static final int FLOOR_STRIP_X = 2;
    /** The top of the planks floor, the rim of the strip's hole once it is lifted. */
    private static final int FLOOR_TOP = 1;
    /** Long enough to lift, clear the rim and settle on the look. */
    private static final int LOOK_DOWN_TICKS = TerrakinesisStep.WINDUP_TICKS + 34;
    /** How far below the rim the chunk's bottom may dip before it counts as slipping back in. */
    private static final double RIM_SLACK = 0.01;
    /** Long enough after letting go for the chunk to fall, land, place and finish lingering. */
    private static final int LANDING_TICKS = 40;

    private static final Identifier ROCK_TERRAKINESIS = Identifier.parse("goo:rock_terrakinesis");
    private static final BlockPos STAND_POS = new BlockPos(0, 1, 2);
    /** The slab of stone: one block deep along x, two high and three wide, the look striking its middle top. */
    private static final int SLAB_X = 2;
    private static final int SLAB_LOW_Y = 1;
    private static final int SLAB_HIGH_Y = 2;
    private static final int SLAB_LOW_Z = 1;
    private static final int SLAB_HIGH_Z = 3;
    /** The slab's middle bottom cell, below the struck block, and its top north cell, open to the air. */
    private static final BlockPos COAL_CELL = new BlockPos(SLAB_X, SLAB_LOW_Y, 2);
    private static final BlockPos GRASS_CELL = new BlockPos(SLAB_X, SLAB_HIGH_Y, SLAB_LOW_Z);
    /** The cobblestone wall the hunk is hucked into, across the bay's far side. */
    private static final int WALL_X = 5;
    private static final int WALL_LOW_Y = 1;
    private static final int WALL_HIGH_Y = 3;
    private static final BlockPos WALL_MIDDLE = new BlockPos(WALL_X, 2, 2);
    /** Yaw looking east, along +x. */
    private static final float FACING_EAST = -90f;
    private static final float LEVEL = 0f;
    /** Through the wind-up, then long enough for the hunk to rise out of its hole and reach the hold. */
    private static final int HOLD_TICKS = TerrakinesisStep.WINDUP_TICKS + 14;
    /** Every block of a hucked hunk has crumbled by then. */
    private static final int SHATTER_TICKS = BlockHunk.BLOCK_STAGGER_TICKS * 6 + BlockHunk.CRUMBLE_TICKS + 2;
    private static final int FLIGHT_TICKS = 10;
    private static final int CASTER_GOO = 100 * GooStacks.THOUSAND;
    private static final double REACH = 6;
    /** How far past the bay the dropped items may scatter and still be counted. */
    private static final double SCATTER = 3;
    private static final String ABILITY_REQUIRED = "goo:rock_terrakinesis must be loaded";
    private static final String CELL_STANDS = "The lifted cell at %s should stand empty, holds %s";
    private static final String NO_HUNK = "One hunk should carry the %d stone blocks, found %s";
    private static final String CHARGED_WRONG = "The lift should spend rock goo, spent %d mB";
    private static final String WALL_STANDS = "The wall at %s should be crushed, holds %s";
    private static final String HUNK_STANDS = "The hunk should break apart on impact";

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
        GooTestFunctions.registerTest(registrar, DROPS_WHAT_IT_MINES, TerrakinesisTests::hunkDropsWhatItsBlocksMine);
        GooTestFunctions.registerTest(registrar, WAITS_AFTER_A_THROW, TerrakinesisTests::waitsAfterAThrow);
        GooTestFunctions.registerTest(registrar, RELEASE_PLACES, TerrakinesisTests::releasePlacesTheChunkOnTheGrid);
        GooTestFunctions.registerTest(registrar, STAYS_IN, TerrakinesisTests::heldLookingDownStaysInItsSlotUntilPulledUp);
        GooTestFunctions.registerTest(registrar, SLOTS_BACK, TerrakinesisTests::carriedAwayAndAimedBackSlotsIntoItsHole);
        GooTestFunctions.registerTest(registrar, WIDE_SLOTS_BACK, TerrakinesisTests::wideChunkAimedBackDownSlotsIntoItsHole);
        GooTestFunctions.registerTest(registrar, WIDE_STAYS_IN, TerrakinesisTests::wideChunkStaysInItsSlotUntilPulledUp);
        GooTestFunctions.registerTest(registrar, LOOKED_DOWN_INTO, TerrakinesisTests::wideChunkLookedDownIntoItsHoleSlotsIn);
        GooTestFunctions.registerTest(registrar, HELD_INTO, TerrakinesisTests::heldChunkEasesIntoItsSlotWhileHeld);
        GooTestFunctions.registerTest(registrar, FROM_IN_THE_HOLE, TerrakinesisTests::heldChunkSlotsInFromInsideItsHole);
        GooTestFunctions.registerTest(registrar, RAISED_THEN_PULLED, TerrakinesisTests::heldUpThenPulledDownOverItsHoleSeats);
    }

    /**
     * Holding Terrakinesis on the slab through its wind-up empties the slab's
     * cells into one hunk of stone and spends rock goo.
     *
     * @param helper the gametest helper
     */
    public static void liftsAHunkOfStone(GameTestHelper helper) {
        List<BlockPos> slab = placeSlab(helper);
        embedInPlanks(helper);
        ServerPlayer player = caster(helper);
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, holdTick(player)));
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
            // The tear spends rock goo, whatever its tuned price.
            int spent = CASTER_GOO - GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.ROCK, 0);
            helper.assertTrue(spent > 0, String.format(CHARGED_WRONG, spent));
            leave(helper, player);
        });
    }

    /**
     * A hunk hucked along the look into a cobblestone wall crushes the wall
     * where it strikes and breaks whole, every stone block it carried
     * dropping cobblestone on the same tick.
     * decision diagnose-and-correct-the-hunks-silk-touch-drops
     *
     * @param helper the gametest helper
     */
    public static void hucksAHunkThroughAWall(GameTestHelper helper) {
        List<BlockPos> slab = placeSlab(helper);
        buildWall(helper);
        ServerPlayer player = caster(helper);
        holdFor(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> helper.assertTrue(GrabEvents.throwHeld(player),
                "The player should hold the hunk to huck it"));
        // The crushed wall drops cobblestone on impact, ticks before the hunk's own blocks crumble, so the
        // hunk's drop is the cobblestone that lands on the tick the hunk ends.
        int[] cobblestoneBefore = {0};
        boolean[] shattered = {false};
        for (int tick = HOLD_TICKS + 2; tick <= HOLD_TICKS + 1 + FLIGHT_TICKS + SHATTER_TICKS; tick++) {
            helper.runAfterDelay(tick, () -> {
                int cobblestone = dropped(helper, Items.COBBLESTONE);
                if (hunksIn(helper).isEmpty() && !shattered[0]) {
                    shattered[0] = true;
                    int landed = cobblestone - cobblestoneBefore[0];
                    helper.assertTrue(landed == slab.size(), "The hunk should break whole, dropping "
                            + slab.size() + " cobblestone on the tick it ends, dropped " + landed);
                }
                cobblestoneBefore[0] = cobblestone;
            });
        }
        helper.runAfterDelay(HOLD_TICKS + 2 + FLIGHT_TICKS + SHATTER_TICKS, () -> {
            helper.assertTrue(helper.getBlockState(WALL_MIDDLE).isAir(),
                    String.format(WALL_STANDS, WALL_MIDDLE, helper.getBlockState(WALL_MIDDLE)));
            helper.assertTrue(hunksIn(helper).isEmpty(), HUNK_STANDS);
            leave(helper, player);
        });
    }

    /**
     * A hunk carrying a coal ore block and a grass block, hucked into the
     * wall, crumbles into the loot Blast's break gives each: coal and dirt.
     * decision diagnose-and-correct-the-hunks-silk-touch-drops
     *
     * @param helper the gametest helper
     */
    public static void hunkDropsWhatItsBlocksMine(GameTestHelper helper) {
        placeSlab(helper);
        // Grass on the slab's top row, open to the air, so no random tick turns it to dirt before the tear.
        helper.setBlock(COAL_CELL, Blocks.COAL_ORE);
        helper.setBlock(GRASS_CELL, Blocks.GRASS_BLOCK);
        buildWall(helper);
        ServerPlayer player = caster(helper);
        holdFor(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            List<BlockHunk> hunks = hunksIn(helper);
            helper.assertTrue(hunks.size() == 1 && hunks.getFirst().blocks().stream()
                    .anyMatch(block -> block.state().is(Blocks.COAL_ORE)) && hunks.getFirst().blocks().stream()
                    .anyMatch(block -> block.state().is(Blocks.GRASS_BLOCK)),
                    "One hunk should carry the coal ore and the grass block, found " + hunks.stream()
                            .map(hunk -> hunk.blocks().stream().map(block -> block.state().getBlock()).toList())
                            .toList());
            helper.assertTrue(GrabEvents.throwHeld(player), "The player should hold the hunk to huck it");
        });
        helper.runAfterDelay(HOLD_TICKS + 2 + FLIGHT_TICKS + SHATTER_TICKS, () -> {
            helper.assertTrue(hunksIn(helper).isEmpty(), HUNK_STANDS);
            int coal = dropped(helper, Items.COAL);
            int dirt = dropped(helper, Items.DIRT);
            helper.assertTrue(coal > 0 && dirt > 0, "The coal ore should drop coal and the grass block dirt,"
                    + " dropped " + coal + " coal and " + dirt + " dirt");
            leave(helper, player);
        });
    }

    private static void buildWall(GameTestHelper helper) {
        for (int y = WALL_LOW_Y; y <= WALL_HIGH_Y; y++) {
            for (int z = SLAB_LOW_Z; z <= SLAB_HIGH_Z; z++) {
                helper.setBlock(new BlockPos(WALL_X, y, z), Blocks.COBBLESTONE);
            }
        }
    }

    /**
     * Holding right click on after a throw grabs no new chunk inside the
     * cooldown, so a second chunk never comes up while the first flies.
     *
     * @param helper the gametest helper
     */
    public static void waitsAfterAThrow(GameTestHelper helper) {
        placeSlab(helper);
        ServerPlayer player = caster(helper);
        BlockPos fresh = new BlockPos(SLAB_X, SLAB_HIGH_Y, 2);
        int thrownOn = HOLD_TICKS + 1;
        for (int held = 1; held <= thrownOn + TerrakinesisStep.THROW_COOLDOWN_TICKS; held++) {
            int holdTick = held;
            helper.runAfterDelay(held, () -> {
                if (holdTick == thrownOn) {
                    helper.assertTrue(GrabEvents.throwHeld(player), "The player should hold the chunk to throw it");
                    helper.setBlock(fresh, Blocks.STONE);
                }
                GooStreamHandler.streamTick(player, holdTick(player));
                // The one read: a fresh wind-up begun at the throw would have torn by now, but the cooldown holds it off.
                if (holdTick == thrownOn + TerrakinesisStep.WINDUP_TICKS) {
                    helper.assertTrue(helper.getBlockState(fresh).is(Blocks.STONE), "The fresh stone should stand "
                            + TerrakinesisStep.WINDUP_TICKS + " ticks after a throw, inside its cooldown");
                }
            });
        }
        helper.runAfterDelay(thrownOn + TerrakinesisStep.THROW_COOLDOWN_TICKS + 1, () -> leave(helper, player));
    }

    /**
     * A chunk let go without a throw falls, lands and becomes its own stone on
     * the grid, and the chunk entity ends.
     * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
     *
     * @param helper the gametest helper
     */
    public static void releasePlacesTheChunkOnTheGrid(GameTestHelper helper) {
        List<BlockPos> slab = placeSlab(helper);
        ServerPlayer player = caster(helper);
        holdFor(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + LANDING_TICKS, () -> {
            helper.assertTrue(hunksIn(helper).isEmpty(), "The released chunk should have become blocks");
            int stone = 0;
            // The bay's own floor is the test platform under it, so the chunk lands in the bay's lowest row.
            for (BlockPos cell : BlockPos.betweenClosed(new BlockPos(0, 0, 0),
                    new BlockPos(WALL_X, WALL_HIGH_Y + 1, SLAB_HIGH_Z + 1))) {
                stone += helper.getBlockState(cell).is(Blocks.STONE) ? 1 : 0;
            }
            helper.assertTrue(stone == slab.size(), "The released chunk should stand as its " + slab.size()
                    + " stone blocks on the grid, found " + stone);
            leave(helper, player);
        });
    }

    /**
     * A chunk grabbed out of the floor, the look held down on it, stays shaking
     * in its slot; nothing lifts it but the holder's own hold, pulled up.
     * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
     *
     * @param helper the gametest helper
     */
    public static void heldLookingDownStaysInItsSlotUntilPulledUp(GameTestHelper helper) {
        layFloorWithAStrip(helper);
        ServerPlayer player = caster(helper);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(FLOOR_STRIP_X + 0.5, FLOOR_TOP,
                STAND_POS.getZ() + 0.5)));
        staysInItsSlotUntilPulledUp(helper, player);
    }


    /**
     * A chunk lifted out of the floor, carried aside, then aimed back down
     * into its hole sits in the hole, and let go there it becomes its stone
     * again in the cells it was lifted from.
     * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
     *
     * @param helper the gametest helper
     */
    public static void carriedAwayAndAimedBackSlotsIntoItsHole(GameTestHelper helper) {
        layFloorWithAStrip(helper);
        ServerPlayer player = caster(helper);
        Vec3 hole = helper.absoluteVec(new Vec3(FLOOR_STRIP_X + 0.5, FLOOR_TOP, STAND_POS.getZ() + 0.5));
        Vec3 aside = helper.absoluteVec(new Vec3(ASIDE_X + 0.5, FLOOR_TOP, ASIDE_Z + 0.5));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, hole);
        int lifted = HOLD_TICKS;
        int carried = lifted + PULL_UP_TICKS + CARRY_TICKS;
        int aimedBack = carried + CARRY_TICKS;
        for (int held = 1; held <= aimedBack; held++) {
            int holdTick = held;
            helper.runAfterDelay(held, () -> {
                if (holdTick == lifted) {
                    player.setXRot(HOLDING_UP_PITCH);
                } else if (holdTick == lifted + PULL_UP_TICKS) {
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, aside);
                } else if (holdTick == carried) {
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, hole);
                }
                GooStreamHandler.streamTick(player, holdTick(player));
            });
        }
        helper.runAfterDelay(aimedBack + LANDING_TICKS, () -> {
            for (int z = SLAB_LOW_Z; z <= SLAB_HIGH_Z; z++) {
                BlockPos cell = new BlockPos(FLOOR_STRIP_X, 0, z);
                helper.assertTrue(helper.getBlockState(cell).is(Blocks.STONE), "Aimed back into its hole and let"
                        + " go, the chunk should stand as stone in " + cell + ", which holds "
                        + helper.getBlockState(cell));
            }
            helper.assertTrue(hunksIn(helper).isEmpty(), "The chunk should have become its blocks");
            leave(helper, player);
        });
    }


    /**
     * A wide chunk, two by two and two deep, lifted from afar, drawn aside
     * toward the caster, then aimed back down at its hole, sits in the hole,
     * and let go there it becomes its stone again where it was lifted from.
     * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
     *
     * @param helper the gametest helper
     */
    public static void wideChunkAimedBackDownSlotsIntoItsHole(GameTestHelper helper) {
        layFloorWithAStrip(helper);
        List<BlockPos> patch = new ArrayList<>();
        for (int z = SLAB_LOW_Z; z <= SLAB_HIGH_Z; z++) {
            helper.setBlock(new BlockPos(FLOOR_STRIP_X, 0, z), Blocks.OAK_PLANKS);
        }
        for (int x = FAR_STRIP_X - 1; x <= FAR_STRIP_X; x++) {
            for (int z = STAND_POS.getZ(); z <= STAND_POS.getZ() + 1; z++) {
                for (int y = -1; y <= 0; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    patch.add(new BlockPos(x, y, z));
                }
            }
        }
        ServerPlayer player = caster(helper);
        Vec3 hole = helper.absoluteVec(new Vec3(FAR_STRIP_X, FLOOR_TOP, STAND_POS.getZ() + 1));
        Vec3 aside = helper.absoluteVec(new Vec3(ASIDE_X + 0.5, FLOOR_TOP, ASIDE_Z + 0.5));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(FAR_STRIP_X + 0.5, FLOOR_TOP,
                STAND_POS.getZ() + 1.5)));
        int lifted = HOLD_TICKS;
        int carried = lifted + PULL_UP_TICKS + CARRY_TICKS;
        int aimedBack = carried + CARRY_TICKS;
        for (int held = 1; held <= aimedBack; held++) {
            int holdTick = held;
            helper.runAfterDelay(held, () -> {
                if (holdTick == lifted) {
                    player.setXRot(HOLDING_UP_PITCH);
                } else if (holdTick == lifted + PULL_UP_TICKS) {
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, aside);
                } else if (holdTick == carried) {
                    // The caster steps up beside the hole, within the hold's reach, and aims back into it.
                    Vec3 beside = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(FAR_STRIP_X - 2,
                            FLOOR_TOP, STAND_POS.getZ())));
                    player.setPos(beside.x, beside.y, beside.z);
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, hole);
                }
                GooStreamHandler.streamTick(player, holdTick(player));
            });
        }
        helper.runAfterDelay(aimedBack + LANDING_TICKS, () -> {
            List<BlockPos> holding = patch.stream().filter(cell -> helper.getBlockState(cell).is(Blocks.STONE))
                    .toList();
            helper.assertTrue(hunksIn(helper).isEmpty() && holding.size() == patch.size(), "Aimed back down into"
                    + " its hole and let go, the wide chunk should stand as stone in all " + patch.size()
                    + " cells it was lifted from; stone stands in " + holding.size() + ", hunks left "
                    + hunksIn(helper).size() + ", stone elsewhere at " + BlockPos.betweenClosedStream(new BlockPos(0, -1, 0),
                    new BlockPos(WALL_X, WALL_HIGH_Y, SLAB_HIGH_Z + 1))
                    .filter(cell -> helper.getBlockState(cell).is(Blocks.STONE)).map(BlockPos::immutable).toList());
            leave(helper, player);
        });
    }

    /**
     * A wide chunk grabbed from a block away, the look held down on it, stays
     * shaking in its slot until the holder pulls it up and out.
     * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
     *
     * @param helper the gametest helper
     */
    public static void wideChunkStaysInItsSlotUntilPulledUp(GameTestHelper helper) {
        layNearPatch(helper);
        ServerPlayer player = caster(helper);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, nearPatchMiddle(helper));
        staysInItsSlotUntilPulledUp(helper, player);
    }


    /**
     * A wide chunk carried clear of its hole, the holder then standing at the
     * hole's edge leaning over it and looking steeply down into it, goes into
     * the hole, and let go there becomes its stone again where it came from.
     * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
     *
     * @param helper the gametest helper
     */
    public static void wideChunkLookedDownIntoItsHoleSlotsIn(GameTestHelper helper) {
        layFloorWithAStrip(helper);
        List<BlockPos> patch = new ArrayList<>();
        for (int z = SLAB_LOW_Z; z <= SLAB_HIGH_Z; z++) {
            helper.setBlock(new BlockPos(FLOOR_STRIP_X, 0, z), Blocks.OAK_PLANKS);
        }
        for (int x = FAR_STRIP_X - 1; x <= FAR_STRIP_X; x++) {
            for (int z = STAND_POS.getZ(); z <= STAND_POS.getZ() + 1; z++) {
                for (int y = -1; y <= 0; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                    patch.add(new BlockPos(x, y, z));
                }
            }
        }
        ServerPlayer player = caster(helper);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(FAR_STRIP_X + 0.5, FLOOR_TOP,
                STAND_POS.getZ() + 1.5)));
        Vec3 aside = helper.absoluteVec(new Vec3(ASIDE_X + 0.5, FLOOR_TOP, ASIDE_Z + 0.5));
        int lifted = HOLD_TICKS;
        int carried = lifted + PULL_UP_TICKS + CARRY_TICKS;
        int lookedIn = carried + CARRY_TICKS;
        for (int held = 1; held <= lookedIn; held++) {
            int holdTick = held;
            helper.runAfterDelay(held, () -> {
                if (holdTick == lifted) {
                    player.setXRot(HOLDING_UP_PITCH);
                } else if (holdTick == lifted + PULL_UP_TICKS) {
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, aside);
                } else if (holdTick == carried) {
                    // At the hole's west edge, leaning over it, looking steeply down into it.
                    Vec3 edge = helper.absoluteVec(new Vec3(FAR_STRIP_X - 1, FLOOR_TOP, STAND_POS.getZ() + 1));
                    player.setPos(edge.x, edge.y, edge.z);
                    player.setYRot(FACING_EAST);
                    player.setXRot(INTO_THE_HOLE_PITCH);
                }
                GooStreamHandler.streamTick(player, holdTick(player));
            });
        }
        helper.runAfterDelay(lookedIn + LANDING_TICKS, () -> {
            List<BlockPos> holding = patch.stream().filter(cell -> helper.getBlockState(cell).is(Blocks.STONE))
                    .toList();
            helper.assertTrue(hunksIn(helper).isEmpty() && holding.size() == patch.size(), "Looked down into its"
                    + " hole and let go, the wide chunk should stand as stone in all " + patch.size() + " cells;"
                    + " stone stands in " + holding.size() + ", hunks left " + hunksIn(helper).size()
                    + ", stone elsewhere at " + BlockPos.betweenClosedStream(new BlockPos(0, -1, 0),
                    new BlockPos(WALL_X, WALL_HIGH_Y, SLAB_HIGH_Z + 1))
                    .filter(cell -> helper.getBlockState(cell).is(Blocks.STONE)).map(BlockPos::immutable).toList());
            leave(helper, player);
        });
    }

    /**
     * A wide chunk lifted, carried off its hole, then held with the crosshair
     * pointed into its hole eases into its slot while still held, seated
     * exactly where it was lifted from, before it is ever let go.
     * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
     *
     * @param helper the gametest helper
     */
    public static void heldChunkEasesIntoItsSlotWhileHeld(GameTestHelper helper) {
        layNearPatch(helper);
        ServerPlayer player = caster(helper);
        Vec3 hole = nearPatchMiddle(helper);
        Vec3 aside = helper.absoluteVec(new Vec3(ASIDE_X + 0.5, FLOOR_TOP, ASIDE_Z + 0.5));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, hole);
        int lifted = HOLD_TICKS;
        int carried = lifted + PULL_UP_TICKS + CARRY_TICKS;
        int aimedIn = carried + CARRY_TICKS;
        for (int held = 1; held <= aimedIn; held++) {
            int holdTick = held;
            helper.runAfterDelay(held, () -> {
                if (holdTick == lifted) {
                    player.setXRot(HOLDING_UP_PITCH);
                } else if (holdTick == lifted + PULL_UP_TICKS) {
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, aside);
                } else if (holdTick == carried) {
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, hole);
                }
                GooStreamHandler.streamTick(player, holdTick(player));
                if (holdTick == aimedIn) {
                    BlockHunk hunk = hunksIn(helper).getFirst();
                    Vec3 slot = Vec3.atBottomCenterOf(hunk.home());
                    helper.assertTrue(hunk.position().distanceTo(slot) < IN_THE_SLOT, "Held with the crosshair in"
                            + " its hole, the chunk should ease into its slot at " + slot + " while still held,"
                            + " stands at " + hunk.position());
                    leave(helper, player);
                }
            });
        }
    }

    /**
     * A wide chunk lifted and carried off its hole, its holder then standing
     * in the hole and aiming down, however steeply, eases into its slot while
     * held, and the holder ends standing on top of it rather than inside it.
     * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
     *
     * @param helper the gametest helper
     */
    public static void heldChunkSlotsInFromInsideItsHole(GameTestHelper helper) {
        layNearPatch(helper);
        ServerPlayer player = caster(helper);
        Vec3 aside = helper.absoluteVec(new Vec3(ASIDE_X + 0.5, FLOOR_TOP, ASIDE_Z + 0.5));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, nearPatchMiddle(helper));
        int lifted = HOLD_TICKS;
        int carried = lifted + PULL_UP_TICKS + CARRY_TICKS;
        int aimedDown = carried + CARRY_TICKS * AIM_DOWN_PITCHES.length;
        for (int held = 1; held <= aimedDown; held++) {
            int holdTick = held;
            helper.runAfterDelay(held, () -> {
                if (holdTick == lifted) {
                    player.setXRot(HOLDING_UP_PITCH);
                } else if (holdTick == lifted + PULL_UP_TICKS) {
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, aside);
                } else if (holdTick == carried) {
                    // Into the hole, on its floor, facing east across it.
                    Vec3 floor = helper.absoluteVec(new Vec3(NEAR_PATCH_X + 0.5, -1, STAND_POS.getZ() + 0.5));
                    player.setPos(floor.x, floor.y, floor.z);
                    player.setYRot(FACING_EAST);
                }
                if (holdTick >= carried) {
                    player.setXRot(AIM_DOWN_PITCHES[Math.min((holdTick - carried) / CARRY_TICKS,
                            AIM_DOWN_PITCHES.length - 1)]);
                }
                GooStreamHandler.streamTick(player, holdTick(player));
                boolean endOfAPitch = holdTick > carried && (holdTick - carried) % CARRY_TICKS == 0;
                if (endOfAPitch) {
                    BlockHunk hunk = hunksIn(helper).getFirst();
                    Vec3 slot = Vec3.atBottomCenterOf(hunk.home());
                    helper.assertTrue(hunk.position().distanceTo(slot) < IN_THE_SLOT, "Held from inside its hole,"
                            + " aiming down at pitch " + player.getXRot() + ", the chunk should be in its slot at "
                            + slot + ", stands at " + hunk.position());
                    helper.assertTrue(!inTheHolder(hunk, player), "The holder should stand on the slotted chunk,"
                            + " not in it; holder " + player.getBoundingBox() + ", chunk " + hunk.getBoundingBox());
                }
                if (holdTick == aimedDown) {
                    leave(helper, player);
                }
            });
        }
    }

    /**
     * A wide chunk lifted, held up clear of its hole without ever leaving
     * the hole's footprint, then pulled back down over the hole, seats in its
     * slot while still held, as the operator puts a chunk back.
     * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
     *
     * @param helper the gametest helper
     */
    public static void heldUpThenPulledDownOverItsHoleSeats(GameTestHelper helper) {
        layNearPatch(helper);
        ServerPlayer player = caster(helper);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, nearPatchMiddle(helper));
        int lifted = HOLD_TICKS;
        int heldUp = lifted + CARRY_TICKS;
        int pulledDown = heldUp + CARRY_TICKS;
        for (int held = 1; held <= pulledDown; held++) {
            int holdTick = held;
            helper.runAfterDelay(held, () -> {
                if (holdTick == lifted) {
                    player.setXRot(HOLDING_UP_PITCH);
                } else if (holdTick == heldUp) {
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, nearPatchMiddle(helper));
                }
                GooStreamHandler.streamTick(player, holdTick(player));
                if (holdTick == pulledDown) {
                    BlockHunk hunk = hunksIn(helper).getFirst();
                    Vec3 slot = Vec3.atBottomCenterOf(hunk.home());
                    helper.assertTrue(hunk.position().distanceTo(slot) < IN_THE_SLOT, "Held up, then pulled back down"
                            + " over its hole, the chunk should seat in its slot at " + slot + " while held, stands at "
                            + hunk.position());
                    leave(helper, player);
                }
            });
        }
    }

    private static void staysInItsSlotUntilPulledUp(GameTestHelper helper, ServerPlayer player) {
        double rim = helper.absolutePos(new BlockPos(0, FLOOR_TOP, 0)).getY();
        int pulledUp = LOOK_DOWN_TICKS + PULL_UP_TICKS * 2;
        for (int held = 1; held <= pulledUp; held++) {
            int holdTick = held;
            helper.runAfterDelay(held, () -> {
                if (holdTick == LOOK_DOWN_TICKS) {
                    player.setXRot(HOLDING_UP_PITCH);
                }
                GooStreamHandler.streamTick(player, holdTick(player));
                List<BlockHunk> hunks = hunksIn(helper);
                if (hunks.isEmpty()) {
                    return;
                }
                BlockHunk hunk = hunks.getFirst();
                if (holdTick < LOOK_DOWN_TICKS) {
                    helper.assertTrue(hunk.heldInItsSlot(), "Grabbed with the look held down on it, the chunk"
                            + " should stay shaking in its slot at " + Vec3.atBottomCenterOf(hunk.home())
                            + ", stands at " + hunk.position() + " on hold tick " + holdTick);
                } else if (holdTick == pulledUp) {
                    helper.assertTrue(hunk.getBoundingBox().minY >= rim - RIM_SLACK && !hunk.heldInItsSlot(),
                            "Pulled up by the hold, the chunk should come out of its slot; its bottom stands at "
                            + hunk.getBoundingBox().minY + ", the rim at " + rim);
                    leave(helper, player);
                }
            });
        }
    }

    private static boolean inTheHolder(BlockHunk hunk, ServerPlayer player) {
        AABB body = player.getBoundingBox();
        return hunk.cellsAt(hunk.position()).stream().anyMatch(cell -> cell.intersects(body));
    }

    private static void layNearPatch(GameTestHelper helper) {
        layFloorWithAStrip(helper);
        for (int z = SLAB_LOW_Z; z <= SLAB_HIGH_Z; z++) {
            helper.setBlock(new BlockPos(FLOOR_STRIP_X, 0, z), Blocks.OAK_PLANKS);
        }
        for (int x = NEAR_PATCH_X; x <= NEAR_PATCH_X + 1; x++) {
            for (int z = STAND_POS.getZ(); z <= STAND_POS.getZ() + 1; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, -1, z), Blocks.STONE);
            }
        }
    }

    private static Vec3 nearPatchMiddle(GameTestHelper helper) {
        return helper.absoluteVec(new Vec3(NEAR_PATCH_X + 1, FLOOR_TOP, STAND_POS.getZ() + 1));
    }

    private static void layFloorWithAStrip(GameTestHelper helper) {
        for (int x = 0; x <= WALL_X; x++) {
            for (int z = 0; z <= SLAB_HIGH_Z + 1; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.OAK_PLANKS);
            }
        }
        for (int z = SLAB_LOW_Z; z <= SLAB_HIGH_Z; z++) {
            helper.setBlock(new BlockPos(FLOOR_STRIP_X, 0, z), Blocks.STONE);
        }
    }

    private static int dropped(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, bay(helper)).stream()
                .filter(entity -> entity.getItem().is(item)).mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    /**
     * Sets the slab in a hole of planks, which carry no rock goo: walled on
     * the north and south up to the slab's top, and on the west along its
     * bottom row, below the look; the east, toward the hold, stays open, so the
     * hold stands in open air and seats nothing.
     *
     * @param helper the gametest helper
     */
    private static void embedInPlanks(GameTestHelper helper) {
        for (int y = SLAB_LOW_Y; y <= SLAB_HIGH_Y; y++) {
            helper.setBlock(new BlockPos(SLAB_X, y, SLAB_LOW_Z - 1), Blocks.OAK_PLANKS);
            helper.setBlock(new BlockPos(SLAB_X, y, SLAB_HIGH_Z + 1), Blocks.OAK_PLANKS);
        }
        for (int z = SLAB_LOW_Z - 1; z <= SLAB_HIGH_Z + 1; z++) {
            helper.setBlock(new BlockPos(SLAB_X - 1, SLAB_LOW_Y, z), Blocks.OAK_PLANKS);
        }
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


    private static void leave(GameTestHelper helper, ServerPlayer player) {
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer caster(GameTestHelper helper) {
        return caster(helper, STAND_POS);
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer caster(GameTestHelper helper, BlockPos standPos) {
        AbilityDefinition terrakinesis = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_TERRAKINESIS);
        helper.assertTrue(terrakinesis != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(standPos));
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
