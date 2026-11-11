package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.hole.HoleOpening;
import com.mercuriusxeno.goo.ability.hole.PortableHole;
import com.mercuriusxeno.goo.ability.hole.PortableHoles;
import com.mercuriusxeno.goo.ability.pocket.PocketShelves;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Gametests for Void's hole, an in-world portable hole: cast on a block's top
 * face it opens there and swallows the items dropped into its mouth onto the
 * caster's shelf for a fixed cost and no void reserved; cast on a bottom face
 * it pours the shelf out beneath; one stands per player, it closes after 600
 * ticks, and the owner leaving its dimension dismisses it.
 * decision hole-is-an-in-world-portable-hole-for-items
 */
@RegistersGameTests
public final class HoleTests {

    private static final String SWALLOWS_A_DROPPED_ITEM = "hole_swallows_a_dropped_item";
    private static final String POURS_ITS_SHELF_FROM_A_CEILING = "hole_pours_its_shelf_from_a_ceiling";
    private static final String STANDS_ONE_AT_A_TIME = "hole_stands_one_at_a_time";
    private static final String CLOSES_AFTER_ITS_LIFETIME = "hole_closes_after_its_lifetime";
    private static final String DISMISSED_BY_LEAVING_ITS_DIMENSION = "hole_dismissed_by_leaving_its_dimension";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SWALLOWS_A_DROPPED_ITEM, HoleTests::swallowsADroppedItem);
        GooTestFunctions.registerTest(registrar, POURS_ITS_SHELF_FROM_A_CEILING, HoleTests::poursItsShelfFromACeiling);
        GooTestFunctions.registerTest(registrar, STANDS_ONE_AT_A_TIME, HoleTests::standsOneAtATime);
        GooTestFunctions.registerTest(registrar, CLOSES_AFTER_ITS_LIFETIME, HoleTests::closesAfterItsLifetime);
        GooTestFunctions.registerTest(registrar, DISMISSED_BY_LEAVING_ITS_DIMENSION,
                HoleTests::dismissedByLeavingItsDimension);
        GooTestFunctions.registerTest(registrar, MOUTH_COVERS_THREE_BY_THREE, HoleTests::mouthCoversThreeByThree);
        GooTestFunctions.registerTest(registrar, MOUTH_FITS_A_ONE_BY_TWO_PLATFORM,
                HoleTests::mouthFitsAOneByTwoPlatform);
        GooTestFunctions.registerTest(registrar, MOUTH_FITS_A_LONE_PILLAR, HoleTests::mouthFitsALonePillar);
    }

    private static final String MOUTH_COVERS_THREE_BY_THREE = "hole_mouth_covers_three_by_three";
    private static final String MOUTH_FITS_A_ONE_BY_TWO_PLATFORM = "hole_mouth_fits_a_one_by_two_platform";
    private static final String MOUTH_FITS_A_LONE_PILLAR = "hole_mouth_fits_a_lone_pillar";
    /** The cell, relative, a 3x3 mouth centred on FLOOR covers at its corner. */
    private static final BlockPos CORNER = new BlockPos(1, 0, 1);
    /** The cell, relative, beside FLOOR that a 1x2 platform runs onto. */
    private static final BlockPos PLATFORM_END = new BlockPos(3, 0, 2);
    /** The cell, relative, beside FLOOR that no small mouth covers. */
    private static final BlockPos BESIDE = new BlockPos(1, 0, 2);
    private static final int FULL_FOOTPRINT = 9;
    private static final int PLATFORM_FOOTPRINT = 2;

    /**
     * Cast on the middle of a flat 3x3 floor, the mouth covers all nine top
     * faces, and an item dropped over a corner cell is swallowed onto the
     * caster's shelf.
     * decision hole-mouth-opens-three-by-three-and-fits-its-room
     *
     * @param helper the gametest helper
     */
    public static void mouthCoversThreeByThree(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 1);
        layFloor(helper, cell -> true);
        castAt(caster, helper.absolutePos(FLOOR), Direction.UP);
        ItemEntity[] dropped = new ItemEntity[1];
        int[] cells = new int[1];
        helper.runAfterDelay(LANDED, () -> {
            cells[0] = PortableHoles.of(caster.getUUID()).map(hole -> hole.footprint().size()).orElse(0);
            dropped[0] = dropOver(helper, CORNER);
        });
        helper.runAfterDelay(LANDED + SWALLOWED, () -> {
            long stored = PocketShelves.get(helper.getLevel()).countOf(caster.getUUID(), new ItemStack(Items.COBBLESTONE));
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(cells[0] == FULL_FOOTPRINT, "The mouth should cover " + FULL_FOOTPRINT + " cells, covers "
                    + cells[0]);
            helper.assertTrue(dropped[0].isRemoved(), "The item dropped over the corner should be swallowed");
            helper.assertTrue(stored == DROPPED, "The shelf should hold " + DROPPED + " cobblestone, holds " + stored);
            helper.succeed();
        });
    }

    /**
     * Cast on a 1x2 platform, the mouth covers its two top faces, and an item
     * dropped over the cell beside it stays in the world.
     * decision hole-mouth-opens-three-by-three-and-fits-its-room
     *
     * @param helper the gametest helper
     */
    public static void mouthFitsAOneByTwoPlatform(GameTestHelper helper) {
        assertSmallMouth(helper, cell -> cell.equals(FLOOR) || cell.equals(PLATFORM_END), PLATFORM_FOOTPRINT);
    }

    /**
     * Cast on a lone pillar, the mouth covers its one top face, and an item
     * dropped over the cell beside it stays in the world.
     * decision hole-mouth-opens-three-by-three-and-fits-its-room
     *
     * @param helper the gametest helper
     */
    public static void mouthFitsALonePillar(GameTestHelper helper) {
        assertSmallMouth(helper, cell -> cell.equals(FLOOR), 1);
    }

    private static void assertSmallMouth(GameTestHelper helper, Predicate<BlockPos> solid,
                                         int expected) {
        ServerPlayer caster = caster(helper, 1);
        layFloor(helper, solid);
        castAt(caster, helper.absolutePos(FLOOR), Direction.UP);
        ItemEntity[] dropped = new ItemEntity[1];
        int[] cells = new int[1];
        helper.runAfterDelay(LANDED, () -> {
            cells[0] = PortableHoles.of(caster.getUUID()).map(hole -> hole.footprint().size()).orElse(0);
            dropped[0] = dropOver(helper, BESIDE);
        });
        helper.runAfterDelay(LANDED + SWALLOWED, () -> {
            int shelved = PocketShelves.get(helper.getLevel()).shelfOf(caster.getUUID()).size();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(cells[0] == expected, "The mouth should cover " + expected + " cells, covers "
                    + cells[0]);
            helper.assertTrue(!dropped[0].isRemoved(), "The item dropped beside the mouth should stay in the world");
            helper.assertTrue(shelved == 0, "The shelf should stand empty, holds " + shelved + " entries");
            helper.succeed();
        });
    }

    /** Lays the 3x3 around FLOOR as stone where the cell is solid and air elsewhere, with air above each. */
    private static void layFloor(GameTestHelper helper, Predicate<BlockPos> solid) {
        for (int x = FLOOR.getX() - 1; x <= FLOOR.getX() + 1; x++) {
            for (int z = FLOOR.getZ() - 1; z <= FLOOR.getZ() + 1; z++) {
                BlockPos cell = new BlockPos(x, FLOOR.getY(), z);
                helper.setBlock(cell, solid.test(cell) ? Blocks.STONE : Blocks.AIR);
                helper.setBlock(cell.above(), Blocks.AIR);
            }
        }
    }

    /** Drops a still cobblestone stack just over a cell's top face. */
    private static ItemEntity dropOver(GameTestHelper helper, BlockPos cell) {
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(cell.above()).add(0, IN_THE_MOUTH.y() - 1, 0));
        ItemEntity item = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.COBBLESTONE, DROPPED));
        item.setDeltaMovement(Vec3.ZERO);
        helper.getLevel().addFreshEntity(item);
        return item;
    }

    private static final Identifier VOID_HOLE = Identifier.parse("goo:void_hole");
    /** void_hole.json's fixed cost. */
    private static final int HOLE_COST = 1000;
    /** Void the caster holds past the casts' cost, which no storing may touch. */
    private static final int SPARE_VOID = 500;
    /** void_hole.json's lifetime: the hole closes after thirty seconds. */
    private static final int LIFETIME = 600;
    /** The floor block, relative, whose top face the hole opens on. */
    private static final BlockPos FLOOR = new BlockPos(2, 0, 2);
    /** A second floor block, relative, a second cast opens on. */
    private static final BlockPos SECOND_FLOOR = new BlockPos(3, 0, 3);
    /** The ceiling block, relative, whose bottom face the hole pours from. */
    private static final BlockPos CEILING = new BlockPos(2, 3, 2);
    /** Where the caster stands. */
    private static final Vec3 START = new Vec3(2.5, 1, 0.5);
    /** Just over the floor's top face, inside the hole's mouth. */
    private static final Vec3 IN_THE_MOUTH = new Vec3(2.5, 1.05, 2.5);
    /** Ticks the throw is given to land and open the hole. */
    private static final int LANDED = 30;
    /** Ticks a dropped item is given to fall into the mouth. */
    private static final int SWALLOWED = 5;
    private static final int DROPPED = 5;
    private static final int SEEDED_STONE = 70;
    private static final int SEEDED_DIAMONDS = 3;

    private HoleTests() {
    }

    /**
     * Cast on a floor's top face, the hole opens there; a cobblestone stack
     * dropped into its mouth leaves the world and stands on the caster's
     * shelf, and the caster's void fell by the hole's fixed cost alone.
     *
     * @param helper the gametest helper
     */
    public static void swallowsADroppedItem(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 1);
        castAt(caster, helper.absolutePos(FLOOR), Direction.UP);
        ItemEntity[] dropped = new ItemEntity[1];
        helper.runAfterDelay(LANDED, () -> {
            assertHoleOn(helper, caster, FLOOR, Direction.UP);
            Vec3 at = helper.absoluteVec(IN_THE_MOUTH);
            dropped[0] = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.COBBLESTONE, DROPPED));
            dropped[0].setDeltaMovement(Vec3.ZERO);
            helper.getLevel().addFreshEntity(dropped[0]);
        });
        helper.runAfterDelay(LANDED + SWALLOWED, () -> {
            long stored = PocketShelves.get(helper.getLevel()).countOf(caster.getUUID(), new ItemStack(Items.COBBLESTONE));
            int voidLeft = voidHeld(caster);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(dropped[0].isRemoved(), "The item dropped into the mouth should be gone from the world");
            helper.assertTrue(stored == DROPPED, "The shelf should hold " + DROPPED + " cobblestone, holds " + stored);
            helper.assertTrue(voidLeft == SPARE_VOID, "The caster's void should fall by the fixed cost alone, "
                    + SPARE_VOID + " expected left, " + voidLeft + " left");
            helper.succeed();
        });
    }

    /**
     * With cobblestone and diamonds on the shelf, cast on a ceiling's bottom
     * face the hole pours them all out as items beneath it and leaves the
     * shelf empty.
     *
     * @param helper the gametest helper
     */
    public static void poursItsShelfFromACeiling(GameTestHelper helper) {
        helper.setBlock(CEILING, Blocks.STONE);
        ServerPlayer caster = caster(helper, 1);
        PocketShelves shelves = PocketShelves.get(helper.getLevel());
        shelves.add(caster.getUUID(), new ItemStack(Items.COBBLESTONE), SEEDED_STONE);
        shelves.add(caster.getUUID(), new ItemStack(Items.DIAMOND), SEEDED_DIAMONDS);
        castAt(caster, helper.absolutePos(CEILING), Direction.DOWN);
        helper.runAfterDelay(LANDED, () -> {
            AABB below = new AABB(helper.absolutePos(CEILING.below(2))).expandTowards(0, 2, 0).inflate(1);
            int stone = countOf(helper.getLevel(), below, Items.COBBLESTONE);
            int diamonds = countOf(helper.getLevel(), below, Items.DIAMOND);
            int left = shelves.shelfOf(caster.getUUID()).size();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(stone == SEEDED_STONE, "The hole should pour " + SEEDED_STONE + " cobblestone, poured "
                    + stone);
            helper.assertTrue(diamonds == SEEDED_DIAMONDS, "The hole should pour " + SEEDED_DIAMONDS
                    + " diamonds, poured " + diamonds);
            helper.assertTrue(left == 0, "The shelf should stand empty, holds " + left + " entries");
            helper.succeed();
        });
    }

    /**
     * A second cast while the first hole stands dismisses the first: one hole
     * stands, on the second face.
     *
     * @param helper the gametest helper
     */
    public static void standsOneAtATime(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 2);
        castAt(caster, helper.absolutePos(FLOOR), Direction.UP);
        helper.runAfterDelay(LANDED, () -> {
            assertHoleOn(helper, caster, FLOOR, Direction.UP);
            castAt(caster, helper.absolutePos(SECOND_FLOOR), Direction.UP);
        });
        helper.runAfterDelay(LANDED + LANDED, () -> {
            long standing = PortableHoles.open().stream().filter(hole -> hole.owner().equals(caster.getUUID())).count();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(standing == 1, "One hole should stand for the caster, " + standing + " stand");
            assertHoleOn(helper, caster, SECOND_FLOOR, Direction.UP);
            helper.succeed();
        });
    }

    /**
     * A hole standing past its 600 ticks is gone.
     *
     * @param helper the gametest helper
     */
    public static void closesAfterItsLifetime(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 1);
        castAt(caster, helper.absolutePos(FLOOR), Direction.UP);
        helper.runAfterDelay(LANDED, () -> assertHoleOn(helper, caster, FLOOR, Direction.UP));
        helper.runAfterDelay(LANDED + LIFETIME, () -> {
            Optional<PortableHole> hole = PortableHoles.of(caster.getUUID());
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(hole.isEmpty(), "The hole should close after " + LIFETIME + " ticks, stands " + hole);
            helper.succeed();
        });
    }

    /**
     * The owner gone to the Nether leaves no hole standing the next tick.
     *
     * @param helper the gametest helper
     */
    public static void dismissedByLeavingItsDimension(GameTestHelper helper) {
        // A cast leaves synced attachments a mock connection refuses on a level change, so the hole opens uncast.
        helper.setBlock(FLOOR, Blocks.STONE);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        HoleOpening.open(helper.getLevel(), caster, helper.absolutePos(FLOOR), Direction.UP, LIFETIME);
        helper.runAfterDelay(1, () -> {
            assertHoleOn(helper, caster, FLOOR, Direction.UP);
            ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
            caster.teleportTo(nether, 0.5, 64, 0.5, Set.of(), 0F, 0F, false);
        });
        helper.runAfterDelay(2, () -> {
            Optional<PortableHole> hole = PortableHoles.of(caster.getUUID());
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(hole.isEmpty(), "Leaving the hole's dimension should dismiss it, stands " + hole);
            helper.succeed();
        });
    }

    private static void assertHoleOn(GameTestHelper helper, ServerPlayer caster, BlockPos block, Direction face) {
        Optional<PortableHole> hole = PortableHoles.of(caster.getUUID());
        helper.assertTrue(hole.isPresent() && hole.get().block().equals(helper.absolutePos(block))
                        && hole.get().face() == face,
                "The caster's hole should stand on " + face + " of " + helper.absolutePos(block) + ", stands " + hole);
    }

    private static int countOf(ServerLevel level, AABB box, Item item) {
        return level.getEntitiesOfClass(ItemEntity.class, box, entity -> entity.getItem().is(item)).stream()
                .mapToInt(entity -> entity.getItem().getCount()).sum();
    }

    /** A survival Void caster knowing the hole and holding its cost for some casts and a spare. */
    private static ServerPlayer caster(GameTestHelper helper, int casts) {
        helper.setBlock(FLOOR, Blocks.STONE);
        helper.setBlock(SECOND_FLOOR, Blocks.STONE);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        caster.setGameMode(GameType.SURVIVAL);
        caster.connection.markClientLoaded();
        caster.getInventory().clearContent();
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.VOID, HOLE_COST * casts + SPARE_VOID));
        KnownRecipes.teachRequires(caster, AbilityRegistry.of(caster.level()).getAbility(VOID_HOLE));
        caster.snapTo(helper.absoluteVec(START));
        return caster;
    }

    private static void castAt(ServerPlayer caster, BlockPos struck, Direction face) {
        GooGloveItem.setSelection(caster.getMainHandItem(), GloveSelection.ofAbility(GooTypes.VOID, VOID_HOLE));
        GooThrowHandler.execute(caster, new GooThrowPayload(GooTypes.id(GooTypes.VOID), -1, struck,
                face.ordinal(), false, VOID_HOLE.toString(), caster.getEyePosition()));
    }

    private static int voidHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.VOID, 0);
    }
}
