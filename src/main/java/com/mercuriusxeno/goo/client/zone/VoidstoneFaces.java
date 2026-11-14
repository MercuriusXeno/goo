package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.zone.ZoneRooms;
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
 * and again in each view through a linked portal, around that view's camera.
 * Every voidstone out to the render distance draws: inside the room, on the
 * boundary and past it, where the shader shows the copy standing there, so
 * the copies' floor needs none of the room's blocks loaded. A row of floor
 * tops draws as one quad, so the reach costs a few quads a section.
 * decision outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform
 * decision boundary-cut-voidstone-draws-without-its-stars
 * decision room-tiles-to-render-distance-held-to-the-picture
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class VoidstoneFaces {

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
    /** Where a packed face keeps how many blocks east its quad runs, less one: past its direction's bits. */
    private static final int RUN_SHIFT = 15;
    private static final int RUN_MASK = 0xF;
    /** Where a packed face keeps how many blocks south its quad runs, less one: past its run east. */
    private static final int DEPTH_SHIFT = 19;
    /** No run of tops or bottoms yet in a layer. */
    private static final int NO_RUN = -1;
    /** A ring's north and south rows lie twice its radius apart. */
    private static final int SIDES_PER_RING = 2;
    /** The new sections a frame scans at most, nearest first, the rest drawing from the frames after. */
    private static final int SCANS_PER_FRAME = 24;
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
    private static int scansThisFrame;
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
        if (level == null || level.dimension() != ZoneRooms.POCKET) {
            return;
        }
        // inside a view through a linked portal the faces stand around the camera the view is drawn from
        Vec3 camera = PortalView.drawing() ? PortalView.cameraPosition() : mc.gameRenderer.getMainCamera().position();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.VOIDSTONE_TYPE);
        PoseStack.Pose pose = event.getPoseStack().last();
        int floorSection = SectionPos.blockToSectionCoord(ZoneRooms.ROOM_FLOOR_Y);
        int centerX = SectionPos.blockToSectionCoord(camera.x);
        int centerZ = SectionPos.blockToSectionCoord(camera.z);
        int reach = mc.options.getEffectiveRenderDistance();
        scansThisFrame = 0;
        // ring by ring out from the camera, so the sections a frame's scan budget reaches first are the nearest
        for (int ring = 0; ring <= reach; ring++) {
            for (int sectionX = centerX - ring; sectionX <= centerX + ring; sectionX++) {
                int step = sectionX == centerX - ring || sectionX == centerX + ring ? 1
                        : Math.max(1, SIDES_PER_RING * ring);
                for (int sectionZ = centerZ - ring; sectionZ <= centerZ + ring; sectionZ += step) {
                    drawSection(level, consumer, pose, camera, sectionX, floorSection, sectionZ);
                }
            }
        }
        buffers.endBatch(GooRenderTypes.VOIDSTONE_TYPE);
    }

    private static void drawSection(ClientLevel level, VertexConsumer consumer, PoseStack.Pose pose, Vec3 camera,
                                    int sectionX, int sectionY, int sectionZ) {
        long key = SectionPos.asLong(sectionX, sectionY, sectionZ);
        int[] faces = SECTIONS.get(key);
        if (level.getChunkSource().getChunk(sectionX, sectionZ, false) == null) {
            // the pocket's floor stands whole everywhere (decision voidstone-floor-stands-whole-and-growth-moves-
            // the-boundary), so a chunk the client has yet to receive, as after crossing a room wider than the
            // client's view, still draws its floor and the ground never flashes away
            faces = wholeFloorOf(sectionY);
        } else if (faces == null) {
            // a frame scans a bounded number of new sections, so chunks loading by the hundred spread over frames
            if (scansThisFrame >= SCANS_PER_FRAME) {
                return;
            }
            faces = scan(level, sectionX, sectionY, sectionZ);
            SECTIONS.put(key, faces);
        }
        int cornerX = SectionPos.sectionToBlockCoord(sectionX);
        int cornerZ = SectionPos.sectionToBlockCoord(sectionZ);
        double originX = cornerX - camera.x;
        double originY = SectionPos.sectionToBlockCoord(sectionY) - camera.y;
        double originZ = cornerZ - camera.z;
        for (int face : faces) {
            float blockX = (float) (originX + (face & AXIS_MASK));
            float blockY = (float) (originY + (face >> AXIS_BITS & AXIS_MASK));
            float blockZ = (float) (originZ + (face >> Z_SHIFT & AXIS_MASK));
            emitFace(consumer, pose, blockX, blockY, blockZ,
                    Direction.from3DDataValue(face >> DIRECTION_SHIFT & DIRECTION_MASK),
                    (face >> RUN_SHIFT & RUN_MASK) + 1, (face >> DEPTH_SHIFT & RUN_MASK) + 1);
        }
    }

    /**
     * A section's floor layer drawn whole: its top and its bottom, each one quad across the section, where the
     * floor height crosses the section, and nothing where it does not.
     *
     * @param sectionY the section's y
     * @return the faces, packed as a scan packs them
     */
    static int[] wholeFloorOf(int sectionY) {
        int layer = ZoneRooms.ROOM_FLOOR_Y - SectionPos.sectionToBlockCoord(sectionY);
        if (layer < 0 || layer >= SECTION_BLOCKS) {
            return NO_FACES;
        }
        int whole = RUN_MASK << RUN_SHIFT | RUN_MASK << DEPTH_SHIFT | layer << AXIS_BITS;
        return new int[] {whole | Direction.DOWN.get3DDataValue() << DIRECTION_SHIFT,
            whole | Direction.UP.get3DDataValue() << DIRECTION_SHIFT};
    }

    /**
     * Emits one voidstone face as a quad over its block's side, stretched east and south over a rectangle of
     * blocks.
     *
     * @param consumer  the voidstone pipeline's vertex consumer
     * @param pose      the pose
     * @param blockX    the block's low x corner, as the pose lays it out
     * @param blockY    the block's low y corner
     * @param blockZ    the block's low z corner
     * @param direction the side the face covers
     * @param run       how many blocks east the face runs, 1 for its own block alone
     * @param depth     how many blocks south the face runs, 1 for its own block alone
     */
    private static void emitFace(VertexConsumer consumer, PoseStack.Pose pose, float blockX, float blockY,
            float blockZ, Direction direction, int run, int depth) {
        for (float[] corner : CORNERS[direction.get3DDataValue()]) {
            consumer.addVertex(pose, blockX + corner[X] * run, blockY + corner[Y], blockZ + corner[Z] * depth)
                    .setColor(OPAQUE_WHITE)
                    .setNormal(pose, direction.getStepX(), direction.getStepY(), direction.getStepZ());
        }
    }

    /**
     * Whether a voidstone shows its face one way: its neighbour that way does not cover it whole.
     *
     * @param level     the client level
     * @param beside    the neighbour's place
     * @param direction the way from the voidstone to its neighbour
     * @return true where the face shows
     */
    private static boolean showsFace(ClientLevel level, BlockPos beside, Direction direction) {
        return level.getBlockState(beside).getFaceOcclusionShape(direction.getOpposite()) != Shapes.block();
    }

    /**
     * Finds a section's showing voidstone faces: every face of a voidstone its
     * neighbour does not cover whole, the tops and the bottoms merged into
     * rectangles, so a whole floor section draws as one quad each way.
     *
     * @param level    the client level
     * @param sectionX the section's x
     * @param sectionY the section's y
     * @param sectionZ the section's z
     * @return the faces, each packed as its block's place in the section, its direction and its rectangle
     */
    private static int[] scan(ClientLevel level, int sectionX, int sectionY, int sectionZ) {
        scansThisFrame++;
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
        int[] lastRun = {NO_RUN, NO_RUN};
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
                        if (!showsFace(level, beside, direction)) {
                            continue;
                        }
                        int face = x | y << AXIS_BITS | z << Z_SHIFT | direction.get3DDataValue() << DIRECTION_SHIFT;
                        boolean flat = direction.getAxis() == Direction.Axis.Y;
                        int last = flat ? lastRun[direction.get3DDataValue()] : NO_RUN;
                        if (last >= 0 && extendsRun(faces[last], face)) {
                            faces[last] += 1 << RUN_SHIFT;
                            continue;
                        }
                        if (count == faces.length) {
                            faces = Arrays.copyOf(faces, faces.length * GROWTH);
                        }
                        if (flat) {
                            lastRun[direction.get3DDataValue()] = count;
                        }
                        faces[count++] = face;
                    }
                }
            }
        }
        return mergeRows(faces, count);
    }

    /**
     * Merges each run of tops or bottoms with the identical runs in the rows south of it, into one rectangle.
     *
     * @param faces the faces, rows in order north to south within each layer
     * @param count how many of them stand
     * @return the faces merged
     */
    static int[] mergeRows(int[] faces, int count) {
        boolean[] merged = new boolean[count];
        int[] kept = new int[count];
        int keptCount = 0;
        for (int first = 0; first < count; first++) {
            if (merged[first]) {
                continue;
            }
            int face = faces[first];
            for (int later = first + 1; later < count; later++) {
                if (!merged[later] && continuesSouth(face, faces[later])) {
                    face += 1 << DEPTH_SHIFT;
                    merged[later] = true;
                }
            }
            kept[keptCount++] = face;
        }
        return Arrays.copyOf(kept, keptCount);
    }

    /**
     * Whether a run of tops or bottoms continues a rectangle south: the same run east, the same layer and side,
     * in the row just past the rectangle's south end.
     *
     * @param rectangle the packed rectangle so far
     * @param run       the packed run
     * @return true where the run extends the rectangle
     */
    private static boolean continuesSouth(int rectangle, int run) {
        int direction = rectangle >> DIRECTION_SHIFT & DIRECTION_MASK;
        if (Direction.from3DDataValue(direction).getAxis() != Direction.Axis.Y
                || (run >> DEPTH_SHIFT & RUN_MASK) != 0) {
            return false;
        }
        int depth = (rectangle >> DEPTH_SHIFT & RUN_MASK) + 1;
        int southEnd = (rectangle >> Z_SHIFT & AXIS_MASK) + depth;
        int runRow = run >> Z_SHIFT & AXIS_MASK;
        return sameRunAndLayer(rectangle, run) && southEnd == runRow;
    }

    /**
     * Whether two packed faces share their run east, their x, their layer and their side.
     *
     * @param one   a packed face
     * @param other another
     * @return true where all four match
     */
    private static boolean sameRunAndLayer(int one, int other) {
        int kept = one ^ other;
        int compared = kept & ~(AXIS_MASK << Z_SHIFT);
        int belowDepth = (1 << DEPTH_SHIFT) - 1;
        return (compared & belowDepth) == 0;
    }

    /**
     * Whether a top or bottom face continues a run: the same row and side, the block just past the run's east
     * end.
     *
     * @param run  the packed run so far
     * @param face the packed top face
     * @return true where the face extends the run
     */
    static boolean extendsRun(int run, int face) {
        int length = (run >> RUN_SHIFT & RUN_MASK) + 1;
        int eastEnd = (run & AXIS_MASK) + length;
        int faceX = face & AXIS_MASK;
        return sameRowAndSide(run, face) && eastEnd == faceX;
    }

    /**
     * Whether two packed faces share their layer, their row and their side, whatever their x or run.
     *
     * @param one   a packed face
     * @param other another
     * @return true where all three match
     */
    private static boolean sameRowAndSide(int one, int other) {
        int differing = one ^ other;
        int rowBits = (1 << RUN_SHIFT) - 1 - AXIS_MASK;
        return (differing & rowBits) == 0;
    }
}
