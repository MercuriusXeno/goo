package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ability.PortalView;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jspecify.annotations.Nullable;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Draws every voidstone face around the player through the voidstone shader,
 * with no block entity: the faces of each chunk section on the floor's layer
 * are found once by scanning the section's palette and blocks, kept until the
 * level renderer marks that section dirty, and drawn in one batch a frame,
 * and again in each view through a room's edge, around that view's camera.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 * decision outsider-room-wraps-and-repeats-at-the-platform-edge
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class VoidstoneFaces {

    /** Chunks out from the player whose voidstone the stars draw on, past which the plain dark block stands. */
    private static final int REACH_CHUNKS = 12;
    private static final int SECTION_BLOCKS = 16;
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    private static final int[] NO_FACES = new int[0];
    private static final int AXIS_BITS = 4;
    /** Where a packed face keeps its z: past x's and y's bits. */
    private static final int Z_SHIFT = 2 * AXIS_BITS;
    /** How much a face list grows when it fills. */
    private static final int GROWTH = 2;
    private static final int AXIS_MASK = 0xF;
    private static final int DIRECTION_SHIFT = 12;
    private static final int DIRECTION_MASK = 0x7;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;
    private static final float[][][] CORNERS = {
        {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}},
        {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
        {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}},
        {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}},
        {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}},
        {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}},
    };

    private static final Map<Long, int[]> SECTIONS = new HashMap<>();
    private static @Nullable ClientLevel cachedLevel;

    private VoidstoneFaces() {
    }

    /**
     * Forgets a section's faces, as the level renderer marks it dirty when a block in or beside it changes.
     *
     * @param sectionX the section's x
     * @param sectionY the section's y
     * @param sectionZ the section's z
     */
    public static void markDirty(int sectionX, int sectionY, int sectionZ) {
        SECTIONS.remove(SectionPos.asLong(sectionX, sectionY, sectionZ));
    }

    /**
     * Draws the voidstone faces around the player once the world's blocks are drawn.
     *
     * @param event the level stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level != cachedLevel) {
            SECTIONS.clear();
            cachedLevel = level;
        }
        if (level == null || level.dimension() != OutsiderRooms.POCKET) {
            return;
        }
        // outsider-room-wraps-and-repeats-at-the-platform-edge: inside the view through a room's edge the faces
        // stand around the camera the view is drawn from, so the room's far copy shows its stars in place
        Vec3 camera = PortalView.drawing() ? PortalView.cameraPosition() : mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.VOIDSTONE_TYPE);
        PoseStack.Pose pose = event.getPoseStack().last();
        int floorSection = SectionPos.blockToSectionCoord(OutsiderRooms.ROOM_FLOOR_Y);
        int centerX = SectionPos.blockToSectionCoord(camera.x);
        int centerZ = SectionPos.blockToSectionCoord(camera.z);
        int reach = Math.min(REACH_CHUNKS, mc.options.getEffectiveRenderDistance());
        for (int sectionX = centerX - reach; sectionX <= centerX + reach; sectionX++) {
            for (int sectionZ = centerZ - reach; sectionZ <= centerZ + reach; sectionZ++) {
                drawSection(level, consumer, pose, camera, sectionX, floorSection, sectionZ);
            }
        }
        buffers.endBatch(GooRenderTypes.VOIDSTONE_TYPE);
    }

    private static void drawSection(ClientLevel level, VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera,
                                    int sectionX, int sectionY, int sectionZ) {
        int[] faces = SECTIONS.computeIfAbsent(SectionPos.asLong(sectionX, sectionY, sectionZ),
                key -> scan(level, sectionX, sectionY, sectionZ));
        double originX = SectionPos.sectionToBlockCoord(sectionX) - camera.x;
        double originY = SectionPos.sectionToBlockCoord(sectionY) - camera.y;
        double originZ = SectionPos.sectionToBlockCoord(sectionZ) - camera.z;
        for (int face : faces) {
            float blockX = (float) (originX + (face & AXIS_MASK));
            float blockY = (float) (originY + (face >> AXIS_BITS & AXIS_MASK));
            float blockZ = (float) (originZ + (face >> Z_SHIFT & AXIS_MASK));
            Direction direction = Direction.from3DDataValue(face >> DIRECTION_SHIFT & DIRECTION_MASK);
            for (float[] corner : CORNERS[direction.get3DDataValue()]) {
                consumer.addVertex(pose, blockX + corner[X], blockY + corner[Y], blockZ + corner[Z])
                        .setColor(OPAQUE_WHITE)
                        .setNormal(pose, direction.getStepX(), direction.getStepY(), direction.getStepZ());
            }
        }
    }

    /**
     * Finds a section's showing voidstone faces: every face of a voidstone its neighbour does not cover whole.
     *
     * @param level    the client level
     * @param sectionX the section's x
     * @param sectionY the section's y
     * @param sectionZ the section's z
     * @return the faces, each packed as its block's place in the section and its direction
     */
    private static int[] scan(ClientLevel level, int sectionX, int sectionY, int sectionZ) {
        LevelChunk chunk = level.getChunkSource().getChunk(sectionX, sectionZ, false);
        if (chunk == null || sectionY < chunk.getMinSectionY() || sectionY > chunk.getMaxSectionY()) {
            return NO_FACES;
        }
        LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sectionY));
        if (section.hasOnlyAir() || !section.maybeHas(state -> state.is(GooBlocks.VOIDSTONE.get()))) {
            return NO_FACES;
        }
        int[] faces = new int[SECTION_BLOCKS * SECTION_BLOCKS * SECTION_BLOCKS];
        int count = 0;
        BlockPos.MutableBlockPos beside = new BlockPos.MutableBlockPos();
        for (int y = 0; y < SECTION_BLOCKS; y++) {
            for (int z = 0; z < SECTION_BLOCKS; z++) {
                for (int x = 0; x < SECTION_BLOCKS; x++) {
                    if (!section.getBlockState(x, y, z).is(GooBlocks.VOIDSTONE.get())) {
                        continue;
                    }
                    for (Direction direction : Direction.values()) {
                        beside.set(SectionPos.sectionToBlockCoord(sectionX) + x + direction.getStepX(),
                                SectionPos.sectionToBlockCoord(sectionY) + y + direction.getStepY(),
                                SectionPos.sectionToBlockCoord(sectionZ) + z + direction.getStepZ());
                        if (level.getBlockState(beside).getFaceOcclusionShape(direction.getOpposite())
                                != Shapes.block()) {
                            if (count == faces.length) {
                                faces = Arrays.copyOf(faces, faces.length * GROWTH);
                            }
                            faces[count++] = x | y << AXIS_BITS | z << Z_SHIFT
                                    | direction.get3DDataValue() << DIRECTION_SHIFT;
                        }
                    }
                }
            }
        }
        return Arrays.copyOf(faces, count);
    }
}
