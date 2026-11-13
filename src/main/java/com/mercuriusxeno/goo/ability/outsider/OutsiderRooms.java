package com.mercuriusxeno.goo.ability.outsider;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.outsider.DimensionDoorBlockEntity;
import com.mercuriusxeno.goo.network.RoomEdgesSyncPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooParticles;
import com.mercuriusxeno.goo.registry.GooSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Void's Outsider rooms in goo:pocket, an empty dimension where each
 * player's room stands apart from every other and keeps whatever was built
 * in it. The floor is voidstone, standing whole across the pocket from the
 * moment a room is laid, {@link PocketFloor}; a room starts with an extent
 * holding the 9 by 9 around its center, where a two-tall, door-shaped
 * dimension doorway stands alone on the center voidstone, and the player arrives on the block just south of the doorway,
 * facing it. Walking into the doorway leads back to where the player cast
 * Outsider from; the doorway and the center voidstone stand again if
 * anything removes them. The room wraps: its edges are linked gates,
 * {@link RoomEdges}, so a player walking off one side of the grown square
 * carries on in from the opposite side, and only a player falling below the
 * floor, a failsafe for creative flight, is set back south of the door
 * unharmed, the pocket folding them back. A block may be placed only where
 * it lies wholly inside the room's extent. Each player's room is saved to
 * them forever, and every way in, catch and bound reads that room alone.
 * Rooms stand a million blocks apart along {@link RoomGrid}. Held on inside,
 * Outsider feeds void into the room, whose {@link RoomExtent} grows outward
 * on all sides at once. Each part shows itself: void motes spiral in and a
 * hum rises while the hold charges, void bursts where the player leaves and
 * arrives, motes stream off the player while they feed the room, and each
 * full block the extent gains wubs and raises void motes along every wall.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 * decision outsider-room-starts-nine-by-nine
 * decision outsider-room-wraps-and-repeats-at-the-platform-edge
 * decision outsider-room-grows-fractionally-on-all-sides-at-once
 * decision voidstone-floor-stands-whole-and-growth-moves-the-boundary
 * decision grown-extent-is-the-one-wrap-and-build-boundary
 * decision full-block-growth-wubs-and-raises-void-motes
 */
public final class OutsiderRooms {

    /** The empty dimension the rooms stand in. */
    public static final ResourceKey<Level> POCKET = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(Goo.MODID, "pocket"));
    /** Ticks between checks that every loaded room still holds its platform and door. */
    public static final int RESTORE_INTERVAL = 20;
    /** The height of every room's floor. */
    public static final int ROOM_FLOOR_Y = 63;
    /** How far below the floor a falling player drops before the pocket folds them back. */
    static final int CATCH_DEPTH = 8;
    /** The yaw facing north, toward the door from the arrival cell south of it. */
    private static final float FACING_DOOR = 180f;
    private static final int FOLD_PARTICLES = 48;
    private static final double FOLD_SPREAD = 0.5;
    private static final double FOLD_SPEED = 0.6;
    private static final float FOLD_VOLUME = 0.6f;
    private static final float FOLD_PITCH = 0.7f;
    private static final int CHARGE_MOTES = 6;
    private static final double CHARGE_RADIUS = 1.4;
    private static final double CHARGE_TURN = 0.35;
    private static final double CHARGE_LIFT = 0.8;
    private static final double MOTE_FLOOR = 0.3;
    private static final int HUM_EVERY = 5;
    private static final float HUM_VOLUME = 0.35f;
    private static final float HUM_PITCH = 0.6f;
    private static final int BURST_PARTICLES = 40;
    private static final double BURST_SPREAD = 0.4;
    private static final double BURST_SPEED = 0.5;
    private static final int FEED_MOTES = 2;
    private static final double FEED_SPREAD = 0.35;
    private static final double FEED_SPEED = 0.05;
    /** The door's sound plays where its player stands, as loud as the door's conjuring. */
    private static final float DOOR_VOLUME = 0.8f;
    /** The wub plays at full volume where its player stands. */
    private static final float WUB_VOLUME = 1f;
    /** The step a wub's pitch moves by, as the operator ruled: shifts in increments of 0.125. */
    private static final float WUB_PITCH_STEP = 0.125f;
    /** The most steps a wub's pitch moves up or down: three, from 0.625 to 1.375. */
    private static final int WUB_PITCH_STEPS = 3;
    /** The sides a wub's pitch steps to, below its own and above it. */
    private static final int PITCH_SIDES = 2;
    /** The most motes one wall's line holds, past which they space out. */
    private static final int MOTES_PER_WALL = 48;
    /** How fast a wall's motes float up on average, blocks a tick. */
    private static final double MOTE_RISE = 0.025;
    /** How widely a mote's rise spreads about the average, as a share of it from slowest to fastest. */
    private static final double MOTE_RISE_SPAN = 1.2;
    /** How high off the floor a mote may start, in blocks. */
    private static final double MOTE_SCATTER_UP = 1.2;
    /** The widest sideways drift a mote takes, blocks a tick. */
    private static final double MOTE_DRIFT = 0.012;
    /** The chunks around a room's center loaded ahead of an entry: its own and the ring the arrival may fall in. */
    private static final int ROOM_LOAD_RADIUS = 2;
    /** The middle of a random share, which a sideways drift is centred on. */
    private static final double HALF = 0.5;

    private OutsiderRooms() {
    }

    /**
     * The voidstone at a room's center, the platform's cell 0, which the door stands on.
     *
     * @param room the room number
     * @return the voidstone's block
     */
    public static BlockPos voidstoneOf(int room) {
        PlatformSpiral.Cell center = RoomGrid.centerOf(room);
        return new BlockPos(center.x(), ROOM_FLOOR_Y, center.z());
    }

    /**
     * The doorway's lower block, standing on the center voidstone; its upper
     * block stands on it.
     *
     * @param room the room number
     * @return the doorway's lower block
     */
    public static BlockPos doorOf(int room) {
        return voidstoneOf(room).above();
    }

    /**
     * The doorway's two cells, lower then upper.
     *
     * @param room the room number
     * @return the doorway's blocks
     */
    private static List<BlockPos> doorwayOf(int room) {
        BlockPos door = doorOf(room);
        return List.of(door, door.above());
    }


    /**
     * Where a player arriving in a room stands: on the voidstone just south of the door.
     *
     * @param room the room number
     * @return the arrival point
     */
    public static Vec3 arrivalOf(int room) {
        return Vec3.atBottomCenterOf(voidstoneOf(room).south().above());
    }

    /**
     * A player's room number, if one was handed out.
     *
     * @param level  any level of the server
     * @param player the player's id
     * @return the room number, or null for a player with no room yet
     */
    public static @Nullable Integer roomOf(ServerLevel level, UUID player) {
        return VoidPockets.get(level).roomOf(player);
    }

    /**
     * The void a tick of the hold feeds a player's room: the cost of the ring
     * its extent grows through spread over the ring time.
     *
     * @param player      the player in their room
     * @param ringTicks   the ticks every ring takes
     * @param base        the void a cell costs at the center (mB)
     * @param perDistance the void each block of Manhattan distance adds (mB)
     * @return the feed (mB)
     */
    public static int feedOf(ServerPlayer player, int ringTicks, int base, int perDistance) {
        double extent = extentOf(player.level(), player.getUUID());
        return (int) Math.min(Integer.MAX_VALUE, RoomExtent.feedFor(extent, base, perDistance, ringTicks));
    }

    /**
     * How far a player's room has grown.
     *
     * @param level  any level of the server
     * @param player the player's id
     * @return the room's {@link RoomExtent}
     */
    public static double extentOf(ServerLevel level, UUID player) {
        return VoidPockets.get(level).platformOf(player).extent();
    }

    /**
     * Feeds void into the room a player stands in: its extent rises by the
     * share of a block the feed covers, on every side at once, and places no
     * block, the floor already standing whole. A feed carrying the extent
     * across a whole-block mark wubs and raises motes, {@link #cueGrowth}.
     * decision outsider-room-grows-fractionally-on-all-sides-at-once
     * decision voidstone-floor-stands-whole-and-growth-moves-the-boundary
     *
     * @param player      the player in their room
     * @param feed        the void fed (mB)
     * @param base        the void a cell costs at the center (mB)
     * @param perDistance the void each block of Manhattan distance from the center adds (mB)
     */
    public static void feedPlatform(ServerPlayer player, int feed, int base, int perDistance) {
        ServerLevel pocket = player.level();
        VoidPockets pockets = VoidPockets.get(pocket);
        Integer room = pockets.roomOf(player.getUUID());
        if (room == null || pocket.dimension() != POCKET) {
            return;
        }
        pocket.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX(), player.getY() + 1, player.getZ(),
                FEED_MOTES, FEED_SPREAD, FEED_SPREAD, FEED_SPREAD, FEED_SPEED);
        double before = pockets.platformOf(player.getUUID()).extent();
        double after = RoomExtent.grown(before, feed, base, perDistance);
        pockets.keepPlatform(player.getUUID(), new VoidPockets.Platform(after));
        cueGrowth(room, before, after, new PocketCues(pocket, player));
        announceGrowth(room, before, after, payload -> sendTo(player, payload));
    }

    /** Where a room's growth shows itself. */
    interface GrowthCues {

        /** Plays the room's wub to its player. */
        void wub();

        /**
         * Raises a line of void motes along one wall of the boundary.
         *
         * @param from the line's one end
         * @param to   its other end
         */
        void moteLine(Vec3 from, Vec3 to);
    }

    /**
     * Shows a feed that carried a room's extent across a whole-block mark,
     * the moment a new ring of blocks becomes buildable: the room wubs once,
     * and each of its four walls raises a line of void motes along the new
     * boundary at the floor's top. A feed crossing no mark shows nothing.
     * decision full-block-growth-wubs-and-raises-void-motes
     *
     * @param room         the room number
     * @param extentBefore the room's extent before the feed
     * @param extentAfter  its extent after
     * @param cues         where the growth shows
     */
    static void cueGrowth(int room, double extentBefore, double extentAfter, GrowthCues cues) {
        if (RoomExtent.wholeRings(extentAfter) <= RoomExtent.wholeRings(extentBefore)) {
            return;
        }
        BlockPos center = voidstoneOf(room);
        RoomExtent.Planes planes = RoomExtent.planesAround(center, extentAfter);
        double floorTop = ROOM_FLOOR_Y + 1;
        cues.wub();
        cues.moteLine(new Vec3(planes.east(), floorTop, planes.north()), new Vec3(planes.east(), floorTop,
                planes.south()));
        cues.moteLine(new Vec3(planes.west(), floorTop, planes.north()), new Vec3(planes.west(), floorTop,
                planes.south()));
        cues.moteLine(new Vec3(planes.west(), floorTop, planes.south()), new Vec3(planes.east(), floorTop,
                planes.south()));
        cues.moteLine(new Vec3(planes.west(), floorTop, planes.north()), new Vec3(planes.east(), floorTop,
                planes.north()));
    }

    /**
     * The pocket's own growth cues: the wub sent to the room's own player
     * alone, the one player who hears it, at full volume where they stand,
     * as the operator ruled once a player in a wide room's corner heard
     * nothing of a wub played from its center; one of its pooled swells
     * picked by the game and its pitch moved by a whole number of 0.125
     * steps, up to three either way, as the operator ruled;
     * and each line of motes sent as about a speck a block, thinned past a
     * cap so a long wall stays a line without flooding the client, each
     * speck rising slowly.
     * decision full-block-growth-wubs-and-raises-void-motes
     *
     * @param pocket the pocket dimension
     * @param player the room's player, feeding it
     */
    private record PocketCues(ServerLevel pocket, ServerPlayer player) implements GrowthCues {

        @Override
        public void wub() {
            RandomSource random = pocket.getRandom();
            int steps = random.nextInt(PITCH_SIDES * WUB_PITCH_STEPS + 1) - WUB_PITCH_STEPS;
            float pitch = 1f + steps * WUB_PITCH_STEP;
            player.connection.send(new ClientboundSoundPacket(GooSounds.OUTSIDER_WUB, SoundSource.PLAYERS,
                    player.getX(), player.getY(), player.getZ(), WUB_VOLUME, pitch, random.nextLong()));
        }

        @Override
        public void moteLine(Vec3 from, Vec3 to) {
            // The operator found motes at even steps rising alike read as balls on a grid, so each speck takes its
            // own spot along the wall, its own height off the floor, and its own rise and drift.
            RandomSource random = pocket.getRandom();
            int motes = (int) Math.min(MOTES_PER_WALL, Math.ceil(from.distanceTo(to)));
            for (int mote = 0; mote <= motes; mote++) {
                Vec3 at = from.lerp(to, random.nextDouble()).add(0, random.nextDouble() * MOTE_SCATTER_UP, 0);
                double rise = MOTE_RISE * (1 + (random.nextDouble() - HALF) * MOTE_RISE_SPAN);
                pocket.sendParticles(GooParticles.VOID_MOTE.get(), false, true, at.x, at.y, at.z, 0,
                        (random.nextDouble() - HALF) * MOTE_DRIFT, rise, (random.nextDouble() - HALF) * MOTE_DRIFT,
                        1);
            }
        }
    }

    /**
     * Whether the pocket folds a player back onto their arrival cell: only
     * once they fall the catch depth below the floor, a failsafe for a
     * player flying in creative, since walking off an edge carries them in
     * from the opposite one.
     * outsider-room-wraps-and-repeats-at-the-platform-edge
     *
     * @param feet the player's feet
     * @return true where the pocket folds them back
     */
    static boolean foldsBack(Vec3 feet) {
        return feet.y() < ROOM_FLOOR_Y - CATCH_DEPTH;
    }

    /**
     * Sends a room's new edges whenever a feed moved its extent, every fed
     * tick, so its player's client carries them across, and draws the room
     * through them, at the planes where they stand now.
     * decision outsider-room-wraps-and-repeats-at-the-platform-edge
     * decision grown-extent-is-the-one-wrap-and-build-boundary
     *
     * @param room         the room number
     * @param extentBefore the room's extent before the feed
     * @param extentAfter  its extent after
     * @param send         sends the payload to the room's player
     */
    static void announceGrowth(int room, double extentBefore, double extentAfter,
            Consumer<RoomEdgesSyncPayload> send) {
        if (Double.compare(extentAfter, extentBefore) != 0) {
            send.accept(new RoomEdgesSyncPayload(RoomEdges.pairsOf(room, extentAfter)));
        }
    }

    /**
     * Tells a player standing in the pocket their room's edges.
     *
     * @param player the player
     */
    public static void sendEdges(ServerPlayer player) {
        VoidPockets pockets = VoidPockets.get(player.level());
        Integer room = pockets.roomOf(player.getUUID());
        if (room != null && player.level().dimension() == POCKET) {
            sendTo(player, new RoomEdgesSyncPayload(RoomEdges.pairsOf(room,
                    pockets.platformOf(player.getUUID()).extent())));
        }
    }

    /**
     * Holds the far side of each room edge a player stands near loaded, as
     * a linked portal holds its partner's chunk, so their crossing lands on
     * ground the server already holds however wide the room. The tickets
     * lapse on their own, so a player who walks away from the edge lets the
     * far side go.
     * decision outsider-room-wraps-and-repeats-at-the-platform-edge
     *
     * @param player a player in the pocket
     */
    public static void holdFarSideLoaded(ServerPlayer player) {
        if (player.level().dimension() != POCKET || !(player.level() instanceof ServerLevel pocket)) {
            return;
        }
        VoidPockets pockets = VoidPockets.get(pocket);
        Integer room = pockets.roomOf(player.getUUID());
        if (room == null) {
            return;
        }
        BlockPos center = voidstoneOf(room);
        RoomExtent.Bounds bounds = new RoomExtent.Bounds(center.getX(), center.getZ(),
                pockets.platformOf(player.getUUID()).extent());
        for (RoomFarSide.Spot spot : RoomFarSide.across(bounds, player.getX(), player.getZ())) {
            pocket.getChunkSource().addTicketWithRadius(TicketType.PORTAL,
                    ChunkPos.containing(BlockPos.containing(spot.x(), ROOM_FLOOR_Y, spot.z())), ROOM_LOAD_RADIUS);
        }
    }

    private static void sendTo(ServerPlayer player, RoomEdgesSyncPayload payload) {
        // A listener that never negotiated the mod's channels, a gametest's mock player, gets no sync.
        if (player.connection.hasChannel(payload)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /**
     * Whether a player may place a block at a spot: only in the pocket, in
     * a column whose whole footprint lies inside the player's own room's
     * extent, so a block the boundary cuts refuses; anywhere outside the
     * pocket is not this rule's to judge.
     * decision grown-extent-is-the-one-wrap-and-build-boundary
     *
     * @param player the player placing
     * @param pos    the block placed
     * @return true where the placement may stand
     */
    public static boolean mayBuildAt(ServerPlayer player, BlockPos pos) {
        if (player.level().dimension() != POCKET) {
            return true;
        }
        VoidPockets pockets = VoidPockets.get(player.level());
        Integer room = pockets.roomOf(player.getUUID());
        if (room == null) {
            return false;
        }
        BlockPos center = voidstoneOf(room);
        return new RoomExtent.Bounds(center.getX(), center.getZ(), pockets.platformOf(player.getUUID()).extent())
                .holdsWholeBlock(pos.getX(), pos.getZ());
    }

    /**
     * Carries a player from where they stand into their room, keeping where
     * they cast from for the door, and laying the room the first time; a
     * player already in the pocket, or a server not holding it, goes nowhere.
     *
     * @param player the caster
     * @return true once the player stands in their room
     */
    public static boolean enter(ServerPlayer player) {
        ServerLevel pocket = player.level().getServer().getLevel(POCKET);
        if (pocket == null || player.level().dimension() == POCKET) {
            return false;
        }
        VoidPockets pockets = VoidPockets.get(pocket);
        // The room is laid whole on the way in, so a room the grid moved stands again at once.
        int room = layRoomFor(pocket, player.getUUID());
        pockets.keepReturn(player.getUUID(), new VoidPockets.ReturnPoint(player.level().dimension(),
                player.position(), player.getYRot(), player.getXRot()));
        burst(player.level(), player.position());
        Vec3 arrival = arrivalOf(room);
        // decision door-carries-its-own-sound-through-the-change
        sendDoorSound(player);
        player.teleport(new TeleportTransition(pocket, arrival, Vec3.ZERO, FACING_DOOR, 0f,
                TeleportTransition.DO_NOTHING));
        sendDoorSound(player);
        burst(pocket, arrival);
        sendEdges(player);
        // decision matching-doorway-opens-in-the-room-and-releases-you
        ConjuredDoors.release(player);
        return true;
    }

    /**
     * Sends the door's own sound to the player it carries, where they stand,
     * on their own connection, so the level change cuts no sound a level
     * played: once as the door envelops them, once as they arrive.
     * decision door-carries-its-own-sound-through-the-change
     *
     * @param player the player the door carries
     */
    private static void sendDoorSound(ServerPlayer player) {
        player.connection.send(new ClientboundSoundPacket(GooSounds.OUTSIDER_DOOR, SoundSource.PLAYERS,
                player.getX(), player.getY(), player.getZ(), DOOR_VOLUME, 1f, player.getRandom().nextLong()));
    }

    /**
     * Starts loading a caster's room while their charge runs, handing them
     * one where they hold none, so the full charge's tick finds the room's
     * chunk loaded: read cold, the room's first block loads its chunk on the
     * server thread, a stall of several ticks. The ticket lapses on its own.
     * decision diagnose-and-correct-the-slow-load-into-the-room
     *
     * @param caster the player channeling toward the pocket
     */
    public static void loadRoomAhead(ServerPlayer caster) {
        ServerLevel pocket = caster.level().getServer().getLevel(POCKET);
        if (pocket == null || caster.level().dimension() == POCKET) {
            return;
        }
        VoidPockets pockets = VoidPockets.get(pocket);
        Integer room = pockets.roomOf(caster.getUUID());
        int loading = room == null ? pockets.handOut(caster.getUUID()) : room;
        pocket.getChunkSource().addTicketWithRadius(TicketType.PORTAL, ChunkPos.containing(voidstoneOf(loading)),
                ROOM_LOAD_RADIUS);
    }

    /**
     * Shows a hold charging Outsider: void motes circle the player, closing in
     * and rising as the charge fills, under a hum that rises with them.
     *
     * @param player the player holding
     * @param held   the hold's tick count
     * @param charge the ticks the charge takes
     */
    public static void showCharge(ServerPlayer player, int held, int charge) {
        ServerLevel level = player.level();
        double share = Math.min(1.0, held / (double) charge);
        double radius = CHARGE_RADIUS * (1 - share) + MOTE_FLOOR;
        for (int mote = 0; mote < CHARGE_MOTES; mote++) {
            double angle = held * CHARGE_TURN + mote * Math.TAU / CHARGE_MOTES;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, player.getX() + Math.cos(angle) * radius,
                    player.getY() + MOTE_FLOOR + share * CHARGE_LIFT, player.getZ() + Math.sin(angle) * radius,
                    1, 0, 0, 0, 0);
        }
        if (held % HUM_EVERY == 1) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PORTAL_AMBIENT,
                    SoundSource.PLAYERS, HUM_VOLUME, HUM_PITCH + (float) share);
        }
    }

    /**
     * Bursts void at a point, where a player leaves for the pocket or arrives in it.
     *
     * @param level the level
     * @param at    the point
     */
    private static void burst(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + 1, at.z, BURST_PARTICLES,
                BURST_SPREAD, BURST_SPREAD, BURST_SPREAD, BURST_SPEED);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS,
                FOLD_VOLUME, 1f);
    }

    /**
     * The way through the doorway back to the exact place and facing a
     * player cast Outsider from; a player with no such place, or one whose
     * level is gone, goes to the world's respawn point.
     *
     * @param player the player walking into the doorway
     * @return the transition home
     */
    public static TeleportTransition wayHome(ServerPlayer player) {
        VoidPockets.ReturnPoint point = VoidPockets.get(player.level()).returnOf(player.getUUID());
        ServerLevel home = point == null ? null : player.level().getServer().getLevel(point.dimension());
        if (point == null || home == null) {
            return TeleportTransition.createDefault(player, TeleportTransition.PLAY_PORTAL_SOUND);
        }
        return new TeleportTransition(home, point.position(), Vec3.ZERO, point.yRot(), point.xRot(),
                TeleportTransition.PLAY_PORTAL_SOUND);
    }

    /**
     * Stands the doorway and the center voidstone again in each loaded room.
     *
     * @param pocket the pocket dimension
     */
    public static void restoreFixtures(ServerLevel pocket) {
        VoidPockets.get(pocket).extentsByRoom().keySet().forEach(room -> {
            if (pocket.isLoaded(voidstoneOf(room))) {
                restoreRoom(pocket, room);
            }
        });
    }

    /**
     * Stands a room's center voidstone and its doorway again; the rest of the
     * floor is the pocket's own, laid by {@link PocketFloor}.
     *
     * @param pocket the pocket dimension
     * @param room   the room number
     */
    private static void restoreRoom(ServerLevel pocket, int room) {
        BlockState door = GooBlocks.DIMENSION_DOOR.get().defaultBlockState();
        if (pocket.getBlockState(voidstoneOf(room)).isAir()) {
            pocket.setBlock(voidstoneOf(room), GooBlocks.VOIDSTONE.get().defaultBlockState(), Block.UPDATE_ALL);
        }
        for (BlockPos doorway : doorwayOf(room)) {
            restandDoorway(pocket, doorway, door);
        }
    }

    /**
     * Stands one doorway cell again where it is gone, or where it stands
     * without the block entity its starfield draws from.
     *
     * @param pocket  the pocket dimension
     * @param doorway the doorway cell
     * @param door    the door's block state
     */
    private static void restandDoorway(ServerLevel pocket, BlockPos doorway, BlockState door) {
        boolean standing = pocket.getBlockState(doorway).is(door.getBlock());
        if (standing && !holdsDoorEntity(pocket, doorway)) {
            // A door cell saved before the door had a block entity loads without one, and placing the same
            // state again changes nothing, so the cell is cleared and stood again to gain it.
            // decision room-door-wears-the-end-portal-starfield
            pocket.setBlock(doorway, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        if (!standing || !holdsDoorEntity(pocket, doorway)) {
            pocket.setBlock(doorway, door, Block.UPDATE_ALL);
        }
    }

    /**
     * Whether a doorway cell holds the door's block entity as its chunk
     * stands, reading it without creating one.
     *
     * @param pocket  the pocket dimension
     * @param doorway the doorway cell
     * @return true where the block entity stands
     */
    private static boolean holdsDoorEntity(ServerLevel pocket, BlockPos doorway) {
        return pocket.getChunkAt(doorway).getBlockEntity(doorway,
                LevelChunk.EntityCreationType.CHECK) instanceof DimensionDoorBlockEntity;
    }

    /**
     * Catches a player who falls out of their room: one fallen the catch
     * depth below the floor is set back on their own arrival cell with no
     * fall built up, the pocket folding them back in a burst of void. A
     * player past the grown cells stays where they are, since the room's
     * edges carry a walker across to the opposite side. A player in the
     * pocket with no room of their own is sent back where they came from.
     * outsider-room-wraps-and-repeats-at-the-platform-edge
     *
     * @param player the player
     * @return true when the player was moved
     */
    public static boolean keepInRoom(ServerPlayer player) {
        if (player.level().dimension() != POCKET) {
            return false;
        }
        ServerLevel pocket = player.level();
        VoidPockets pockets = VoidPockets.get(pocket);
        Integer room = pockets.roomOf(player.getUUID());
        if (room == null) {
            player.teleport(wayHome(player));
            return true;
        }
        if (!foldsBack(player.position())) {
            return false;
        }
        Vec3 arrival = arrivalOf(room);
        player.teleport(new TeleportTransition(pocket, arrival, Vec3.ZERO, FACING_DOOR, 0f,
                TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
        pocket.sendParticles(ParticleTypes.REVERSE_PORTAL, arrival.x, arrival.y + 1, arrival.z, FOLD_PARTICLES,
                FOLD_SPREAD, FOLD_SPREAD, FOLD_SPREAD, FOLD_SPEED);
        pocket.playSound(null, arrival.x, arrival.y, arrival.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS,
                FOLD_VOLUME, FOLD_PITCH);
        return true;
    }

    /**
     * Puts a player's own room back as it is first laid: everything built in
     * its grown square cleared, the floor whole, the extent the 9 by 9, the
     * center voidstone and the doorway standing. The room number and the
     * return point stay, so the doorway still leads where the player cast
     * from. A player standing in the pocket is set back at the doorway, in
     * the room as laid, and told the new edges; a player elsewhere stays where
     * they stand.
     * decision reset-command-returns-my-room-to-nine-by-nine-and-clears-its-blocks
     * decision reset-sets-a-player-in-the-room-back-at-the-doorway
     *
     * @param player the room's owner
     * @return true once the room is reset, false for a player holding no room
     */
    public static boolean resetRoom(ServerPlayer player) {
        ServerLevel pocket = player.level().getServer().getLevel(POCKET);
        VoidPockets pockets = VoidPockets.get(player.level());
        Integer room = pockets.roomOf(player.getUUID());
        if (pocket == null || room == null) {
            return false;
        }
        // Read before lowering: the grown extent bounds everything built.
        double grown = Math.max(RoomExtent.LAID, pockets.platformOf(player.getUUID()).extent());
        RoomClearance.clear(pocket, voidstoneOf(room), grown);
        pockets.keepPlatform(player.getUUID(), VoidPockets.Platform.LAID);
        restoreRoom(pocket, room);
        if (player.level().dimension() == POCKET) {
            setBackAtTheDoor(player, pocket, room);
        }
        sendEdges(player);
        return true;
    }

    /**
     * Sets a player in the pocket on their room's arrival cell facing the
     * door, with no motion and no fall built up.
     * decision reset-sets-a-player-in-the-room-back-at-the-doorway
     *
     * @param player the player in the pocket
     * @param pocket the pocket dimension
     * @param room   the player's room number
     */
    private static void setBackAtTheDoor(ServerPlayer player, ServerLevel pocket, int room) {
        player.teleport(new TeleportTransition(pocket, arrivalOf(room), Vec3.ZERO, FACING_DOOR, 0f,
                TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
    }

    /**
     * Lays an owner's room whole, handing the owner one first where they hold none.
     *
     * @param pocket the pocket dimension
     * @param owner  the owner's id
     * @return the room number
     */
    public static int layRoomFor(ServerLevel pocket, UUID owner) {
        VoidPockets pockets = VoidPockets.get(pocket);
        Integer room = pockets.roomOf(owner);
        int laid = room == null ? pockets.handOut(owner) : room;
        restoreRoom(pocket, laid);
        return laid;
    }
}
