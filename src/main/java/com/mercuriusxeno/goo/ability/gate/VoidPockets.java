package com.mercuriusxeno.goo.ability.gate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The pocket rooms a server has handed out, one per player, saved in the
 * overworld's data so a player's room stands at the same place in the pocket
 * dimension for as long as the world lasts.
 * decision void-opens-a-pocket-dimension
 */
public class VoidPockets extends SavedData {

    /** Saves each player's room number. */
    public static final Codec<VoidPockets> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.INT).fieldOf("rooms").forGetter(pockets -> pockets.rooms)
    ).apply(inst, VoidPockets::new));

    /** Stored as goo/void_pockets.dat in the overworld's data. */
    public static final SavedDataType<VoidPockets> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath("goo", "void_pockets"), VoidPockets::new, CODEC);

    private final Map<UUID, Integer> rooms;

    /** Creates a server's pockets with no room handed out. */
    public VoidPockets() {
        this(Map.of());
    }

    /**
     * Creates a server's pockets from what it saved.
     *
     * @param rooms each player's room number
     */
    public VoidPockets(Map<UUID, Integer> rooms) {
        this.rooms = new HashMap<>(rooms);
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
}
