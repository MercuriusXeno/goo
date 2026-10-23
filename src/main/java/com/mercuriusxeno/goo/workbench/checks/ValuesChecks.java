package com.mercuriusxeno.goo.workbench.checks;

import com.mercuriusxeno.goo.AnnotatedClasses;
import com.mercuriusxeno.goo.data.GooBaseValuesPreview;
import net.minecraft.resources.Identifier;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Finds every {@link ValuesCheckRule} class and runs them over a draft, through the game's loader with no
 * server (decision type-source-checks-are-rules-that-gate-promote).
 */
public final class ValuesChecks {
    private static final String CHECK_PACKAGE = ValuesChecks.class.getPackageName();
    private static final Function<Identifier, Set<Identifier>> NO_TAGS = tag -> Set.of();

    private ValuesChecks() {
    }

    /**
     * @return every discovered check, in its declared order, then by class name
     */
    public static List<ValuesCheck> discovered() {
        return AnnotatedClasses.instances(ValuesCheckRule.class, ValuesCheck.class, CHECK_PACKAGE,
                type -> type.getAnnotation(ValuesCheckRule.class).order());
    }

    /**
     * @param json base_values.json text
     * @return every check's answer over it, with no item tag known
     */
    public static List<CheckResult> run(String json) {
        return run(json, NO_TAGS);
    }

    /**
     * @param json        base_values.json text
     * @param tagResolver resolves an item tag to its member ids, standing in for the game's registry
     * @return every check's answer over it, in the checks' order
     */
    public static List<CheckResult> run(String json, Function<Identifier, Set<Identifier>> tagResolver) {
        DraftValues values = new DraftValues(GooBaseValuesPreview.postConverted(json, tagResolver));
        return discovered().stream().map(check -> new CheckResult(check.title(), check.wants(values))).toList();
    }
}
