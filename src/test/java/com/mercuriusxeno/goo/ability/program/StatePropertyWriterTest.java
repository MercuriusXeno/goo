package com.mercuriusxeno.goo.ability.program;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

/**
 * Property names and value names written by a program resolve to the
 * properties' own values, and a name the block or the property does not
 * carry refuses naming it.
 */
class StatePropertyWriterTest {

    private static final EnumProperty<Direction> FACING = EnumProperty.create("facing", Direction.class);
    private static final BooleanProperty LIT = BooleanProperty.create("lit");
    private static final String BLOCK = "goo:glow_crystal";
    private static final String NO_SUCH = "no_such";

    private static StateDefinition<?, ?> definitionOf(Property<?>... properties) {
        StateDefinition<?, ?> definition = mock(StateDefinition.class);
        doReturn(null).when(definition).getProperty(anyString());
        for (Property<?> property : properties) {
            doReturn(property).when(definition).getProperty(property.getName());
        }
        return definition;
    }

    @Test
    void resolvesEachNamedValueThroughItsProperty() {
        Map<String, String> names = new LinkedHashMap<>();
        names.put("facing", "west");
        names.put("lit", "true");

        List<Property.Value<?>> values = StatePropertyWriter.resolve(definitionOf(FACING, LIT), names, BLOCK);

        assertEquals(List.of(new Property.Value<>(FACING, Direction.WEST), new Property.Value<>(LIT, true)), values);
    }

    @Test
    void unknownPropertyRefusesNamingBlockAndProperty() {
        StateDefinition<?, ?> definition = definitionOf(FACING);

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> StatePropertyWriter.resolve(definition, Map.of(NO_SUCH, "west"), BLOCK));

        assertTrue(refusal.getMessage().contains(NO_SUCH), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(BLOCK), refusal.getMessage());
    }

    @Test
    void unknownValueRefusesNamingPropertyAndValue() {
        StateDefinition<?, ?> definition = definitionOf(FACING);

        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> StatePropertyWriter.resolve(definition, Map.of("facing", NO_SUCH), BLOCK));

        assertTrue(refusal.getMessage().contains("facing"), refusal.getMessage());
        assertTrue(refusal.getMessage().contains(NO_SUCH), refusal.getMessage());
    }
}
