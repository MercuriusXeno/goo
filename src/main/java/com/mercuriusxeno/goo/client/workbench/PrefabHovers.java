package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The tooltip every prefab reference the manager draws shows on hover: in an Items value, a Groups expression, a
 * Prefabs read view and a Mutators row, the prefab's name then its value read from the last evaluate reply, so it
 * refreshes as the evaluation does (decision every-prefab-reference-hovers-its-value).
 */
final class PrefabHovers {
    // A hover key no prefab name, item id or hint spells.
    private static final String HOVER_PREFIX = "goo-prefab:";
    private static final String AMOUNT_SEPARATOR = ": ";

    private PrefabHovers() {
    }

    /**
     * @param name a prefab's name
     * @return the hover key whose tooltip shows the prefab's value
     */
    static String hoverKey(String name) {
        return HOVER_PREFIX + name;
    }

    /**
     * @param variables the Prefabs tab's state, holding the last evaluate reply
     * @param hoverKey  what the hovered area names
     * @return the prefab's name, then one line per goo type with its amount for a tree, its value for a scalar, or
     *     its error; empty where the area is no prefab reference's
     */
    static Optional<List<Component>> tooltipFor(VariablesTab variables, String hoverKey) {
        if (!hoverKey.startsWith(HOVER_PREFIX)) {
            return Optional.empty();
        }
        String name = hoverKey.substring(HOVER_PREFIX.length());
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal(name));
        variables.prefabValue(name).ifPresent(value -> lines.addAll(valueLines(value)));
        return Optional.of(lines);
    }

    private static List<Component> valueLines(VariablesTab.PrefabValue value) {
        if (!value.error().isEmpty()) {
            return List.of(Component.literal(value.error()));
        }
        if (value.tree().isEmpty()) {
            return List.of(Component.literal(value.scalar()));
        }
        List<Component> lines = new ArrayList<>();
        value.tree().forEach((type, amount) -> lines.add(Component.empty().append(GooTypeIcons.nameOf(type))
                .append(Component.literal(AMOUNT_SEPARATOR + amount))));
        return lines;
    }
}
