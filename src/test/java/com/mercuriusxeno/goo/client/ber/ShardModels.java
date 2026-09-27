package com.mercuriusxeno.goo.client.ber;

import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.List;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Builds the models a shard test cuts: a generated item's front, back and edge strips, and a
 * unit cube's six faces, every texel opaque, each face's UV its own position over the face.
 */
final class ShardModels {

    /** The atlas every test quad's sprite sits on. */
    static final Identifier ITEM_ATLAS = Identifier.withDefaultNamespace("textures/atlas/items.png");
    /** The texels across a test model's side. */
    static final int TEXELS = 16;
    /** A generated item's front face depth. */
    static final float FRONT_Z = 8.5f / 16f;
    /** A generated item's back face depth. */
    static final float BACK_Z = 7.5f / 16f;
    /** A generated item's bounding box. */
    static final AABB FLAT_BOX = new AABB(0, 0, BACK_Z, 1, 1, FRONT_Z);
    /** A unit cube's bounding box. */
    static final AABB CUBE_BOX = new AABB(0, 0, 0, 1, 1, 1);

    private ShardModels() {
    }

    /**
     * Builds one quad whose corners run a, a + along B, a + along A and B, a + along A, its UV
     * the corner's fraction along A and B.
     */
    static BakedQuad quad(Vector3f origin, Vector3f alongA, Vector3f alongB, Direction direction) {
        TextureAtlasSprite sprite = mock(TextureAtlasSprite.class);
        when(sprite.atlasLocation()).thenReturn(ITEM_ATLAS);
        BakedQuad.MaterialInfo material = new BakedQuad.MaterialInfo(
                sprite, ChunkSectionLayer.CUTOUT, null, -1, true, 0, false);
        return new BakedQuad(new Vector3f(origin), new Vector3f(origin).add(alongB),
                new Vector3f(origin).add(alongA).add(alongB), new Vector3f(origin).add(alongA),
                UVPair.pack(0f, 1f), UVPair.pack(0f, 0f), UVPair.pack(1f, 0f), UVPair.pack(1f, 1f),
                direction, material);
    }

    /** A generated item's quads, one set so a model cut from them finds each quad by identity. */
    private static final List<BakedQuad> FLAT_ITEM = buildFlatItem();
    /** A unit cube's quads, one set so a model cut from them finds each quad by identity. */
    private static final List<BakedQuad> CUBE = buildCube();

    /**
     * @return a generated item's quads: its front and back faces and one strip per edge pixel
     */
    static List<BakedQuad> flatItem() {
        return FLAT_ITEM;
    }

    /**
     * @return a unit cube's six faces
     */
    static List<BakedQuad> cube() {
        return CUBE;
    }

    private static List<BakedQuad> buildFlatItem() {
        List<BakedQuad> quads = new ArrayList<>();
        quads.add(quad(new Vector3f(0, 0, FRONT_Z), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), Direction.SOUTH));
        quads.add(quad(new Vector3f(0, 0, BACK_Z), new Vector3f(1, 0, 0), new Vector3f(0, 1, 0), Direction.NORTH));
        float pixel = 1f / TEXELS;
        Vector3f depth = new Vector3f(0, 0, FRONT_Z - BACK_Z);
        for (int i = 0; i < TEXELS; i++) {
            quads.add(quad(new Vector3f(0, i * pixel, BACK_Z), depth, new Vector3f(0, pixel, 0), Direction.WEST));
            quads.add(quad(new Vector3f(1, i * pixel, BACK_Z), depth, new Vector3f(0, pixel, 0), Direction.EAST));
            quads.add(quad(new Vector3f(i * pixel, 0, BACK_Z), new Vector3f(pixel, 0, 0), depth, Direction.DOWN));
            quads.add(quad(new Vector3f(i * pixel, 1, BACK_Z), new Vector3f(pixel, 0, 0), depth, Direction.UP));
        }
        return quads;
    }

    private static List<BakedQuad> buildCube() {
        Vector3f x = new Vector3f(1, 0, 0);
        Vector3f y = new Vector3f(0, 1, 0);
        Vector3f z = new Vector3f(0, 0, 1);
        return List.of(
                quad(new Vector3f(0, 0, 1), x, y, Direction.SOUTH),
                quad(new Vector3f(0, 0, 0), x, y, Direction.NORTH),
                quad(new Vector3f(1, 0, 0), z, y, Direction.EAST),
                quad(new Vector3f(0, 0, 0), z, y, Direction.WEST),
                quad(new Vector3f(0, 1, 0), x, z, Direction.UP),
                quad(new Vector3f(0, 0, 0), x, z, Direction.DOWN));
    }

    /**
     * Cuts quads as a layer with no transform of its own submits them, every cell opaque.
     *
     * @param quads the model's quads
     * @param box   the model's bounding box
     * @param seed  the item's seed
     * @return the sharded model
     */
    static ShardedModel cut(List<BakedQuad> quads, AABB box, long seed) {
        List<ShardedModel.CapturedQuad> captured = new ArrayList<>();
        for (BakedQuad quad : quads) {
            captured.add(new ShardedModel.CapturedQuad(quad, QuadRectClipper.verticesOf(quad),
                    quad.direction().getUnitVec3f(), ShardedModel.CellOpacity.ALL));
        }
        return ShardedModel.cut(captured, box, TEXELS, seed);
    }

    /**
     * @param itemId an item id
     * @return a generated item's model cut by that item's seed
     */
    static ShardedModel flatFor(String itemId) {
        return cut(flatItem(), FLAT_BOX, ItemShardCutter.seedOf(itemId));
    }

    /**
     * @param itemId an item id
     * @return a unit cube cut by that item's seed
     */
    static ShardedModel cubeFor(String itemId) {
        return cut(cube(), CUBE_BOX, ItemShardCutter.seedOf(itemId));
    }
}
