package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.hole.HoleDrift;
import com.mercuriusxeno.goo.ability.hole.HoleFrame;
import com.mercuriusxeno.goo.ability.hole.HoleOpening;
import com.mercuriusxeno.goo.ability.hole.PortableHole;
import com.mercuriusxeno.goo.ability.hole.PortableHoles;
import com.mercuriusxeno.goo.ability.pocket.PocketShelves;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.network.PortableHolesSyncPayload;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Gametests for Void's hole, an in-world portable hole: cast on a block's top
 * face it opens there and swallows the items dropped into its mouth onto the
 * caster's shelf for a fixed cost and no void reserved; cast on a bottom face
 * it pours the shelf out beneath; one stands per player, and the owner leaving
 * its dimension dismisses it.
 * decision hole-is-an-in-world-portable-hole-for-items
 */
@RegistersGameTests
public final class HoleTests {

    private static final String SWALLOWS_A_DROPPED_ITEM = "hole_swallows_a_dropped_item";
    private static final String POURS_ITS_SHELF_FROM_A_CEILING = "hole_pours_its_shelf_from_a_ceiling";
    private static final String POUR_FALLS_STRAIGHT_DOWN = "hole_pour_falls_straight_down";
    private static final String SWALLOW_TELLS_ITS_CLIENTS = "hole_swallow_tells_its_clients";
    private static final String STANDS_ONE_AT_A_TIME = "hole_stands_one_at_a_time";
    private static final String DISMISSED_BY_LEAVING_ITS_DIMENSION = "hole_dismissed_by_leaving_its_dimension";
    private static final String MOUTH_LISTS_EVERY_KIND_STORED = "hole_mouth_lists_every_kind_stored";
    private static final String WAITS_TO_OPEN_BEFORE_IT_POURS = "hole_waits_to_open_before_it_pours";
    private static final String POURS_EACH_KIND_FROM_WHERE_IT_SWIMS = "hole_pours_each_kind_from_where_it_swims";
    private static final String CLOSED_WHILE_OPENING_KEEPS_ITS_SHELF = "hole_closed_while_opening_keeps_its_shelf";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, SWALLOWS_A_DROPPED_ITEM, HoleTests::swallowsADroppedItem);
        GooTestFunctions.registerTest(registrar, POURS_ITS_SHELF_FROM_A_CEILING, HoleTests::poursItsShelfFromACeiling);
        GooTestFunctions.registerTest(registrar, POUR_FALLS_STRAIGHT_DOWN, HoleTests::pourFallsStraightDown);
        GooTestFunctions.registerTest(registrar, SWALLOW_TELLS_ITS_CLIENTS, HoleTests::swallowTellsItsClients);
        GooTestFunctions.registerTest(registrar, STANDS_ONE_AT_A_TIME, HoleTests::standsOneAtATime);
        GooTestFunctions.registerTest(registrar, DISMISSED_BY_LEAVING_ITS_DIMENSION,
                HoleTests::dismissedByLeavingItsDimension);
        GooTestFunctions.registerTest(registrar, MOUTH_COVERS_THREE_BY_THREE, HoleTests::mouthCoversThreeByThree);
        GooTestFunctions.registerTest(registrar, MOUTH_FITS_A_ONE_BY_TWO_PLATFORM,
                HoleTests::mouthFitsAOneByTwoPlatform);
        GooTestFunctions.registerTest(registrar, MOUTH_FITS_A_LONE_PILLAR, HoleTests::mouthFitsALonePillar);
        GooTestFunctions.registerTest(registrar, MOUTH_LISTS_EVERY_KIND_STORED, HoleTests::mouthListsEveryKindStored);
        GooTestFunctions.registerTest(registrar, WAITS_TO_OPEN_BEFORE_IT_POURS, HoleTests::waitsToOpenBeforeItPours);
        GooTestFunctions.registerTest(registrar, POURS_EACH_KIND_FROM_WHERE_IT_SWIMS,
                HoleTests::poursEachKindFromWhereItSwims);
        GooTestFunctions.registerTest(registrar, CLOSED_WHILE_OPENING_KEEPS_ITS_SHELF,
                HoleTests::closedWhileOpeningKeepsItsShelf);
    }

    private static final String MOUTH_COVERS_THREE_BY_THREE = "hole_mouth_covers_three_by_three";
    private static final String MOUTH_FITS_A_ONE_BY_TWO_PLATFORM = "hole_mouth_fits_a_one_by_two_platform";
    private static final String MOUTH_FITS_A_LONE_PILLAR = "hole_mouth_fits_a_lone_pillar";
    /** The cell, relative, a 3x3 mouth centred on FLOOR covers at its corner. */
    static final BlockPos CORNER = new BlockPos(1, 0, 1);
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
    static void layFloor(GameTestHelper helper, Predicate<BlockPos> solid) {
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
    /** void_hole.json's lifetime: the hole closes after ten seconds. */
    static final int LIFETIME = 200;
    /** void_hole.json's refresh: a swallow floors the time left at five seconds. */
    static final int REFRESH = 100;
    /** The floor block, relative, whose top face the hole opens on. */
    static final BlockPos FLOOR = new BlockPos(2, 0, 2);
    /** A second floor block, relative, a second cast opens on. */
    private static final BlockPos SECOND_FLOOR = new BlockPos(3, 0, 3);
    /** The ceiling block, relative, whose bottom face the hole pours from. */
    private static final BlockPos CEILING = new BlockPos(2, 3, 2);
    /** Where the caster stands. */
    private static final Vec3 START = new Vec3(2.5, 1, 0.5);
    /** Just over the floor's top face, inside the hole's mouth. */
    static final Vec3 IN_THE_MOUTH = new Vec3(2.5, 1.05, 2.5);
    /** Ticks the throw is given to land and open the hole. */
    static final int LANDED = 30;
    /** Ticks a dropped item is given to fall into the mouth. */
    private static final int SWALLOWED = 5;
    /** Ticks past the opening a poured stack is given to fall to the floor and settle. */
    private static final int POUR_FALLEN = 40;
    /** How far, on x and on z, a poured stack may land from where it appeared. */
    private static final double POUR_COLUMN_SLACK = 0.05;
    /** How far, on x and on z, a poured stack may appear from where its kind swims: a float's rounding. */
    private static final double SAME_PLACE = 1e-4;
    static final int SEEDED_GOLD = 5;
    static final int DROPPED = 5;
    static final int SEEDED_STONE = 70;
    static final int SEEDED_DIAMONDS = 3;
    /** More kinds than the twelve a mouth once showed at most. */
    private static final int MANY_KINDS = 20;

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
     * A cobblestone stack dropped into an open hole is recorded for the
     * clients as swallowed: its kind, a place inside the mouth's box, and the
     * game time it left the world, within the one tick the watch may see the
     * removal late.
     * decision swallowed-item-transitions-into-the-hole-smoothly
     *
     * @param helper the gametest helper
     */
    public static void swallowTellsItsClients(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 1);
        castAt(caster, helper.absolutePos(FLOOR), Direction.UP);
        ItemEntity[] dropped = new ItemEntity[1];
        long[] goneAt = {-1};
        helper.runAfterDelay(LANDED, () -> {
            Vec3 at = helper.absoluteVec(IN_THE_MOUTH);
            dropped[0] = new ItemEntity(helper.getLevel(), at.x, at.y, at.z, new ItemStack(Items.COBBLESTONE, DROPPED));
            dropped[0].setDeltaMovement(Vec3.ZERO);
            helper.getLevel().addFreshEntity(dropped[0]);
        });
        helper.onEachTick(() -> {
            if (dropped[0] != null && goneAt[0] < 0 && dropped[0].isRemoved()) {
                goneAt[0] = helper.getLevel().getGameTime();
            }
        });
        helper.runAfterDelay(LANDED + SWALLOWED, () -> {
            Optional<PortableHole> hole = PortableHoles.of(caster.getUUID());
            List<PortableHolesSyncPayload.Swallowed> told = PortableHoles.swallowedInto(caster.getUUID());
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(hole.isPresent() && goneAt[0] > 0, "The hole should stand and swallow the dropped item");
            helper.assertTrue(told.size() == 1, "One swallow should be told to the clients, told " + told);
            PortableHolesSyncPayload.Swallowed swallowed = told.getFirst();
            helper.assertTrue(swallowed.item().equals(BuiltInRegistries.ITEM.getKey(Items.COBBLESTONE)),
                    "The swallow should name cobblestone, names " + swallowed.item());
            helper.assertTrue(hole.get().mouth().contains(swallowed.at()), "The swallow's place " + swallowed.at()
                    + " should lie inside the mouth " + hole.get().mouth());
            helper.assertTrue(Math.abs(swallowed.time() - goneAt[0]) <= 1, "The swallow's time " + swallowed.time()
                    + " should be the tick the item left the world, " + goneAt[0]);
            helper.succeed();
        });
    }

    /**
     * Cast on a ceiling's bottom face, the hole holds its shelf while it
     * opens: on the last tick before the opening span ends no item has poured
     * beneath it, the shelf holds every seeded count, and the mouth the
     * clients are told of lists the seeded kinds, so they draw them swimming.
     * decision hole-pours-each-kind-from-where-it-swims-once-open
     *
     * @param helper the gametest helper
     */
    public static void waitsToOpenBeforeItPours(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 1);
        Map<Item, Integer> seeded = seedCeilingPour(helper, caster);
        PocketShelves shelves = PocketShelves.get(helper.getLevel());
        PourWatch watch = PourWatch.over(helper, caster);
        int[] pouredWhileOpening = {-1};
        Map<Item, Long> heldWhileOpening = new HashMap<>();
        List<Identifier> toldWhileOpening = new ArrayList<>();
        helper.onEachTick(() -> {
            if (watch.hole != null && helper.getLevel().getGameTime() == watch.hole.opensFullyAt() - 1) {
                pouredWhileOpening[0] = watch.beneath().size();
                seeded.keySet().forEach(item -> heldWhileOpening.put(item,
                        shelves.countOf(caster.getUUID(), new ItemStack(item))));
                PortableHoles.mouthOf(shelves, watch.hole).stored()
                        .forEach(stored -> toldWhileOpening.add(stored.item()));
            }
        });
        helper.runAfterDelay(LANDED + POUR_FALLEN, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(pouredWhileOpening[0] >= 0, "The ceiling hole should stand and be read while it opens");
            helper.assertTrue(pouredWhileOpening[0] == 0, "No item should pour while the hole opens, "
                    + pouredWhileOpening[0] + " stand beneath it");
            seeded.forEach((item, count) -> helper.assertTrue(heldWhileOpening.get(item) == count.longValue(),
                    "The shelf should hold " + count + " " + item + " while the hole opens, holds "
                            + heldWhileOpening.get(item)));
            List<Identifier> seededKinds = seeded.keySet().stream().map(BuiltInRegistries.ITEM::getKey).toList();
            helper.assertTrue(toldWhileOpening.equals(seededKinds), "The mouth should list " + seededKinds
                    + " while the hole opens, lists " + toldWhileOpening);
            helper.succeed();
        });
    }

    /**
     * Cast on a ceiling's bottom face, the hole pours every stack in the one
     * tick it stands fully open, its opening span past the tick it opened:
     * the poured stacks sum to the seeded counts, the shelf stands empty and
     * the mouth the clients are told of lists no kind. A stack's tick is the
     * game time it was first seen less the ticks it had lived by then, the
     * tick it entered the world.
     * decision hole-pours-each-kind-from-where-it-swims-once-open
     *
     * @param helper the gametest helper
     */
    public static void poursItsShelfFromACeiling(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 1);
        Map<Item, Integer> seeded = seedCeilingPour(helper, caster);
        PocketShelves shelves = PocketShelves.get(helper.getLevel());
        PourWatch watch = PourWatch.over(helper, caster);
        helper.runAfterDelay(LANDED + POUR_FALLEN, () -> {
            int left = shelves.shelfOf(caster.getUUID()).size();
            List<PortableHolesSyncPayload.Stored> told = watch.hole == null ? List.of()
                    : PortableHoles.mouthOf(shelves, watch.hole).stored();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(watch.hole != null, "The ceiling hole should stand");
            Set<Long> ticks = watch.seen.values().stream().map(Seen::enteredAt).collect(Collectors.toSet());
            helper.assertTrue(ticks.equals(Set.of(watch.hole.opensFullyAt())), "Every stack should pour in tick "
                    + watch.hole.opensFullyAt() + ", the hole opened at " + watch.hole.openedAt() + " and the stacks"
                    + " poured in ticks " + ticks);
            seeded.forEach((item, count) -> {
                int poured = watch.seen.values().stream().filter(seen -> seen.item().getItem().is(item))
                        .mapToInt(seen -> seen.item().getItem().getCount()).sum();
                helper.assertTrue(poured == count, "The hole should pour " + count + " " + item + ", poured " + poured);
            });
            helper.assertTrue(left == 0, "The shelf should stand empty, holds " + left + " entries");
            helper.assertTrue(told.isEmpty(), "The mouth should list no stored kind, lists " + told);
            helper.succeed();
        });
    }

    /**
     * Cast on a ceiling's bottom face, each poured stack appears across the
     * mouth where its kind swims at the pour tick, as the drift the client
     * draws with answers for its kind's shelf index over the hole's frame:
     * every stack of the many-stack kind at one place, two kinds at two.
     * decision hole-pours-each-kind-from-where-it-swims-once-open
     *
     * @param helper the gametest helper
     */
    public static void poursEachKindFromWhereItSwims(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 1);
        List<Item> kinds = List.copyOf(seedCeilingPour(helper, caster).keySet());
        PourWatch watch = PourWatch.over(helper, caster);
        helper.runAfterDelay(LANDED + POUR_FALLEN, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(watch.hole != null, "The ceiling hole should stand");
            HoleFrame frame = HoleFrame.of(watch.hole.footprint(), watch.hole.face());
            Map<Item, Set<Vec3>> placesOf = new HashMap<>();
            for (Seen seen : watch.seen.values()) {
                Item item = seen.item().getItem().getItem();
                int index = kinds.indexOf(item);
                Vec3 swims = frame.toWorld(HoleDrift.offset(index, kinds.size(), seen.enteredAt(), frame.halfAcross(),
                        frame.halfAlong()));
                double off = Math.max(Math.abs(seen.at().x() - swims.x()), Math.abs(seen.at().z() - swims.z()));
                helper.assertTrue(off <= SAME_PLACE, "A stack of " + item + " should appear where its kind swims, "
                        + swims + ", appears at " + seen.at());
                placesOf.computeIfAbsent(item, unused -> new HashSet<>())
                        .add(new Vec3(seen.at().x(), 0, seen.at().z()));
            }
            Set<Vec3> stonePlaces = placesOf.getOrDefault(Items.COBBLESTONE, Set.of());
            Set<Vec3> diamondPlaces = placesOf.getOrDefault(Items.DIAMOND, Set.of());
            helper.assertTrue(stonePlaces.size() == 1, "The cobblestone stacks should share one place, stand at "
                    + stonePlaces);
            helper.assertTrue(diamondPlaces.size() == 1 && !diamondPlaces.equals(stonePlaces),
                    "The diamonds should stand apart from the cobblestone, at " + diamondPlaces);
            helper.succeed();
        });
    }

    /**
     * Cast on a ceiling's bottom face, every stack the hole pours leaves the
     * mouth with no sideways and no upward velocity, and lands in the column
     * beneath the place it appeared. Each poured item's velocity is read the
     * first tick it is seen, and its place once the pour has had time to land.
     * decision hole-pour-falls-straight-down
     *
     * @param helper the gametest helper
     */
    public static void pourFallsStraightDown(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 1);
        seedCeilingPour(helper, caster);
        PourWatch watch = PourWatch.over(helper, caster);
        helper.runAfterDelay(LANDED + POUR_FALLEN, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(watch.seen.size() > 1, "The hole should pour several stacks, poured "
                    + watch.seen.size());
            for (Seen seen : watch.seen.values()) {
                Vec3 velocity = seen.velocity();
                helper.assertTrue(velocity.x() == 0 && velocity.z() == 0 && velocity.y() <= 0,
                        "A poured stack should leave with no sideways or upward velocity, left at " + velocity);
                ItemEntity item = seen.item();
                double off = Math.max(Math.abs(item.getX() - seen.at().x()), Math.abs(item.getZ() - seen.at().z()));
                helper.assertTrue(item.onGround() && off <= POUR_COLUMN_SLACK, "A poured stack should land within "
                        + POUR_COLUMN_SLACK + " of where it appeared, " + seen.at() + ", stands at "
                        + item.position() + (item.onGround() ? "" : " in the air"));
            }
            helper.succeed();
        });
    }

    /**
     * Cast on a ceiling's bottom face and punched shut the first tick it
     * stands, before its opening span ends, the hole pours nothing and the
     * shelf keeps every seeded count.
     * decision hole-pours-each-kind-from-where-it-swims-once-open
     *
     * @param helper the gametest helper
     */
    public static void closedWhileOpeningKeepsItsShelf(GameTestHelper helper) {
        ServerPlayer caster = caster(helper, 1);
        Map<Item, Integer> seeded = seedCeilingPour(helper, caster);
        PocketShelves shelves = PocketShelves.get(helper.getLevel());
        PourWatch watch = PourWatch.over(helper, caster);
        long[] dismissedAt = {-1};
        helper.onEachTick(() -> {
            if (watch.hole != null && dismissedAt[0] < 0) {
                PortableHoles.dismissAt(helper.getLevel(), watch.hole.block(), Direction.DOWN);
                dismissedAt[0] = helper.getLevel().getGameTime();
            }
        });
        helper.runAfterDelay(LANDED + POUR_FALLEN, () -> {
            Map<Item, Long> held = new HashMap<>();
            seeded.keySet().forEach(item -> held.put(item, shelves.countOf(caster.getUUID(), new ItemStack(item))));
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(watch.hole != null && dismissedAt[0] < watch.hole.opensFullyAt(), "The ceiling hole"
                    + " should stand and close before it opens fully, closed at " + dismissedAt[0]);
            helper.assertTrue(watch.seen.isEmpty(), "No item should pour, " + watch.seen.size() + " poured");
            seeded.forEach((item, count) -> helper.assertTrue(held.get(item) == count.longValue(), "The shelf should"
                    + " keep " + count + " " + item + ", holds " + held.get(item)));
            helper.succeed();
        });
    }

    /**
     * Sets the ceiling block and seeds the caster's shelf with three kinds,
     * cobblestone over two full stacks among them.
     *
     * @return each seeded kind and its count, in shelf order
     */
    private static Map<Item, Integer> seedCeilingPour(GameTestHelper helper, ServerPlayer caster) {
        helper.setBlock(CEILING, Blocks.STONE);
        Map<Item, Integer> seeded = new LinkedHashMap<>();
        seeded.put(Items.COBBLESTONE, SEEDED_STONE + Items.COBBLESTONE.getDefaultMaxStackSize());
        seeded.put(Items.DIAMOND, SEEDED_DIAMONDS);
        seeded.put(Items.GOLD_INGOT, SEEDED_GOLD);
        PocketShelves shelves = PocketShelves.get(helper.getLevel());
        seeded.forEach((item, count) -> shelves.add(caster.getUUID(), new ItemStack(item), count));
        return seeded;
    }

    /**
     * A poured stack as first seen.
     *
     * @param item      the stack's entity
     * @param enteredAt the game time it entered the world
     * @param at        its place when first seen
     * @param velocity  its velocity when first seen
     */
    private record Seen(ItemEntity item, long enteredAt, Vec3 at, Vec3 velocity) {
    }

    /** Watches a caster's ceiling hole each tick: the hole once it stands, and each stack beneath it as first seen. */
    private static final class PourWatch {

        private final GameTestHelper helper;
        private final AABB below;
        private final Map<Integer, Seen> seen = new LinkedHashMap<>();
        private PortableHole hole;

        private PourWatch(GameTestHelper helper) {
            this.helper = helper;
            this.below = new AABB(helper.absolutePos(CEILING.below(2))).expandTowards(0, 2, 0).inflate(1);
        }

        static PourWatch over(GameTestHelper helper, ServerPlayer caster) {
            PourWatch watch = new PourWatch(helper);
            castAt(caster, helper.absolutePos(CEILING), Direction.DOWN);
            helper.onEachTick(() -> {
                if (watch.hole == null) {
                    PortableHoles.of(caster.getUUID()).ifPresent(hole -> watch.hole = hole);
                }
                long now = helper.getLevel().getGameTime();
                for (ItemEntity item : watch.beneath()) {
                    watch.seen.putIfAbsent(item.getId(), new Seen(item, now - item.tickCount, item.position(),
                            item.getDeltaMovement()));
                }
            });
            return watch;
        }

        List<ItemEntity> beneath() {
            return helper.getLevel().getEntitiesOfClass(ItemEntity.class, below);
        }
    }

    /**
     * With more kinds on the shelf than the twelve a mouth once showed, the
     * mouth the clients are told of lists every kind, in shelf order.
     * decision hole-shows-every-kind-stored
     *
     * @param helper the gametest helper
     */
    public static void mouthListsEveryKindStored(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.STONE);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        PocketShelves shelves = PocketShelves.get(helper.getLevel());
        List<Identifier> seeded = BuiltInRegistries.ITEM.stream().filter(item -> item != Items.AIR)
                .limit(MANY_KINDS).map(BuiltInRegistries.ITEM::getKey).toList();
        seeded.forEach(kind -> shelves.add(caster.getUUID(), new ItemStack(BuiltInRegistries.ITEM.getValue(kind)), 1));
        HoleOpening.open(helper.getLevel(), caster, helper.absolutePos(FLOOR), Direction.UP, LIFETIME, REFRESH);
        helper.runAfterDelay(1, () -> {
            Optional<PortableHole> hole = PortableHoles.of(caster.getUUID());
            List<Identifier> told = hole.map(open -> PortableHoles.mouthOf(shelves, open).stored().stream()
                    .map(PortableHolesSyncPayload.Stored::item).toList()).orElse(List.of());
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(hole.isPresent(), "The caster's hole should stand");
            helper.assertTrue(told.equals(seeded), "The mouth should list all " + seeded.size()
                    + " seeded kinds in shelf order, lists " + told.size() + ": " + told);
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
     * The owner gone to the Nether leaves no hole standing the next tick.
     *
     * @param helper the gametest helper
     */
    public static void dismissedByLeavingItsDimension(GameTestHelper helper) {
        // A cast leaves synced attachments a mock connection refuses on a level change, so the hole opens uncast.
        helper.setBlock(FLOOR, Blocks.STONE);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        HoleOpening.open(helper.getLevel(), caster, helper.absolutePos(FLOOR), Direction.UP, LIFETIME, REFRESH);
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

    /** A survival Void caster knowing the hole and holding its cost for some casts and a spare. */
    static ServerPlayer caster(GameTestHelper helper, int casts) {
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

    static void castAt(ServerPlayer caster, BlockPos struck, Direction face) {
        GooGloveItem.setSelection(caster.getMainHandItem(), GloveSelection.ofAbility(GooTypes.VOID, VOID_HOLE));
        GooThrowHandler.execute(caster, new GooThrowPayload(GooTypes.id(GooTypes.VOID), -1, struck,
                face.ordinal(), false, VOID_HOLE.toString(), caster.getEyePosition()));
    }

    private static int voidHeld(ServerPlayer player) {
        return GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.VOID, 0);
    }
}
