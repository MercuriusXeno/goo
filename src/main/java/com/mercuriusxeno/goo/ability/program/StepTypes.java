package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry of {@link StepType}s and the dispatch codec that reads a step
 * tree: {@link #CODEC} reads {@code "type"}, finds the type by name and
 * hands the rest of the object to that type's codec. A name no type
 * carries refuses at load.
 */
public final class StepTypes {

    private static final Map<String, StepType<?>> TYPES = new LinkedHashMap<>();
    private static final String ERR_UNKNOWN = "Unknown step type: ";
    private static final String TYPE_FIELD = "type";

    /**
     * Codec for one step, dispatching on the {@code "type"} field.
     */
    public static final Codec<Step> CODEC = Codec.STRING
            .comapFlatMap(StepTypes::byName, StepType::name)
            .dispatch(TYPE_FIELD, Step::type, StepType::codec);

    /**
     * Codec for a step list, the body of a program or a container step.
     */
    public static final Codec<List<Step>> LIST_CODEC = CODEC.listOf();

    static {
        register(WaitStep.TYPE);
        register(AwaitEntityStep.TYPE);
        register(ExplodeStep.TYPE);
        register(DamageStep.TYPE);
        register(PotionStep.TYPE);
        register(TargetStep.TYPE);
        register(SetHealthStep.TYPE);
        register(FreezeTicksStep.TYPE);
        register(SetAiStep.TYPE);
        register(SetInvulnerableStep.TYPE);
        register(CloneEntityStep.TYPE);
        register(DropItemStep.TYPE);
        register(IgniteStep.TYPE);
        register(EntitiesStep.TYPE);
        register(ParticlesStep.TYPE);
        register(SoundStep.TYPE);
        register(TeleportStep.TYPE);
        register(PlaceBlockStep.TYPE);
        register(ProgressiveAreaStep.TYPE);
        register(FieldEffectStep.TYPE);
        register(PhasedStep.TYPE);
        register(PullStep.TYPE);
        register(ConsumeBlocksStep.TYPE);
        register(DropConsumedStep.TYPE);
    }

    private StepTypes() {
    }

    /**
     * Registers a step type under its name.
     *
     * @param type the type to register
     */
    public static void register(StepType<?> type) {
        TYPES.put(type.name(), type);
    }

    /**
     * Returns every registered type, in registration order.
     *
     * @return the types
     */
    public static Collection<StepType<?>> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    /**
     * Finds a type by the name the JSON carries.
     *
     * @param name the type name
     * @return the type, or an error naming the unknown type
     */
    private static DataResult<StepType<?>> byName(String name) {
        StepType<?> type = TYPES.get(name);
        if (type == null) {
            return DataResult.error(() -> ERR_UNKNOWN + name);
        }
        return DataResult.success(type);
    }
}
