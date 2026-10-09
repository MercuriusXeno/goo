package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Sets properties on the host's own block, keeping the block and its block
 * entity, and finishes. Glow's Bulb lights the prism it lands on with
 * {@code set_state state={lit: true}}.
 * decision bulb-one-model-max-light-beacon-combo
 *
 * @param state each state property, by name, to the value that resolves it
 */
public record SetStateStep(Map<String, StateValue> state) implements Step {

    private static final String NAME = "set_state";
    private static final String FIELD_STATE = "state";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<SetStateStep> CODEC = Codec.unboundedMap(Codec.STRING, StateValue.CODEC)
            .fieldOf(FIELD_STATE).xmap(SetStateStep::new, SetStateStep::state);

    /**
     * The registered type.
     */
    public static final StepType<SetStateStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<SetStateStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        Map<String, String> resolved = new LinkedHashMap<>();
        state.forEach((property, value) -> resolved.put(property, value.resolve(context)));
        context.hostAs(StateWriteHost.class).writeOwnState(resolved);
        return true;
    }

    @Override
    public Stream<Expr> expressions() {
        return state.values().stream().flatMap(StateValue::expressions);
    }

    @Override
    public Set<HostCapability> requires() {
        Set<HostCapability> needed = EnumSet.of(HostCapability.STATE_WRITE);
        state.values().forEach(value -> needed.addAll(value.requires()));
        return needed;
    }
}
