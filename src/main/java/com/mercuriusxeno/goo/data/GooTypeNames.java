package com.mercuriusxeno.goo.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The goo type names a base_values entry's text spells, read the way the loader reads them: each key of a per-type
 * value object, as {@link GooValueJsonFormat} parses one, and the type of each $prefab.type member term, split off
 * its token as {@link GooValueExpression} splits it (decision draft-load-drops-entries-naming-an-unknown-type).
 */
public final class GooTypeNames {
    private static final char CONSTANT_PREFIX = '$';
    private static final char TYPE_SEPARATOR = '.';

    private GooTypeNames() {
    }

    /**
     * @param value an entry's value: a per-type object, an expression string, or a structure holding them
     * @return every type name it spells, in text order
     */
    public static Set<String> namedIn(JsonElement value) {
        Set<String> names = new LinkedHashSet<>();
        collect(value, names);
        return names;
    }

    private static void collect(JsonElement value, Set<String> names) {
        if (value.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : value.getAsJsonObject().entrySet()) {
                names.add(entry.getKey());
                collect(entry.getValue(), names);
            }
        } else if (value.isJsonArray()) {
            value.getAsJsonArray().forEach(element -> collect(element, names));
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            collectMemberTerms(value.getAsJsonPrimitive(), names);
        }
    }

    private static void collectMemberTerms(JsonPrimitive expression, Set<String> names) {
        for (String token : ExpressionTokenizer.tokenize(expression.getAsString())) {
            String type = token.charAt(0) == CONSTANT_PREFIX ? typeSuffix(token.substring(1)) : null;
            if (type != null) {
                names.add(type);
            }
        }
    }

    /**
     * @param dottedName a name such as log.leaf
     * @return the type after its last dot, null where no dot splits the name into a prefab and a type
     */
    static @Nullable String typeSuffix(String dottedName) {
        int dot = dottedName.lastIndexOf(TYPE_SEPARATOR);
        return dot > 0 && dot < dottedName.length() - 1 ? dottedName.substring(dot + 1) : null;
    }
}
