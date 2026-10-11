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
 * the blob stands. A blob already standing where its entity forms, as a
 * conjured spawn's splat does, makes no hop either: it morphs in place for
 * the whole transformation (decision spawn-goo-morphs-into-the-mob-it-births).
 * A blob turning into a block makes no hop: the block's
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

    /** How close the blob's origin stands to its entity, in blocks, for the blob to morph in place. */
    static final double IN_PLACE_REACH = 0.05;

    /** A model at full size. */
    private static final float FULL = 1f;

    /** The smoothstep cubic's terms: 3t^2 - 2t^3. */
    private static final float SMOOTHSTEP_SQUARE = 3f;
    private static final float SMOOTHSTEP_CUBE = 2f;

    /**
     * Game ticks a finished shrink keeps its last size, so a model shrunk to
     * nothing stays hidden until the server's removal of its entity arrives.
     */
    static final int SHRINK_HOLD_TICKS = 10;

    private final List<Transformation> live = new ArrayList<>();
    private final List<Shrink> shrinks = new ArrayList<>();

    /**
     * One model shrink playing: Rewind's adult shrinking into its baby, or a
     * mob shrinking into its egg.
     * rewind-shrinks-adult-to-baby-to-egg
     *
     * @param entityId  the shrinking entity's id
     * @param fromScale the model's size as it begins, as a multiple of its drawn size
     * @param toScale   the model's size as it ends
     * @param babyModel true when it draws on the baby model, so it holds off until the entity reads as a baby
     * @param startTick the game time it began
     * @param ticks     the game ticks it takes
     */
    public record Shrink(int entityId, float fromScale, float toScale, boolean babyModel, long startTick, int ticks) {

        /**
         * The model's size at a game time, eased by the smoothstep; on the
         * baby model, full until the entity reads as a baby.
         *
         * @param gameTime the game time including the partial tick
         * @param baby     whether the entity reads as a baby
         * @return the size, as a multiple of the drawn size
         */
        public float scale(float gameTime, boolean baby) {
            if (babyModel && !baby) {
                return FULL;
            }
            float share = smoothstep(Math.clamp((gameTime - startTick) / ticks, 0f, 1f));
            return fromScale + (toScale - fromScale) * share;
        }

        boolean isOver(long now) {
            return now - startTick >= ticks + SHRINK_HOLD_TICKS;
        }
    }

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
         * @return true when the blob hops to its entity before it morphs: an
         *         entity target standing off from where the blob leaves
         */
        boolean hops() {
            return targetBlock == null && from.distanceToSqr(to) > IN_PLACE_REACH * IN_PLACE_REACH;
        }

        /**
         * How far the blob has become the model: nothing through an entity's hop,
         * then a smoothstep to whole at the end; a block's morph takes the whole time.
         *
         * @param gameTime the game time including the partial tick
         * @return 0 to 1
         */
        public float morph(float gameTime) {
            float hop = hops() ? HOP_SHARE : 0f;
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
            if (!hops()) {
                return from;
            }
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
     * Starts a model shrink.
     *
     * @param entityId  the shrinking entity's id
     * @param fromScale the model's size as it begins
     * @param toScale   the model's size as it ends
     * @param babyModel true when it draws on the baby model
     * @param now       the game time it begins
     * @param ticks     the game ticks it takes
     */
    public void shrink(int entityId, float fromScale, float toScale, boolean babyModel, long now, int ticks) {
        shrinks.removeIf(shrink -> shrink.isOver(now));
        shrinks.add(new Shrink(entityId, fromScale, toScale, babyModel, now, Math.max(1, ticks)));
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
     * into it gives, times the latest shrink on it, or full where none plays.
     *
     * @param entityId the entity's id
     * @param baby     whether the entity reads as a baby
     * @param gameTime the game time including the partial tick
     * @return the size, as a multiple of the drawn size
     */
    public float modelScaleOf(int entityId, boolean baby, float gameTime) {
        float scale = shrinkScaleOf(entityId, baby, gameTime);
        for (Transformation transformation : live) {
            if (transformation.targetBlock() == null && transformation.targetEntityId() == entityId
                    && !transformation.isOver((long) Math.floor(gameTime))) {
                scale = Math.min(scale, transformation.modelScale(gameTime));
            }
        }
        return scale;
    }

    /**
     * The size the latest shrink on an entity gives its model, or full where
     * none plays.
     * rewind-shrinks-adult-to-baby-to-egg
     *
     * @param entityId the entity's id
     * @param baby     whether the entity reads as a baby
     * @param gameTime the game time including the partial tick
     * @return the size, as a multiple of the drawn size
     */
    private float shrinkScaleOf(int entityId, boolean baby, float gameTime) {
        long now = (long) Math.floor(gameTime);
        for (int index = shrinks.size() - 1; index >= 0; index--) {
            Shrink shrink = shrinks.get(index);
            if (shrink.entityId() == entityId && !shrink.isOver(now)) {
                return shrink.scale(gameTime, baby);
            }
        }
        return FULL;
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
        shrinks.clear();
    }
}
