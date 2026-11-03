package com.mercuriusxeno.goo.client.workbench;

import net.minecraft.network.chat.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The tooltip every referent to a group shows on hover, a #group link and an iterator link alike since an iterator
 * fills its group: the group's members in shorthand on one line, cut with an ellipsis, then its value's amounts
 * where it has one, which the tooltip lays as a grid (decisions group-referent-previews-shorthand-members-then-its-
 * value, group-link-hovers-member-icons-then-plus-n, every-linkable-thing-hovers-its-preview-value).
 */
final class GroupHovers {
    // A hover key no prefab name, item id or hint spells.
    private static final String HOVER_PREFIX = "goo-group:";
    private static final String ITERATOR_MARK = "#";
    private static final String NAMESPACE = "minecraft:";
    private static final String SEPARATOR = ", ";
    private static final String ELLIPSIS = "…";
    private static final String NO_TEXT = "";
    // How many characters the member line reads before it cuts.
    private static final int MEMBER_LINE_LIMIT = 36;

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
     * @return the hover key previewing that group
     */
    static String iteratorHoverKey(String iterator) {
        return hoverKey(iterator.startsWith(ITERATOR_MARK) ? iterator.substring(ITERATOR_MARK.length()) : iterator);
    }

    /**
     * @param groups   the Groups tab's state
     * @param values   the Items tab's state, holding the members' values
     * @param hoverKey what the hovered area names
     * @return the member line, then the value's amounts; empty where the area previews no group
     */
    static Optional<List<TooltipLine>> tooltipFor(GroupsTab groups, ValuesTab values, String hoverKey) {
        if (!hoverKey.startsWith(HOVER_PREFIX)) {
            return Optional.empty();
        }
        String name = hoverKey.substring(HOVER_PREFIX.length());
        Optional<GroupsTab.Group> group = groups.group(name);
        List<String> members = group.map(found -> found.members().stream().map(GroupsTab.Member::id).toList())
                .orElseGet(() -> values.groupMembers(name));
        List<TooltipLine> lines = new ArrayList<>();
        lines.add(new TooltipLine.Text(Component.literal(shorthand(members))));
        Map<String, String> worth = group.map(found -> GroupsPane.worthOf(found, values)).orElse(Map.of());
        worth.forEach((type, amount) -> lines.add(new TooltipLine.GooAmount(type, amount)));
        return Optional.of(lines);
    }

    // The members by their ids without the minecraft namespace, as many as the line holds, then an ellipsis.
    private static String shorthand(List<String> members) {
        StringBuilder line = new StringBuilder();
        for (String member : members) {
            String shown = member.startsWith(NAMESPACE) ? member.substring(NAMESPACE.length()) : member;
            String joined = (line.isEmpty() ? NO_TEXT : SEPARATOR) + shown;
            if (line.length() + joined.length() > MEMBER_LINE_LIMIT) {
                return line.isEmpty() ? ELLIPSIS : line + SEPARATOR + ELLIPSIS;
            }
            line.append(joined);
        }
        return line.toString();
    }
}
