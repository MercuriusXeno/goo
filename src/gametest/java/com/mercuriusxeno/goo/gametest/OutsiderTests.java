package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.outsider.ConjuredDoor;
import com.mercuriusxeno.goo.ability.outsider.ConjuredDoors;
import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomEdges;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import com.mercuriusxeno.goo.ability.outsider.VoidPockets;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.GooStreamPayload;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Gametests for Void's Outsider: held for its charge it carries the caster
 * into their room in goo:pocket, standing on the voidstone; the room's door
 * takes them back to the exact place they cast from; the door and voidstone
 * stand again when removed; and a player falling below the floor is set back
 * on it unharmed
 * (decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform).
 */
@RegistersGameTests
public final class OutsiderTests {
    private static final String OUTSIDER_CARRIES_IN_AND_THE_DOOR_RETURNS = "outsider_carries_in_and_the_door_returns";
    private static final String OUTSIDER_DOOR_AND_VOIDSTONE_RESPAWN = "outsider_door_and_voidstone_respawn";
    private static final String OUTSIDER_VOID_CATCHES_THE_FALLER = "outsider_void_catches_the_faller";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, OUTSIDER_CARRIES_IN_AND_THE_DOOR_RETURNS,
                OutsiderTests::outsiderCarriesInAndTheDoorReturns);
        GooTestFunctions.registerTest(registrar, OUTSIDER_DOOR_AND_VOIDSTONE_RESPAWN,
                OutsiderTests::doorAndVoidstoneRespawn);
        GooTestFunctions.registerTest(registrar, OUTSIDER_VOID_CATCHES_THE_FALLER, OutsiderTests::voidCatchesTheFaller);
        GooTestFunctions.registerTest(registrar, OUTSIDER_FLOOR_STANDS_WHOLE,
                OutsiderTests::floorStandsWholeAndGrowthPlacesNoBlock);
        GooTestFunctions.registerTest(registrar, OUTSIDER_POCKET_SHOWS_NO_SKY, OutsiderTests::pocketShowsNoSky);
        GooTestFunctions.registerTest(registrar, OUTSIDER_SHOWS_ITS_CHARGE_AND_GROWTH,
                OutsiderTests::showsItsChargeAndGrowth);
        GooTestFunctions.registerTest(registrar, ROOM_EDGE_CARRIES_A_BODY_TO_THE_OPPOSITE_SIDE,
                OutsiderTests::roomEdgeCarriesABodyToTheOppositeSide);
        GooTestFunctions.registerTest(registrar, ROOM_EDGE_CARRIES_A_WALKER_AT_A_FRACTIONAL_EXTENT,
                OutsiderTests::roomEdgeCarriesAWalkerAtAFractionalExtent);
        GooTestFunctions.registerTest(registrar, OUTSIDER_REFUSES_BUILDING_PAST_THE_PLATFORM,
                OutsiderTests::refusesBuildingPastThePlatform);
        GooTestFunctions.registerTest(registrar, OUTSIDER_CONJURES_A_DOOR_THAT_GROWS,
                OutsiderTests::conjuresADoorThatGrowsWithTheCharge);
        GooTestFunctions.registerTest(registrar, OUTSIDER_DOOR_ENVELOPS_THE_CASTER,
                OutsiderTests::doorEnvelopsTheCasterOnTheFullCharge);
        GooTestFunctions.registerTest(registrar, OUTSIDER_DOOR_COLLAPSES_WHEN_LET_GO,
                OutsiderTests::doorCollapsesWhenTheChannelIsLetGoEarly);
        GooTestFunctions.registerTest(registrar, OUTSIDER_CONJURES_NO_DOOR_INSIDE,
                OutsiderTests::conjuresNoDoorInsideThePocket);
        GooTestFunctions.registerTest(registrar, OUTSIDER_WUB_REACHES_THE_CORNER,
                OutsiderTests::wubReachesTheRoomsCorner);
        GooTestFunctions.registerTest(registrar, OUTSIDER_GROWS_A_BLOCK_A_SECOND, OutsiderTests::growsOneBlockASecond);
    }

    private static final String ROOM_EDGE_CARRIES_A_BODY_TO_THE_OPPOSITE_SIDE =
            "room_edge_carries_a_body_to_the_opposite_side";
    private static final String OUTSIDER_REFUSES_BUILDING_PAST_THE_PLATFORM =
            "outsider_refuses_building_past_the_platform";
    private static final String SHOULD_REFUSE = "A block placed past the room's extent at %s should be refused, %s stands there";
    private static final String SHOULD_PLACE = "A block placed inside the room's extent at %s should stand, %s stands there";
    /**
     * The item's own ticks after which it has crossed the east edge and come
     * out of the west one: one tick to be seen before the edge, one to cross
     * it, and one more so the server's carry that tick has run before the
     * test reads it.
     */
    private static final int CROSSED_BY = 3;
    /** How far inside the east edge the item starts, its middle. */
    private static final double INSIDE_THE_EDGE = 0.3;
    /** The item's eastward speed, enough to cross the edge within a tick. */
    private static final double EASTWARD = 0.4;
    /**
     * How far past the square's width the item may have moved on: its speed
     * over the ticks it took, and a tick more, its start's own lead on the edge.
     */
    private static final double MOVED_ON = (CROSSED_BY + 1) * EASTWARD;
    private static final double KEPT = 1.0E-6;
    private static final String SHOULD_CARRY_ACROSS = "An item crossing the room's east edge should stand the square's "
            + "width %d west of %s, moved on by at most %.2f, with its y and z kept, stands at %s";
    private static final String SHOULD_KEEP_MOTION = "The carried item should keep moving east, moves %s";

    /**
     * An item moving east across a room's east edge stands, once it has
     * ticked across, the square's width west, just inside the west edge,
     * with its height, its z and its eastward motion kept. The test reads the
     * item by its own tick count, since a freshly forced chunk starts ticking
     * its entities a varying number of server ticks on
     * (decision outsider-room-wraps-and-repeats-at-the-platform-edge).
     *
     * @param helper the gametest helper
     */
    public static void roomEdgeCarriesABodyToTheOppositeSide(GameTestHelper helper) {
        ServerLevel pocket = helper.getLevel().getServer().getLevel(OutsiderRooms.POCKET);
        helper.assertTrue(pocket != null, "The server should hold the pocket dimension");
        int room = OutsiderRooms.layRoomFor(pocket, java.util.UUID.randomUUID());
        BlockPos center = OutsiderRooms.voidstoneOf(room);
        int across = (int) (2 * RoomExtent.LAID);
        // The room's square may straddle chunks, and the item lands in whichever holds the west edge, so every
        // chunk around the square is held ticking, else the carried item stops ticking where it lands.
        List<ChunkPos> chunks = chunksAround(center, across);
        chunks.forEach(chunk -> pocket.setChunkForced(chunk.x(), chunk.z(), true));
        double eastEdge = center.getX() + (across + 1) / 2.0;
        Vec3 start = new Vec3(eastEdge - INSIDE_THE_EDGE, center.getY() + 1.5, center.getZ() + HALF);
        ItemEntity item = new ItemEntity(pocket, start.x(), start.y(), start.z(), new ItemStack(Items.STONE),
                EASTWARD, 0, 0);
        item.setNoGravity(true);
        pocket.addFreshEntity(item);
        boolean[] read = {false};
        helper.onEachTick(() -> {
            if (read[0] || item.tickCount < CROSSED_BY) {
                return;
            }
            read[0] = true;
            Vec3 now = item.position();
            double moved = now.x() - start.x();
            Vec3 motion = item.getDeltaMovement();
            item.discard();
            chunks.forEach(chunk -> pocket.setChunkForced(chunk.x(), chunk.z(), false));
            helper.assertTrue(moved > -across && moved < -across + MOVED_ON
                            && Math.abs(now.y() - start.y()) < KEPT && Math.abs(now.z() - start.z()) < KEPT,
                    String.format(SHOULD_CARRY_ACROSS, across, start, MOVED_ON, now));
            helper.assertTrue(motion.x() > 0, String.format(SHOULD_KEEP_MOTION, motion));
            helper.succeed();
        });
    }

    private static final String ROOM_EDGE_CARRIES_A_WALKER_AT_A_FRACTIONAL_EXTENT =
            "room_edge_carries_a_walker_at_a_fractional_extent";
    /** An extent ending three tenths of the way into the blocks two out from the center. */
    private static final double PARTWAY = 2.3;
    /** How far past the east edge the walker's eye stands as its client says it crossed. */
    private static final double PAST_THE_EDGE = 0.3;
    /** Ticks after the extent is set by which the server has gathered the room's new edges. */
    private static final int EDGES_GATHERED = 2;
    private static final Vec3 WALKING_EAST = new Vec3(0.21, -0.08, 0.04);
    private static final float WALKER_YAW = -84f;
    private static final float WALKER_PITCH = 12f;
    /** The drift a heading picks up from Minecraft's table sine turning it through the pair, in degrees. */
    private static final float TABLE_TRIG = 0.01f;
    private static final String SHOULD_ARRIVE_INSIDE = "A walker %.2f past the east edge at %.2f should arrive that far "
            + "inside the west edge, at %s, stands at %s";
    private static final String SHOULD_KEEP_WALKING = "The walker should keep moving %s facing %.1f, moves %s facing %.1f";

    /**
     * A player in their room whose extent ends partway through a block walks
     * past the east edge, its client saying so as a client does, and the
     * server sets it the same distance inside the west edge, moving and
     * facing as it went (decision grown-extent-is-the-one-wrap-and-build-boundary).
     *
     * @param helper the gametest helper
     */
    public static void roomEdgeCarriesAWalkerAtAFractionalExtent(GameTestHelper helper) {
        ServerPlayer walker = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        holdFor(helper, walker, CHARGE_TICKS);
        int[] room = new int[1];
        helper.runAfterDelay(CHARGE_TICKS + 1, () -> {
            room[0] = assertEntered(helper, walker);
            VoidPockets.get((ServerLevel) walker.level()).keepPlatform(walker.getUUID(),
                    new VoidPockets.Platform(PARTWAY));
        });
        helper.runAfterDelay(CHARGE_TICKS + 1 + EDGES_GATHERED, () -> {
            BlockPos center = OutsiderRooms.voidstoneOf(room[0]);
            RoomEdges.Edges edges = RoomEdges.around(center, PARTWAY);
            double middleX = center.getX() + HALF;
            Vec3 feet = new Vec3(middleX + PARTWAY + PAST_THE_EDGE, center.getY() + 1, center.getZ() + HALF);
            walker.setPos(feet);
            GatePassage.announced(walker, edges.east(), feet, WALKING_EAST, WALKER_YAW, WALKER_PITCH);
            Vec3 arrival = new Vec3(middleX - PARTWAY + PAST_THE_EDGE, feet.y(), feet.z());
            helper.assertTrue(walker.position().distanceTo(arrival) < KEPT,
                    String.format(SHOULD_ARRIVE_INSIDE, PAST_THE_EDGE, middleX + PARTWAY, arrival, walker.position()));
            helper.assertTrue(walker.getDeltaMovement().distanceTo(WALKING_EAST) < KEPT
                            && Math.abs(walker.getYRot() - WALKER_YAW) < TABLE_TRIG,
                    String.format(SHOULD_KEEP_WALKING, WALKING_EAST, WALKER_YAW, walker.getDeltaMovement(),
                            walker.getYRot()));
            helper.succeed();
        });
    }

    /**
     * The chunks a room's square and the block past each of its edges stand in.
     *
     * @param center the room's center voidstone
     * @param across the square's width in blocks
     * @return the chunks
     */
    private static List<ChunkPos> chunksAround(BlockPos center, int across) {
        int reach = across / 2 + 1;
        ChunkPos low = ChunkPos.containing(center.offset(-reach, 0, -reach));
        ChunkPos high = ChunkPos.containing(center.offset(reach, 0, reach));
        List<ChunkPos> chunks = new java.util.ArrayList<>();
        for (int x = low.x(); x <= high.x(); x++) {
            for (int z = low.z(); z <= high.z(); z++) {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    /**
     * A player in their room places stone on the floor just past the
     * extent's east side, and it is refused; placed on the floor just inside
     * it, it stands (decision grown-extent-is-the-one-wrap-and-build-boundary).
     *
     * @param helper the gametest helper
     */
    public static void refusesBuildingPastThePlatform(GameTestHelper helper) {
        ServerPlayer builder = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        holdFor(helper, builder, CHARGE_TICKS);
        helper.runAfterDelay(CHARGE_TICKS + 1, () -> {
            int room = assertEntered(helper, builder);
            ServerLevel pocket = (ServerLevel) builder.level();
            BlockPos edge = OutsiderRooms.voidstoneOf(room).east();
            BlockPos past = edge.east();
            builder.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STONE, 2));

            place(builder, past, Direction.UP);
            helper.assertTrue(pocket.getBlockState(past.above()).isAir(),
                    String.format(SHOULD_REFUSE, past.above(), pocket.getBlockState(past.above())));

            place(builder, edge, Direction.UP);
            helper.assertTrue(pocket.getBlockState(edge.above()).is(Blocks.STONE),
                    String.format(SHOULD_PLACE, edge.above(), pocket.getBlockState(edge.above())));
            helper.succeed();
        });
    }

    private static final String OUTSIDER_CONJURES_A_DOOR_THAT_GROWS = "outsider_conjures_a_door_that_grows";
    private static final String OUTSIDER_DOOR_ENVELOPS_THE_CASTER = "outsider_door_envelops_the_caster";
    private static final String OUTSIDER_DOOR_COLLAPSES_WHEN_LET_GO = "outsider_door_collapses_when_let_go";
    private static final String OUTSIDER_CONJURES_NO_DOOR_INSIDE = "outsider_conjures_no_door_inside";
    /** How far before the caster's feet the door stands while it grows. */
    private static final double DOOR_STANDS_OFF = 1.5;
    /** Ticks after a channel is let go by which its door is gone. */
    private static final int DOOR_GONE_BY = 4;
    /** Ticks a caster holds on inside the pocket, past the door the way in left. */
    private static final int HELD_ON_INSIDE = 20;
    private static final String SHOULD_CONJURE = "A channel toward the pocket should conjure a door at tick %d";
    private static final String SHOULD_STAND_BEFORE = "The door should stand %.1f before the caster's facing, "
            + "stands at %s with the caster at %s facing %.1f";
    private static final String SHOULD_GROW_DOOR = "The door should grow with the charge, sized %.3f, %.3f, %.3f";
    private static final String SHOULD_CONJURE_SOUND = "The conjuring sound should play once, sounds sent %s";
    private static final String SHOULD_ENVELOP = "The full charge's door should envelop the caster at %s, is %s";
    private static final String SHOULD_COLLAPSE = "A channel let go early should leave no door, leaves %s";
    private static final String SHOULD_STAY = "A channel let go early should leave the caster at %s in %s, stands at "
            + "%s in %s";
    private static final String SHOULD_CONJURE_NONE = "A channel inside the pocket should conjure no door, conjures %s";
    private static final String SHOULD_GROW_INSIDE = "A channel inside the pocket should grow the room past %.3f, "
            + "stands at %.3f";

    /**
     * A caster channeling Outsider from outside the pocket conjures a door
     * before them, the way they face, which grows with the held share of the
     * charge, its conjuring sound playing once at its start
     * (decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player).
     *
     * @param helper the gametest helper
     */
    public static void conjuresADoorThatGrowsWithTheCharge(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        caster.setYRot(CAST_YAW);
        PacketRecorder packets = PacketRecorder.attachTo(caster);
        holdFor(helper, caster, CHARGE_TICKS);
        double[] sizes = {-1, 0, 0};
        Vec3[] conjuredAt = new Vec3[1];
        // The door's first tick is the first the channel's program runs, read as it first stands.
        helper.onEachTick(() -> ConjuredDoors.doorOf(caster.getUUID()).filter(door -> sizes[0] < 0)
                .ifPresent(door -> {
                    Vec3 facing = Vec3.directionFromRotation(0f, caster.getYRot());
                    Vec3 expected = caster.position().add(facing.scale(DOOR_STANDS_OFF));
                    helper.assertTrue(door.at().distanceTo(expected) < FACING_KEPT, String.format(
                            SHOULD_STAND_BEFORE, DOOR_STANDS_OFF, door.at(), caster.position(), caster.getYRot()));
                    sizes[0] = door.size();
                    conjuredAt[0] = door.at();
                }));
        helper.runAfterDelay(CHARGE_TICKS / 2, () -> sizes[1] = doorOf(helper, caster, CHARGE_TICKS / 2).size());
        helper.runAfterDelay(CHARGE_TICKS, () -> {
            sizes[2] = doorOf(helper, caster, CHARGE_TICKS).size();
            helper.assertTrue(sizes[0] > 0 && sizes[0] < sizes[1] && sizes[1] < sizes[2],
                    String.format(SHOULD_GROW_DOOR, sizes[0], sizes[1], sizes[2]));
            // Casters in the gametests beside this one conjure within earshot, so only this door's sound counts.
            List<String> sounds = packets.sentOf(ClientboundSoundPacket.class).stream()
                    .filter(sound -> conjuredAt[0] != null && Math.abs(sound.getX() - conjuredAt[0].x()) < 1
                            && Math.abs(sound.getZ() - conjuredAt[0].z()) < 1)
                    .map(sound -> sound.getSound().value().location().toString()).toList();
            helper.assertTrue(sounds.stream().filter(GooSoundIds.OUTSIDER_CONJURE.toString()::equals).count() == 1,
                    String.format(SHOULD_CONJURE_SOUND, sounds));
            helper.succeed();
        });
    }

    /**
     * A channel held to its full charge moves the door onto the caster, and
     * the caster stands in their room
     * (decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player).
     *
     * @param helper the gametest helper
     */
    public static void doorEnvelopsTheCasterOnTheFullCharge(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        holdFor(helper, caster, CHARGE_TICKS);
        // Read the tick after the full charge: two callbacks on one tick run in no fixed order, so a read on the
        // charge's own tick may come before its hold; the door outlasts its last hold by a tick, so it still stands.
        helper.runAfterDelay(CHARGE_TICKS + 1, () -> {
            ConjuredDoor door = doorOf(helper, caster, CHARGE_TICKS + 1);
            assertEntered(helper, caster);
            // Where the caster stood as they went: the return point the way in kept.
            VoidPockets.ReturnPoint left = VoidPockets.get((ServerLevel) caster.level()).returnOf(caster.getUUID());
            helper.assertTrue(left != null && door.phase() == ConjuredDoor.Phase.ENVELOPING
                            && door.at().distanceTo(left.position()) < KEPT,
                    String.format(SHOULD_ENVELOP, left == null ? null : left.position(), door));
            helper.succeed();
        });
    }

    /**
     * A channel let go halfway through its charge leaves no door, and the
     * caster stands where they stood, in the level they stood in
     * (decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player).
     *
     * @param helper the gametest helper
     */
    public static void doorCollapsesWhenTheChannelIsLetGoEarly(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        ServerLevel level = helper.getLevel();
        Vec3 castFrom = caster.position();
        holdFor(helper, caster, CHARGE_TICKS / 2);
        helper.runAfterDelay(CHARGE_TICKS / 2 + DOOR_GONE_BY, () -> {
            helper.assertTrue(ConjuredDoors.doorOf(caster.getUUID()).isEmpty(),
                    String.format(SHOULD_COLLAPSE, ConjuredDoors.doorOf(caster.getUUID())));
            helper.assertTrue(caster.level() == level && caster.position().equals(castFrom),
                    String.format(SHOULD_STAY, castFrom, level.dimension().identifier(), caster.position(),
                            caster.level().dimension().identifier()));
            helper.succeed();
        });
    }

    /**
     * A caster holding on inside their room grows it and conjures no door
     * (decision channeling-to-the-pocket-conjures-a-door-that-envelops-the-player).
     *
     * @param helper the gametest helper
     */
    public static void conjuresNoDoorInsideThePocket(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.VOID, VOID_FOR_GROWTH));
        holdFor(helper, caster, CHARGE_TICKS + HELD_ON_INSIDE);
        double[] entered = new double[1];
        helper.runAfterDelay(CHARGE_TICKS + 1, () -> {
            assertEntered(helper, caster);
            entered[0] = OutsiderRooms.extentOf((ServerLevel) caster.level(), caster.getUUID());
        });
        helper.runAfterDelay(CHARGE_TICKS + HELD_ON_INSIDE, () -> {
            double extent = OutsiderRooms.extentOf((ServerLevel) caster.level(), caster.getUUID());
            helper.assertTrue(ConjuredDoors.doorOf(caster.getUUID()).isEmpty(),
                    String.format(SHOULD_CONJURE_NONE, ConjuredDoors.doorOf(caster.getUUID())));
            helper.assertTrue(extent > entered[0], String.format(SHOULD_GROW_INSIDE, entered[0], extent));
            helper.succeed();
        });
    }

    private static final String OUTSIDER_GROWS_A_BLOCK_A_SECOND = "outsider_grows_a_block_a_second";
    /** One held second inside: void_outsider.json's ring_ticks. */
    private static final int HELD_A_SECOND = 20;
    /** The void the way in costs, void_outsider.json's enter. */
    private static final int ENTER_COST = 6;
    /** Ring 2's price at the shipped cell_cost 10 and distance_cost 1: 80R + 12R squared. */
    private static final int RING_TWO_PRICE = 208;
    private static final String SHOULD_GROW_A_BLOCK = "A second held inside should grow the room from %.1f to %.1f "
            + "and drain %d void past the way in, stands at %.6f having drained %d";

    /**
     * A caster holding Outsider for a second inside their room stands in a
     * room grown one whole block on every side, their void down by the price
     * of the ring grown
     * (decision outsider-room-grows-one-block-a-second-on-every-side).
     *
     * @param helper the gametest helper
     */
    public static void growsOneBlockASecond(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.VOID, VOID_FOR_GROWTH));
        int before = GooSourceScanner.aggregateAvailable(caster).getOrDefault(GooTypes.VOID, 0);
        holdFor(helper, caster, CHARGE_TICKS + HELD_A_SECOND);
        helper.runAfterDelay(CHARGE_TICKS + HELD_A_SECOND + 1, () -> {
            double extent = OutsiderRooms.extentOf((ServerLevel) caster.level(), caster.getUUID());
            int drained = before - ENTER_COST
                    - GooSourceScanner.aggregateAvailable(caster).getOrDefault(GooTypes.VOID, 0);
            helper.assertTrue(Math.abs(extent - FIRST_RING_WHOLE) < KEPT && drained == RING_TWO_PRICE,
                    String.format(SHOULD_GROW_A_BLOCK, RoomExtent.LAID, FIRST_RING_WHOLE, RING_TWO_PRICE, extent,
                            drained));
            helper.succeed();
        });
    }

    private static ConjuredDoor doorOf(GameTestHelper helper, ServerPlayer caster, int tick) {
        Optional<ConjuredDoor> door = ConjuredDoors.doorOf(caster.getUUID());
        helper.assertTrue(door.isPresent(), String.format(SHOULD_CONJURE, tick));
        return door.orElseThrow();
    }

    /** How near the door stands to the spot before the caster's facing: within the mock caster's drift in a tick. */
    private static final double FACING_KEPT = 0.1;

    private static final String OUTSIDER_WUB_REACHES_THE_CORNER = "outsider_wub_reaches_the_corner";
    /** A wide room's extent, just short of a whole-block mark, so its corner stands far past a sound's own reach. */
    private static final double WIDE_SHORT_OF_A_MARK = 20.45;
    /** How far inside the corner the caster stands, on each axis. */
    private static final double INSIDE_THE_CORNER = 0.6;
    /** Void enough to carry the wide room across its next whole-block mark. */
    private static final int FEED_ACROSS_THE_MARK = 5000;
    /** The fullest a sound plays, which a wub heard where its player stands reaches. */
    private static final float FULL = 1f;
    private static final String SHOULD_HEAR_IN_THE_CORNER = "A player in the room's corner at %s should hear the wub "
            + "at full volume where they stand; wubs sent to them %s";

    /**
     * A player standing in the far corner of a wide room hears the wub as
     * the room gains a full block, at full volume where they stand
     * (decision full-block-growth-wubs-and-raises-void-motes).
     *
     * @param helper the gametest helper
     */
    public static void wubReachesTheRoomsCorner(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        holdFor(helper, caster, CHARGE_TICKS);
        helper.runAfterDelay(CHARGE_TICKS + 1, () -> {
            int room = assertEntered(helper, caster);
            ServerLevel pocket = (ServerLevel) caster.level();
            VoidPockets.get(pocket).keepPlatform(caster.getUUID(), new VoidPockets.Platform(WIDE_SHORT_OF_A_MARK));
            BlockPos center = OutsiderRooms.voidstoneOf(room);
            double corner = WIDE_SHORT_OF_A_MARK - INSIDE_THE_CORNER;
            caster.setPos(center.getX() + HALF + corner, center.getY() + 1, center.getZ() + HALF + corner);
            PacketRecorder packets = PacketRecorder.attachTo(caster);
            OutsiderRooms.feedPlatform(caster, FEED_ACROSS_THE_MARK, 10, 1);
            List<ClientboundSoundPacket> wubs = packets.sentOf(ClientboundSoundPacket.class).stream()
                    .filter(sound -> sound.getSound().value().location().equals(GooSoundIds.OUTSIDER_WUB))
                    .toList();
            List<String> heard = wubs.stream().map(sound -> String.format("(%.1f, %.1f, %.1f) at volume %.2f",
                    sound.getX(), sound.getY(), sound.getZ(), sound.getVolume())).toList();
            helper.assertTrue(wubs.stream().anyMatch(sound -> caster.position().distanceTo(
                            new Vec3(sound.getX(), sound.getY(), sound.getZ())) < 1 && sound.getVolume() >= FULL),
                    String.format(SHOULD_HEAR_IN_THE_CORNER, caster.position(), heard));
            helper.succeed();
        });
    }

    private static void place(ServerPlayer builder, BlockPos against, Direction face) {
        Vec3 hit = Vec3.atCenterOf(against).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(HALF));
        builder.getOffhandItem().useOn(new UseOnContext(builder, InteractionHand.OFF_HAND,
                new BlockHitResult(hit, face, against, false)));
    }

    private static final double HALF = 0.5;

    private static final String OUTSIDER_SHOWS_ITS_CHARGE_AND_GROWTH = "outsider_shows_its_charge_and_growth";
    /** Ticks held inside before the first ring's cost is fed over the ring time, and a tick more. */
    private static final int HELD_TO_FIRST_RING = 21;
    private static final String SHOULD_SHOW_CHARGE = "A hold short of its charge should send void motes, sent %d particle packets";
    private static final String SHOULD_WUB = "A full block gained should wub, sounds sent %s";

    /**
     * A caster charging Outsider sees void motes gather before the charge
     * lands, and once inside, the first full block the hold gains wubs
     * (decision full-block-growth-wubs-and-raises-void-motes).
     *
     * @param helper the gametest helper
     */
    public static void showsItsChargeAndGrowth(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.VOID, VOID_FOR_GROWTH));
        PacketRecorder packets = PacketRecorder.attachTo(caster);
        holdFor(helper, caster, CHARGE_TICKS + HELD_TO_FIRST_RING);
        helper.runAfterDelay(CHARGE_TICKS - 1, () -> {
            int motes = packets.sentOf(ClientboundLevelParticlesPacket.class).size();
            helper.assertTrue(motes > 0, String.format(SHOULD_SHOW_CHARGE, motes));
        });
        helper.runAfterDelay(CHARGE_TICKS + HELD_TO_FIRST_RING + 1, () -> {
            List<String> sounds = packets.sentOf(ClientboundSoundPacket.class).stream()
                    .map(sound -> sound.getSound().value().location().toString()).toList();
            helper.assertTrue(sounds.contains(GooSoundIds.OUTSIDER_WUB.toString()),
                    String.format(SHOULD_WUB, sounds));
            helper.succeed();
        });
    }

    private static final String OUTSIDER_POCKET_SHOWS_NO_SKY = "outsider_pocket_shows_no_sky";
    private static final ResourceKey<Biome> POCKET_VOID = ResourceKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath(Goo.MODID, "pocket_void"));
    private static final String SHOULD_DRAW_NO_SKY = "The pocket should draw no skybox, so no sun, draws %s";
    private static final String SHOULD_BE_VOID_BIOME = "The pocket's rooms should stand in goo:pocket_void, stand in %s";

    /**
     * The pocket dimension draws no skybox, so neither sun nor moon nor
     * stars, and its rooms stand in a biome of black sky and fog: nothing
     * above, around or below.
     *
     * @param helper the gametest helper
     */
    public static void pocketShowsNoSky(GameTestHelper helper) {
        ServerLevel pocket = helper.getLevel().getServer().getLevel(OutsiderRooms.POCKET);
        helper.assertTrue(pocket != null, "The server should hold the pocket dimension");
        helper.assertTrue(pocket.dimensionType().skybox() == DimensionType.Skybox.NONE,
                String.format(SHOULD_DRAW_NO_SKY, pocket.dimensionType().skybox()));
        Holder<Biome> biome = pocket.getBiome(OutsiderRooms.voidstoneOf(0));
        helper.assertTrue(biome.is(POCKET_VOID), String.format(SHOULD_BE_VOID_BIOME, biome.getRegisteredName()));
        helper.succeed();
    }

    private static final String OUTSIDER_FLOOR_STANDS_WHOLE = "outsider_floor_stands_whole_and_growth_places_no_block";
    /**
     * Ticks the caster keeps holding once inside: one ring time, as
     * void_outsider.json's ring_ticks names, and a tick more, so the feed
     * carries the extent across the first whole-block mark.
     */
    private static final int HELD_INSIDE = 21;
    /** How far out the floor is checked and watched: well past the starting 3 by 3. */
    private static final int FLOOR_REACH = 6;
    /** The rows watched for change: the floor and the two above it. */
    private static final int WATCHED_ROWS = 3;
    /** The extent once the first grown ring is whole: the 5 by 5 around the center. */
    private static final double FIRST_RING_WHOLE = 2.5;
    private static final int VOID_FOR_GROWTH = 20 * GooStacks.THOUSAND;
    private static final String SHOULD_FLOOR = "Voidstone should stand at the floor at %s, beyond the starting extent, "
            + "%s stands there";
    private static final String SHOULD_RISE = "The room's extent should rise past %.1f, stands at %.3f";
    private static final String SHOULD_PLACE_NOTHING = "Growth should place no block, %s at %s became %s";

    /**
     * A new room's voidstone floor stands well beyond its starting extent
     * the moment it is laid, and a caster held on inside for a ring time
     * carries the extent across a whole-block mark while no block in the
     * room changes (decision voidstone-floor-stands-whole-and-growth-moves-the-boundary).
     *
     * @param helper the gametest helper
     */
    public static void floorStandsWholeAndGrowthPlacesNoBlock(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.VOID, VOID_FOR_GROWTH));
        holdFor(helper, caster, CHARGE_TICKS + HELD_INSIDE);
        Map<BlockPos, BlockState> laid = new HashMap<>();
        helper.runAfterDelay(CHARGE_TICKS + 1, () -> {
            ServerLevel pocket = (ServerLevel) caster.level();
            BlockPos center = OutsiderRooms.voidstoneOf(assertEntered(helper, caster));
            for (Direction side : Direction.Plane.HORIZONTAL) {
                BlockPos floor = center.relative(side, FLOOR_REACH);
                helper.assertTrue(pocket.getBlockState(floor).is(GooBlocks.VOIDSTONE.get()),
                        String.format(SHOULD_FLOOR, floor, pocket.getBlockState(floor)));
            }
            BlockPos.betweenClosed(center.offset(-FLOOR_REACH, 0, -FLOOR_REACH),
                            center.offset(FLOOR_REACH, WATCHED_ROWS - 1, FLOOR_REACH))
                    .forEach(at -> laid.put(at.immutable(), pocket.getBlockState(at)));
        });
        helper.runAfterDelay(CHARGE_TICKS + HELD_INSIDE + 1, () -> {
            ServerLevel pocket = (ServerLevel) caster.level();
            double extent = OutsiderRooms.extentOf(pocket, caster.getUUID());
            helper.assertTrue(extent > FIRST_RING_WHOLE, String.format(SHOULD_RISE, FIRST_RING_WHOLE, extent));
            laid.forEach((at, state) -> helper.assertTrue(pocket.getBlockState(at) == state,
                    String.format(SHOULD_PLACE_NOTHING, state, at, pocket.getBlockState(at))));
            helper.succeed();
        });
    }

    private static final Identifier OUTSIDER = Identifier.parse("goo:void_outsider");
    /** The hold Outsider asks before it carries the caster in: one charge. */
    private static final int CHARGE_TICKS = 20;
    private static final float CAST_YAW = 37f;
    private static final double FALL_BELOW_FLOOR = 20;
    private static final double FALL_ASIDE = 6;
    private static final float FALLEN = 30f;
    /** The yaw facing north, toward the door from the arrival cell south of it. */
    private static final float FACING_DOOR = 180f;
    private static final String SHOULD_WAIT_FOR_THE_CHARGE = "A hold short of its charge should leave the caster in %s";
    private static final String SHOULD_ENTER = "The caster should stand south of the door at %s facing it in goo:pocket, stands at %s facing %s in %s";
    private static final String SHOULD_STAND_DOOR = "The room should hold a dimension doorway at %s, holds %s";
    private static final String SHOULD_STAND_OPEN = "The doorway should stand alone, two tall, with open air at %s, %s stands there";
    private static final String SHOULD_RETURN = "The door should return the caster to %s facing %s in %s, they stand at %s facing %s in %s";
    private static final String SHOULD_RESPAWN = "The %s at %s should stand again, %s stands there";
    private static final String SHOULD_CATCH = "The faller should stand south of the door at %s, stands at %s";
    private static final String SHOULD_BE_UNHARMED = "The faller should keep %.1f health with no fall built up, has %.1f and %.1f";

    private OutsiderTests() {
    }

    /**
     * A caster holding Outsider stays put through a hold short of its charge;
     * the charge's tick carries them into their room in goo:pocket, onto the
     * voidstone south of the two-tall doorway standing alone on the center,
     * facing it; walking into the doorway sets them back at the exact
     * place and facing they cast from.
     *
     * @param helper the gametest helper
     */
    public static void outsiderCarriesInAndTheDoorReturns(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        caster.setYRot(CAST_YAW);
        Vec3 castFrom = caster.position();
        holdFor(helper, caster, CHARGE_TICKS - 1);
        helper.runAfterDelay(CHARGE_TICKS - 1, () -> helper.assertTrue(caster.level() == level,
                String.format(SHOULD_WAIT_FOR_THE_CHARGE, caster.level().dimension().identifier())));
        holdAt(helper, caster, CHARGE_TICKS);
        helper.runAfterDelay(CHARGE_TICKS + 1, () -> {
            int room = assertEntered(helper, caster);
            ServerLevel pocket = (ServerLevel) caster.level();
            BlockPos door = doorOf(room);
            for (BlockPos doorway : List.of(door, door.above())) {
                helper.assertTrue(pocket.getBlockState(doorway).is(GooBlocks.DIMENSION_DOOR.get()),
                        String.format(SHOULD_STAND_DOOR, doorway, pocket.getBlockState(doorway)));
            }
            for (BlockPos around : aroundDoorwayOf(room)) {
                helper.assertTrue(pocket.getBlockState(around).isAir(),
                        String.format(SHOULD_STAND_OPEN, around, pocket.getBlockState(around)));
            }
            TeleportTransition home = GooBlocks.DIMENSION_DOOR.get().getPortalDestination(pocket, caster, door);
            helper.assertTrue(home != null, String.format(SHOULD_STAND_DOOR, door, "no way home"));
            caster.teleport(home);
            helper.assertTrue(caster.level() == level && caster.position().equals(castFrom)
                            && caster.getYRot() == CAST_YAW,
                    String.format(SHOULD_RETURN, castFrom, CAST_YAW, level.dimension().identifier(),
                            caster.position(), caster.getYRot(), caster.level().dimension().identifier()));
            helper.succeed();
        });
    }

    /**
     * Once the caster stands in their room, the center voidstone and both
     * doorway cells removed stand again by the next restore check.
     *
     * @param helper the gametest helper
     */
    public static void doorAndVoidstoneRespawn(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        holdFor(helper, caster, CHARGE_TICKS);
        int[] room = new int[1];
        helper.runAfterDelay(CHARGE_TICKS + 1, () -> {
            room[0] = assertEntered(helper, caster);
            ServerLevel pocket = (ServerLevel) caster.level();
            BlockPos center = OutsiderRooms.voidstoneOf(room[0]);
            for (BlockPos removed : List.of(doorOf(room[0]), doorOf(room[0]).above(), center)) {
                pocket.setBlockAndUpdate(removed, Blocks.AIR.defaultBlockState());
            }
        });
        helper.runAfterDelay(CHARGE_TICKS + 2 + OutsiderRooms.RESTORE_INTERVAL, () -> {
            ServerLevel pocket = (ServerLevel) caster.level();
            BlockPos center = OutsiderRooms.voidstoneOf(room[0]);
            BlockPos door = doorOf(room[0]);
            helper.assertTrue(pocket.getBlockState(center).is(GooBlocks.VOIDSTONE.get()),
                    String.format(SHOULD_RESPAWN, "voidstone", center, pocket.getBlockState(center)));
            for (BlockPos doorway : List.of(door, door.above())) {
                helper.assertTrue(pocket.getBlockState(doorway).is(GooBlocks.DIMENSION_DOOR.get()),
                        String.format(SHOULD_RESPAWN, "doorway", doorway, pocket.getBlockState(doorway)));
            }
            helper.succeed();
        });
    }

    /**
     * A caster in their room who falls off the platform, well below the
     * floor with a long fall built up, stands back south of the door after
     * their next tick with their health whole and no fall left to land.
     *
     * @param helper the gametest helper
     */
    public static void voidCatchesTheFaller(GameTestHelper helper) {
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.VOID);
        holdFor(helper, caster, CHARGE_TICKS);
        helper.runAfterDelay(CHARGE_TICKS + 1, () -> {
            int room = assertEntered(helper, caster);
            Vec3 arrival = arrivalOf(room);
            float health = caster.getHealth();
            caster.setPos(arrival.x + FALL_ASIDE, arrival.y - FALL_BELOW_FLOOR, arrival.z);
            caster.fallDistance = FALLEN;
            caster.doTick();
            helper.assertTrue(caster.position().equals(arrival),
                    String.format(SHOULD_CATCH, arrival, caster.position()));
            helper.assertTrue(caster.getHealth() == health && caster.fallDistance == 0,
                    String.format(SHOULD_BE_UNHARMED, health, caster.getHealth(), caster.fallDistance));
            helper.succeed();
        });
    }

    private static void holdFor(GameTestHelper helper, ServerPlayer caster, int ticks) {
        for (int held = 1; held <= ticks; held++) {
            holdAt(helper, caster, held);
        }
    }

    private static void holdAt(GameTestHelper helper, ServerPlayer caster, int tick) {
        helper.runAfterDelay(tick, () -> GooStreamHandler.streamTick(caster,
                new GooStreamPayload(GooTypes.id(GooTypes.VOID), OUTSIDER.toString(), caster.getEyePosition(),
                        caster.getEyePosition().add(caster.getLookAngle()), caster.blockPosition().below(),
                        GooStreamPayload.NO_FACE)));
    }

    private static int assertEntered(GameTestHelper helper, ServerPlayer caster) {
        ServerLevel pocket = helper.getLevel().getServer().getLevel(OutsiderRooms.POCKET);
        Integer room = pocket == null ? null : OutsiderRooms.roomOf(pocket, caster.getUUID());
        Vec3 arrival = room == null ? Vec3.ZERO : arrivalOf(room);
        helper.assertTrue(room != null && caster.level() == pocket && caster.position().equals(arrival)
                        && caster.getYRot() == FACING_DOOR,
                String.format(SHOULD_ENTER, arrival, caster.position(), caster.getYRot(),
                        caster.level().dimension().identifier()));
        return room;
    }

    /** The doorway's lower block: standing on the center voidstone. */
    private static BlockPos doorOf(int room) {
        return OutsiderRooms.voidstoneOf(room).above();
    }

    /** The open air the doorway stands in: either side of both its cells, and over it. */
    private static List<BlockPos> aroundDoorwayOf(int room) {
        BlockPos door = doorOf(room);
        return List.of(door.east(), door.east().above(), door.west(), door.west().above(), door.above(2));
    }

    /** Where a player arrives: on the voidstone just south of the door. */
    private static Vec3 arrivalOf(int room) {
        return Vec3.atBottomCenterOf(OutsiderRooms.voidstoneOf(room).south().above());
    }
}
