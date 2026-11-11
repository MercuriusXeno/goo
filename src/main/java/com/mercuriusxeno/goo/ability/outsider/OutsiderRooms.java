package com.mercuriusxeno.goo.ability.outsider;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.network.RoomEdgesSyncPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
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
 * holding the 3 by 3 around its center, where a two-tall, door-shaped
 * dimension doorway stands alone on the center voidstone, as the operator
 * ruled, and the player arrives on the block just south of the doorway,
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
 * whole ring the extent gains chimes, higher the farther out it stands.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 * decision outsider-room-wraps-and-repeats-at-the-platform-edge
 * decision outsider-room-grows-fractionally-on-all-sides-at-once
 * decision voidstone-floor-stands-whole-and-growth-moves-the-boundary
 * decision grown-extent-is-the-one-wrap-and-build-boundary
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
    private static final float CHIME_VOLUME = 0.8f;
    private static final float CHIME_PITCH = 0.8f;
    private static final float CHIME_RISE = 0.04f;
    private static final float CHIME_TOP = 2.0f;

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
     * across a whole-block mark chimes, higher the farther out the mark.
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
        if (RoomExtent.wholeRings(after) > RoomExtent.wholeRings(before)) {
            pocket.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, CHIME_VOLUME,
                    Math.min(CHIME_TOP, CHIME_PITCH + RoomExtent.wholeRings(after) * CHIME_RISE));
        }
        announceGrowth(room, before, after, payload -> sendTo(player, payload));
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
     * Sends a room's new edges once its extent holds another whole ring, so
     * its player's client carries them across the grown square's edges.
     * decision outsider-room-wraps-and-repeats-at-the-platform-edge
     *
     * @param room         the room number
     * @param extentBefore the room's extent before the feed
     * @param extentAfter  its extent after
     * @param send         sends the payload to the room's player
     */
    static void announceGrowth(int room, double extentBefore, double extentAfter,
            Consumer<RoomEdgesSyncPayload> send) {
        if (RoomExtent.wholeRings(extentAfter) > RoomExtent.wholeRings(extentBefore)) {
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
        return RoomExtent.holdsWholeBlock(pockets.platformOf(player.getUUID()).extent(),
                pos.getX() - center.getX(), pos.getZ() - center.getZ());
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
        player.teleport(new TeleportTransition(pocket, arrival, Vec3.ZERO, FACING_DOOR, 0f,
                TeleportTransition.PLAY_PORTAL_SOUND));
        burst(pocket, arrival);
        sendEdges(player);
        return true;
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
            if (!pocket.getBlockState(doorway).is(door.getBlock())) {
                pocket.setBlock(doorway, door, Block.UPDATE_ALL);
            }
        }
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
