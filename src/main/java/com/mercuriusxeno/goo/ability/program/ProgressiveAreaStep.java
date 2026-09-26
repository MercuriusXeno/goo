package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.BlockEffectType;
import com.mercuriusxeno.goo.ability.LayerAudioType;
import com.mercuriusxeno.goo.ability.LayerVisualsType;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * The progressive-area sub-chain as one step: walks the layers of a
 * footprint, previewing layer {@code i} on tick {@code i} and striking it
 * on tick {@code i + preview_delay} with a per-cell block effect, then the
 * layer's visuals and audio scaled by the cells the effect changed, and
 * finishes once the last layer is struck. The effect, the visuals and the
 * audio are the data-selected delegates of {@link BlockEffectType},
 * {@link LayerVisualsType} and {@link LayerAudioType}; a name none of
 * them holds refuses at load. Rock, blaze and frost each run this step
 * with their own delegates (decision vocabulary-from-all-designs).
 *
 * @param shape        the footprint shape
 * @param effect       the block effect's registered name
 * @param visuals      the layer visuals' registered name
 * @param audio        the layer audio's registered name
 * @param previewDelay ticks between a layer's preview and its strike
 */
public record ProgressiveAreaStep(AreaShape shape, String effect, String visuals, String audio,
                                  Expr previewDelay) implements Step {

    private static final String NAME = "progressive_area";
    private static final String FIELD_SHAPE = "shape";
    private static final String FIELD_EFFECT = "effect";
    private static final String FIELD_VISUALS = "visuals";
    private static final String FIELD_AUDIO = "audio";
    private static final String FIELD_PREVIEW_DELAY = "preview_delay";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<ProgressiveAreaStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            AreaShape.CODEC.fieldOf(FIELD_SHAPE).forGetter(ProgressiveAreaStep::shape),
            registered(BlockEffectType::byName).fieldOf(FIELD_EFFECT).forGetter(ProgressiveAreaStep::effect),
            registered(LayerVisualsType::byName).fieldOf(FIELD_VISUALS).forGetter(ProgressiveAreaStep::visuals),
            registered(LayerAudioType::byName).fieldOf(FIELD_AUDIO).forGetter(ProgressiveAreaStep::audio),
            Expr.CODEC.fieldOf(FIELD_PREVIEW_DELAY).forGetter(ProgressiveAreaStep::previewDelay)
    ).apply(inst, ProgressiveAreaStep::new));

    /**
     * The registered type.
     */
    public static final StepType<ProgressiveAreaStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * Builds a name codec that refuses a name a delegate registry does
     * not hold, with the registry's own message.
     *
     * @param lookup the registry lookup, throwing on an unknown name
     * @return the codec
     */
    private static Codec<String> registered(Consumer<String> lookup) {
        return Codec.STRING.validate(name -> {
            try {
                lookup.accept(name);
                return DataResult.success(name);
            } catch (IllegalArgumentException refusal) {
                return DataResult.error(refusal::getMessage);
            }
        });
    }

    /**
     * Counts the blocks this step's footprint covers at a stack count.
     *
     * @param stacks the marker's stack count
     * @return the block count
     */
    public int footprintBlocks(int stacks) {
        return AreaLayers.blockCount(shape, stacks);
    }

    @Override
    public StepType<ProgressiveAreaStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        LayerWalkHost host = context.hostAs(LayerWalkHost.class);
        int layers = AreaLayers.layerCount(shape, context.hostAs(StacksHost.class).stackCount());
        int delay = previewDelay.evaluateInt(context);
        int tick = context.stepTicks();
        if (tick < layers) {
            host.previewLayer(visuals, tick);
        }
        int struck = tick - delay;
        if (struck >= 0 && struck < layers) {
            strike(context, struck);
            host.reportMinedLayers(struck + 1);
        }
        return tick + 1 >= layers + delay;
    }

    /**
     * Applies the effect to every cell of one layer, then plays the
     * layer's fx scaled by the cells the effect changed.
     *
     * @param context the tick context, whose host walks the layers
     * @param layer   the layer index
     */
    private void strike(StepContext context, int layer) {
        LayerWalkHost host = context.hostAs(LayerWalkHost.class);
        int destroyed = 0;
        for (BlockPos cell : AreaLayers.layerCells(shape, context.hostAs(StacksHost.class).stackCount(),
                host.position(), context.hostAs(PlacedFaceHost.class).placedFace(), layer)) {
            if (host.applyBlockEffect(effect, cell)) {
                destroyed++;
            }
        }
        host.strikeLayerFx(visuals, audio, layer, destroyed);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.of(previewDelay);
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.LAYER_WALK, HostCapability.STACKS, HostCapability.PLACED_FACE,
                HostCapability.TICKING);
    }
}
