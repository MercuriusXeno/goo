package com.mercuriusxeno.goo.ability.program;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * One value of a {@link PlaceBlockStep} state map, resolved to a property
 * value name when the step runs. Written as a JSON string it is the value's
 * name ({@code "bump"}), or the keyword {@code face} for the host's placed
 * face; written as an object it is a pick, an expression whose integer
 * result indexes a list of value names, clamped to the list. The pick is
 * how a state reads the host: {@code {"by": "flat", "values": ["bump",
 * "flat"]}} sets the shape by the flat predicate (decision
 * ability-params-in-datapack).
 */
public sealed interface StateValue permits StateValue.Named, StateValue.PlacedFace, StateValue.Pick {

    /**
     * The string a state map writes for the placed face.
     */
    String FACE_KEYWORD = "face";

    /**
     * Codec for the pick object; an empty list refuses at load. It stands
     * on the interface so the interface's own initialization reads no
     * nested record.
     */
    Codec<Pick> PICK_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Expr.CODEC.fieldOf(Pick.FIELD_BY).forGetter(Pick::by),
            Codec.STRING.listOf(1, Integer.MAX_VALUE).fieldOf(Pick.FIELD_VALUES).forGetter(Pick::values)
    ).apply(inst, Pick::new));

    /**
     * Codec reading a string as a name or the face keyword, and an object
     * as a pick.
     */
    Codec<StateValue> CODEC = Codec.either(Codec.STRING, PICK_CODEC).xmap(StateValue::fromEither, StateValue::toEither);

    /**
     * Resolves the value name to set on the property.
     *
     * @param context the running step's scope
     * @return the value name, as the property's codec spells it
     */
    String resolve(StepContext context);

    /**
     * Names the expressions this value evaluates, for load-time checks.
     *
     * @return the expressions, none by default
     */
    default Stream<Expr> expressions() {
        return Stream.empty();
    }

    /**
     * Names what this value asks of the host beyond writing the block.
     *
     * @return the capabilities, none by default
     */
    default Set<HostCapability> requires() {
        return Set.of();
    }

    /**
     * Decodes the codec's either: the face keyword is the placed face,
     * any other string a name, an object a pick.
     *
     * @param either the raw value
     * @return the state value
     */
    private static StateValue fromEither(Either<String, Pick> either) {
        return either.map(
                text -> FACE_KEYWORD.equals(text) ? new PlacedFace() : new Named(text),
                Function.identity());
    }

    /**
     * Encodes for the codec, writing the placed face as its keyword.
     *
     * @param value the state value
     * @return the raw value
     */
    private static Either<String, Pick> toEither(StateValue value) {
        return switch (value) {
            case Named named -> Either.left(named.value());
            case PlacedFace face -> Either.left(FACE_KEYWORD);
            case Pick pick -> Either.right(pick);
        };
    }

    /**
     * A value name written as is.
     *
     * @param value the property value's name
     */
    record Named(String value) implements StateValue {
        @Override
        public String resolve(StepContext context) {
            return value;
        }
    }

    /**
     * The face the host was placed on, for a direction property.
     */
    record PlacedFace() implements StateValue {
        @Override
        public String resolve(StepContext context) {
            return context.hostAs(PlacedFaceHost.class).placedFace().getName();
        }

        @Override
        public Set<HostCapability> requires() {
            return Set.of(HostCapability.PLACED_FACE);
        }
    }

    /**
     * A value name picked from a list by an expression's integer result,
     * clamped to the list's ends.
     *
     * @param by     the expression, evaluated when the step runs
     * @param values the value names in index order
     */
    record Pick(Expr by, List<String> values) implements StateValue {

        private static final String FIELD_BY = "by";
        private static final String FIELD_VALUES = "values";

        @Override
        public String resolve(StepContext context) {
            int index = Math.clamp(by.evaluateInt(context), 0, values.size() - 1);
            return values.get(index);
        }

        @Override
        public Stream<Expr> expressions() {
            return Stream.of(by);
        }
    }
}
