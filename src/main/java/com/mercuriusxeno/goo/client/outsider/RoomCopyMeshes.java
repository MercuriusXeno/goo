package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import com.mercuriusxeno.goo.client.ability.SectionMeshes;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The meshes every copy of the Outsider room the player stands in draws, laid
 * out once and kept on the GPU until a block in the room changes or the room
 * grows a ring: each section's blocks standing wholly inside the boundary,
 * the floor's starred voidstone faces, and the block entities the room holds.
 * The floor's own plain dark blocks are left out, since the pocket's floor
 * stands whole under every copy already.
 * decision room-tiles-to-render-distance-held-to-the-picture
 * decision voidstone-floor-stands-whole-and-growth-moves-the-boundary
 */
final class RoomCopyMeshes {

    private static final String SECTION_LABEL = "Goo room copy section";
    private static final String FLOOR_LABEL = "Goo room copy floor";
    private static final int FLOOR_BUFFER_START = 1 << 12;

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
    private static boolean dirty;
    private static final Map<ChunkSectionLayer, List<Uploaded>> LAYERS = new EnumMap<>(ChunkSectionLayer.class);
    private static @Nullable Uploaded floor;
    private static final List<BlockEntity> BLOCK_ENTITIES = new ArrayList<>();
    private static int bottom = OutsiderRooms.ROOM_FLOOR_Y;
    private static int top = OutsiderRooms.ROOM_FLOOR_Y + 1;

    private RoomCopyMeshes() {
    }

    /**
     * Notes a section's blocks changed, so the meshes lay out afresh where the room stands in it.
     *
     * @param sectionX the section's x
     * @param sectionZ the section's z
     */
    static void markDirty(int sectionX, int sectionZ) {
        Key room = built;
        if (room != null && overlaps(sectionX, room.centerX(), room.wholeRings())
                && overlaps(sectionZ, room.centerZ(), room.wholeRings())) {
            dirty = true;
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
     * Lays the meshes out afresh where the room changed since they were laid.
     *
     * @param level  the client level
     * @param bounds the room's boundary
     */
    static void refresh(ClientLevel level, RoomExtent.Bounds bounds) {
        Key room = new Key(level, bounds.centerX(), bounds.centerZ(), RoomExtent.wholeRings(bounds.extent()));
        if (room.equals(built) && !dirty) {
            return;
        }
        release();
        built = room;
        dirty = false;
        layOut(level, bounds);
    }

    /** Frees every mesh, as the room is left. */
    static void release() {
        LAYERS.values().forEach(meshes -> meshes.forEach(mesh -> mesh.vertices().close()));
        LAYERS.clear();
        if (floor != null) {
            floor.vertices().close();
            floor = null;
        }
        BLOCK_ENTITIES.clear();
        built = null;
    }

    /**
     * @param layer a section layer
     * @return the room's meshes in that layer
     */
    static List<Uploaded> layer(ChunkSectionLayer layer) {
        return LAYERS.getOrDefault(layer, List.of());
    }

    /**
     * @return the floor's starred voidstone faces, null where the room holds none
     */
    static @Nullable Uploaded floor() {
        return floor;
    }

    /**
     * @return the block entities standing wholly inside the room
     */
    static List<BlockEntity> blockEntities() {
        return BLOCK_ENTITIES;
    }

    /**
     * @return the lowest block the room's meshes reach
     */
    static int bottom() {
        return bottom;
    }

    /**
     * @return just above the highest block the room's meshes reach
     */
    static int top() {
        return top;
    }

    private static void layOut(ClientLevel level, RoomExtent.Bounds bounds) {
        int reach = RoomExtent.wholeRings(bounds.extent());
        bottom = OutsiderRooms.ROOM_FLOOR_Y;
        top = OutsiderRooms.ROOM_FLOOR_Y + 1;
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
        floor = floorOf(level, bounds);
    }

    private static void layOutChunk(ClientLevel level, LevelChunk chunk, RoomExtent.Bounds bounds) {
        chunk.getBlockEntities().values().stream()
                .filter(entity -> bounds.holdsWholeBlock(entity.getBlockPos().getX(), entity.getBlockPos().getZ()))
                .forEach(BLOCK_ENTITIES::add);
        for (int index = 0; index < chunk.getSections().length; index++) {
            LevelChunkSection section = chunk.getSection(index);
            if (!section.hasOnlyAir()) {
                layOutSection(level, SectionPos.of(chunk.getPos(), chunk.getSectionYFromSectionIndex(index)),
                        bounds);
            }
        }
    }

    private static void layOutSection(ClientLevel level, SectionPos section, RoomExtent.Bounds bounds) {
        SectionMeshes.Built meshes = SectionMeshes.tesselate(level, section,
                (pos, state) -> bounds.holdsWholeBlock(pos.getX(), pos.getZ()) && !isFloor(pos, state));
        meshes.meshes().forEach((layer, mesh) -> {
            LAYERS.computeIfAbsent(layer, key -> new ArrayList<>()).add(upload(section.origin(), mesh,
                    SECTION_LABEL));
            bottom = Math.min(bottom, section.minBlockY());
            top = Math.max(top, section.maxBlockY() + 1);
        });
        meshes.close();
    }

    private static boolean isFloor(BlockPos pos, BlockState state) {
        return pos.getY() == OutsiderRooms.ROOM_FLOOR_Y && state.is(GooBlocks.VOIDSTONE.get());
    }

    /**
     * The starred voidstone faces of the room's floor, laid out from the
     * room's center block: every face of a floor block wholly inside the
     * boundary that its neighbour does not cover.
     *
     * @param level  the client level
     * @param bounds the room's boundary
     * @return the faces on the GPU, null where none shows
     */
    private static @Nullable Uploaded floorOf(ClientLevel level, RoomExtent.Bounds bounds) {
        BlockPos origin = new BlockPos(bounds.centerX(), OutsiderRooms.ROOM_FLOOR_Y, bounds.centerZ());
        int reach = RoomExtent.wholeRings(bounds.extent());
        PoseStack.Pose identity = new PoseStack().last();
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(FLOOR_BUFFER_START)) {
            BufferBuilder faces = new BufferBuilder(bytes, VertexFormat.Mode.QUADS,
                    DefaultVertexFormat.POSITION_COLOR_NORMAL);
            BlockPos.MutableBlockPos beside = new BlockPos.MutableBlockPos();
            for (BlockPos block : BlockPos.betweenClosed(origin.offset(-reach, 0, -reach),
                    origin.offset(reach, 0, reach))) {
                if (!level.getBlockState(block).is(GooBlocks.VOIDSTONE.get())) {
                    continue;
                }
                for (Direction direction : Direction.values()) {
                    if (VoidstoneFaces.showsFace(level, beside.setWithOffset(block, direction), direction)) {
                        VoidstoneFaces.emitFace(faces, identity, block.getX() - origin.getX(), 0,
                                block.getZ() - origin.getZ(), direction);
                    }
                }
            }
            MeshData mesh = faces.build();
            if (mesh == null) {
                return null;
            }
            try (mesh) {
                return upload(origin, mesh, FLOOR_LABEL);
            }
        }
    }

    private static Uploaded upload(BlockPos origin, MeshData mesh, String label) {
        GpuBuffer vertices = RenderSystem.getDevice().createBuffer(() -> label, GpuBuffer.USAGE_VERTEX,
                mesh.vertexBuffer());
        return new Uploaded(origin, vertices, mesh.drawState().indexCount());
    }
}
