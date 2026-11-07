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
    private static final String STAYS_OUT = "terrakinesis_held_looking_down_stays_out_of_its_hole";
    private static final String SLOTS_BACK = "terrakinesis_carried_away_and_aimed_back_slots_into_its_hole";
    private static final String NEVER_IN_THE_HOLDER = "terrakinesis_held_looking_down_never_clips_into_the_holder";
    private static final String STAYS_OUT_FROM_AFAR = "terrakinesis_lifted_from_afar_stays_out_of_its_hole";
    private static final String WIDE_SLOTS_BACK = "terrakinesis_wide_chunk_aimed_back_down_slots_into_its_hole";
    /** The far patch's east edge: three by three, two blocks deep in the floor, its middle four blocks off. */
    private static final int FAR_STRIP_X = 5;
    /** Pitches, looking down, the holder sweeps through: steep, near straight down, and straight down. */
    private static final float[] DOWN_PITCHES = {60f, 80f, 89f, 90f};
    /** Where the caster carries the chunk aside to, on the planks floor east of its hole. */
    private static final int ASIDE_X = 4;
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
        GooTestFunctions.registerTest(registrar, STAYS_OUT, TerrakinesisTests::heldLookingDownStaysOutOfItsHole);
        GooTestFunctions.registerTest(registrar, SLOTS_BACK, TerrakinesisTests::carriedAwayAndAimedBackSlotsIntoItsHole);
        GooTestFunctions.registerTest(registrar, NEVER_IN_THE_HOLDER, TerrakinesisTests::heldLookingDownNeverClipsIntoTheHolder);
        GooTestFunctions.registerTest(registrar, STAYS_OUT_FROM_AFAR, TerrakinesisTests::liftedFromAfarStaysOutOfItsHole);
        GooTestFunctions.registerTest(registrar, WIDE_SLOTS_BACK, TerrakinesisTests::wideChunkAimedBackDownSlotsIntoItsHole);
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
        boolean[] clearedOnce = {false};
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
                } else if (holdTick > TerrakinesisStep.WINDUP_TICKS) {
                    assertOverItsHoleUntilClear(helper, clearedOnce);
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
            // Out of its hole, it rests on what the look strikes within the grab's range, in none of its own cells.
            AABB box = hunks.getFirst().getBoundingBox();
            AABB hole = AABB.encapsulatingFullBlocks(helper.absolutePos(slab.getFirst()),
                    helper.absolutePos(slab.getLast())).deflate(RIM_SLACK);
            double out = box.getCenter().distanceTo(player.getEyePosition());
            double range = AbilityRegistry.of(helper.getLevel()).getAbility(ROCK_TERRAKINESIS).behaviors().stream()
                    .filter(TerrakinesisStep.class::isInstance).map(TerrakinesisStep.class::cast).findFirst()
                    .orElseThrow().range();
            helper.assertTrue(!box.intersects(hole) && out <= range, "Clear of its hole, the hunk should stand out"
                    + " of it within " + range + " of the eye; it stands at " + box + ", " + out + " out, its hole"
                    + " at " + hole);
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
     * A chunk lifted out of the floor while the caster keeps looking down at
     * it rides on the surface the look strikes, its own hole counting as solid
     * until it is carried out of the hole's column, so it never slips back
     * into the hole it came out of, and ends sitting on the hole.
     * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
     *
     * @param helper the gametest helper
     */
    public static void heldLookingDownStaysOutOfItsHole(GameTestHelper helper) {
        layFloorWithAStrip(helper);
        ServerPlayer player = caster(helper);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(FLOOR_STRIP_X + 0.5, FLOOR_TOP,
                STAND_POS.getZ() + 0.5)));
        double rim = helper.absolutePos(new BlockPos(0, FLOOR_TOP, 0)).getY();
        boolean[] clearedOnce = {false};
        for (int held = 1; held <= LOOK_DOWN_TICKS; held++) {
            int holdTick = held;
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(player, holdTick(player));
                List<BlockHunk> hunks = hunksIn(helper);
                if (hunks.isEmpty()) {
                    return;
                }
                double bottom = hunks.getFirst().getBoundingBox().minY;
                clearedOnce[0] |= bottom >= rim - RIM_SLACK;
                helper.assertTrue(!clearedOnce[0] || bottom >= rim - RIM_SLACK, "Once out, the chunk held on a"
                        + " look down at its hole should sit on the hole, not slip back in; its bottom stands at "
                        + bottom + ", the rim at " + rim);
                if (holdTick == LOOK_DOWN_TICKS) {
                    helper.assertTrue(Math.abs(bottom - rim) < SETTLED, "The chunk should come to sit on its hole,"
                            + " its bottom at the rim " + rim + ", stands at " + bottom);
                    leave(helper, player);
                }
            });
        }
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
        Vec3 aside = helper.absoluteVec(new Vec3(ASIDE_X + 0.5, FLOOR_TOP, STAND_POS.getZ() + 0.5));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, hole);
        int lifted = HOLD_TICKS;
        int carried = lifted + CARRY_TICKS;
        int aimedBack = carried + CARRY_TICKS;
        for (int held = 1; held <= aimedBack; held++) {
            int holdTick = held;
            helper.runAfterDelay(held, () -> {
                if (holdTick == lifted) {
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, aside);
                } else if (holdTick == carried) {
                    helper.assertTrue(hunksIn(helper).getFirst().carriedAway(), "Carried aside onto the planks,"
                            + " the chunk should count as carried out of its hole's column, stands at "
                            + hunksIn(helper).getFirst().position());
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
                    AABB hunk = hunks.getFirst().getBoundingBox();
                    helper.assertTrue(!hunk.intersects(player.getBoundingBox()), "Looking down at pitch "
                            + player.getXRot() + ", the held chunk " + hunk + " should stand clear of its holder "
                            + player.getBoundingBox());
                }
                if (holdTick == last) {
                    leave(helper, player);
                }
            });
        }
    }

    /**
     * A wide chunk two blocks deep lifted from four blocks off, the look held on
     * where it came from, never slips back into its hole once it pokes out:
     * its hole stays solid to it until it is carried out of the hole's column.
     * decision terrakinesis-release-drops-the-hunk-and-places-it-on-the-grid
     *
     * @param helper the gametest helper
     */
    public static void liftedFromAfarStaysOutOfItsHole(GameTestHelper helper) {
        layFloorWithAStrip(helper);
        for (int z = SLAB_LOW_Z; z <= SLAB_HIGH_Z; z++) {
            helper.setBlock(new BlockPos(FLOOR_STRIP_X, 0, z), Blocks.OAK_PLANKS);
            for (int x = FAR_STRIP_X - 2; x <= FAR_STRIP_X; x++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, -1, z), Blocks.STONE);
            }
        }
        ServerPlayer player = caster(helper);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(FAR_STRIP_X - 0.5, FLOOR_TOP,
                STAND_POS.getZ() + 0.5)));
        double rim = helper.absolutePos(new BlockPos(0, FLOOR_TOP, 0)).getY();
        boolean[] clearedOnce = {false};
        for (int held = 1; held <= LOOK_DOWN_TICKS; held++) {
            int holdTick = held;
            helper.runAfterDelay(held, () -> {
                GooStreamHandler.streamTick(player, holdTick(player));
                List<BlockHunk> hunks = hunksIn(helper);
                if (hunks.isEmpty()) {
                    return;
                }
                double bottom = hunks.getFirst().getBoundingBox().minY;
                clearedOnce[0] |= bottom >= rim - RIM_SLACK;
                helper.assertTrue(!clearedOnce[0] || bottom >= rim - RIM_SLACK, "Once it pokes out, a chunk"
                        + " lifted from afar should never drop back into its hole; its bottom stands at " + bottom
                        + ", the rim at " + rim);
                if (holdTick == LOOK_DOWN_TICKS) {
                    helper.assertTrue(clearedOnce[0], "The chunk should have come out of its hole");
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
        Vec3 aside = helper.absoluteVec(new Vec3(FLOOR_STRIP_X + 0.5, FLOOR_TOP, STAND_POS.getZ() + 1));
        player.lookAt(EntityAnchorArgument.Anchor.EYES, helper.absoluteVec(new Vec3(FAR_STRIP_X + 0.5, FLOOR_TOP,
                STAND_POS.getZ() + 1.5)));
        int lifted = HOLD_TICKS;
        int carried = lifted + CARRY_TICKS;
        int aimedBack = carried + CARRY_TICKS;
        for (int held = 1; held <= aimedBack; held++) {
            int holdTick = held;
            helper.runAfterDelay(held, () -> {
                if (holdTick == lifted) {
                    player.lookAt(EntityAnchorArgument.Anchor.EYES, aside);
                } else if (holdTick == carried) {
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

    /**
     * Until the hunk's bottom first clears the hole's rim it rises straight
     * up over the cells it left, never sideways through the hole's walls;
     * once clear it may come down toward a hold below the rim.
     *
     * @param helper      the gametest helper
     * @param clearedOnce in its first slot, whether the hunk has cleared the rim yet
     */
    private static void assertOverItsHoleUntilClear(GameTestHelper helper, boolean[] clearedOnce) {
        BlockHunk hunk = hunksIn(helper).getFirst();
        if (clearedOnce[0] || hunk.getBoundingBox().minY >= helper.absolutePos(new BlockPos(0, HOLE_TOP, 0)).getY()) {
            clearedOnce[0] = true;
            return;
        }
        Vec3 ground = Vec3.atBottomCenterOf(hunk.home());
        helper.assertTrue(Math.abs(hunk.getX() - ground.x) < OVER_THE_HOLE && Math.abs(hunk.getZ() - ground.z)
                < OVER_THE_HOLE, "Until it clears its hole the hunk should rise straight up over " + ground
                + ", stands at " + hunk.position());
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
