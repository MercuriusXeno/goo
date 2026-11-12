package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import org.joml.Vector3fc;
import java.util.HashMap;
import java.util.Map;

/**
 * A model's skin size in texels, width and height apart, read off its baked
 * cubes, which keep their size in texels while their texture coordinates are
 * divided by the skin's size. The encasement shader lays one stone texel per
 * skin texel from it, so the stone keeps the mob's own texel size on a skin of
 * any shape, a spider's 64 by 32 as a zombie's 64 by 64.
 * Decision diagnose-and-correct-petrify-stone-stretching-off-native-texels.
 *
 * @param width  the skin's texels across
 * @param height the skin's texels down
 */
public record ModelSkinSize(int width, int height) {

    /** The common mob skin, answered for a model with no textured face to read. */
    static final ModelSkinSize SQUARE_SKIN = new ModelSkinSize(64, 64);

    /** The overlay's v rides the high short of the packed coordinates. */
    private static final int OVERLAY_V_SHIFT = 16;
    private static final int SHORT_MASK = 0xFFFF;
    /** A face's axis lies along its normal's largest component. */
    private static final float AXIS_LEAN = 0.5f;

    /**
     * Reads a model's skin size: each cube face's size in texels over the span of
     * texture coordinates it covers, the size most faces agree on along each axis.
     *
     * @param root the model's root part
     * @return the skin size, or {@link #SQUARE_SKIN} when no face has area
     */
    static ModelSkinSize of(ModelPart root) {
        Map<Integer, Integer> widthVotes = new HashMap<>();
        Map<Integer, Integer> heightVotes = new HashMap<>();
        root.visit(new PoseStack(), (pose, partPath, cubeIndex, cube) -> {
            for (ModelPart.Polygon face : cube.polygons) {
                voteFace(cube, face, widthVotes, heightVotes);
            }
        });
        return new ModelSkinSize(mostVoted(widthVotes, SQUARE_SKIN.width),
                mostVoted(heightVotes, SQUARE_SKIN.height));
    }

    /**
     * The overlay coordinates the encasement layer hands the shader this size in:
     * the width in u, the height in v, as petrify_stone.vsh reads UV1.
     *
     * @return the packed overlay coordinates
     */
    int overlayCoords() {
        return (width & SHORT_MASK) | (height & SHORT_MASK) << OVERLAY_V_SHIFT;
    }

    private static void voteFace(ModelPart.Cube cube, ModelPart.Polygon face, Map<Integer, Integer> widthVotes,
                                 Map<Integer, Integer> heightVotes) {
        float sizeX = cube.maxX - cube.minX;
        float sizeY = cube.maxY - cube.minY;
        float sizeZ = cube.maxZ - cube.minZ;
        Vector3fc normal = face.normal();
        float acrossTexels;
        float downTexels;
        if (Math.abs(normal.x()) > AXIS_LEAN) {
            acrossTexels = sizeZ;
            downTexels = sizeY;
        } else if (Math.abs(normal.y()) > AXIS_LEAN) {
            acrossTexels = sizeX;
            downTexels = sizeZ;
        } else {
            acrossTexels = sizeX;
            downTexels = sizeY;
        }
        vote(widthVotes, acrossTexels, coordinateSpan(face, false));
        vote(heightVotes, downTexels, coordinateSpan(face, true));
    }

    private static void vote(Map<Integer, Integer> votes, float texels, float coordinateSpan) {
        if (texels > 0f && coordinateSpan > 0f) {
            votes.merge(Math.round(texels / coordinateSpan), 1, Integer::sum);
        }
    }

    private static float coordinateSpan(ModelPart.Polygon face, boolean down) {
        float low = Float.MAX_VALUE;
        float high = -Float.MAX_VALUE;
        for (ModelPart.Vertex vertex : face.vertices()) {
            float coordinate = down ? vertex.v() : vertex.u();
            low = Math.min(low, coordinate);
            high = Math.max(high, coordinate);
        }
        return high - low;
    }

    private static int mostVoted(Map<Integer, Integer> votes, int fallback) {
        int winner = fallback;
        int winnerVotes = 0;
        for (Map.Entry<Integer, Integer> entry : votes.entrySet()) {
            if (entry.getValue() > winnerVotes) {
                winner = entry.getKey();
                winnerVotes = entry.getValue();
            }
        }
        return winner;
    }
}
