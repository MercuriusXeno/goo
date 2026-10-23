package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.regex.Pattern;

/**
 * Every entry form base_values.json supports, written as the file spells it: an amount or expression per goo
 * type, an expression over items and constants, a ratio to another item or constant, a type as a share of
 * another item's type, a denial, and a _restricted membership (decision workbench-authors-base-values-whole).
 */
final class ValueEntries {
    private static final String RESTRICTED = "_restricted";
    private static final String DENIED = "denied";
    private static final String TYPE_SEPARATOR = ".";
    private static final String MEMBER_SEPARATOR = ", ";
    private static final String OBJECT_OPEN = "{";
    private static final String OBJECT_CLOSE = "}";
    private static final String KEY_SEPARATOR = ": ";
    private static final String TERM_SEPARATOR = " ";
    private static final Pattern WHOLE_NUMBER = Pattern.compile("-?\\d+");

    private ValueEntries() {
    }

    /**
     * @param amounts each goo type's amount or expression, in the order the entry lists them
     * @return the value object, a whole amount written as a number
     */
    static String perType(Map<String, String> amounts) {
        StringJoiner object = new StringJoiner(MEMBER_SEPARATOR, OBJECT_OPEN, OBJECT_CLOSE);
        amounts.forEach((type, amount) -> object.add(quoted(type) + KEY_SEPARATOR
                + (WHOLE_NUMBER.matcher(amount.trim()).matches() ? amount.trim() : quoted(amount.trim()))));
        return object.toString();
    }

    /**
     * @param expression an expression over items and constants, such as "bucket + $food"
     * @return the expression string
     */
    static String expression(String expression) {
        return quoted(expression.trim());
    }

    /**
     * @param times   how many of the operand the item is worth, a whole number or a fraction such as 3 / 4
     * @param operand an item id or a $constant
     * @return the expression valuing the item at that many of the operand
     */
    static String ratio(String times, String operand) {
        return expression(times.trim() + TERM_SEPARATOR + operand.trim());
    }

    /**
     * @param type    the goo type
     * @param times   the share, a whole number or a fraction
     * @param operand the item id or $constant whose type it shares
     * @return the value object holding that type as a share of the operand's
     */
    static String typeShare(String type, String times, String operand) {
        return perType(Map.of(type, times.trim() + TERM_SEPARATOR + operand.trim() + TYPE_SEPARATOR + type));
    }

    /**
     * @return the denial
     */
    static String denied() {
        return quoted(DENIED);
    }

    /**
     * @param model the model
     * @param id    the item id or #group to restrict
     * @return the model with the id in _restricted, once
     */
    static BaseValuesModel withRestricted(BaseValuesModel model, String id) {
        String text = quoted(id);
        List<BaseValuesModel.Member> changed = new ArrayList<>();
        for (BaseValuesModel.Member member : model.members()) {
            if (RESTRICTED.equals(member.key()) && member.entries().stream().noneMatch(entry -> sameString(entry, id))) {
                List<BaseValuesModel.Member> entries = new ArrayList<>(member.entries());
                entries.add(BaseValuesModel.Member.entry(null, text));
                changed.add(member.withEntries(entries));
            } else {
                changed.add(member);
            }
        }
        return model.withMembers(changed);
    }

    /**
     * @param model the model
     * @param id    the item id or #group to lift the restriction from
     * @return the model without the id in _restricted
     */
    static BaseValuesModel withoutRestricted(BaseValuesModel model, String id) {
        List<BaseValuesModel.Member> changed = new ArrayList<>();
        for (BaseValuesModel.Member member : model.members()) {
            changed.add(RESTRICTED.equals(member.key())
                    ? member.withEntries(member.entries().stream().filter(entry -> !sameString(entry, id)).toList())
                    : member);
        }
        return model.withMembers(changed);
    }

    private static boolean sameString(BaseValuesModel.Member entry, String id) {
        try {
            return id.equals(JsonParser.parseString(entry.text()).getAsString());
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static String quoted(String text) {
        return new JsonPrimitive(text).toString();
    }
}
