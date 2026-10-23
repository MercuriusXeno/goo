package com.mercuriusxeno.goo.workbench.checks;

/**
 * One item a check wants and what it lacks.
 *
 * @param item  the item's id
 * @param lacks what the item lacks, or holds that it should not, in words the Checks tab shows
 */
public record Want(String item, String lacks) {
}
