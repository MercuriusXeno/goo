package com.mercuriusxeno.goo.ability.outsider;

import com.mercuriusxeno.goo.Goo;
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
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * Void's Outsider rooms in goo:pocket, an empty dimension where each
 * player's room stands apart from every other and keeps whatever was built
 * in it. Every platform cell is voidstone, as the operator ruled: a room
 * starts as a 3 by 3 of it, with the dimension door standing on the center
 * cell, and the player arrives on the cell just south of the door, facing
 * it. The door leads back to where the player cast Outsider from; the door
 * and every platform cell stand again if anything removes them; and a player
 * falling below the floor is set back south of the door unharmed, the pocket
 * folding them back. Held on inside, Outsider feeds void into the platform,
 * which grows in voidstone along {@link PlatformSpiral}.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 */
public final class OutsiderRooms {

    /** The empty dimension the rooms stand in. */
    public static final ResourceKey<Level> POCKET = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(Goo.MODID, "pocket"));
    /** Ticks between checks that every loaded room still holds its platform and door. */
    public static final int RESTORE_INTERVAL = 20;
    /** Blocks along x between one room's centre and the next, so no room reaches another. */
    static final int ROOM_SPACING = 1024;
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

    private OutsiderRooms() {
    }

    /**
     * The voidstone at a room's center, the platform's cell 0, which the door stands on.
     *
     * @param room the room number
     * @return the voidstone's block
     */
    public static BlockPos voidstoneOf(int room) {
        return new BlockPos(room * ROOM_SPACING, ROOM_FLOOR_Y, 0);
    }

    /**
     * The dimension door's block, standing on the center voidstone.
     *
     * @param room the room number
     * @return the door's block
     */
    public static BlockPos doorOf(int room) {
        return voidstoneOf(room).above();
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
     * Feeds void into the platform of the room a player stands in: what
     * was fed reaching the next cell's cost grows that cell along the
     * spiral, one cell a feed at most, and what is left over carries to the
     * cell after. A cell a player built into already counts as grown.
     *
     * @param player   the player in their room
     * @param feed     the void fed (mB)
     * @param costStep the void each cell costs more than the one before (mB)
     */
    public static void feedPlatform(ServerPlayer player, int feed, int costStep) {
        ServerLevel pocket = player.level();
        VoidPockets pockets = VoidPockets.get(pocket);
        Integer room = pockets.roomOf(player.getUUID());
        if (room == null || pocket.dimension() != POCKET) {
            return;
        }
        VoidPockets.Platform platform = pockets.platformOf(player.getUUID());
        int cells = platform.cells();
        long fed = platform.fed() + feed;
        long cost = PlatformSpiral.costOf(cells, costStep);
        if (fed >= cost) {
            BlockPos at = platformCellOf(room, cells);
            if (pocket.getBlockState(at).isAir()) {
                pocket.setBlock(at, GooBlocks.VOIDSTONE.get().defaultBlockState(), Block.UPDATE_ALL);
            }
            cells++;
            fed -= cost;
        }
        pockets.keepPlatform(player.getUUID(), new VoidPockets.Platform(cells, fed));
    }

    /**
     * The room whose column a point in the pocket stands nearest.
     *
     * @param x the point's x
     * @return the room number
     */
    static int roomNear(double x) {
        return (int) Math.max(0, Math.round(x / ROOM_SPACING));
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
        Integer room = pockets.roomOf(player.getUUID());
        if (room == null) {
            room = pockets.handOut(player.getUUID());
            layRoom(pocket, room);
        }
        pockets.keepReturn(player.getUUID(), new VoidPockets.ReturnPoint(player.level().dimension(),
                player.position(), player.getYRot(), player.getXRot()));
        Vec3 arrival = arrivalOf(room);
        player.teleport(new TeleportTransition(pocket, arrival, Vec3.ZERO, FACING_DOOR, 0f,
                TeleportTransition.PLAY_PORTAL_SOUND));
        return true;
    }

    /**
     * Carries a player through the door back to the exact place and facing
     * they cast Outsider from; a player with no such place, or one whose
     * level is gone, goes to the world's respawn point.
     *
     * @param player the player using the door
     */
    public static void returnHome(ServerPlayer player) {
        VoidPockets.ReturnPoint point = VoidPockets.get(player.level()).returnOf(player.getUUID());
        ServerLevel home = point == null ? null : player.level().getServer().getLevel(point.dimension());
        if (point == null || home == null) {
            player.teleport(TeleportTransition.createDefault(player, TeleportTransition.PLAY_PORTAL_SOUND));
            return;
        }
        player.teleport(new TeleportTransition(home, point.position(), Vec3.ZERO, point.yRot(), point.xRot(),
                TeleportTransition.PLAY_PORTAL_SOUND));
    }

    /**
     * Stands the door and every missing platform cell again in each loaded
     * room; a platform cell a player built into is left as they built it.
     *
     * @param pocket the pocket dimension
     */
    public static void restoreFixtures(ServerLevel pocket) {
        BlockState voidstone = GooBlocks.VOIDSTONE.get().defaultBlockState();
        BlockState door = GooBlocks.DIMENSION_DOOR.get().defaultBlockState();
        VoidPockets.get(pocket).platformCellsByRoom().forEach((room, cells) -> {
            for (int index = 0; index < cells; index++) {
                BlockPos at = platformCellOf(room, index);
                if (pocket.isLoaded(at) && pocket.getBlockState(at).isAir()) {
                    pocket.setBlock(at, voidstone, Block.UPDATE_ALL);
                }
            }
            BlockPos doorAt = doorOf(room);
            if (pocket.isLoaded(doorAt) && !pocket.getBlockState(doorAt).is(door.getBlock())) {
                pocket.setBlock(doorAt, door, Block.UPDATE_ALL);
            }
        });
    }

    /**
     * Sets a player who fell below their room's floor back south of its door
     * with no fall built up, the pocket folding them back in a burst of void.
     *
     * @param player the player
     * @return true when the player was caught
     */
    public static boolean catchFalling(ServerPlayer player) {
        if (player.level().dimension() != POCKET || player.getY() >= ROOM_FLOOR_Y - CATCH_DEPTH) {
            return false;
        }
        ServerLevel pocket = player.level();
        Vec3 arrival = arrivalOf(roomNear(player.getX()));
        player.teleport(new TeleportTransition(pocket, arrival, Vec3.ZERO, FACING_DOOR, 0f,
                TeleportTransition.DO_NOTHING));
        player.resetFallDistance();
        pocket.sendParticles(ParticleTypes.REVERSE_PORTAL, arrival.x, arrival.y + 1, arrival.z, FOLD_PARTICLES,
                FOLD_SPREAD, FOLD_SPREAD, FOLD_SPREAD, FOLD_SPEED);
        pocket.playSound(null, arrival.x, arrival.y, arrival.z, SoundEvents.PLAYER_TELEPORT, SoundSource.PLAYERS,
                FOLD_VOLUME, FOLD_PITCH);
        return true;
    }

    private static void layRoom(ServerLevel pocket, int room) {
        for (int index = 0; index < PlatformSpiral.START_CELLS; index++) {
            pocket.setBlock(platformCellOf(room, index), GooBlocks.VOIDSTONE.get().defaultBlockState(),
                    Block.UPDATE_ALL);
        }
        pocket.setBlock(doorOf(room), GooBlocks.DIMENSION_DOOR.get().defaultBlockState(), Block.UPDATE_ALL);
    }
}
