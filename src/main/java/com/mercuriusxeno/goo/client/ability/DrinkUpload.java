package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A drink's upload for the drink field shader, cut by region of space rather
 * than by stream: the space about the drink is tiled in one-block regions,
 * and every region some body's field reaches becomes one proxy box with one
 * uniform block holding only the bodies that reach it, grouped per stream so
 * the shader reads each skeleton at its least distance and sums them as the
 * field does, the rings those bodies need copied in, and the coat of every
 * stream present so a hit reads its own stream's texture, light and goo. A
 * pixel is so marched once for each region its ray crosses rather than once
 * for every stream that overlaps there, which is what keeps a crowd of
 * streams cheap. Positions are camera-relative.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkUpload {

    /** Blocks a region spans each way; a region is one proxy the shader marches, aligned to the world grid. */
    public static final double REGION = 1;
    /** The most streams one region's block holds coats and rings for. */
    static final int MOST_STREAMS = 16;
    /** The most rings a region's block carries. */
    static final int MOST_RINGS = 224;
    /** The most boxes a region's block carries. */
    static final int MOST_BOXES = 8;
    /** The most table entries a region lists. */
    static final int MOST_ENTRIES = 1024;
    /** The most goo types mingled over one stream. */
    public static final int MOST_LAYERS = 3;
    /** The flag on a table entry that begins a skeleton's run. */
    static final int RUN_START = 1 << 16;
    /** Table entries at and over this name a box, below it a segment. */
    static final int BOX_BASE = 1 << 12;
    /** The vec4 slots one stream's coat takes in the block. */
    static final int STREAM_VEC4S = 14;
    static final int COAT_SLOT = 0;
    static final int TINT_SLOT = 1;
    static final int SPRITE_SLOT = 2;
    static final int SIDE_SLOT = 3;
    static final int ACROSS_SLOT = 4;
    static final int LAYER_TINT_SLOT = 5;
    static final int LAYER_SPRITE_SLOT = 8;
    static final int LAYER_SHARE_SLOT = 11;

    static final int VEC4 = 16;
    static final int REGION_AT = 0;
    static final int COUNTS_AT = REGION_AT + VEC4;
    static final int STREAMS_AT = COUNTS_AT + VEC4;
    static final int TABLE_AT = STREAMS_AT + MOST_STREAMS * STREAM_VEC4S * VEC4;
    static final int BOXES_AT = TABLE_AT + MOST_ENTRIES * Integer.BYTES;
    static final int RINGS_AT = BOXES_AT + MOST_BOXES * 2 * VEC4;
    /** The block's size in bytes. */
    public static final int BYTES = RINGS_AT + MOST_RINGS * 2 * VEC4;

    private static final int PAIR = 2;
    private static final int Y = 1;
    private static final int Z = 2;
    private static final int W = 3;
    /** No ring, or no entry. */
    private static final int NONE = -1;

    /**
     * What a stream is drawn with.
     *
     * @param tint       the block sprite's tint, ARGB
     * @param sprite     the block's sprite, or unstable goo's while the zoop flies
     * @param blockLight the block light along the stream, in lightmap coordinates
     * @param skyLight   the sky light along the stream, in lightmap coordinates
     * @param zoop       whether the zoop is flying, which wears unstable goo's sprite and no blotches
     * @param layers     the block's goo types mingled over it, largest first
     */
    public record Coat(int tint, GooRenderUtil.UvRect sprite, int blockLight, int skyLight, boolean zoop,
                       List<Layer> layers) {
    }

    /**
     * One goo type's layer over a stream.
     *
     * @param tint       the type's tint, ARGB
     * @param sprite     the type's sprite
     * @param before     the volume ratio of every earlier layer together
     * @param cumulative before plus this type's ratio
     * @param seed       the type's noise seed
     */
    public record Layer(int tint, GooRenderUtil.UvRect sprite, float before, float cumulative, int seed) {
    }

    /**
     * One proxy box, camera-relative.
     *
     * @param low  its low corner
     * @param high its high corner
     */
    public record Proxy(Vec3 low, Vec3 high) {
    }

    /**
     * One region's upload.
     *
     * @param bytes   the uniform block, {@link #BYTES} long
     * @param proxies the proxy boxes to draw, the region's one
     * @param dropped how many bodies and table entries the caps left out, 0 for a whole upload
     */
    public record Block(ByteBuffer bytes, List<Proxy> proxies, int dropped) {
    }

    /**
     * A body with the bounds its field reaches.
     *
     * @param skeleton the skeleton's index
     * @param body     the body's index within it
     * @param low      the low corner of its reach
     * @param high     the high corner of its reach
     */
    record Bound(int skeleton, int body, Vec3 low, Vec3 high) {
    }

    private final List<DrinkField.Skeleton> skeletons;
    private final List<Coat> coats;
    private final Vec3 camera;
    private final BlockPos region;
    private final Map<Integer, Integer> streamOf = new LinkedHashMap<>();
    private final Map<Integer, Integer> ringBaseOf = new LinkedHashMap<>();
    private final Map<Integer, Integer> firstRingOf = new LinkedHashMap<>();
    private final Map<Integer, Integer> boxOf = new LinkedHashMap<>();
    private final List<DrinkStream.Ring> rings = new ArrayList<>();
    private final List<Integer> ringStreams = new ArrayList<>();
    private final List<DrinkBody.Box> boxes = new ArrayList<>();
    private final List<Integer> boxStreams = new ArrayList<>();
    private final List<Integer> table = new ArrayList<>();
    private int dropped;

    private DrinkUpload(List<DrinkField.Skeleton> skeletons, List<Coat> coats, Vec3 camera, BlockPos region) {
        this.skeletons = skeletons;
        this.coats = coats;
        this.camera = camera;
        this.region = region;
    }

    /**
     * Builds a drink's uploads, one for every region some body's field reaches.
     *
     * @param skeletons      every skeleton of the drink with liquid to show
     * @param coats          what each skeleton's stream is drawn with, in the same order
     * @param camera         the camera's position
     * @param depthZeroToOne whether the device's depth runs 0 to 1 rather than -1 to 1
     * @return the uploads, in region order
     */
    public static List<Block> of(List<DrinkField.Skeleton> skeletons, List<Coat> coats, Vec3 camera,
                                 boolean depthZeroToOne) {
        Map<BlockPos, List<Bound>> regions = new TreeMap<>();
        for (int index = 0; index < skeletons.size(); index++) {
            for (int body = 0; body < skeletons.get(index).bodies(); body++) {
                if (radiates(skeletons.get(index), body)) {
                    spread(regions, boundOf(skeletons.get(index), index, body));
                }
            }
        }
        List<Block> blocks = new ArrayList<>();
        regions.forEach((key, bounds) -> blocks.add(new DrinkUpload(skeletons, coats, camera, key)
                .build(bounds, depthZeroToOne)));
        return blocks;
    }

    /**
     * @param skeleton a skeleton
     * @param body     a body's index within it
     * @return whether the body has any radius, so its field reaches anywhere
     */
    static boolean radiates(DrinkField.Skeleton skeleton, int body) {
        List<DrinkStream.Ring> chain = skeleton.rings();
        if (body < chain.size() - 1) {
            return Math.max(chain.get(body).radius(), chain.get(body + 1).radius()) > 0;
        }
        return skeleton.box() != null && skeleton.box().half() > 0;
    }

    /**
     * @param skeleton a skeleton
     * @param index    its index
     * @param body     a body's index within it
     * @return the body with the bounds its field reaches, {@link DrinkField#REACH} past its own
     */
    static Bound boundOf(DrinkField.Skeleton skeleton, int index, int body) {
        return new Bound(index, body,
                skeleton.lowOf(body).subtract(DrinkField.REACH, DrinkField.REACH, DrinkField.REACH),
                skeleton.highOf(body).add(DrinkField.REACH, DrinkField.REACH, DrinkField.REACH));
    }

    /**
     * @param coordinate a world coordinate
     * @return the index of the region holding it along that axis
     */
    static int regionOf(double coordinate) {
        return (int) Math.floor(coordinate / REGION);
    }

    private static void spread(Map<BlockPos, List<Bound>> regions, Bound bound) {
        for (int x = regionOf(bound.low().x); x <= regionOf(bound.high().x); x++) {
            for (int y = regionOf(bound.low().y); y <= regionOf(bound.high().y); y++) {
                for (int z = regionOf(bound.low().z); z <= regionOf(bound.high().z); z++) {
                    regions.computeIfAbsent(new BlockPos(x, y, z), ignored -> new ArrayList<>()).add(bound);
                }
            }
        }
    }

    private Block build(List<Bound> bounds, boolean depthZeroToOne) {
        Map<Integer, List<Integer>> bySkeleton = new TreeMap<>();
        for (Bound bound : bounds) {
            bySkeleton.computeIfAbsent(bound.skeleton(), ignored -> new ArrayList<>()).add(bound.body());
        }
        bySkeleton.forEach((index, bodies) -> {
            bodies.sort(null);
            if (copy(index, bodies)) {
                list(index, bodies);
            } else {
                dropped += bodies.size();
            }
        });
        Vec3 low = new Vec3(region.getX(), region.getY(), region.getZ()).scale(REGION);
        Proxy proxy = new Proxy(low.subtract(camera), low.add(REGION, REGION, REGION).subtract(camera));
        return new Block(write(depthZeroToOne), List.of(proxy), dropped);
    }

    /**
     * Copies a skeleton into the block: the span of its rings its listed
     * segments need, its box where listed, and its stream's coat slot.
     *
     * @param index  the skeleton
     * @param bodies its listed bodies, ascending
     * @return whether the caps left room for it
     */
    private boolean copy(int index, List<Integer> bodies) {
        DrinkField.Skeleton skeleton = skeletons.get(index);
        int segments = skeleton.rings().size() - 1;
        boolean withBox = bodies.getLast() >= segments && skeleton.box() != null;
        int firstRing = bodies.getFirst() < segments ? bodies.getFirst() : 0;
        int lastRing = bodies.getFirst() < segments ? Math.min(bodies.getLast(), segments - 1) + 1 : NONE;
        int count = lastRing - firstRing + 1;
        if (!roomFor(count, withBox)) {
            return false;
        }
        streamOf.put(index, streamOf.size());
        ringBaseOf.put(index, rings.size());
        firstRingOf.put(index, firstRing);
        for (int ring = firstRing; ring <= lastRing; ring++) {
            rings.add(skeleton.rings().get(ring));
            ringStreams.add(index);
        }
        if (withBox) {
            boxOf.put(index, boxes.size());
            boxes.add(skeleton.box());
            boxStreams.add(index);
        }
        return true;
    }

    /**
     * @param count   rings to copy
     * @param withBox whether a box comes with them
     * @return whether the caps leave room for another stream, the rings and the box
     */
    private boolean roomFor(int count, boolean withBox) {
        boolean roomForBox = !withBox || boxes.size() < MOST_BOXES;
        return streamOf.size() < MOST_STREAMS && rings.size() + count <= MOST_RINGS && roomForBox;
    }

    /**
     * Lists a copied skeleton's bodies in the table as one run, up to the table's cap.
     *
     * @param index  the skeleton
     * @param bodies its listed bodies
     */
    private void list(int index, List<Integer> bodies) {
        boolean starting = true;
        for (int body : bodies) {
            int entry = entryOf(index, body);
            if (entry < 0) {
                continue;
            }
            if (table.size() >= MOST_ENTRIES) {
                dropped++;
            } else {
                table.add(starting ? entry | RUN_START : entry);
                starting = false;
            }
        }
    }

    /**
     * @param index a copied skeleton
     * @param body  a body of it
     * @return the table entry naming the body in the block, or {@link #NONE} where it was not copied
     */
    private int entryOf(int index, int body) {
        int segments = skeletons.get(index).rings().size() - 1;
        if (body < segments) {
            return ringBaseOf.get(index) + body - firstRingOf.get(index);
        }
        Integer box = boxOf.get(index);
        return box == null ? NONE : BOX_BASE + box;
    }

    private ByteBuffer write(boolean depthZeroToOne) {
        ByteBuffer bytes = ByteBuffer.allocate(BYTES).order(ByteOrder.nativeOrder());
        Vec3 low = new Vec3(region.getX(), region.getY(), region.getZ()).scale(REGION).subtract(camera);
        putVec3(bytes, REGION_AT, low, depthZeroToOne ? 1 : 0);
        putVec4(bytes, COUNTS_AT, streamOf.size(), rings.size(), boxes.size(), table.size());
        for (Map.Entry<Integer, Integer> stream : streamOf.entrySet()) {
            writeStream(bytes, stream.getKey(), stream.getValue());
        }
        for (int entry = 0; entry < table.size(); entry++) {
            bytes.putInt(TABLE_AT + entry * Integer.BYTES, table.get(entry));
        }
        writeBodies(bytes);
        return bytes;
    }

    private void writeStream(ByteBuffer bytes, int index, int stream) {
        int at = STREAMS_AT + stream * STREAM_VEC4S * VEC4;
        Coat coat = coats.get(index);
        List<Layer> layers = coat.layers().subList(0, Math.min(MOST_LAYERS, coat.layers().size()));
        putVec4(bytes, at + COAT_SLOT * VEC4, coat.blockLight(), coat.skyLight(), coat.zoop() ? 1 : 0, layers.size());
        putTint(bytes, at + TINT_SLOT * VEC4, coat.tint());
        putSprite(bytes, at + SPRITE_SLOT * VEC4, coat.sprite());
        DrinkStream.Path path = skeletons.get(index).stream().path();
        putVec3(bytes, at + SIDE_SLOT * VEC4, DrinkStream.sideOf(path), 0);
        putVec3(bytes, at + ACROSS_SLOT * VEC4, DrinkStream.acrossOf(path), 0);
        for (int layer = 0; layer < layers.size(); layer++) {
            putTint(bytes, at + (LAYER_TINT_SLOT + layer) * VEC4, layers.get(layer).tint());
            putSprite(bytes, at + (LAYER_SPRITE_SLOT + layer) * VEC4, layers.get(layer).sprite());
            putVec4(bytes, at + (LAYER_SHARE_SLOT + layer) * VEC4, layers.get(layer).before(),
                    layers.get(layer).cumulative(), layers.get(layer).seed(), 0);
        }
    }

    private void writeBodies(ByteBuffer bytes) {
        for (int index = 0; index < boxes.size(); index++) {
            DrinkBody.Box box = boxes.get(index);
            putVec3(bytes, BOXES_AT + index * PAIR * VEC4, box.center().subtract(camera), box.half());
            putVec4(bytes, BOXES_AT + (index * PAIR + 1) * VEC4, box.rounding(), streamOf.get(boxStreams.get(index)),
                    0, 0);
        }
        for (int index = 0; index < rings.size(); index++) {
            DrinkStream.Ring ring = rings.get(index);
            putVec3(bytes, RINGS_AT + index * PAIR * VEC4, ring.center().subtract(camera), ring.radius());
            putVec4(bytes, RINGS_AT + (index * PAIR + 1) * VEC4, ring.material(), ring.share(),
                    streamOf.get(ringStreams.get(index)), 0);
        }
    }

    private static void putVec4(ByteBuffer bytes, int at, double x, double y, double z, double w) {
        bytes.putFloat(at, (float) x);
        bytes.putFloat(at + Y * Float.BYTES, (float) y);
        bytes.putFloat(at + Z * Float.BYTES, (float) z);
        bytes.putFloat(at + W * Float.BYTES, (float) w);
    }

    private static void putVec3(ByteBuffer bytes, int at, Vec3 v, double w) {
        putVec4(bytes, at, v.x, v.y, v.z, w);
    }

    private static void putTint(ByteBuffer bytes, int at, int argb) {
        putVec4(bytes, at, ARGB.redFloat(argb), ARGB.greenFloat(argb), ARGB.blueFloat(argb), 1);
    }

    private static void putSprite(ByteBuffer bytes, int at, GooRenderUtil.UvRect sprite) {
        putVec4(bytes, at, sprite.u0(), sprite.v0(), sprite.u1(), sprite.v1());
    }
}
