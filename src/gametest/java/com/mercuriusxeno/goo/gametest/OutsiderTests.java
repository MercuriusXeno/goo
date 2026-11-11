package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.PlatformSpiral;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.GooStreamPayload;
import com.mercuriusxeno.goo.network.SelfDeliveryTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
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
import net.minecraft.sounds.SoundEvents;
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
        GooTestFunctions.registerTest(registrar, OUTSIDER_REFUSES_BUILDING_PAST_THE_PLATFORM,
                OutsiderTests::refusesBuildingPastThePlatform);
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
        int across = 2 * PlatformSpiral.ringOf(PlatformSpiral.START_CELLS - 1) + 1;
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

    private static void place(ServerPlayer builder, BlockPos against, Direction face) {
        Vec3 hit = Vec3.atCenterOf(against).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(HALF));
        builder.getOffhandItem().useOn(new UseOnContext(builder, InteractionHand.OFF_HAND,
                new BlockHitResult(hit, face, against, false)));
    }

    private static final double HALF = 0.5;

    private static final String OUTSIDER_SHOWS_ITS_CHARGE_AND_GROWTH = "outsider_shows_its_charge_and_growth";
    /** Ticks held inside before the first ring's cost is fed over the ring time, and a tick more. */
    private static final int HELD_TO_FIRST_RING = 41;
    private static final String SHOULD_SHOW_CHARGE = "A hold short of its charge should send void motes, sent %d particle packets";
    private static final String SHOULD_CHIME = "A whole ring gained should chime, sounds sent %s";

    /**
     * A caster charging Outsider sees void motes gather before the charge
     * lands, and once inside, the first whole ring the hold gains chimes.
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
            helper.assertTrue(sounds.contains(SoundEvents.AMETHYST_BLOCK_CHIME.location().toString()),
                    String.format(SHOULD_CHIME, sounds));
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
    private static final int HELD_INSIDE = 41;
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
