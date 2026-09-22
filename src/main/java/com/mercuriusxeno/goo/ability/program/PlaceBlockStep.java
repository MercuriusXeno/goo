package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Writes a block at the host position with a state read from the program,
 * and finishes. The glow crystal is one {@code place_block} step naming
 * {@code goo:glow_crystal} with its facing from the placed face, its shape
 * from the flat predicate and its size from the stack count.
 *
 * @param block the block's registry id, resolved by the host when the step runs
 * @param state each state property, by name, to the value that resolves it
 */
public record PlaceBlockStep(Identifier block, Map<String, StateValue> state) implements Step {

    private static final String NAME = "place_block";
    private static final String FIELD_BLOCK = "block";
    private static final String FIELD_STATE = "state";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<PlaceBlockStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Identifier.CODEC.fieldOf(FIELD_BLOCK).forGetter(PlaceBlockStep::block),
            Codec.unboundedMap(Codec.STRING, StateValue.CODEC).optionalFieldOf(FIELD_STATE, Map.of())
                    .forGetter(PlaceBlockStep::state)
    ).apply(inst, PlaceBlockStep::new));

    /**
     * The registered type.
     */
    public static final StepType<PlaceBlockStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<PlaceBlockStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        Map<String, String> resolved = new LinkedHashMap<>();
        state.forEach((property, value) -> resolved.put(property, value.resolve(context)));
        context.host().placeBlock(block, resolved);
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return state.values().stream().flatMap(StateValue::expressions);
    }

    @Override
    public Set<HostCapability> requires() {
        Set<HostCapability> needed = EnumSet.of(HostCapability.PLACE_BLOCK);
        state.values().forEach(value -> needed.addAll(value.requires()));
        return needed;
    }
}
