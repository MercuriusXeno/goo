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
 * in it. Every platform cell is voidstone, as the operator ruled: a room
 * starts as a 3 by 3 of it, with a two-tall, door-shaped dimension doorway
 * standing alone on the center cell, as the operator ruled, and the player
 * arrives on the cell just south of the doorway, facing it. Walking into the
 * doorway leads back to where the player cast Outsider from; the doorway and
 * every platform cell stand again if anything removes them. The room wraps:
 * its edges are linked gates, {@link RoomEdges}, so a player walking off one
 * side of the grown square carries on in from the opposite side, and only a
 * player falling below the floor, a failsafe for creative flight, is set
 * back south of the door unharmed, the pocket folding them back. No block
 * may be placed outside the grown cells. Each player's room is saved to them
 * forever, and every way in, catch and bound reads that room alone. Rooms stand a million
 * blocks apart along {@link RoomGrid}. Held on inside, Outsider feeds void into the platform,
 * which grows in voidstone along {@link PlatformSpiral}. Each part shows
 * itself: void motes spiral in and a hum rises while the hold charges, void
 * bursts where the player leaves and arrives, motes stream off the player
 * while they feed the platform, and each grown cell puffs void and chimes,
 * higher the farther out it stands.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 * decision outsider-room-wraps-and-repeats-at-the-platform-edge
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
    private static final int GROW_PARTICLES = 14;
    private static final double GROW_SPREAD = 0.3;
    private static final double GROW_RISE = 0.15;
    private static final double GROW_SPEED = 0.04;
    private static final float CHIME_VOLUME = 0.8f;
    private static final float CHIME_PITCH = 0.8f;
    private static final float CHIME_RISE = 0.04f;
    private static final float CHIME_TOP = 2.0f;
    private static final double HALF_BLOCK = 0.5;

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
     * A platform cell's block.
     *
     * @param room  the room number
     * @param index the cell's place along the spiral
     * @return the cell's block on the floor
     */
    private static BlockPos platformCellOf(int room, int index) {
        PlatformSpiral.Cell cell = PlatformSpiral.cellAt(index);
        return voidstoneOf(room).offset(cell.x(), 0, cell.z());
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
     * The void a tick of the hold feeds a player's platform: its current
     * ring's cost spread over the ring time.
     *
     * @param player      the player in their room
     * @param ringTicks   the ticks every ring takes
     * @param base        the void a cell costs at the center (mB)
     * @param perDistance the void each block of Manhattan distance adds (mB)
     * @return the feed (mB)
     */
    public static int feedOf(ServerPlayer player, int ringTicks, int base, int perDistance) {
        int cells = VoidPockets.get(player.level()).platformOf(player.getUUID()).cells();
        return (int) Math.min(Integer.MAX_VALUE, PlatformSpiral.feedFor(cells, base, perDistance, ringTicks));
    }

    /**
     * The cells a player's platform holds.
     *
     * @param level  any level of the server
     * @param player the player's id
     * @return the cell count
     */
    public static int platformCells(ServerLevel level, UUID player) {
        return VoidPockets.get(level).platformOf(player).cells();
    }

    /**
     * Feeds void into the platform of the room a player stands in: as many
     * cells grow along the spiral as what was fed covers, each at its own
     * cost, and what is left over carries to the cell after. A cell a player
     * built into already counts as grown. Only the first cell a feed grows
     * chimes, so a fast ring rings once a tick.
     *
     * @param player   the player in their room
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
        VoidPockets.Platform platform = pockets.platformOf(player.getUUID());
        int cells = platform.cells();
        long fed = platform.fed() + feed;
        long cost = PlatformSpiral.costOf(cells, base, perDistance);
        boolean chimed = false;
        while (fed >= cost) {
            BlockPos at = platformCellOf(room, cells);
            if (pocket.getBlockState(at).isAir()) {
                pocket.setBlock(at, GooBlocks.VOIDSTONE.get().defaultBlockState(), Block.UPDATE_ALL);
            }
            showGrowth(pocket, at, at.distManhattan(voidstoneOf(room)), !chimed);
            chimed = true;
            cells++;
            fed -= cost;
            cost = PlatformSpiral.costOf(cells, base, perDistance);
        }
        pockets.keepPlatform(player.getUUID(), new VoidPockets.Platform(cells, fed));
        announceGrowth(room, platform.cells(), cells, payload -> sendTo(player, payload));
    }

    /**
     * Shows a cell growing: void puffs up off it and it chimes, higher the
     * farther it stands from the center.
     *
     * @param pocket   the pocket dimension
     * @param at       the grown cell
     * @param distance the cell's Manhattan distance from the center
     * @param chimes   true to chime with the puff
     */
    private static void showGrowth(ServerLevel pocket, BlockPos at, int distance, boolean chimes) {
        Vec3 top = Vec3.atCenterOf(at).add(0, HALF_BLOCK, 0);
        pocket.sendParticles(ParticleTypes.REVERSE_PORTAL, top.x, top.y, top.z, GROW_PARTICLES,
                GROW_SPREAD, GROW_RISE, GROW_SPREAD, GROW_SPEED);
        if (chimes) {
            pocket.playSound(null, top.x, top.y, top.z, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS,
                    CHIME_VOLUME, Math.min(CHIME_TOP, CHIME_PITCH + distance * CHIME_RISE));
        }
    }

    /**
     * Whether a column stands over one of a room's grown cells, the only
     * columns a block may be placed in.
     *
     * @param room  the room number
     * @param cells the platform's cell count
     * @param x     the column's x
     * @param z     the column's z
     * @return true over a grown cell
     */
    static boolean overGrownCell(int room, int cells, int x, int z) {
        BlockPos center = voidstoneOf(room);
        PlatformSpiral.Cell cell = new PlatformSpiral.Cell(x - center.getX(), z - center.getZ());
        if (Math.max(Math.abs(cell.x()), Math.abs(cell.z())) > PlatformSpiral.ringOf(cells)) {
            return false;
        }
        return PlatformSpiral.indexOf(cell) < cells;
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
     * Sends a room's new edges once its platform grew, so its player's
     * client carries them across the grown square's edges.
     * outsider-room-wraps-and-repeats-at-the-platform-edge
     *
     * @param room        the room number
     * @param cellsBefore the platform's cell count before the feed
     * @param cellsAfter  its cell count after
     * @param send        sends the payload to the room's player
     */
    static void announceGrowth(int room, int cellsBefore, int cellsAfter, Consumer<RoomEdgesSyncPayload> send) {
        if (cellsAfter > cellsBefore) {
            send.accept(new RoomEdgesSyncPayload(RoomEdges.pairsOf(room, cellsAfter)));
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
                    pockets.platformOf(player.getUUID()).cells())));
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
     * a column over one of the player's own grown cells; anywhere outside
     * the pocket is not this rule's to judge.
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
        return room != null && overGrownCell(room, pockets.platformOf(player.getUUID()).cells(), pos.getX(),
                pos.getZ());
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
     * Stands the doorway and every missing platform cell again in each loaded
     * room; a platform cell a player built into is left as they built it.
     *
     * @param pocket the pocket dimension
     */
    public static void restoreFixtures(ServerLevel pocket) {
        VoidPockets.get(pocket).platformCellsByRoom().forEach((room, cells) -> {
            if (pocket.isLoaded(voidstoneOf(room))) {
                restoreRoom(pocket, room, cells);
            }
        });
    }

    /**
     * Stands a room's missing platform cells and its doorway again.
     *
     * @param pocket the pocket dimension
     * @param room   the room number
     * @param cells  the platform's cell count
     */
    private static void restoreRoom(ServerLevel pocket, int room, int cells) {
        BlockState voidstone = GooBlocks.VOIDSTONE.get().defaultBlockState();
        BlockState door = GooBlocks.DIMENSION_DOOR.get().defaultBlockState();
        for (int index = 0; index < cells; index++) {
            BlockPos at = platformCellOf(room, index);
            if (pocket.getBlockState(at).isAir()) {
                pocket.setBlock(at, voidstone, Block.UPDATE_ALL);
            }
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
        restoreRoom(pocket, laid, pockets.platformOf(owner).cells());
        return laid;
    }
}
