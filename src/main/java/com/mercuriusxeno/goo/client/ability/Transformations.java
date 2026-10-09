package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The model transformations playing on this client: a goo blob hops from
 * its origin to an entity or a block over the first HOP_SHARE of the
 * transformation, then shrinks to nothing as the target's model grows from
 * nothing to full size on a smoothstep, so the target is never whole while
 * the blob stands. A blob turning into a block makes no hop: the block's
 * renderer morphs it out of the struck face from the tick it lands
 * (decision prism-is-one-pointed-quartz-column).
 * Decision model-transformation-is-one-animation.
 * Decision prism-blob-becomes-a-milky-quartz-crystal.
 */
public final class Transformations {

    /** The list the client's transformation handler, renderer and render-state modifier share. */
    public static final Transformations CLIENT = new Transformations();

    /** The share of the transformation the blob spends hopping, whole, before the morph. */
    static final float HOP_SHARE = 0.4f;

    /** Blocks the blob's hop arcs above the straight line at its peak. */
    static final float HOP_HEIGHT = 0.6f;

    /** A model at full size. */
    private static final float FULL = 1f;

    /** The smoothstep cubic's terms: 3t^2 - 2t^3. */
    private static final float SMOOTHSTEP_SQUARE = 3f;
    private static final float SMOOTHSTEP_CUBE = 2f;

    private final List<Transformation> live = new ArrayList<>();

    /**
     * The smoothstep ease: still at both ends, fastest at the middle.
     *
     * @param share how far along, 0 to 1
     * @return the eased share, 0 to 1
     */
    static float smoothstep(float share) {
        return share * share * (SMOOTHSTEP_SQUARE - SMOOTHSTEP_CUBE * share);
    }

    /**
     * One transformation playing.
     *
     * @param gooType        the goo type of the blob
     * @param from           the world point the blob leaves from
     * @param to             the world point the target stands at
     * @param targetEntityId the entity the blob becomes, negative for a block target
     * @param targetBlock    the block the blob becomes, null for an entity target
     * @param startTick      the game time it began
     * @param ticks          the game ticks it takes
     */
    public record Transformation(ResourceKey<GooTypeDefinition> gooType, Vec3 from, Vec3 to, int targetEntityId,
            @Nullable BlockPos targetBlock, long startTick, int ticks) {

        /**
         * A transformation into an entity.
         *
         * @param gooType        the goo type of the blob
         * @param from           the world point the blob leaves from
         * @param to             the world point the entity stands at
         * @param targetEntityId the entity the blob becomes
         * @param startTick      the game time it began
         * @param ticks          the game ticks it takes
         */
        public Transformation(ResourceKey<GooTypeDefinition> gooType, Vec3 from, Vec3 to, int targetEntityId,
                long startTick, int ticks) {
            this(gooType, from, to, targetEntityId, null, startTick, ticks);
        }

        /**
         * @param gameTime the game time including the partial tick
         * @return how far the transformation has run, 0 to 1
         */
        float progress(float gameTime) {
            return Math.clamp((gameTime - startTick) / ticks, 0f, 1f);
        }

        /**
         * How far the blob has become the model: nothing through an entity's hop,
         * then a smoothstep to whole at the end; a block's morph takes the whole time.
         *
         * @param gameTime the game time including the partial tick
         * @return 0 to 1
         */
        public float morph(float gameTime) {
            float hop = targetBlock == null ? HOP_SHARE : 0f;
            return smoothstep(Math.clamp((progress(gameTime) - hop) / (FULL - hop), 0f, 1f));
        }

        /**
         * @param gameTime the game time including the partial tick
         * @return the blob's size, whole through the hop, then shrinking to nothing as an entity's
         *         model grows; none for a block, whose renderer morphs the blob itself
         */
        public float blobScale(float gameTime) {
            if (targetBlock != null) {
                return 0f;
            }
            return FULL - morph(gameTime);
        }

        /**
         * @param gameTime the game time including the partial tick
         * @return the model's size, nothing until the hop lands and full at the end
         */
        public float modelScale(float gameTime) {
            return morph(gameTime);
        }

        /**
         * Where the blob stands: along an arc from its origin to the entity
         * over the hop, then at the entity while it morphs.
         *
         * @param gameTime the game time including the partial tick
         * @return the blob's world point
         */
        public Vec3 blobPosition(float gameTime) {
            float hop = Math.min(FULL, progress(gameTime) / HOP_SHARE);
            float along = smoothstep(hop);
            return from.lerp(to, along).add(0, HOP_HEIGHT * Math.sin(Math.PI * hop), 0);
        }

        /**
         * @param now the game time
         * @return true once the model stands whole
         */
        boolean isOver(long now) {
            return now - startTick >= ticks;
        }
    }

    /**
     * Starts a transformation.
     *
     * @param gooType        the goo type of the blob
     * @param from           the world point the blob leaves from
     * @param to             the world point the entity stands at
     * @param targetEntityId the entity the blob becomes
     * @param now            the game time it begins
     * @param ticks          the game ticks it takes
     */
    public void add(ResourceKey<GooTypeDefinition> gooType, Vec3 from, Vec3 to, int targetEntityId, long now,
            int ticks) {
        add(gooType, from, to, targetEntityId, null, now, ticks);
    }

    /**
     * Starts a transformation into an entity or a block.
     *
     * @param gooType        the goo type of the blob
     * @param from           the world point the blob leaves from
     * @param to             the world point the target stands at
     * @param targetEntityId the entity the blob becomes, negative for a block target
     * @param targetBlock    the block the blob becomes, null for an entity target
     * @param now            the game time it begins
     * @param ticks          the game ticks it takes
     */
    public void add(ResourceKey<GooTypeDefinition> gooType, Vec3 from, Vec3 to, int targetEntityId,
            @Nullable BlockPos targetBlock, long now, int ticks) {
        live.add(new Transformation(gooType, from, to, targetEntityId, targetBlock, now, Math.max(1, ticks)));
    }

    /**
     * Drops every transformation that has finished and answers the rest.
     *
     * @param now the game time
     * @return the transformations still playing, in the order they began
     */
    public List<Transformation> live(long now) {
        live.removeIf(transformation -> transformation.isOver(now));
        return List.copyOf(live);
    }

    /**
     * The size an entity's model draws at: the smallest a transformation
     * into it gives, or full where none plays.
     *
     * @param entityId the entity's id
     * @param gameTime the game time including the partial tick
     * @return 0 to 1
     */
    public float modelScaleOf(int entityId, float gameTime) {
        float scale = FULL;
        for (Transformation transformation : live) {
            if (transformation.targetBlock() == null && transformation.targetEntityId() == entityId
                    && !transformation.isOver((long) Math.floor(gameTime))) {
                scale = Math.min(scale, transformation.modelScale(gameTime));
            }
        }
        return scale;
    }

    /**
     * The size a block's model draws at: the smallest a transformation into
     * it gives, or full where none plays.
     *
     * @param pos      the block's position
     * @param gameTime the game time including the partial tick
     * @return 0 to 1
     */
    public float modelScaleAt(BlockPos pos, float gameTime) {
        float scale = FULL;
        for (Transformation transformation : live) {
            if (pos.equals(transformation.targetBlock()) && !transformation.isOver((long) Math.floor(gameTime))) {
                scale = Math.min(scale, transformation.modelScale(gameTime));
            }
        }
        return scale;
    }

    /**
     * The transformation playing into a block, for its renderer to morph the blob.
     *
     * @param pos      the block's position
     * @param gameTime the game time including the partial tick
     * @return the transformation, or null where none plays
     */
    public @Nullable Transformation intoBlockAt(BlockPos pos, float gameTime) {
        for (Transformation transformation : live) {
            if (pos.equals(transformation.targetBlock()) && !transformation.isOver((long) Math.floor(gameTime))) {
                return transformation;
            }
        }
        return null;
    }

    /** Drops every transformation, as the client leaves a level. */
    public void clear() {
        live.clear();
    }
}
