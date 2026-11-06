package com.mercuriusxeno.goo.ability.outsider;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Outsider rooms a server has handed out, one per player, and the place
 * each player last cast Outsider from, saved in the overworld's data so a
 * player's room stands at the same place in the pocket dimension for as long
 * as the world lasts and its door always leads back where they came in from.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 */
public class VoidPockets extends SavedData {

    /** Saves each player's room number and return point. */
    public static final Codec<VoidPockets> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).fieldOf("rooms").forGetter(pockets -> pockets.rooms),
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, ReturnPoint.CODEC).optionalFieldOf("returns", Map.of())
                    .forGetter(pockets -> pockets.returns)
    ).apply(inst, VoidPockets::new));

    /** Stored as goo/void_pockets.dat in the overworld's data. */
    public static final SavedDataType<VoidPockets> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("goo", "void_pockets"), VoidPockets::new, CODEC);

    private final Map<UUID, Integer> rooms;
    private final Map<UUID, ReturnPoint> returns;

    /**
     * Where a player cast Outsider from: the level, the exact position and the facing.
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
        this(Map.of(), Map.of());
    }

    /**
     * Creates a server's pockets from what it saved.
     *
     * @param rooms   each player's room number
     * @param returns each player's return point
     */
    public VoidPockets(Map<UUID, Integer> rooms, Map<UUID, ReturnPoint> returns) {
        this.rooms = new HashMap<>(rooms);
        this.returns = new HashMap<>(returns);
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
     * Every room handed out.
     *
     * @return the room numbers
     */
    public List<Integer> rooms() {
        return List.copyOf(rooms.values());
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
     * Where a player last cast Outsider from.
     *
     * @param player the player's id
     * @return the return point, or null for a player who never cast it
     */
    public @Nullable ReturnPoint returnOf(UUID player) {
        return returns.get(player);
    }

    /**
     * Keeps where a player cast Outsider from, replacing any earlier point.
     *
     * @param player the player's id
     * @param point  the return point
     */
    public void keepReturn(UUID player, ReturnPoint point) {
        returns.put(player, point);
        setDirty();
    }
}
