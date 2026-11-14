package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.ability.zone.RoomExtent;
import com.mercuriusxeno.goo.ability.zone.ZoneRooms;
import com.mercuriusxeno.goo.client.ability.SectionMeshes;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The meshes every copy of the Zone room the player stands in draws, kept
 * on the GPU section by section: each section's blocks standing wholly inside
 * the boundary, and the block entities the room holds. The whole room lays
 * out when it grows a ring; a section the level renderer marks dirty, as a
 * block changes or a chunk loads, lays out afresh alone, a few a frame, so
 * the chunks loading as the player crosses the room's edge cost a section
 * each rather than the whole room each. The floor's voidstone is left out,
 * since the pocket's floor stands whole under every copy already and
 * VoidstoneFaces draws its stars as each copy's.
 * decision room-tiles-to-render-distance-held-to-the-picture
 * decision voidstone-floor-stands-whole-and-growth-moves-the-boundary
 */
final class RoomCopyMeshes {

    private static final String SECTION_LABEL = "Goo room copy section";
    /** The dirty sections a frame lays out afresh at most, the rest waiting for the frames after. */
    private static final int SECTIONS_PER_FRAME = 64;

    /** The room the meshes were laid out for: its level, its center column and the rings it holds whole. */
    private record Key(ClientLevel level, int centerX, int centerZ, int wholeRings) {
    }

    /**
     * One mesh on the GPU, laid out from its origin.
     *
     * @param origin     the block its positions count from
     * @param vertices   its vertices
     * @param indexCount the indices it draws
     */
    record Uploaded(BlockPos origin, GpuBuffer vertices, int indexCount) {
    }

    private static @Nullable Key built;
    private static final Map<Long, Map<ChunkSectionLayer, Uploaded>> SECTIONS = new HashMap<>();
    private static final Map<Long, List<BlockEntity>> CHUNK_ENTITIES = new HashMap<>();
    private static final Set<Long> DIRTY = new LinkedHashSet<>();

    private RoomCopyMeshes() {
    }

    /**
     * Notes a section's blocks changed, so it lays out afresh where the room stands in it.
     *
     * @param sectionX the section's x
     * @param sectionY the section's y
     * @param sectionZ the section's z
     */
    static void markDirty(int sectionX, int sectionY, int sectionZ) {
        Key room = built;
        if (room != null && overlaps(sectionX, room.centerX(), room.wholeRings())
                && overlaps(sectionZ, room.centerZ(), room.wholeRings())) {
            DIRTY.add(SectionPos.asLong(sectionX, sectionY, sectionZ));
        }
    }

    /**
     * Whether a section's span along one axis reaches the room's whole
     * blocks, or the ring just past them whose blocks neighbour theirs.
     *
     * @param section the section's coordinate along the axis
     * @param center  the room's center block along it
     * @param rings   the rings the room holds whole
     * @return true where the section touches the room
     */
    private static boolean overlaps(int section, int center, int rings) {
        int low = SectionPos.sectionToBlockCoord(section);
        return low + SectionPos.SECTION_MAX_INDEX >= center - rings - 1 && low <= center + rings + 1;
    }

    /**
     * Lays the whole room out where it grew or moved since it was laid, and
     * otherwise lays out afresh a few of the sections marked dirty.
     *
     * @param level  the client level
     * @param bounds the room's boundary
     */
    static void refresh(ClientLevel level, RoomExtent.Bounds bounds) {
        Key room = new Key(level, bounds.centerX(), bounds.centerZ(), RoomExtent.wholeRings(bounds.extent()));
        Key was = built;
        if (was == null || was.level() != room.level() || was.centerX() != room.centerX()
                || was.centerZ() != room.centerZ()) {
            release();
            built = room;
            layOut(level, bounds);
            return;
        }
        if (was.wholeRings() != room.wholeRings()) {
            // a ring grown changes which blocks stand wholly inside only in the sections that ring touches
            built = room;
            markRingsDirty(level, room, Math.min(was.wholeRings(), room.wholeRings()),
                    Math.max(was.wholeRings(), room.wholeRings()));
        }
        Iterator<Long> marked = DIRTY.iterator();
        for (int laid = 0; laid < SECTIONS_PER_FRAME && marked.hasNext(); laid++) {
            long section = marked.next();
            marked.remove();
            layOutAfresh(level, SectionPos.of(section), bounds);
        }
    }

    /**
     * Marks dirty every section a band of rings touches, those whose blocks
     * changed between standing wholly inside the room and not, so they lay
     * out afresh a few a frame while the rest keep their meshes.
     *
     * @param level the client level
     * @param room  the room
     * @param inner the inner ring of the band, whose blocks stood whole before and after
     * @param outer the outer ring of the band
     */
    private static void markRingsDirty(ClientLevel level, Key room, int inner, int outer) {
        int reach = outer + 1;
        for (int chunkX = SectionPos.blockToSectionCoord(room.centerX() - reach);
                chunkX <= SectionPos.blockToSectionCoord(room.centerX() + reach); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(room.centerZ() - reach);
                    chunkZ <= SectionPos.blockToSectionCoord(room.centerZ() + reach); chunkZ++) {
                if (chunkInsideRing(chunkX, room.centerX(), inner) && chunkInsideRing(chunkZ, room.centerZ(), inner)) {
                    continue;
                }
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) {
                    continue;
                }
                for (int index = 0; index < chunk.getSections().length; index++) {
                    if (!chunk.getSection(index).hasOnlyAir()) {
                        DIRTY.add(SectionPos.asLong(chunkX, chunk.getSectionYFromSectionIndex(index), chunkZ));
                    }
                }
            }
        }
    }

    /**
     * Whether a chunk's span along one axis lies wholly within a ring of the room, so its blocks stood whole
     * before the ring grew and still do.
     *
     * @param chunk  the chunk's coordinate along the axis
     * @param center the room's center block along it
     * @param ring   the ring
     * @return true where the chunk's span lies inside the ring
     */
    private static boolean chunkInsideRing(int chunk, int center, int ring) {
        int low = SectionPos.sectionToBlockCoord(chunk);
        return low >= center - ring && low + SectionPos.SECTION_MAX_INDEX <= center + ring;
    }

    /** Frees every mesh, as the room is left. */
    static void release() {
        SECTIONS.values().forEach(RoomCopyMeshes::close);
        SECTIONS.clear();
        CHUNK_ENTITIES.clear();
        DIRTY.clear();
        built = null;
    }

    /**
     * @param layer a section layer
     * @return the room's meshes in that layer
     */
    static List<Uploaded> layer(ChunkSectionLayer layer) {
        List<Uploaded> meshes = new ArrayList<>();
        for (Map<ChunkSectionLayer, Uploaded> section : SECTIONS.values()) {
            Uploaded mesh = section.get(layer);
            if (mesh != null) {
                meshes.add(mesh);
            }
        }
        return meshes;
    }

    /**
     * @return the block entities standing wholly inside the room
     */
    static List<BlockEntity> blockEntities() {
        return CHUNK_ENTITIES.values().stream().flatMap(List::stream).toList();
    }

    /**
     * @return the lowest block the room's meshes reach
     */
    static int bottom() {
        return SECTIONS.keySet().stream().mapToInt(section -> SectionPos.sectionToBlockCoord(SectionPos.y(section)))
                .min().orElse(ZoneRooms.ROOM_FLOOR_Y);
    }

    /**
     * @return just above the highest block the room's meshes reach
     */
    static int top() {
        return SECTIONS.keySet().stream()
                .mapToInt(section -> SectionPos.sectionToBlockCoord(SectionPos.y(section)) + SectionPos.SECTION_SIZE)
                .max().orElse(ZoneRooms.ROOM_FLOOR_Y + 1);
    }

    private static void layOut(ClientLevel level, RoomExtent.Bounds bounds) {
        int reach = RoomExtent.wholeRings(bounds.extent());
        for (int chunkX = SectionPos.blockToSectionCoord(bounds.centerX() - reach);
                chunkX <= SectionPos.blockToSectionCoord(bounds.centerX() + reach); chunkX++) {
            for (int chunkZ = SectionPos.blockToSectionCoord(bounds.centerZ() - reach);
                    chunkZ <= SectionPos.blockToSectionCoord(bounds.centerZ() + reach); chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk != null) {
                    layOutChunk(level, chunk, bounds);
                }
            }
        }
    }

    private static void layOutChunk(ClientLevel level, LevelChunk chunk, RoomExtent.Bounds bounds) {
        takeBlockEntities(chunk, bounds);
        for (int index = 0; index < chunk.getSections().length; index++) {
            LevelChunkSection section = chunk.getSection(index);
            if (!section.hasOnlyAir()) {
                layOutSection(level, SectionPos.of(chunk.getPos(), chunk.getSectionYFromSectionIndex(index)),
                        bounds);
            }
        }
    }

    /**
     * Lays one marked section out afresh, and its chunk's block entities with it.
     *
     * @param level   the client level
     * @param section the section
     * @param bounds  the room's boundary
     */
    private static void layOutAfresh(ClientLevel level, SectionPos section, RoomExtent.Bounds bounds) {
        Map<ChunkSectionLayer, Uploaded> old = SECTIONS.remove(section.asLong());
        if (old != null) {
            close(old);
        }
        LevelChunk chunk = level.getChunkSource().getChunk(section.x(), section.z(), false);
        if (chunk == null) {
            CHUNK_ENTITIES.remove(ChunkPos.pack(section.x(), section.z()));
            return;
        }
        takeBlockEntities(chunk, bounds);
        if (section.y() >= chunk.getMinSectionY() && section.y() <= chunk.getMaxSectionY()
                && !chunk.getSection(chunk.getSectionIndexFromSectionY(section.y())).hasOnlyAir()) {
            layOutSection(level, section, bounds);
        }
    }

    private static void takeBlockEntities(LevelChunk chunk, RoomExtent.Bounds bounds) {
        CHUNK_ENTITIES.put(chunk.getPos().pack(), chunk.getBlockEntities().values().stream()
                .filter(entity -> bounds.holdsWholeBlock(entity.getBlockPos().getX(), entity.getBlockPos().getZ()))
                .toList());
    }

    private static void layOutSection(ClientLevel level, SectionPos section, RoomExtent.Bounds bounds) {
        SectionMeshes.Built meshes = SectionMeshes.tesselate(level, section,
                (pos, state) -> bounds.holdsWholeBlock(pos.getX(), pos.getZ()) && !isFloor(pos, state));
        meshes.meshes().forEach((layer, mesh) -> SECTIONS
                .computeIfAbsent(section.asLong(), key -> new EnumMap<>(ChunkSectionLayer.class))
                .put(layer, upload(section.origin(), mesh)));
        meshes.close();
    }

    private static void close(Map<ChunkSectionLayer, Uploaded> section) {
        section.values().forEach(mesh -> mesh.vertices().close());
    }

    private static boolean isFloor(BlockPos pos, BlockState state) {
        return pos.getY() == ZoneRooms.ROOM_FLOOR_Y && state.is(GooBlocks.VOIDSTONE.get());
    }

    private static Uploaded upload(BlockPos origin, MeshData mesh) {
        GpuBuffer vertices = RenderSystem.getDevice().createBuffer(() -> SECTION_LABEL, GpuBuffer.USAGE_VERTEX,
                mesh.vertexBuffer());
        return new Uploaded(origin, vertices, mesh.drawState().indexCount());
    }
}
