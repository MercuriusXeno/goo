package com.mercuriusxeno.goo.client.workbench;

import com.mercuriusxeno.goo.workbench.Processes;
import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The tooltip every referent to a group shows on hover, a #group link and an iterator link alike since an iterator
 * fills its group: the group's members as sprites on one line, cut at eight with +n for the rest, then its value's
 * amounts where it has one, which the tooltip lays as a grid, an iterator's ending on its ratio and the mutators it
 * applies (decisions group-referent-previews-shorthand-members-then-its-value, group-link-hovers-member-icons-then-
 * plus-n, every-linkable-thing-hovers-its-preview-value, iterator-hover-adds-its-ratio-and-mutators).
 */
final class GroupHovers {
    // Hover keys no prefab name, item id or hint spells.
    private static final String HOVER_PREFIX = "goo-group:";
    private static final String ITERATOR_PREFIX = "goo-iterator:";
    private static final String ITERATOR_MARK = "#";
    private static final String STACK_MARK = "@";
    private static final String CONVERSIONS = "_conversions";
    // Decision iterator-drops-its-ratio-from-row-and-grammar: an iterator copies its source one to one.
    private static final String RATIO = "x1";
    private static final String APPLIES = " -> ";
    private static final String LIST_SEPARATOR = ", ";
    // How many member sprites the line draws before +n stands for the rest.
    private static final int SHOWN_MEMBERS = 8;

    private GroupHovers() {
    }

    /**
     * @param group a group's name
     * @return the hover key whose tooltip previews the group
     */
    static String hoverKey(String group) {
        return HOVER_PREFIX + group;
    }

    /**
     * @param iterator an iterator's key, the group it fills after its #
     * @return the hover key previewing that group, then what the iterator does
     */
    static String iteratorHoverKey(String iterator) {
        return ITERATOR_PREFIX + iterator;
    }

    /**
     * @param groups    the Groups tab's state
     * @param values    the Items tab's state, holding the members' values
     * @param processes the Conversions tab's state, holding what an iterator applies
     * @param hoverKey  what the hovered area names
     * @return the member line, then the value's amounts, then for an iterator its ratio and the mutators it applies;
     *     empty where the area previews no group
     */
    static Optional<List<TooltipLine>> tooltipFor(GroupsTab groups, ValuesTab values, ProcessesTab processes,
                                                   String hoverKey) {
        if (hoverKey.startsWith(ITERATOR_PREFIX)) {
            String iterator = hoverKey.substring(ITERATOR_PREFIX.length());
            String filled = iterator.startsWith(ITERATOR_MARK) ? iterator.substring(ITERATOR_MARK.length()) : iterator;
            List<TooltipLine> lines = new ArrayList<>(groupLines(groups, values, filled));
            lines.add(new TooltipLine.Text(Component.literal(doesLine(processes, iterator))));
            return Optional.of(lines);
        }
        return hoverKey.startsWith(HOVER_PREFIX)
                ? Optional.of(groupLines(groups, values, hoverKey.substring(HOVER_PREFIX.length())))
                : Optional.empty();
    }

    // Decision iterator-hover-adds-its-ratio-and-mutators: what the iterator does, its ratio then each mutator it
    // applies in the order its row reads them, as x1 -> waxed, cooked.
    private static String doesLine(ProcessesTab processes, String iterator) {
        List<String> chain = processes.formOf(CONVERSIONS, iterator) instanceof Processes.Assignment assignment
                ? assignment.chain().stream().map(link -> link.startsWith(STACK_MARK)
                ? link.substring(STACK_MARK.length()) : link).toList() : List.of();
        return chain.isEmpty() ? RATIO : RATIO + APPLIES + String.join(LIST_SEPARATOR, chain);
    }

    private static List<TooltipLine> groupLines(GroupsTab groups, ValuesTab values, String name) {
        Optional<GroupsTab.Group> group = groups.group(name);
        List<String> members = group.map(found -> found.members().stream().map(GroupsTab.Member::id).toList())
                .orElseGet(() -> values.groupMembers(name));
        List<TooltipLine> lines = new ArrayList<>();
        lines.add(new TooltipLine.Members(members.subList(0, Math.min(members.size(), SHOWN_MEMBERS)),
                Math.max(0, members.size() - SHOWN_MEMBERS)));
        Map<String, String> worth = group.map(found -> GroupsPane.worthOf(found, values)).orElse(Map.of());
        worth.forEach((type, amount) -> lines.add(new TooltipLine.GooAmount(type, amount)));
        return lines;
    }
}
