package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One stream's upload for the drink field shader: the uniform block the
 * shader marches, and the proxy boxes it marches inside. Every body of the
 * stream that radiates gets a proxy, its bounds grown by the field's reach,
 * and the block lists for each proxy every body of the drink whose reach
 * meets it, the stream's own bodies first and each other stream's as its own
 * run, so the shader reads each skeleton at its least distance and sums them
 * as the field does; the rings those bodies need are copied into the block in
 * runs, with the frame of their own path, so a point on any of them reads its
 * texture in that path's frame. Positions are camera-relative.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class DrinkUpload {

    /** The most rings a stream's block carries, its own and the copied runs of its neighbours'. */
    static final int MOST_RINGS = 192;
    /** The most boxes a stream's block carries. */
    static final int MOST_BOXES = 8;
    /** The most proxies a stream draws. */
    static final int MOST_PROXIES = 64;
    /**
     * The most table entries a stream's proxies list: the longest stream's sixty proxies each list the nine
     * segments within reach of them and the bodies of every neighbour at a join, and the block stays under
     * the 16 KB every device grants a uniform block.
     */
    static final int MOST_ENTRIES = 1536;
    /** The most paths whose frames a block carries, the stream's own and its neighbours'. */
    static final int MOST_FRAMES = 8;
    /** The most goo types mingled over one stream. */
    public static final int MOST_LAYERS = 3;
    /** The flag on a table entry that begins a skeleton's run. */
    static final int RUN_START = 1 << 16;
    /** Table entries at and over this name a box, below it a segment. */
    static final int BOX_BASE = 1 << 12;

    static final int VEC4 = 16;
    static final int COAT_AT = 0;
    static final int TINT_AT = COAT_AT + VEC4;
    static final int SPRITE_AT = TINT_AT + VEC4;
    static final int COUNTS_AT = SPRITE_AT + VEC4;
    static final int LAYER_TINT_AT = COUNTS_AT + VEC4;
    static final int LAYER_SPRITE_AT = LAYER_TINT_AT + MOST_LAYERS * VEC4;
    static final int LAYER_SHARE_AT = LAYER_SPRITE_AT + MOST_LAYERS * VEC4;
    static final int FRAMES_AT = LAYER_SHARE_AT + MOST_LAYERS * VEC4;
    static final int PROXY_LOW_AT = FRAMES_AT + MOST_FRAMES * 2 * VEC4;
    static final int PROXY_HIGH_AT = PROXY_LOW_AT + MOST_PROXIES * VEC4;
    static final int TABLE_AT = PROXY_HIGH_AT + MOST_PROXIES * VEC4;
    static final int BOXES_AT = TABLE_AT + MOST_ENTRIES * Integer.BYTES;
    static final int RINGS_AT = BOXES_AT + MOST_BOXES * 2 * VEC4;
    /** The block's size in bytes. */
    public static final int BYTES = RINGS_AT + MOST_RINGS * 2 * VEC4;

    private static final int PAIR = 2;
    /** No ring, or no entry. */
    private static final int NONE = -1;
    private static final int Y = 1;
    private static final int Z = 2;
    private static final int W = 3;

    /**
     * What a stream is drawn with.
     *
     * @param tint           the block sprite's tint, ARGB
     * @param sprite         the block's sprite, or unstable goo's while the zoop flies
     * @param blockLight     the block light along the stream, in lightmap coordinates
     * @param skyLight       the sky light along the stream, in lightmap coordinates
     * @param zoop           whether the zoop is flying, which wears unstable goo's sprite and no blotches
     * @param depthZeroToOne whether the device's depth runs 0 to 1 rather than -1 to 1
     * @param layers         the block's goo types mingled over it, largest first
     */
    public record Coat(int tint, GooRenderUtil.UvRect sprite, int blockLight, int skyLight, boolean zoop,
                       boolean depthZeroToOne, List<Layer> layers) {
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
     * A stream's upload.
     *
     * @param bytes   the uniform block, {@link #BYTES} long
     * @param proxies the proxy boxes to draw, in the block's order
     * @param dropped how many table entries the cap left out, 0 for a whole upload
     */
    public record Block(ByteBuffer bytes, List<Proxy> proxies, int dropped) {
    }

    private final List<DrinkField.Skeleton> skeletons;
    private final Vec3 camera;
    private final List<Proxy> proxies = new ArrayList<>();
    private final List<Map<Integer, List<Integer>>> candidates = new ArrayList<>();
    private final Map<Integer, Integer> frameOf = new LinkedHashMap<>();
    private final Map<Integer, Integer> ringBaseOf = new LinkedHashMap<>();
    private final Map<Integer, Integer> firstRingOf = new LinkedHashMap<>();
    private final Map<Integer, Integer> boxOf = new LinkedHashMap<>();
    private final List<Integer> ringSkeletons = new ArrayList<>();
    private final List<DrinkStream.Ring> rings = new ArrayList<>();
    private final List<DrinkBody.Box> boxes = new ArrayList<>();
    private final List<Integer> table = new ArrayList<>();
    private final List<int[]> ranges = new ArrayList<>();
    private int dropped;

    private DrinkUpload(List<DrinkField.Skeleton> skeletons, Vec3 camera) {
        this.skeletons = skeletons;
        this.camera = camera;
    }

    /**
     * Builds one stream's upload.
     *
     * @param skeletons every skeleton of the drink with liquid to show
     * @param own       the index of the stream's skeleton
     * @param camera    the camera's position
     * @param coat      what the stream is drawn with
     * @return the upload
     */
    public static Block of(List<DrinkField.Skeleton> skeletons, int own, Vec3 camera, Coat coat) {
        DrinkUpload upload = new DrinkUpload(skeletons, camera);
        upload.layProxies(own);
        upload.copy(own);
        upload.copyNeighbours(own);
        upload.tabulate();
        return new Block(upload.write(coat), List.copyOf(upload.proxies), upload.dropped);
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
     * @param body     a body's index within it
     * @param low      a box's low corner
     * @param high     its high corner
     * @return whether the body's field, reaching {@link DrinkField#REACH} past its own bounds, meets the box
     */
    static boolean reaches(DrinkField.Skeleton skeleton, int body, Vec3 low, Vec3 high) {
        Vec3 bodyLow = skeleton.lowOf(body);
        Vec3 bodyHigh = skeleton.highOf(body);
        return overlaps(bodyLow.x, bodyHigh.x, low.x, high.x) && overlaps(bodyLow.y, bodyHigh.y, low.y, high.y)
                && overlaps(bodyLow.z, bodyHigh.z, low.z, high.z);
    }

    private static boolean overlaps(double bodyLow, double bodyHigh, double low, double high) {
        return bodyLow - DrinkField.REACH <= high && bodyHigh + DrinkField.REACH >= low;
    }

    private void layProxies(int own) {
        DrinkField.Skeleton mine = skeletons.get(own);
        for (int body = 0; body < mine.bodies() && proxies.size() < MOST_PROXIES; body++) {
            if (radiates(mine, body)) {
                Vec3 low = mine.lowOf(body).subtract(DrinkField.REACH, DrinkField.REACH, DrinkField.REACH);
                Vec3 high = mine.highOf(body).add(DrinkField.REACH, DrinkField.REACH, DrinkField.REACH);
                proxies.add(new Proxy(low.subtract(camera), high.subtract(camera)));
                candidates.add(candidatesIn(own, low, high));
            }
        }
    }

    /**
     * @param own  the stream's skeleton, listed first
     * @param low  a proxy's low corner
     * @param high its high corner
     * @return the bodies whose field meets the proxy, by skeleton, the stream's own first
     */
    private Map<Integer, List<Integer>> candidatesIn(int own, Vec3 low, Vec3 high) {
        Map<Integer, List<Integer>> found = new LinkedHashMap<>();
        found.put(own, bodiesIn(skeletons.get(own), low, high));
        for (int index = 0; index < skeletons.size(); index++) {
            if (index != own) {
                List<Integer> bodies = bodiesIn(skeletons.get(index), low, high);
                if (!bodies.isEmpty()) {
                    found.put(index, bodies);
                }
            }
        }
        return found;
    }

    private static List<Integer> bodiesIn(DrinkField.Skeleton skeleton, Vec3 low, Vec3 high) {
        List<Integer> bodies = new ArrayList<>();
        for (int body = 0; body < skeleton.bodies(); body++) {
            if (radiates(skeleton, body) && reaches(skeleton, body, low, high)) {
                bodies.add(body);
            }
        }
        return bodies;
    }

    /**
     * Copies a skeleton's rings from the first to the last named into the
     * block, with its box, and gives it a frame; a skeleton the caps leave no
     * room for is left out, so its bodies are not listed.
     *
     * @param index     the skeleton
     * @param firstRing the first ring to copy
     * @param lastRing  the last ring to copy
     * @param withBox   whether to copy its box
     */
    private void copyRun(int index, int firstRing, int lastRing, boolean withBox) {
        DrinkField.Skeleton skeleton = skeletons.get(index);
        boolean copyBox = withBox && skeleton.box() != null;
        if (!roomFor(lastRing - firstRing + 1, copyBox)) {
            return;
        }
        frameOf.put(index, frameOf.size());
        ringBaseOf.put(index, rings.size());
        firstRingOf.put(index, firstRing);
        for (int ring = firstRing; ring <= lastRing; ring++) {
            rings.add(skeleton.rings().get(ring));
            ringSkeletons.add(index);
        }
        if (copyBox) {
            boxOf.put(index, boxes.size());
            boxes.add(skeleton.box());
        }
    }

    /**
     * @param count   rings to copy
     * @param withBox whether a box comes with them
     * @return whether the caps leave room for another frame, the rings and the box
     */
    private boolean roomFor(int count, boolean withBox) {
        boolean roomForBox = !withBox || boxes.size() < MOST_BOXES;
        return frameOf.size() < MOST_FRAMES && rings.size() + count <= MOST_RINGS && roomForBox;
    }

    private void copy(int own) {
        DrinkField.Skeleton mine = skeletons.get(own);
        if (!mine.rings().isEmpty()) {
            copyRun(own, 0, mine.rings().size() - 1, true);
        } else if (mine.box() != null) {
            copyRun(own, 0, NONE, true);
        }
    }

    /**
     * Copies every other skeleton some proxy lists: the span of its rings its
     * listed segments need, and its box where listed.
     *
     * @param own the stream's own skeleton
     */
    private void copyNeighbours(int own) {
        Map<Integer, int[]> spans = new LinkedHashMap<>();
        Map<Integer, Boolean> boxed = new LinkedHashMap<>();
        for (Map<Integer, List<Integer>> listed : candidates) {
            listed.forEach((index, bodies) -> {
                if (index != own) {
                    widen(spans, boxed, index, bodies);
                }
            });
        }
        spans.forEach((index, span) -> {
            boolean withBox = boxed.getOrDefault(index, false);
            copyRun(index, span[0], span[1] == Integer.MIN_VALUE ? NONE : span[1] + 1, withBox);
        });
    }

    private void widen(Map<Integer, int[]> spans, Map<Integer, Boolean> boxed, int index, List<Integer> bodies) {
        int segments = skeletons.get(index).rings().size() - 1;
        int[] span = spans.computeIfAbsent(index, ignored -> new int[]{Integer.MAX_VALUE, Integer.MIN_VALUE});
        for (int body : bodies) {
            if (body < segments) {
                span[0] = Math.min(span[0], body);
                span[1] = Math.max(span[1], body);
            } else {
                boxed.put(index, true);
            }
        }
        if (span[0] == Integer.MAX_VALUE) {
            span[0] = 0;
        }
    }

    /**
     * Lists each proxy's bodies in the table, each copied skeleton's bodies
     * as one run, the stream's own first, up to the table's cap.
     */
    private void tabulate() {
        for (Map<Integer, List<Integer>> listed : candidates) {
            int first = table.size();
            listed.forEach(this::listRun);
            ranges.add(new int[]{first, table.size() - first});
        }
    }

    private void listRun(int index, List<Integer> bodies) {
        if (!frameOf.containsKey(index)) {
            return;
        }
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

    private ByteBuffer write(Coat coat) {
        ByteBuffer bytes = ByteBuffer.allocate(BYTES).order(ByteOrder.nativeOrder());
        writeCoat(bytes, coat);
        writeFrames(bytes);
        writeProxies(bytes);
        for (int entry = 0; entry < table.size(); entry++) {
            bytes.putInt(TABLE_AT + entry * Integer.BYTES, table.get(entry));
        }
        writeBodies(bytes);
        return bytes;
    }

    private void writeCoat(ByteBuffer bytes, Coat coat) {
        List<Layer> layers = coat.layers().subList(0, Math.min(MOST_LAYERS, coat.layers().size()));
        putVec4(bytes, COAT_AT, coat.blockLight(), coat.skyLight(), coat.zoop() ? 1 : 0, coat.depthZeroToOne() ? 1 : 0);
        putTint(bytes, TINT_AT, coat.tint());
        putSprite(bytes, SPRITE_AT, coat.sprite());
        putVec4(bytes, COUNTS_AT, rings.size(), boxes.size(), proxies.size(), layers.size());
        for (int index = 0; index < layers.size(); index++) {
            Layer layer = layers.get(index);
            putTint(bytes, LAYER_TINT_AT + index * VEC4, layer.tint());
            putSprite(bytes, LAYER_SPRITE_AT + index * VEC4, layer.sprite());
            putVec4(bytes, LAYER_SHARE_AT + index * VEC4, layer.before(), layer.cumulative(), layer.seed(), 0);
        }
    }

    private void writeFrames(ByteBuffer bytes) {
        frameOf.forEach((index, frame) -> {
            DrinkStream.Path path = skeletons.get(index).stream().path();
            putVec3(bytes, FRAMES_AT + frame * PAIR * VEC4, DrinkStream.sideOf(path), 0);
            putVec3(bytes, FRAMES_AT + (frame * PAIR + 1) * VEC4, DrinkStream.acrossOf(path), 0);
        });
    }

    private void writeProxies(ByteBuffer bytes) {
        for (int index = 0; index < proxies.size(); index++) {
            putVec3(bytes, PROXY_LOW_AT + index * VEC4, proxies.get(index).low(), ranges.get(index)[0]);
            putVec3(bytes, PROXY_HIGH_AT + index * VEC4, proxies.get(index).high(), ranges.get(index)[1]);
        }
    }

    private void writeBodies(ByteBuffer bytes) {
        for (int index = 0; index < boxes.size(); index++) {
            DrinkBody.Box box = boxes.get(index);
            putVec3(bytes, BOXES_AT + index * PAIR * VEC4, box.center().subtract(camera), box.half());
            putVec4(bytes, BOXES_AT + (index * PAIR + 1) * VEC4, box.rounding(), 0, 0, 0);
        }
        for (int index = 0; index < rings.size(); index++) {
            DrinkStream.Ring ring = rings.get(index);
            putVec3(bytes, RINGS_AT + index * PAIR * VEC4, ring.center().subtract(camera), ring.radius());
            putVec4(bytes, RINGS_AT + (index * PAIR + 1) * VEC4, ring.material(), ring.share(),
                    frameOf.get(ringSkeletons.get(index)), 0);
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
