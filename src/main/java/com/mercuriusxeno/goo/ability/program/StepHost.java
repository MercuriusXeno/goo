package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.BlockEffect;
import com.mercuriusxeno.goo.ability.LayerAudio;
import com.mercuriusxeno.goo.ability.LayerVisuals;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The seam a step program reaches its world through (decision
 * host-agnostic-runtime). The runtime holds a host, never a level or a
 * block entity, so the same program runs on the marker block, the struck
 * entity or the potion holder. Reads answer through {@link Variables} by
 * name and through the typed accessors; world actions are host methods,
 * so a test drives a program against a mock with no level behind it.
 *
 * <p>Each method belongs to a {@link HostCapability}. A host implements
 * the methods of the capabilities its {@link HostKind} provides and
 * refuses the rest; the target, thrower and layer-walk methods refuse by default,
 * so a host lacking those capabilities leaves them unimplemented.
 * {@link ProgramBehavior#forHost} keeps a program from
 * ever reaching a refused method by checking every step's needs against
 * the kind at load.
 */
public interface StepHost extends Variables {

    /**
     * Returns which kind of host this is, which names its capabilities
     * and variables.
     *
     * @return the host kind
     */
    HostKind kind();

    /**
     * Returns the block the program acts from: the marker block, or the
     * block the struck entity stands in.
     *
     * @return the anchor position
     */
    BlockPos position();

    /**
     * Returns the face the marker was placed on; the blast direction is
     * its opposite. Capability {@link HostCapability#PLACED_FACE}.
     *
     * @return the placed face
     */
    Direction placedFace();

    /**
     * Returns the blobs stacked on the host, live. Capability
     * {@link HostCapability#STACKS}.
     *
     * @return the stack count
     */
    int stackCount();

    /**
     * Spends one stacked blob. Capability {@link HostCapability#STACKS}.
     */
    void decrementStack();

    /**
     * Detonates at the anchor's center. Capability
     * {@link HostCapability#EXPLODE}.
     *
     * @param power the explosion power
     * @param mode  how blocks are treated
     */
    void explode(float power, ExplosionMode mode);

    /**
     * Scans the volume around the anchor for an entity every filter keeps.
     * Capability {@link HostCapability#ENTITY_SCAN}.
     *
     * @param shape   the volume shape
     * @param radius  the volume radius in blocks
     * @param filters the filters an entity must pass
     * @return true when at least one entity is in the volume
     */
    boolean anyEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters);

    /**
     * Scans the volume around the anchor and hands the body a host bound
     * to each living entity every filter keeps, so the body's steps act on
     * that entity as their target. Capability
     * {@link HostCapability#ENTITY_SCAN}; the host each body receives
     * provides {@link HostCapability#TARGET}.
     *
     * @param shape   the volume shape
     * @param radius  the volume radius in blocks
     * @param filters the filters an entity must pass
     * @param body    what to run on the host bound to each entity
     */
    void forEachEntityWithin(SelectionShape shape, double radius, Set<EntityFilter> filters,
                             Consumer<StepHost> body);

    /**
     * Hands the body a host bound to the living entity with this id, when
     * one still stands in the host's level; a strike chosen ticks ago lands
     * on its entity this way. Capability {@link HostCapability#ENTITY_SCAN};
     * the host the body receives provides {@link HostCapability#TARGET}.
     *
     * @param entityId the entity's id in the level
     * @param body     what to run on the host bound to the entity
     */
    void forEntity(int entityId, Consumer<StepHost> body);

    /**
     * Returns the living entity the host's program acts on; each effect
     * step's tick acts on it directly (decision step-tick-holds-effect).
     * Capability {@link HostCapability#TARGET}.
     *
     * @return the target entity
     */
    default LivingEntity target() {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    /**
     * Returns the entity that set the program on the target, which a
     * teleport toward or away from it reads. Capability
     * {@link HostCapability#TARGET}.
     *
     * @return the thrower, or null when unknown
     */
    default @Nullable Entity thrower() {
        throw HostCapability.TARGET.refusedBy(kind());
    }

    /**
     * Returns the field-effect state the host keeps for a running field
     * effect. Capability {@link HostCapability#FIELD_EFFECT}.
     *
     * @return the live state, mutated in place by the step
     */
    FieldEffectState fieldEffect();

    /**
     * Rolls a fraction the field effect weighs against its spend chance,
     * drawn from the host's world so a test can fix it (decision
     * metal-spends-charge-by-chance). Capability
     * {@link HostCapability#FIELD_EFFECT}.
     *
     * @return a fraction in [0, 1)
     */
    default double rollFraction() {
        throw HostCapability.FIELD_EFFECT.refusedBy(kind());
    }

    /**
     * Returns the phase cursor the host keeps for a running phased step.
     * Capability {@link HostCapability#PHASED}.
     *
     * @return the live state, mutated in place by the step
     */
    PhasedState phased();

    /**
     * Pulls every living entity within a sphere around the anchor toward
     * the anchor's center. Capability {@link HostCapability#ENTITY_SCAN}.
     *
     * @param radius the sphere radius in blocks
     * @param speed  the velocity added toward the center, in blocks per tick
     */
    void pullEntitiesWithin(double radius, double speed);

    /**
     * Removes every valued block within a sphere around the anchor, adding
     * its goo to the total the host keeps. Capability
     * {@link HostCapability#CONSUMED_GOO}.
     *
     * @param radius the sphere radius in whole blocks
     */
    void consumeValuedBlocks(int radius);

    /**
     * Drops the consumed goo total as blob items at the anchor and empties
     * it. Capability {@link HostCapability#CONSUMED_GOO}.
     */
    void dropConsumedGoo();

    /**
     * Spawns a burst of particles at the anchor. The {@link FxAnchor#TARGET}
     * anchor needs capability {@link HostCapability#TARGET}.
     *
     * @param at    the anchor the burst centers on
     * @param burst the evaluated burst
     */
    void spawnParticles(FxAnchor at, ParticleBurst burst);

    /**
     * Plays a sound at the anchor. The {@link FxAnchor#TARGET} anchor
     * needs capability {@link HostCapability#TARGET}.
     *
     * @param at  the anchor the sound plays at
     * @param cue the evaluated sound
     */
    void playSound(FxAnchor at, SoundCue cue);

    /**
     * Writes a block at the anchor, replacing what stands there.
     * Capability {@link HostCapability#PLACE_BLOCK}.
     *
     * @param block the block's registry id
     * @param state each state property to set, by its name, to the value's name
     */
    void placeBlock(Identifier block, Map<String, String> state);

    /**
     * Applies a block effect to one cell of a layer. Capability
     * {@link HostCapability#LAYER_WALK}.
     *
     * @param effect the per-cell effect
     * @param cell   the block position
     * @return true when the effect changed the block
     */
    default boolean applyBlockEffect(BlockEffect effect, BlockPos cell) {
        throw HostCapability.LAYER_WALK.refusedBy(kind());
    }

    /**
     * Plays the preview of a layer about to be struck. Capability
     * {@link HostCapability#LAYER_WALK}.
     *
     * @param visuals the layer's particle profile
     * @param layer   the layer index, from zero
     */
    default void previewLayer(LayerVisuals visuals, int layer) {
        throw HostCapability.LAYER_WALK.refusedBy(kind());
    }

    /**
     * Plays the fx of a layer just struck. Capability
     * {@link HostCapability#LAYER_WALK}.
     *
     * @param visuals   the layer's particle profile
     * @param audio     the layer's sound profile
     * @param layer     the layer index, from zero
     * @param destroyed how many cells the effect changed
     */
    default void strikeLayerFx(LayerVisuals visuals, LayerAudio audio, int layer, int destroyed) {
        throw HostCapability.LAYER_WALK.refusedBy(kind());
    }

    /**
     * Reports how many layers the walk has struck, which the host's
     * renderer reads to shrink its outline. Capability
     * {@link HostCapability#LAYER_WALK}.
     *
     * @param layers the struck layer count
     */
    default void reportMinedLayers(int layers) {
        throw HostCapability.LAYER_WALK.refusedBy(kind());
    }
}
