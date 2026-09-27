package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleCapacity;
import com.mercuriusxeno.goo.block.crucible.CrucibleMath;
import com.mercuriusxeno.goo.block.crucible.CrucibleShape;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gametests for crucible absorption and insertion paths.
 * Exercises CrucibleAbsorption (item entity intake) and
 * CrucibleInsertion (blob right-click insertion).
 */
public final class CrucibleTests {

    private static final BlockPos BE_POS = new BlockPos(1, 1, 1);
    /** Ticks a still item dropped at the basin center takes to land, rest and be consumed. */
    private static final int ABSORB_DELAY = 10;
    /** Heat granted to a test crucible: an hour of melting, so no test runs it cold. */
    private static final int TEST_HEAT_TICKS = 72_000;
    /** Ticks an item dropped over the mouth takes to fall to the floor and stop. */
    private static final int SETTLE_DELAY = 30;
    /** A drop height above the rim in test-relative Y. */
    private static final double MOUTH_DROP_Y = 2.3;
    /** An item entity's half-width, a quarter block wide. */
    private static final double ITEM_HALF_WIDTH = 0.125;
    /**
     * A ledge spot in test-relative X, the item's west face against the east rim wall;
     * the east side, since barriers bound the test area on the west.
     */
    private static final double LEDGE_X = 1.0 + CrucibleShape.COLLAR_MAX + ITEM_HALF_WIDTH;
    /** The ledge top in test-relative Y. */
    private static final double LEDGE_TOP_Y = 1.0 + CrucibleShape.LEDGE_Y;
    /** How far a resting item's feet may sit from the floor. */
    private static final double FLOOR_SLACK = 0.01;
    private static final String IN_FOOTPRINT = "item rests inside the basin footprint: ";
    private static final String ON_FLOOR = "item rests on the 8/16 basin floor: ";
    private static final String LEDGE_ITEM_STAYS = "ledge item stays";
    private static final String LEDGE_ITEM_WHOLE = "ledge item unshrunk";
    /** Ticks an item at rest on a wall top has to slide into the cavity. */
    private static final int SLIDE_BOUND = 40;
    /** A wall top's middle, three pixels in from the block's edge. */
    private static final double WALL_MIDDLE = 3.0 / 16.0;
    /** The wall tops in test-relative Y. */
    private static final double WALL_TOP_Y = 1.0 + CrucibleBasin.RIM_Y;
    /** An item at rest on the middle of each rim wall top: west, east, north, south. */
    private static final Vec3[] WALL_TOPS = {
        new Vec3(1.0 + WALL_MIDDLE, WALL_TOP_Y, CrucibleSpawns.BASIN_CENTER_XZ),
        new Vec3(2.0 - WALL_MIDDLE, WALL_TOP_Y, CrucibleSpawns.BASIN_CENTER_XZ),
        new Vec3(CrucibleSpawns.BASIN_CENTER_XZ, WALL_TOP_Y, 1.0 + WALL_MIDDLE),
        new Vec3(CrucibleSpawns.BASIN_CENTER_XZ, WALL_TOP_Y, 2.0 - WALL_MIDDLE),
    };
    private static final Item[] WALL_ITEMS = {Items.COBBLESTONE, Items.DIRT, Items.SAND, Items.GRAVEL};
    private static final String SLID_ITEM_STAYS = "slid item still exists: ";
    private static final String SLID_INTO_CAVITY = "wall-top item slid into the cavity: ";
    private static final String LEDGE_ITEM_OUTSIDE = "ledge item still outside the cavity: ";
    private static final String LEDGE_ITEM_TAKEN = "ledge item taken once lifted into the cavity";
    /** Ticks after a ledge item is spawned at which the lift has it in the air, still outside the cavity. */
    private static final int LIFT_CHECK_TICKS = 2;
    /** A throw's start in test-relative X, over the crucible's west ledge in the pull field. */
    private static final double THROW_START_X = 1.05;
    /** A throw's height in test-relative Y, inside the pull field over the rim. */
    private static final double THROW_START_Y = 2.2;
    /** A side throw's start in test-relative X, east of the crucible, out of the field's reach. */
    private static final double SIDE_THROW_START_X = 2.3;
    /** A side throw's height in test-relative Y, low on the body's side. */
    private static final double SIDE_THROW_START_Y = 1.3;
    /** A side throw's speed west, into the body's side. */
    private static final double SIDE_THROW_SPEED = 0.3;
    /** A throw's speed east, enough to cross the whole block in two ticks. */
    private static final double THROW_SPEED = 0.5;
    /** An off-center drop's X offset from the basin center, near the east wall. */
    private static final double OFF_CENTER_X = 0.1;
    /** An off-center drop's Z offset from the basin center, near the north wall. */
    private static final double OFF_CENTER_Z = -0.08;
    /** A drop height above the basin floor, clear of the goo surface below it. */
    private static final double DROP_ABOVE_KILL_BOX = 0.4;
    /** Reservoir goo that stands the surface a pixel and a half over the floor. */
    private static final int SURFACE_GOO = 16_000;
    /** Ticks an item dropped over the cavity takes to fall into the kill box. */
    private static final int KILL_BOX_DELAY = 8;
    private static final String FALLING_ITEM_STAYS = "item still exists while it falls toward the goo";
    private static final String TAKEN_AT_KILL_BOX = "item taken once it reached the goo surface";
    private static final String WAITS_UNCONSUMED = "cold crucible leaves the item";
    private static final String WAITS_UNSHRUNK = "waiting item unshrunk";
    private static final String WAITS_ON_FLOOR = "waiting item sank to the floor: ";
    private static final String WAITS_WHERE_IT_LANDED = "waiting item stays where it landed: ";
    private static final String SHOULD_HAVE_GOO = "Crucible reservoir should contain goo after blob insert";
    private static final String SHOULD_ABSORB = "Crucible should absorb the item entity";
    private static final String RESERVOIR_UNCHANGED = "reservoir unchanged";
    private static final int CAP = CrucibleCapacity.TYPE_CAPACITY;
    /** Cobblestone offered to a pool with room for two items and one mB short of a third. */
    private static final int COBBLE_OFFERED = 5;
    private static final int REFUSED_BLOB = 1_000;
    private static final int POOL_ROCK = 200_000_000;
    private static final int POOL_METAL = 100;
    private static final long FILL_PAST = 2_200_000_000L;
    /** Blob stack size offered to a full reservoir. */
    private static final int BLOBS_OFFERED = 2;
    /** Whole cobblestone the pool has room for. */
    private static final int ITEMS_THAT_FIT = 2;
    /** Types filled to the cap in the accounting test, rock and metal. */
    private static final long FULL_TYPES = 2L;
    private static final String ROCK_TO_CAP = "rock accepted to the cap";
    private static final String METAL_TO_CAP = "metal accepted to the cap";
    private static final String ROCK_PAST_CAP = "rock past the cap";
    private static final String METAL_PAST_CAP = "metal past the cap";
    private static final String BLOB_KEPT_IN = "blob kept in ";
    private static final String BLOB_STAYS = "blob entity stays on the ground";
    private static final String BLOB_COUNT_UNCHANGED = "blob count unchanged";
    private static final String UNFIT_ITEMS_STAY = "unfit items stay";
    private static final String POOL_TOOK_TWO = "pool took two items of ";
    private static final String CONTAINER_STAYS = "container stays on the ground";
    private static final String CONTAINER_UNTOUCHED = "container items untouched";
    private static final String POOL_UNCHANGED = "pool unchanged";
    private static final String MELTED_ITEM_STAYS = "melted item stays on the ground";
    private static final String HOLDS_PAST = "crucible holds past 2.2B: ";
    private static final String EVERY_MB_ACCOUNTED = "every mB offered is held or refused";
    private static final String FILL_ABOVE_ZERO = "surface fill above zero";
    private static final int FULL_STACK = 64;
    /** Whole blobs of room left under the cap in the fill-to-the-cap test. */
    private static final int BLOBS_ROOM = 3;
    private static final String RESERVOIR_TOOK_STACK = "reservoir took the stack's goo";
    private static final String STACK_SPENT = "stack spent";
    private static final String RESERVOIR_AT_CAP = "reservoir filled to the cap";
    private static final String UNFIT_BLOBS_STAY = "blobs that did not fit stay in hand";
    private static final String PUDDLE_SHORT_OF_WALLS = "drawn %s for %d mB melted, %d mB unmelted";
    /** Blaze goo stocked to buy heat: enough for every melt clock these tests run. */
    private static final int BLAZE_STOCK = 1_000;
    private static final double DROP_REACH = 2.0;
    private static final int STACK_OF_FOUR = 4;
    private static final int STACK_OF_THREE = 3;
    /** Unstable goo stocked beside the blaze: enough combo ticks for every clock these tests run. */
    private static final int UNSTABLE_STOCK = 1_000;
    private static final String WHOLE_ON_LAST_TICK = "items' goo whole in the reservoir on melt tick ";
    private static final String SHORT_BEFORE_LAST_TICK = "items' goo short of whole before the last turn";
    private static final String BROKEN_HALFWAY = "crucible broken halfway through the clock";
    private static final String PART_MELTED = "pool part melted at the break: ";
    private static final String ONE_MELTED_ITEM_DROPPED = "one partially melted item dropped";
    private static final String DROP_CARRIES_REMAINDER = "dropped item carries the unmelted remainder";
    private static final String SPARK_RESULT = "the crucible's answer to the flint and steel click";
    private static final String SPARK_HEAT = "heat ticks after the click";
    private static final String FLINT_DAMAGE = "flint and steel damage after the click";

    private CrucibleTests() {}

    /**
     * Right-click a crucible with a rock blob inserts goo into the reservoir.
     * Exercises CrucibleInteraction.tryInsertBlob -> CrucibleInsertion.insertGoo.
     *
     * @param helper the gametest helper
     */
    public static void blobInsertViaInteraction(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND,
            BlobStacks.createForOutput(GooTypes.ROCK, BlobStacks.MB_PER_BLOB));

        BlockPos abs = helper.absolutePos(BE_POS);
        BlockHitResult hit = new BlockHitResult(
            Vec3.atCenterOf(abs), Direction.UP, abs, false);
        helper.useBlock(BE_POS, player, hit);

        helper.assertFalse(crucible.reservoirHandler().isEmpty(), SHOULD_HAVE_GOO);
        helper.succeed();
    }

    /**
     * Dropping an item into a fueled crucible absorbs it via entityInside.
     * Exercises CrucibleAbsorption.tryAbsorbItem -> CrucibleInsertion.insertItem.
     * Requires fuel and the crucible must not be redstone-powered.
     *
     * @param helper the gametest helper
     */
    public static void itemEntityAbsorption(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        crucible.addHeat(TEST_HEAT_TICKS);

        CrucibleSpawns.spawnInBasin(helper, new ItemStack(Items.COBBLESTONE));

        helper.runAfterDelay(ABSORB_DELAY, () -> {
            helper.assertFalse(crucible.reservoirHandler().isEmpty(), SHOULD_ABSORB);
            helper.succeed();
        });
    }

    // -- The cavity (decision collision-is-the-drawn-cavity) --

    /**
     * An item dropped over the mouth of a cold crucible falls into the cavity and
     * rests on the drawn floor at 8/16, not on the old invisible floor at 13/16.
     *
     * @param helper the gametest helper
     */
    public static void droppedItemRestsOnBasinFloor(GameTestHelper helper) {
        placeCrucible(helper);
        ItemEntity dropped = CrucibleSpawns.spawnAt(helper, new ItemStack(Items.COBBLESTONE),
            new Vec3(CrucibleSpawns.BASIN_CENTER_XZ, MOUTH_DROP_Y, CrucibleSpawns.BASIN_CENTER_XZ));
        helper.runAfterDelay(SETTLE_DELAY, () -> {
            Vec3 rel = CrucibleSpawns.relativeToCrucible(helper, dropped);
            helper.assertTrue(rel.x >= CrucibleBasin.FOOTPRINT_MIN && rel.x <= CrucibleBasin.FOOTPRINT_MAX
                && rel.z >= CrucibleBasin.FOOTPRINT_MIN && rel.z <= CrucibleBasin.FOOTPRINT_MAX,
                IN_FOOTPRINT + rel);
            helper.assertTrue(Math.abs(rel.y - CrucibleBasin.FLOOR_Y) < FLOOR_SLACK, ON_FLOOR + rel);
            helper.succeed();
        });
    }

    /**
     * An item on the outer ledge of a heating crucible is whole while it is still outside
     * the cavity, and taken only once the lift has carried it in.
     *
     * @param helper the gametest helper
     */
    public static void ledgeItemTakenOnlyInsideTheCavity(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeFueledCrucible(helper);
        ItemEntity onLedge = spawnOnLedge(helper, new ItemStack(Items.COBBLESTONE, COBBLE_OFFERED));
        helper.runAfterDelay(LIFT_CHECK_TICKS, () -> {
            Vec3 rel = CrucibleSpawns.relativeToCrucible(helper, onLedge);
            helper.assertFalse(CrucibleBasin.holdsPoint(rel.x, rel.y, rel.z), LEDGE_ITEM_OUTSIDE + rel);
            helper.assertFalse(onLedge.isRemoved(), LEDGE_ITEM_STAYS);
            helper.assertValueEqual(COBBLE_OFFERED, onLedge.getItem().getCount(), LEDGE_ITEM_WHOLE);
            helper.assertTrue(crucible.reservoirHandler().isEmpty(), RESERVOIR_UNCHANGED);
        });
        helper.runAfterDelay(SLIDE_BOUND, () -> {
            helper.assertTrue(onLedge.isRemoved(), LEDGE_ITEM_TAKEN);
            helper.succeed();
        });
    }

    /**
     * @param helper the gametest helper
     * @param stack  the stack the entity carries
     * @return a still item grounded on the ledge, its west face against the east rim wall
     */
    private static ItemEntity spawnOnLedge(GameTestHelper helper, ItemStack stack) {
        return CrucibleSpawns.spawnAt(helper, stack, new Vec3(LEDGE_X, LEDGE_TOP_Y, CrucibleSpawns.BASIN_CENTER_XZ));
    }

    // -- Sliding in (decision rim-and-mouth-items-slide-inward) --

    /**
     * An item at rest on each of the four rim wall tops of a cold crucible ends
     * inside the cavity footprint within the slide bound. Each wall carries a
     * different item so the four never merge on meeting at the center.
     *
     * @param helper the gametest helper
     */
    public static void wallTopItemsSlideIntoTheCavity(GameTestHelper helper) {
        placeCrucible(helper);
        List<ItemEntity> onWalls = new ArrayList<>();
        for (int wall = 0; wall < WALL_TOPS.length; wall++) {
            onWalls.add(CrucibleSpawns.spawnAt(helper, new ItemStack(WALL_ITEMS[wall]), WALL_TOPS[wall]));
        }
        helper.runAfterDelay(SLIDE_BOUND, () -> {
            for (ItemEntity item : onWalls) {
                Vec3 rel = CrucibleSpawns.relativeToCrucible(helper, item);
                helper.assertFalse(item.isRemoved(), SLID_ITEM_STAYS + item.getItem());
                helper.assertTrue(CrucibleBasin.holdsPoint(rel.x, rel.y, rel.z), SLID_INTO_CAVITY + rel);
            }
            helper.succeed();
        });
    }

    /**
     * An item grounded on the outer ledge of a cold crucible is lifted up and over the
     * rim wall into the cavity, rather than catching on the collar.
     *
     * @param helper the gametest helper
     */
    public static void ledgeItemLiftedIntoTheCavity(GameTestHelper helper) {
        placeCrucible(helper);
        ItemEntity onLedge = spawnOnLedge(helper, new ItemStack(Items.COBBLESTONE));
        helper.runAfterDelay(SLIDE_BOUND, () -> assertInCavity(helper, onLedge));
    }

    /**
     * An item thrown across the top of a cold crucible, fast enough to sail over it,
     * is caught by the pull field and drops into the cavity.
     *
     * @param helper the gametest helper
     */
    public static void thrownItemCaughtByTheField(GameTestHelper helper) {
        placeCrucible(helper);
        ItemEntity thrown = CrucibleSpawns.spawnAt(helper, new ItemStack(Items.COBBLESTONE),
            new Vec3(THROW_START_X, THROW_START_Y, CrucibleSpawns.BASIN_CENTER_XZ));
        thrown.setDeltaMovement(THROW_SPEED, 0.0, 0.0);
        helper.runAfterDelay(SLIDE_BOUND, () -> assertInCavity(helper, thrown));
    }

    /**
     * An item thrown low into the side of a cold crucible is lifted up and over the
     * collar into the cavity rather than sliding down beside the block. Thrown
     * from the east, since barriers bound the test area on the west.
     *
     * @param helper the gametest helper
     */
    public static void sideHitItemLiftedIntoTheCavity(GameTestHelper helper) {
        placeCrucible(helper);
        ItemEntity thrown = CrucibleSpawns.spawnAt(helper, new ItemStack(Items.COBBLESTONE),
            new Vec3(SIDE_THROW_START_X, SIDE_THROW_START_Y, CrucibleSpawns.BASIN_CENTER_XZ));
        thrown.setDeltaMovement(-SIDE_THROW_SPEED, 0.0, 0.0);
        helper.runAfterDelay(SLIDE_BOUND, () -> assertInCavity(helper, thrown));
    }

    /**
     * Asserts an item still exists and lies in the cavity, then succeeds.
     *
     * @param helper the gametest helper
     * @param item   the item entity
     */
    private static void assertInCavity(GameTestHelper helper, ItemEntity item) {
        Vec3 rel = CrucibleSpawns.relativeToCrucible(helper, item);
        helper.assertFalse(item.isRemoved(), SLID_ITEM_STAYS + item.getItem());
        helper.assertTrue(CrucibleBasin.holdsPoint(rel.x, rel.y, rel.z), SLID_INTO_CAVITY + rel);
        helper.succeed();
    }

    // -- The kill box (decision consume-at-rest-in-place, as the operator reframed it) --

    /**
     * An item dropped off-center over a heating crucible holding goo is taken the tick it
     * reaches the goo surface, wherever it lands in the footprint, with no drift or rest first.
     *
     * @param helper the gametest helper
     */
    public static void droppedItemTakenAtTheKillBox(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeFueledCrucible(helper);
        crucible.insertGoo(GooTypes.ROCK, SURFACE_GOO);
        ItemEntity item = spawnOffCenter(helper, CrucibleSpawns.BASIN_FLOOR_Y + DROP_ABOVE_KILL_BOX);
        helper.runAfterDelay(1, () -> helper.assertFalse(item.isRemoved(), FALLING_ITEM_STAYS));
        helper.runAfterDelay(KILL_BOX_DELAY, () -> {
            helper.assertTrue(item.isRemoved(), TAKEN_AT_KILL_BOX);
            helper.assertFalse(crucible.getMeltingItem().isEmpty(), SHOULD_ABSORB);
            helper.succeed();
        });
    }

    /**
     * An item dropped off-center into a cold crucible holding goo sinks to the basin floor
     * and waits there whole, where it landed.
     *
     * @param helper the gametest helper
     */
    public static void coldCrucibleItemWaitsOnTheFloor(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeCrucible(helper);
        crucible.insertGoo(GooTypes.ROCK, SURFACE_GOO);
        ItemEntity item = spawnOffCenter(helper, CrucibleSpawns.BASIN_FLOOR_Y + DROP_ABOVE_KILL_BOX);
        helper.runAfterDelay(KILL_BOX_DELAY, () -> {
            helper.assertFalse(item.isRemoved(), WAITS_UNCONSUMED);
            helper.assertValueEqual(1, item.getItem().getCount(), WAITS_UNSHRUNK);
            Vec3 rel = CrucibleSpawns.relativeToCrucible(helper, item);
            helper.assertTrue(Math.abs(rel.y - CrucibleBasin.FLOOR_Y) < FLOOR_SLACK, WAITS_ON_FLOOR + rel);
            helper.assertTrue(Math.abs(rel.x - (CrucibleSpawns.BASIN_CENTER_XZ - 1.0 + OFF_CENTER_X)) < FLOOR_SLACK,
                WAITS_WHERE_IT_LANDED + rel);
            helper.succeed();
        });
    }

    /**
     * @param helper the gametest helper
     * @param y      the test-relative drop height
     * @return a still cobblestone entity dropped over the cavity, off the center on both axes
     */
    private static ItemEntity spawnOffCenter(GameTestHelper helper, double y) {
        return CrucibleSpawns.spawnAt(helper, new ItemStack(Items.COBBLESTONE), new Vec3(
            CrucibleSpawns.BASIN_CENTER_XZ + OFF_CENTER_X, y, CrucibleSpawns.BASIN_CENTER_XZ + OFF_CENTER_Z));
    }

    // -- At the cap (decision crucible-refuses-past-two-billion) --

    /**
     * The reservoir takes 2B of each of two types and answers 0 for one mB more of either.
     *
     * @param helper the gametest helper
     */
    public static void reservoirCapsEachType(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeCrucible(helper);
        helper.assertValueEqual(CAP, crucible.insertGoo(GooTypes.ROCK, CAP), ROCK_TO_CAP);
        helper.assertValueEqual(CAP, crucible.insertGoo(GooTypes.METAL, CAP), METAL_TO_CAP);
        helper.assertValueEqual(0, crucible.insertGoo(GooTypes.ROCK, 1), ROCK_PAST_CAP);
        helper.assertValueEqual(0, crucible.insertGoo(GooTypes.METAL, 1), METAL_PAST_CAP);
        helper.succeed();
    }

    /**
     * A blob in hand stays in hand, survival and creative alike, when its type is full.
     *
     * @param helper the gametest helper
     */
    public static void blobInHandRefusedAtCap(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeCrucible(helper);
        crucible.insertGoo(GooTypes.ROCK, CAP);
        for (GameType mode : new GameType[] {GameType.SURVIVAL, GameType.CREATIVE}) {
            Player player = helper.makeMockPlayer(mode);
            player.setItemInHand(InteractionHand.MAIN_HAND, BlobStacks.createForOutput(GooTypes.ROCK, BlobStacks.MB_PER_BLOB));
            BlockPos abs = helper.absolutePos(BE_POS);
            helper.useBlock(BE_POS, player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
            helper.assertValueEqual(BlobStacks.MB_PER_BLOB, BlobStacks.volumeOf(player.getMainHandItem()), BLOB_KEPT_IN + mode);
        }
        helper.assertValueEqual(CAP, crucible.getReservoir().getVolume(GooTypes.ROCK), RESERVOIR_UNCHANGED);
        helper.succeed();
    }

    /**
     * A 64-blob omniblob right-clicked on an empty crucible puts in exactly the goo
     * it took (decision diagnose-then-fix-crucible-blob-duplication).
     *
     * @param helper the gametest helper
     */
    public static void blobStackConsumedWhole(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeCrucible(helper);
        Player player = clickWithBlobs(helper, FULL_STACK);
        helper.assertValueEqual(FULL_STACK * BlobStacks.MB_PER_BLOB,
            crucible.getReservoir().getVolume(GooTypes.ROCK), RESERVOIR_TOOK_STACK);
        helper.assertTrue(player.getMainHandItem().isEmpty(), STACK_SPENT);
        helper.succeed();
    }

    /**
     * A 64-blob omniblob offered to a type a few blobs short of the cap fills it to the
     * cap and leaves the volume that did not fit in hand.
     *
     * @param helper the gametest helper
     */
    public static void blobStackFillsToTheCap(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeCrucible(helper);
        crucible.insertGoo(GooTypes.ROCK, CAP - BLOBS_ROOM * BlobStacks.MB_PER_BLOB);
        Player player = clickWithBlobs(helper, FULL_STACK);
        helper.assertValueEqual(CAP, crucible.getReservoir().getVolume(GooTypes.ROCK), RESERVOIR_AT_CAP);
        helper.assertValueEqual((FULL_STACK - BLOBS_ROOM) * BlobStacks.MB_PER_BLOB,
            BlobStacks.volumeOf(player.getMainHandItem()), UNFIT_BLOBS_STAY);
        helper.succeed();
    }

    /**
     * Right-clicks the crucible with a survival player holding rock blobs.
     *
     * @param helper the gametest helper
     * @param count  the blobs held
     * @return the player, holding what the click left
     */
    private static Player clickWithBlobs(GameTestHelper helper, int count) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, BlobStacks.createForOutput(GooTypes.ROCK, count * BlobStacks.MB_PER_BLOB));
        BlockPos abs = helper.absolutePos(BE_POS);
        helper.useBlock(BE_POS, player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
        return player;
    }

    /**
     * A blob item entity stays on the ground when its type is full.
     *
     * @param helper the gametest helper
     */
    public static void blobEntityRefusedAtCap(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeFueledCrucible(helper);
        crucible.insertGoo(GooTypes.ROCK, CAP);
        ItemEntity blob = spawnInBasin(helper, BlobStacks.createForOutput(GooTypes.ROCK, BLOBS_OFFERED * BlobStacks.MB_PER_BLOB));
        helper.runAfterDelay(ABSORB_DELAY, () -> {
            helper.assertFalse(blob.isRemoved(), BLOB_STAYS);
            helper.assertValueEqual(BLOBS_OFFERED * BlobStacks.MB_PER_BLOB, BlobStacks.volumeOf(blob.getItem()),
                BLOB_COUNT_UNCHANGED);
            helper.assertValueEqual(CAP, crucible.getReservoir().getVolume(GooTypes.ROCK), RESERVOIR_UNCHANGED);
            helper.succeed();
        });
    }

    /**
     * A stack of cobblestone melts in only the whole items that fit the pool;
     * the rest stay as the item entity. The reservoir is full of the same
     * types, so melting leaves the pool as it stands.
     *
     * @param helper the gametest helper
     */
    public static void itemStackMeltsWholeItemsThatFit(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeFueledCrucible(helper);
        GooContents perItem = Goo.GOO_VALUES.lookup(BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE)).toGooContents();
        Map<ResourceKey<GooTypeDefinition>, Integer> room = new HashMap<>();
        perItem.getAll().forEach((type, amount) -> {
            crucible.insertGoo(type, CAP);
            room.put(type, CAP - (ITEMS_THAT_FIT * amount + amount - 1));
        });
        GooContents startPool = new GooContents(room);
        spawnInBasin(helper, PartiallyMeltedItem.createWith(startPool));
        helper.runAfterDelay(ABSORB_DELAY, () -> {
            ItemEntity cobble = spawnInBasin(helper, new ItemStack(Items.COBBLESTONE, COBBLE_OFFERED));
            helper.runAfterDelay(ABSORB_DELAY, () -> {
                helper.assertValueEqual(COBBLE_OFFERED - ITEMS_THAT_FIT, cobble.getItem().getCount(), UNFIT_ITEMS_STAY);
                GooContents pool = PartiallyMeltedItem.getContents(crucible.getMeltingItem());
                perItem.getAll().forEach((type, amount) -> helper.assertValueEqual(
                    startPool.getVolume(type) + ITEMS_THAT_FIT * amount, pool.getVolume(type), POOL_TOOK_TWO + type));
                helper.succeed();
            });
        });
    }

    /**
     * A shulker box whose cobblestone does not fit the full pool is refused whole, items untouched.
     *
     * @param helper the gametest helper
     */
    public static void containerRefusedWholeAtCap(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeFueledCrucible(helper);
        GooContents fullPool = fillPoolAndReservoirForCobblestone(helper, crucible);
        ItemStack box = new ItemStack(Items.SHULKER_BOX);
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(
            List.of(new ItemStack(Items.COBBLESTONE, COBBLE_OFFERED))));
        helper.runAfterDelay(ABSORB_DELAY, () -> {
            ItemEntity boxEntity = spawnInBasin(helper, box);
            helper.runAfterDelay(ABSORB_DELAY, () -> {
                helper.assertFalse(boxEntity.isRemoved(), CONTAINER_STAYS);
                ItemContainerContents inside = boxEntity.getItem().get(DataComponents.CONTAINER);
                helper.assertValueEqual(COBBLE_OFFERED, inside.nonEmptyItemCopyStream()
                    .mapToInt(ItemStack::getCount).sum(), CONTAINER_UNTOUCHED);
                helper.assertValueEqual(fullPool, PartiallyMeltedItem.getContents(crucible.getMeltingItem()),
                    POOL_UNCHANGED);
                helper.succeed();
            });
        });
    }

    /**
     * A dropped partially melted item whose goo does not fit the full pool stays on the ground.
     *
     * @param helper the gametest helper
     */
    public static void meltedItemRefusedWholeAtCap(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeFueledCrucible(helper);
        GooContents fullPool = fillPoolAndReservoirForCobblestone(helper, crucible);
        ResourceKey<GooTypeDefinition> type = fullPool.largestType();
        helper.runAfterDelay(ABSORB_DELAY, () -> {
            ItemEntity dropped = spawnInBasin(helper,
                PartiallyMeltedItem.createWith(GooContents.EMPTY.withAdded(type, 1)));
            helper.runAfterDelay(ABSORB_DELAY, () -> {
                helper.assertFalse(dropped.isRemoved(), MELTED_ITEM_STAYS);
                helper.assertValueEqual(fullPool, PartiallyMeltedItem.getContents(crucible.getMeltingItem()),
                    POOL_UNCHANGED);
                helper.succeed();
            });
        });
    }

    /**
     * Fills a crucible past 2.2B across rock and metal with a melting item in
     * the pool and a blob refused at the cap; what it holds plus what it
     * refused equals what was offered, and the surface fill reads above zero.
     *
     * @param helper the gametest helper
     */
    public static void fillPastTheCapAccountsForEveryMb(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeFueledCrucible(helper);
        GooContents poolOffered = new GooContents(Map.of(GooTypes.ROCK, POOL_ROCK, GooTypes.METAL, POOL_METAL));
        long offered = FULL_TYPES * CAP + REFUSED_BLOB + poolOffered.totalVolume();
        long accepted = (long) crucible.insertGoo(GooTypes.ROCK, CAP)
            + crucible.insertGoo(GooTypes.METAL, CAP)
            + crucible.insertGoo(GooTypes.ROCK, REFUSED_BLOB);
        long refused = FULL_TYPES * CAP + REFUSED_BLOB - accepted;
        spawnInBasin(helper, PartiallyMeltedItem.createWith(poolOffered));
        helper.runAfterDelay(ABSORB_DELAY, () -> {
            long held = CrucibleBasin.heldVolume(crucible.getPoolVolume(), crucible.getReservoir().totalVolume());
            helper.assertTrue(held > FILL_PAST, HOLDS_PAST + held);
            helper.assertValueEqual(offered, held + refused, EVERY_MB_ACCOUNTED);
            helper.assertTrue(CrucibleBasin.fillFraction(held) > 0f, FILL_ABOVE_ZERO);
            helper.succeed();
        });
    }

    /**
     * One cobblestone melting into an empty crucible draws a puddle short of the
     * walls a few ticks in, not the whole floor (decision puddle-touches-walls-at-a-thousand).
     *
     * @param helper the gametest helper
     */
    public static void firstMeltTicksDrawAPuddle(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeFueledCrucible(helper);
        spawnInBasin(helper, new ItemStack(Items.COBBLESTONE));
        helper.runAfterDelay(ABSORB_DELAY, () -> {
            long melted = crucible.getSurfaceVolume();
            CrucibleBasin.PuddleFootprint drawn = CrucibleBasin.footprintForVolume(melted);
            helper.assertTrue(melted > 0 && drawn.max() < CrucibleBasin.FOOTPRINT_MAX,
                String.format(PUDDLE_SHORT_OF_WALLS, drawn, melted, crucible.getPoolVolume()));
            helper.succeed();
        });
    }

    // -- Melt clock (decision melt-time-is-mb-to-a-power) --

    /**
     * One cobblestone alone in a crucible burning blaze goo reaches the reservoir whole on
     * melt tick ceil(V ^ 0.75), V being its mB, and not the tick before.
     *
     * @param helper the gametest helper
     */
    public static void itemMeltsOnItsClock(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeBlazeStockedCrucible(helper);
        GooContents perItem = cobblestoneValue();
        long clock = CrucibleMath.meltTicks(perItem.totalVolume(), GooConfig.BLAZE_MELT_EXPONENT.get());
        Map<Long, Long> meltedByTick = new HashMap<>();
        spawnInBasin(helper, new ItemStack(Items.COBBLESTONE));
        helper.onEachTick(() -> meltedByTick.put(meltTicksBurned(crucible), itemGooIn(crucible, perItem)));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(perItem.totalVolume(), meltedByTick.get(clock), WHOLE_ON_LAST_TICK + clock);
            helper.assertTrue(meltedByTick.get(clock - 1) < perItem.totalVolume(), SHORT_BEFORE_LAST_TICK);
        });
    }

    /**
     * A stack of four cobblestone inserted at once is four items each on its own clock, a lone
     * blaze fuel taking them in turn: the reservoir holds the stack's whole value on melt tick
     * 4 x ceil(V ^ 0.75) and not four ticks before (decision lone-fuel-advances-one-item).
     *
     * @param helper the gametest helper
     */
    public static void stackMeltsItemByItemInTurn(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeBlazeStockedCrucible(helper);
        GooContents perItem = cobblestoneValue();
        long stackVolume = perItem.totalVolume() * STACK_OF_FOUR;
        long clock = STACK_OF_FOUR * CrucibleMath.meltTicks(perItem.totalVolume(), GooConfig.BLAZE_MELT_EXPONENT.get());
        Map<Long, Long> meltedByTick = new HashMap<>();
        spawnInBasin(helper, new ItemStack(Items.COBBLESTONE, STACK_OF_FOUR));
        helper.onEachTick(() -> meltedByTick.put(meltTicksBurned(crucible), itemGooIn(crucible, perItem)));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(stackVolume, meltedByTick.get(clock), WHOLE_ON_LAST_TICK + clock);
            helper.assertTrue(meltedByTick.get(clock - STACK_OF_FOUR) < stackVolume, SHORT_BEFORE_LAST_TICK);
        });
    }

    /**
     * Three cobblestone in a crucible stocked with blaze and unstable goo all finish together
     * on unstable's clock: the reservoir holds their whole value on melt tick ceil(V ^ 0.5)
     * and not the tick before (decision combo-advances-every-item).
     *
     * @param helper the gametest helper
     */
    public static void comboMeltsEveryItemAtOnce(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeBlazeStockedCrucible(helper);
        crucible.insertGoo(GooTypes.UNSTABLE, UNSTABLE_STOCK);
        GooContents perItem = cobblestoneValue();
        long stackVolume = perItem.totalVolume() * STACK_OF_THREE;
        long clock = CrucibleMath.meltTicks(perItem.totalVolume(), GooConfig.UNSTABLE_MELT_EXPONENT.get());
        Map<Long, Long> meltedByTick = new HashMap<>();
        spawnInBasin(helper, new ItemStack(Items.COBBLESTONE, STACK_OF_THREE));
        helper.onEachTick(() -> meltedByTick.put(comboTicksBurned(crucible), itemGooIn(crucible, perItem)));
        helper.succeedWhen(() -> {
            helper.assertValueEqual(stackVolume, meltedByTick.get(clock), WHOLE_ON_LAST_TICK + clock);
            helper.assertTrue(meltedByTick.get(clock - 1) < stackVolume, SHORT_BEFORE_LAST_TICK);
        });
    }

    /**
     * A crucible broken halfway through a cobblestone's clock drops a partially melted item
     * carrying the goo the reservoir had not yet taken.
     *
     * @param helper the gametest helper
     */
    public static void brokenMidMeltDropsTheRemainder(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeBlazeStockedCrucible(helper);
        GooContents perItem = cobblestoneValue();
        long halfway = CrucibleMath.meltTicks(perItem.totalVolume(), GooConfig.BLAZE_MELT_EXPONENT.get()) / 2;
        List<GooContents> unmelted = new ArrayList<>();
        spawnInBasin(helper, new ItemStack(Items.COBBLESTONE));
        helper.onEachTick(() -> {
            if (unmelted.isEmpty() && meltTicksBurned(crucible) >= halfway) {
                unmelted.add(PartiallyMeltedItem.getContents(crucible.getMeltingItem()));
                survivalPlayerBreaks(helper);
            }
        });
        helper.succeedWhen(() -> {
            helper.assertFalse(unmelted.isEmpty(), BROKEN_HALFWAY);
            GooContents remainder = unmelted.getFirst();
            helper.assertTrue(remainder.totalVolume() > 0 && remainder.totalVolume() < perItem.totalVolume(),
                PART_MELTED + remainder);
            List<ItemEntity> dropped = helper.getEntities(EntityType.ITEM, BE_POS, DROP_REACH).stream()
                .filter(entity -> entity.getItem().is(GooItems.PARTIALLY_MELTED_ITEM.get())).toList();
            helper.assertValueEqual(1, dropped.size(), ONE_MELTED_ITEM_DROPPED);
            helper.assertValueEqual(remainder, PartiallyMeltedItem.getContents(dropped.getFirst().getItem()),
                DROP_CARRIES_REMAINDER);
        });
    }

    /**
     * Places a crucible whose reservoir holds blaze goo to buy heat with.
     *
     * @param helper the gametest helper
     * @return the crucible block entity
     */
    private static CrucibleBlockEntity placeBlazeStockedCrucible(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeCrucible(helper);
        crucible.insertGoo(GooTypes.BLAZE, BLAZE_STOCK);
        return crucible;
    }

    /**
     * Returns one cobblestone's goo value.
     *
     * @return the goo one cobblestone carries
     */
    private static GooContents cobblestoneValue() {
        return Goo.GOO_VALUES.lookup(BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE)).toGooContents();
    }

    /**
     * Returns the melt ticks a blaze-stocked crucible has burned: the heat its spent blaze bought, less what is left.
     *
     * @param crucible the crucible block entity
     * @return the melt ticks burned
     */
    private static long meltTicksBurned(CrucibleBlockEntity crucible) {
        long spent = BLAZE_STOCK - crucible.getReservoir().getVolume(GooTypes.BLAZE);
        return spent * GooConfig.BLAZE_TICKS_PER_MB.get() - crucible.heatTicks();
    }

    /**
     * Returns the combo ticks a crucible stocked with both fuels has burned, each taking the drain of unstable.
     *
     * @param crucible the crucible block entity
     * @return the combo ticks burned
     */
    private static long comboTicksBurned(CrucibleBlockEntity crucible) {
        long spent = UNSTABLE_STOCK - crucible.getReservoir().getVolume(GooTypes.UNSTABLE);
        return spent / GooConfig.COMBO_DRAIN_PER_TICK.get();
    }

    /**
     * Returns the reservoir's mB of the types an item carries.
     *
     * @param crucible the crucible block entity
     * @param perItem  the goo one item carries
     * @return the reservoir's volume of those types
     */
    private static long itemGooIn(CrucibleBlockEntity crucible, GooContents perItem) {
        GooContents reservoir = crucible.getReservoir();
        return perItem.getAll().keySet().stream().mapToLong(reservoir::getVolume).sum();
    }

    /**
     * Breaks the crucible the way a survival player's break runs: the block's
     * {@code playerWillDestroy}, which drops its contents, then the removal.
     *
     * @param helper the gametest helper
     */
    private static void survivalPlayerBreaks(GameTestHelper helper) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockPos pos = helper.absolutePos(BE_POS);
        BlockState state = helper.getLevel().getBlockState(pos);
        state.getBlock().playerWillDestroy(helper.getLevel(), pos, state, player);
        helper.getLevel().destroyBlock(pos, true, player);
    }

    // -- The spark gate (decision spark-gate-consumes-the-click) --

    /**
     * A flint and steel click on a crucible holding blaze goo is consumed: no heat, no
     * durability, no fire beside the crucible.
     *
     * @param helper the gametest helper
     */
    public static void sparkRefusedOnBlazeGoo(GameTestHelper helper) {
        placeCrucible(helper).insertGoo(GooTypes.BLAZE, BlobStacks.MB_PER_BLOB);
        assertSparkRefused(helper);
    }

    /**
     * A flint and steel click on a crucible holding heat ticks and no goo is consumed: no
     * heat, no durability, no fire beside the crucible.
     *
     * @param helper the gametest helper
     */
    public static void sparkRefusedOnHeatTicks(GameTestHelper helper) {
        placeCrucible(helper).addHeat(TEST_HEAT_TICKS);
        assertSparkRefused(helper);
    }

    /**
     * A flint and steel click on a cold, empty crucible sparks it for the spark's heat at
     * one durability.
     *
     * @param helper the gametest helper
     */
    public static void sparkLightsColdEmptyCrucible(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeCrucible(helper);
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
        helper.assertValueEqual(InteractionResult.SUCCESS, clickSideWith(helper, flint), SPARK_RESULT);
        helper.assertValueEqual(GooConfig.SPARK_HEAT_TICKS.get(), crucible.heatTicks(), SPARK_HEAT);
        helper.assertValueEqual(1, flint.getDamageValue(), FLINT_DAMAGE);
        helper.succeed();
    }

    private static void assertSparkRefused(GameTestHelper helper) {
        CrucibleBlockEntity crucible = helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
        int heatBefore = crucible.heatTicks();
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
        helper.assertValueEqual(InteractionResult.CONSUME, clickSideWith(helper, flint), SPARK_RESULT);
        helper.assertValueEqual(heatBefore, crucible.heatTicks(), SPARK_HEAT);
        helper.assertValueEqual(0, flint.getDamageValue(), FLINT_DAMAGE);
        for (Direction side : Direction.values()) {
            helper.assertBlockNotPresent(Blocks.FIRE, BE_POS.relative(side));
        }
        helper.succeed();
    }

    /**
     * Right-clicks the crucible's north face with a survival player holding the stack, the
     * way a server player's click runs: the block first, then the item when the block
     * passes. A stone floor under the north neighbor lets a fallen-through spark place fire.
     *
     * @param helper the gametest helper
     * @param stack  the held stack
     * @return the block's answer to the click
     */
    private static InteractionResult clickSideWith(GameTestHelper helper, ItemStack stack) {
        helper.setBlock(BE_POS.north().below(), Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos abs = helper.absolutePos(BE_POS);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.NORTH, abs, false);
        InteractionResult answer = helper.getBlockState(BE_POS)
            .useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        if (!answer.consumesAction()) {
            stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
        }
        return answer;
    }

    private static CrucibleBlockEntity placeCrucible(GameTestHelper helper) {
        helper.setBlock(BE_POS, GooBlocks.CRUCIBLE.get());
        return helper.getBlockEntity(BE_POS, CrucibleBlockEntity.class);
    }

    private static CrucibleBlockEntity placeFueledCrucible(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeCrucible(helper);
        crucible.addHeat(TEST_HEAT_TICKS);
        return crucible;
    }

    /**
     * Fills the reservoir and the pool to the cap for every type cobblestone carries.
     *
     * @param helper   the gametest helper
     * @param crucible the crucible block entity
     * @return the pool contents spawned into the basin
     */
    private static GooContents fillPoolAndReservoirForCobblestone(GameTestHelper helper, CrucibleBlockEntity crucible) {
        GooContents perItem = Goo.GOO_VALUES.lookup(BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE)).toGooContents();
        Map<ResourceKey<GooTypeDefinition>, Integer> full = new HashMap<>();
        perItem.getAll().keySet().forEach(type -> {
            crucible.insertGoo(type, CAP);
            full.put(type, CAP);
        });
        GooContents fullPool = new GooContents(full);
        spawnInBasin(helper, PartiallyMeltedItem.createWith(fullPool));
        return fullPool;
    }

    private static ItemEntity spawnInBasin(GameTestHelper helper, ItemStack stack) {
        return CrucibleSpawns.spawnInBasin(helper, stack);
    }
}
