package com.mercuriusxeno.goo.ability.program;

import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.StateHolder;
import net.minecraft.world.level.block.state.properties.Property;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Sets block state properties by the names a datapack writes them with,
 * the way a blockstate file names them: {@code facing=north},
 * {@code size=large}. Resolution runs over the block's state definition
 * alone, so a test drives it with no block loaded; a property the block
 * lacks, or a value the property does not name, refuses naming both,
 * since a program that names them wrong is a data error the log should
 * show.
 */
final class StatePropertyWriter {

    private static final String ERR_NO_PROPERTY = "Block %s has no property '%s'";
    private static final String ERR_NO_VALUE = "Property '%s' of %s has no value '%s'";

    private StatePropertyWriter() {
    }

    /**
     * Resolves every named value against the definition's properties.
     *
     * @param definition the block's state definition
     * @param values     each property name to the value name to set
     * @param blockName  what a refusal calls the block
     * @return the property values, in the map's order
     */
    static List<Property.Value<?>> resolve(StateDefinition<?, ?> definition, Map<String, String> values,
                                           Object blockName) {
        List<Property.Value<?>> resolved = new ArrayList<>(values.size());
        for (Map.Entry<String, String> entry : values.entrySet()) {
            Property<?> property = definition.getProperty(entry.getKey());
            if (property == null) {
                throw new IllegalArgumentException(String.format(ERR_NO_PROPERTY, blockName, entry.getKey()));
            }
            resolved.add(valueOf(property, entry.getValue(), blockName));
        }
        return resolved;
    }

    /**
     * Applies resolved values to a state.
     *
     * @param base   the state to start from, usually the block's default
     * @param values the property values to set
     * @param <S>    the state type
     * @return the state with every value set
     */
    static <S extends StateHolder<?, S>> S write(S base, List<Property.Value<?>> values) {
        S state = base;
        for (Property.Value<?> value : values) {
            state = set(state, value);
        }
        return state;
    }

    /**
     * Resolves one value name through its property, binding the
     * property's value type.
     *
     * @param property  the property
     * @param valueName the value's name
     * @param blockName what a refusal calls the block
     * @param <T>       the property's value type
     * @return the property value
     */
    private static <T extends Comparable<T>> Property.Value<T> valueOf(Property<T> property, String valueName,
                                                                        Object blockName) {
        T value = property.getValue(valueName).orElseThrow(() -> new IllegalArgumentException(
                String.format(ERR_NO_VALUE, property.getName(), blockName, valueName)));
        return new Property.Value<>(property, value);
    }

    /**
     * Sets one value, binding the property's value type.
     *
     * @param state the state to set on
     * @param value the property value
     * @param <T>   the property's value type
     * @param <S>   the state type
     * @return the state with the value set
     */
    private static <T extends Comparable<T>, S extends StateHolder<?, S>> S set(S state, Property.Value<T> value) {
        return state.setValue(value.property(), value.value());
    }
}
