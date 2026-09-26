package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleCapacity;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.PartiallyMeltedItem;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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
    private static final int ABSORB_DELAY = 5;
    /** Heat granted to a test crucible: an hour of melting, so no test runs it cold. */
    private static final int TEST_HEAT_TICKS = 72_000;
    /** X/Z center of the crucible basin in test-relative coords. */
    private static final float BASIN_CENTER_XZ = 1.5f;
    /** Y position just above the crucible body surface (13/16 + block y=1). */
    private static final float BASIN_SURFACE_Y = 1.85f;
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
            BlobStacks.createBlobStack(GooTypes.ROCK, 1));

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

        // Spawn inside the basin (center of block, just above the body surface)
        helper.spawnItem(Items.COBBLESTONE, BASIN_CENTER_XZ, BASIN_SURFACE_Y, BASIN_CENTER_XZ);

        helper.runAfterDelay(ABSORB_DELAY, () -> {
            helper.assertFalse(crucible.reservoirHandler().isEmpty(), SHOULD_ABSORB);
            helper.succeed();
        });
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
            player.setItemInHand(InteractionHand.MAIN_HAND, BlobStacks.createBlobStack(GooTypes.ROCK, 1));
            BlockPos abs = helper.absolutePos(BE_POS);
            helper.useBlock(BE_POS, player, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
            helper.assertValueEqual(1, player.getMainHandItem().getCount(), BLOB_KEPT_IN + mode);
        }
        helper.assertValueEqual(CAP, crucible.getReservoir().getVolume(GooTypes.ROCK), RESERVOIR_UNCHANGED);
        helper.succeed();
    }

    /**
     * A full blob stack right-clicked on an empty crucible puts in exactly the goo of
     * the blobs it took (decision diagnose-then-fix-crucible-blob-duplication).
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
     * A blob stack offered to a type a few blobs short of the cap fills it to the cap
     * and leaves the blobs that did not fit in hand.
     *
     * @param helper the gametest helper
     */
    public static void blobStackFillsToTheCap(GameTestHelper helper) {
        CrucibleBlockEntity crucible = placeCrucible(helper);
        crucible.insertGoo(GooTypes.ROCK, CAP - BLOBS_ROOM * BlobStacks.MB_PER_BLOB);
        Player player = clickWithBlobs(helper, FULL_STACK);
        helper.assertValueEqual(CAP, crucible.getReservoir().getVolume(GooTypes.ROCK), RESERVOIR_AT_CAP);
        helper.assertValueEqual(FULL_STACK - BLOBS_ROOM, player.getMainHandItem().getCount(), UNFIT_BLOBS_STAY);
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
        player.setItemInHand(InteractionHand.MAIN_HAND, BlobStacks.createBlobStack(GooTypes.ROCK, count));
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
        ItemEntity blob = spawnInBasin(helper, BlobStacks.createBlobStack(GooTypes.ROCK, BLOBS_OFFERED));
        helper.runAfterDelay(ABSORB_DELAY, () -> {
            helper.assertFalse(blob.isRemoved(), BLOB_STAYS);
            helper.assertValueEqual(BLOBS_OFFERED, blob.getItem().getCount(), BLOB_COUNT_UNCHANGED);
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

    /**
     * Spawns a still item entity inside the basin.
     *
     * @param helper the gametest helper
     * @param stack  the stack the entity carries
     * @return the spawned entity
     */
    private static ItemEntity spawnInBasin(GameTestHelper helper, ItemStack stack) {
        Vec3 at = helper.absoluteVec(new Vec3(BASIN_CENTER_XZ, BASIN_SURFACE_Y, BASIN_CENTER_XZ));
        ItemEntity entity = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, stack);
        entity.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(entity);
        return entity;
    }
}
