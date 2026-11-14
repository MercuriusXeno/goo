package com.mercuriusxeno.goo.ability.zone;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Zone rooms a server has handed out, one per player, the place
 * each player last cast Zone from and how far each room has grown,
 * saved in the overworld's data so a
 * player's room stands at the same place in the pocket dimension for as long
 * as the world lasts and its door always leads back where they came in from.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 */
public class VoidPockets extends SavedData {

    /** Saves each player's room number and return point. */
    public static final Codec<VoidPockets> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).fieldOf("rooms").forGetter(pockets -> pockets.rooms),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, ReturnPoint.CODEC).optionalFieldOf("returns", Map.of())
                    .forGetter(pockets -> pockets.returns),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Platform.CODEC).optionalFieldOf("platforms", Map.of())
                    .forGetter(pockets -> pockets.platforms)
    ).apply(inst, VoidPockets::new));

    /** Stored as goo/void_pockets.dat in the overworld's data. */
    public static final SavedDataType<VoidPockets> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("goo", "void_pockets"), VoidPockets::new, CODEC);

    private final Map<UUID, Integer> rooms;
    private final Map<UUID, ReturnPoint> returns;
    private final Map<UUID, Platform> platforms;

    /**
     * How far a room has grown: its {@link RoomExtent}, the void fed held in
     * the fraction of a block it carries. A room saved before the extent, as
     * a spiral cell count and the void fed toward the next cell, loads as the
     * extent of the square its cells completed.
     * decision outsider-room-grows-fractionally-on-all-sides-at-once
     *
     * @param extent the room's extent in blocks from its center
     */
    public record Platform(double extent) {
        /** A room as it is laid. */
        public static final Platform LAID = new Platform(RoomExtent.LAID);
        private static final Codec<Platform> EXTENT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.DOUBLE.fieldOf("extent").forGetter(Platform::extent)
        ).apply(inst, Platform::new));
        private static final Codec<Platform> CELL_COUNT_CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Codec.INT.fieldOf("cells").forGetter(platform -> RoomExtent.wholeCells(platform.extent()))
        ).apply(inst, cells -> new Platform(RoomExtent.ofCompletedSquare(cells))));
        /** Saves a room's extent, and reads one saved as a cell count. */
        public static final Codec<Platform> CODEC = Codec.withAlternative(EXTENT_CODEC, CELL_COUNT_CODEC);
    }

    /**
     * Where a player cast Zone from: the level, the exact position and the facing.
     *
     * @param dimension the level cast in
     * @param position  the caster's position
     * @param yRot      the caster's yaw
     * @param xRot      the caster's pitch
     */
    public record ReturnPoint(ResourceKey<Level> dimension, Vec3 position, float yRot, float xRot) {
        /** Saves a return point. */
        public static final Codec<ReturnPoint> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ReturnPoint::dimension),
                Vec3.CODEC.fieldOf("position").forGetter(ReturnPoint::position),
                Codec.FLOAT.fieldOf("y_rot").forGetter(ReturnPoint::yRot),
                Codec.FLOAT.fieldOf("x_rot").forGetter(ReturnPoint::xRot)
        ).apply(inst, ReturnPoint::new));
    }

    /** Creates a server's pockets with no room handed out. */
    public VoidPockets() {
        this(Map.of(), Map.of(), Map.of());
    }

    /**
     * Creates a server's pockets from what it saved.
     *
     * @param rooms   each player's room number
     * @param returns   each player's return point
     * @param platforms each player's platform
     */
    public VoidPockets(Map<UUID, Integer> rooms, Map<UUID, ReturnPoint> returns, Map<UUID, Platform> platforms) {
        this.rooms = new HashMap<>(rooms);
        this.returns = new HashMap<>(returns);
        this.platforms = new HashMap<>(platforms);
    }

    /**
     * How far a player's platform has grown.
     *
     * @param player the player's id
     * @return the platform, as laid for a player whose platform never grew
     */
    public Platform platformOf(UUID player) {
        return platforms.getOrDefault(player, Platform.LAID);
    }

    /**
     * Keeps how far a player's platform has grown.
     *
     * @param player   the player's id
     * @param platform the platform
     */
    public void keepPlatform(UUID player, Platform platform) {
        platforms.put(player, platform);
        setDirty();
    }

    /**
     * The server's pockets, kept in the overworld's data.
     *
     * @param level any level of the server
     * @return the pockets
     */
    public static VoidPockets get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    /**
     * A player's room number, if one was handed out.
     *
     * @param player the player's id
     * @return the room number, or null for a player with no room yet
     */
    public @Nullable Integer roomOf(UUID player) {
        return rooms.get(player);
    }

    /**
     * Each room handed out with its extent.
     *
     * @return the room's extent by room number
     */
    public Map<Integer, Double> extentsByRoom() {
        Map<Integer, Double> extents = new HashMap<>();
        rooms.forEach((player, room) -> extents.put(room, platformOf(player).extent()));
        return extents;
    }

    /**
     * Hands a player the next free room.
     *
     * @param player the player's id
     * @return the room number handed out
     */
    public int handOut(UUID player) {
        int room = rooms.size();
        rooms.put(player, room);
        setDirty();
        return room;
    }

    /**
     * Where a player last cast Zone from.
     *
     * @param player the player's id
     * @return the return point, or null for a player who never cast it
     */
    public @Nullable ReturnPoint returnOf(UUID player) {
        return returns.get(player);
    }

    /**
     * Keeps where a player cast Zone from, replacing any earlier point.
     *
     * @param player the player's id
     * @param point  the return point
     */
    public void keepReturn(UUID player, ReturnPoint point) {
        returns.put(player, point);
        setDirty();
    }
}
