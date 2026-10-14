package com.mercuriusxeno.goo.ability.gate;

import com.mercuriusxeno.goo.Goo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * Opens Void's pocket: a gate pair whose near gate lies on the struck face
 * and whose mirror lies on the caster's own room floor in goo:pocket, an
 * empty dimension where each player's room stands apart from every other
 * and keeps whatever was built in it. Travellers return through the same
 * pair, which closes on its clock as an End gate pair does; a traveller
 * still in the room when it closes is carried out through the near gate,
 * since the pocket holds no other way out.
 * decision void-opens-a-pocket-dimension
 */
public final class VoidPocketOpening {

    /** The empty dimension the rooms stand in. */
    public static final ResourceKey<Level> POCKET = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(Goo.MODID, "pocket"));
    /** Blocks along x between one room's centre and the next, so no room reaches another. */
    static final int ROOM_SPACING = 1024;
    /** Blocks along x from a room's centre that still count as inside it: half the spacing. */
    static final int ROOM_REACH = ROOM_SPACING / 2;
    /** The height of every room's floor, the block the mirror lies on. */
    static final int ROOM_FLOOR_Y = 63;
    /** Blocks from a room floor's centre to its edge. */
    static final int ROOM_HALF_WIDTH = 4;

    private VoidPocketOpening() {
    }

    /**
     * Opens a pair from a struck face into the caster's room, laying the
     * room's floor the first time the caster opens one. A cast with no
     * caster, a cast inside the pocket, or a server not holding the pocket
     * lays nothing.
     *
     * @param level    the level the blob landed in
     * @param surface  the struck block
     * @param face     the struck face
     * @param lifetime the ticks the pair stands
     * @param caster   the player whose goo landed, or null for none
     * @return true once the pair stands open
     */
    public static boolean open(ServerLevel level, BlockPos surface, Direction face, int lifetime,
                               @Nullable UUID caster) {
        ServerLevel pocket = level.getServer().getLevel(POCKET);
        if (caster == null || pocket == null || level.dimension() == POCKET) {
            return false;
        }
        VoidPockets pockets = VoidPockets.get(level);
        Integer room = pockets.roomOf(caster);
        BlockPos floor;
        if (room == null) {
            floor = roomFloor(pockets.handOut(caster));
            layFloor(pocket, floor);
        } else {
            floor = roomFloor(room);
        }
        return EndGateOpening.openPair(level, surface, face, lifetime, pocket, floor);
    }

    /**
     * The centre of a room's floor.
     *
     * @param room the room number
     * @return the floor block the room's mirror lies on
     */
    public static BlockPos roomFloor(int room) {
        return new BlockPos(room * ROOM_SPACING, ROOM_FLOOR_Y, 0);
    }

    /**
     * Carries every player standing in a closing pair's pocket room out
     * through its near gate; a pair whose mirror lies outside the pocket
     * carries nobody.
     *
     * @param server the server
     * @param pair   the pair closing
     */
    static void carryOutTravellers(MinecraftServer server, EndGates.Pair pair) {
        ServerLevel pocket = server.getLevel(POCKET);
        ServerLevel outside = server.getLevel(pair.near().dimension());
        if (pocket == null || outside == null || pair.far().dimension() != POCKET) {
            return;
        }
        int roomX = pair.far().center().getX();
        List<ServerPlayer> inside = pocket.players().stream()
                .filter(player -> Math.abs(player.getX() - roomX) < ROOM_REACH).toList();
        Vec3 out = pair.near().arrival();
        for (ServerPlayer player : inside) {
            player.teleport(new TeleportTransition(outside, out, Vec3.ZERO, player.getYRot(), player.getXRot(),
                    Relative.ROTATION, TeleportTransition.PLAY_PORTAL_SOUND));
        }
    }

    private static void layFloor(ServerLevel pocket, BlockPos center) {
        for (int x = -ROOM_HALF_WIDTH; x <= ROOM_HALF_WIDTH; x++) {
            for (int z = -ROOM_HALF_WIDTH; z <= ROOM_HALF_WIDTH; z++) {
                pocket.setBlock(center.offset(x, 0, z), Blocks.BLACK_CONCRETE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }
}
