package com.mercuriusxeno.goo.workbench.checks;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a {@link ValuesCheck} the workbench discovers and runs, so a new check is one class and nothing else
 * changes (decision each-check-is-one-rule-added-alone). The class holds a public no-argument constructor.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ValuesCheckRule {

    /**
     * @return where the check stands in the Checks tab, lowest first
     */
    int order();
}
