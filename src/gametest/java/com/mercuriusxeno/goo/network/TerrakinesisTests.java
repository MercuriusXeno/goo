package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.kinetic.GrabEvents;
import com.mercuriusxeno.goo.ability.program.TerrakinesisStep;
import com.mercuriusxeno.goo.data.GooValue;
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
    private static final String GRABS_FROM_AFAR = "terrakinesis_grabs_from_eleven_blocks_away";
    private static final String WAITS_AFTER_A_THROW = "terrakinesis_waits_after_a_throw";
    private static final String RELEASE_PLACES = "terrakinesis_release_places_the_chunk_on_the_grid";
    private static final String STAYS_IN = "terrakinesis_held_looking_down_stays_in_its_slot_until_pulled_up";
    private static final String SLOTS_BACK = "terrakinesis_carried_away_and_aimed_back_slots_into_its_hole";
    private static final String NEVER_IN_THE_HOLDER = "terrakinesis_held_looking_down_never_clips_into_the_holder";
    private static final String WIDE_SLOTS_BACK = "terrakinesis_wide_chunk_aimed_back_down_slots_into_its_hole";
    private static final String WIDE_STAYS_IN = "terrakinesis_wide_chunk_stays_in_its_slot_until_pulled_up";
    private static final String WIDE_NEVER_IN_THE_HOLDER = "terrakinesis_wide_chunk_never_clips_into_the_holder";
    private static final String GRAB_AT_THE_FEET = "terrakinesis_grab_at_the_feet_never_lifts_through_the_holder";
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
    /** Pitches, looking down, the holder sweeps through: steep, near straight down, and straight down. */
    private static final float[] DOWN_PITCHES = {60f, 80f, 89f, 90f};
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
    /** Where the caster stands for the long grab: 11 blocks west of the slab's face, inside the 12 it reaches. */
    private static final BlockPos FAR_STAND_POS = new BlockPos(-9, 1, 2);
    /** The bay's barrier wall where the far caster's level look crosses it. */
    private static final BlockPos BAY_WALL_ON_THE_LOOK = new BlockPos(-1, 2, 2);

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
    /** Through the wind-up, then long enough for the hunk to rise out of its hole and reach the hold. */
    private static final int HOLD_TICKS = TerrakinesisStep.WINDUP_TICKS + 14;
    /** The top of the hole the slab tears out of, the planks' rim around it. */
    private static final int HOLE_TOP = SLAB_HIGH_Y + 1;
    /** How far the hunk may stray across its hole while it rises out of it. */
    private static final double OVER_THE_HOLE = 0.01;
    /** The ability's far hold distance, eye to the hunk's middle. */
    private static final double FAR = 3.5;
    private static final double SETTLED = 0.3;
    /** Every block of a hucked hunk has crumbled by then. */
    private static final int SHATTER_TICKS = BlockHunk.BLOCK_STAGGER_TICKS * 6 + BlockHunk.CRUMBLE_TICKS + 2;
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
        GooTestFunctions.registerTest(registrar, GRABS_FROM_AFAR, TerrakinesisTests::grabsFromElevenBlocksAway);
        GooTestFunctions.registerTest(registrar, WAITS_AFTER_A_THROW, TerrakinesisTests::waitsAfterAThrow);
        GooTestFunctions.registerTest(registrar, RELEASE_PLACES, TerrakinesisTests::releasePlacesTheChunkOnTheGrid);
        GooTestFunctions.registerTest(registrar, STAYS_IN, TerrakinesisTests::heldLookingDownStaysInItsSlotUntilPulledUp);
        GooTestFunctions.registerTest(registrar, SLOTS_BACK, TerrakinesisTests::carriedAwayAndAimedBackSlotsIntoItsHole);
        GooTestFunctions.registerTest(registrar, NEVER_IN_THE_HOLDER, TerrakinesisTests::heldLookingDownNeverClipsIntoTheHolder);
        GooTestFunctions.registerTest(registrar, WIDE_SLOTS_BACK, TerrakinesisTests::wideChunkAimedBackDownSlotsIntoItsHole);
        GooTestFunctions.registerTest(registrar, WIDE_STAYS_IN, TerrakinesisTests::wideChunkStaysInItsSlotUntilPulledUp);
        GooTestFunctions.registerTest(registrar, WIDE_NEVER_IN_THE_HOLDER, TerrakinesisTests::wideChunkNeverClipsIntoTheHolder);
        GooTestFunctions.registerTest(registrar, GRAB_AT_THE_FEET, TerrakinesisTests::grabAtTheFeetNeverLiftsThroughTheHolder);
        GooTestFunctions.registerTest(registrar, LOOKED_DOWN_INTO, TerrakinesisTests::wideChunkLookedDownIntoItsHoleSlotsIn);
        GooTestFunctions.registerTest(registrar, HELD_INTO, TerrakinesisTests::heldChunkEasesIntoItsSlotWhileHeld);
        GooTestFunctions.registerTest(registrar, FROM_IN_THE_HOLE, TerrakinesisTests::heldChunkSlotsInFromInsideItsHole);
        GooTestFunctions.registerTest(registrar, RAISED_THEN_PULLED, TerrakinesisTests::heldUpThenPulledDownOverItsHoleSeats);
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
        embedInPlanks(helper);
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
            // Drawn out along the level look by the holder's own hold, it is held close in front.
            double out = hunks.getFirst().getBoundingBox().getCenter().distanceTo(player.getEyePosition());
            helper.assertTrue(out <= FAR + SETTLED, "The hunk should be held within " + FAR + " of the eye, stands "
                    + out + " out");
            helper.assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(TerrakinesisStep.TEAR.sound()),
                    "The tear's sound " + TerrakinesisStep.TEAR.sound() + " should stand in the sound registry");
            // The tear costs its fixed price, whatever the hunk holds.
            int expected = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_TERRAKINESIS).behaviors().stream()
                    .filter(TerrakinesisStep.class::isInstance).map(TerrakinesisStep.class::cast).findFirst()
                    .orElseThrow().cost();
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
        boolean[] firstDropSeen = {false};
        for (int tick = HOLD_TICKS + 2; tick <= HOLD_TICKS + 1 + FLIGHT_TICKS + SHATTER_TICKS; tick++) {
            helper.runAfterDelay(tick, () -> {
                int dropped = droppedStone(helper);
                if (dropped > 0 && !firstDropSeen[0]) {
                    firstDropSeen[0] = true;
                    helper.assertTrue(dropped < slab.size(), "The hunk should crumble block by block, not drop all "
                            + slab.size() + " of its stone on one tick, dropped " + dropped);
                }
            });
        }
        helper.runAfterDelay(HOLD_TICKS + 2 + FLIGHT_TICKS + SHATTER_TICKS, () -> {
            helper.assertTrue(helper.getBlockState(WALL_MIDDLE).isAir(),
                    String.format(WALL_STANDS, WALL_MIDDLE, helper.getBlockState(WALL_MIDDLE)));
            helper.assertTrue(hunksIn(helper).isEmpty(), HUNK_STANDS);
            int dropped = droppedStone(helper);
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

    /**
     * Terrakinesis tears up the slab from 11 blocks away, inside its reach of 12.
     *
     * @param helper the gametest helper
     */
    public static void grabsFromElevenBlocksAway(GameTestHelper helper) {
        placeSlab(helper);
        // The bay is walled in barrier; the caster stands outside it, so the cell on the look line is opened.
        helper.setBlock(BAY_WALL_ON_THE_LOOK, Blocks.AIR);
        ServerPlayer player = caster(helper, FAR_STAND_POS);
        holdFor(helper, player, HOLD_TICKS);
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            AABB span = new AABB(helper.absolutePos(FAR_STAND_POS)).minmax(new AABB(helper.absolutePos(
                    new BlockPos(SLAB_X, SLAB_HIGH_Y, SLAB_HIGH_Z)))).inflate(SCATTER);
            int torn = helper.getLevel().getEntitiesOfClass(BlockHunk.class, span).size();
            helper.assertTrue(torn == 1, "A slab 11 blocks off should tear free inside the reach of 12, found " + torn);
            leave(helper, player);
        });
    }

    /**
     * Holding right click on after a throw grabs no new chunk until the
     * cooldown has passed, so a second chunk never comes up while the first flies.
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
                if (holdTick > thrownOn) {
                    helper.assertTrue(helper.getBlockState(fresh).is(Blocks.STONE), "No chunk should be grabbed "
                            + (holdTick - thrownOn) + " ticks after a throw, inside its cooldown");
                }
            });
        }
        helper.runAfterDelay(thrownOn + TerrakinesisStep.THROW_COOLDOWN_TICKS + 1, () -> leave(helper, player));
    }

    /**
     * A chunk let go without a throw falls, lands and becomes its own stone on
     * the grid, dropping no item, and the chunk entity ends.
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
            int items = helper.getLevel().getEntitiesOfClass(ItemEntity.class, bay(helper)).size();
            helper.assertTrue(items == 0, "A released chunk should drop nothing, dropped " + items);
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
     * A held chunk never stands in its holder: looking steeply down, even
     * straight down at their own feet, the chunk stays clear of the holder's
     * body, so the holder never sees through it from inside.
     * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
     *
     * @param helper the gametest helper
     */
    public static void heldLookingDownNeverClipsIntoTheHolder(GameTestHelper helper) {
        layFloorWithAStrip(helper);
        ServerPlayer player = caster(helper);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(FLOOR_STRIP_X + 0.5, FLOOR_TOP,
                STAND_POS.getZ() + 0.5)));
        int perPitch = CARRY_TICKS;
        int last = HOLD_TICKS + perPitch * DOWN_PITCHES.length;
        for (int held = 1; held <= last; held++) {
            int holdTick = held;
            helper.runAfterDelay(held, () -> {
                int pitchIndex = (holdTick - HOLD_TICKS) / perPitch;
                if (holdTick >= HOLD_TICKS && pitchIndex < DOWN_PITCHES.length) {
                    player.setXRot(DOWN_PITCHES[pitchIndex]);
                }
                GooStreamHandler.streamTick(player, holdTick(player));
                List<BlockHunk> hunks = hunksIn(helper);
                if (holdTick > HOLD_TICKS && !hunks.isEmpty()) {
                    helper.assertTrue(!inTheHolder(hunks.getFirst(), player), "Looking down at pitch "
                            + player.getXRot() + ", no block of the held chunk " + hunks.getFirst().getBoundingBox()
                            + " should stand in its holder " + player.getBoundingBox());
                }
                if (holdTick == last) {
                    leave(helper, player);
                }
            });
        }
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
     * A wide chunk pulled out from a block away never stands in its holder,
     * the look sweeping down from steep to straight down at the holder's feet.
     * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
     *
     * @param helper the gametest helper
     */
    public static void wideChunkNeverClipsIntoTheHolder(GameTestHelper helper) {
        layNearPatch(helper);
        ServerPlayer player = caster(helper);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, nearPatchMiddle(helper));
        int last = HOLD_TICKS + CARRY_TICKS * DOWN_PITCHES.length;
        for (int held = 1; held <= last; held++) {
            int holdTick = held;
            helper.runAfterDelay(held, () -> {
                int pitchIndex = (holdTick - HOLD_TICKS) / CARRY_TICKS;
                if (holdTick >= HOLD_TICKS && pitchIndex < DOWN_PITCHES.length) {
                    player.setXRot(DOWN_PITCHES[pitchIndex]);
                }
                GooStreamHandler.streamTick(player, holdTick(player));
                List<BlockHunk> hunks = hunksIn(helper);
                if (holdTick > HOLD_TICKS && !hunks.isEmpty()) {
                    helper.assertTrue(!inTheHolder(hunks.getFirst(), player), "Looking down at pitch "
                            + player.getXRot() + ", no block of the wide chunk " + hunks.getFirst().getBoundingBox()
                            + " should stand in its holder " + player.getBoundingBox());
                }
                if (holdTick == last) {
                    leave(helper, player);
                }
            });
        }
    }

    /**
     * Standing on stone and grabbing it a block ahead of the feet, the chunk
     * never rises through its holder, on any tick from the tear on, and the
     * holder never drops: the ground they stand on stays put.
     * decision rock-grabs-hunks-of-rock-goo-blocks-as-a-huckable-entity
     *
     * @param helper the gametest helper
     */
    public static void grabAtTheFeetNeverLiftsThroughTheHolder(GameTestHelper helper) {
        layFloorWithAStrip(helper);
        for (int x = 0; x <= FLOOR_STRIP_X; x++) {
            for (int z = SLAB_LOW_Z; z <= SLAB_HIGH_Z; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, -1, z), Blocks.STONE);
            }
        }
        ServerPlayer player = caster(helper);
        double feet = player.getY();
        player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(1.5, FLOOR_TOP,
                STAND_POS.getZ() + 0.5)));
        for (int held = 1; held <= LOOK_DOWN_TICKS; held++) {
            int holdTick = held;
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(player, holdTick(player));
                player.tick();
                for (BlockHunk hunk : hunksIn(helper)) {
                    helper.assertTrue(!inTheHolder(hunk, player), "Grabbed a block ahead of the feet, no block of"
                            + " the chunk " + hunk.getBoundingBox() + " should ever rise through its holder "
                            + player.getBoundingBox());
                }
                helper.assertTrue(Math.abs(player.getY() - feet) < RIM_SLACK, "The holder should stand on unbroken"
                        + " ground at " + feet + ", stands at " + player.getY());
                if (holdTick == LOOK_DOWN_TICKS) {
                    helper.assertTrue(!hunksIn(helper).isEmpty(), "A chunk should have been lifted");
                    leave(helper, player);
                }
            });
        }
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

    private static int droppedStone(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, bay(helper)).stream()
                .filter(item -> item.getItem().is(Items.STONE)).mapToInt(item -> item.getItem().getCount()).sum();
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
