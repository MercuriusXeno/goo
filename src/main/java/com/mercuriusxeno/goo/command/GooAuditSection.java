package com.mercuriusxeno.goo.command;

import java.util.List;

/**
 * One rule's section of the audit: its name, its header line, the lines under it, the issues it counts toward
 * the audit's total, and the flags the workbench lists first.
 *
 * @param section the section's name, its header without the count
 * @param header  the header line the text holds
 * @param lines   the lines under the header
 * @param issues  the issues it counts toward the audit's total
 * @param flags   each thing it flags, one line each
 */
public record GooAuditSection(String section, String header, List<String> lines, int issues, List<String> flags) {
    private static final int HEADER_AND_SEPARATOR = 2;
    private static final String COUNT_OPEN = " (";

    /**
     * @param section the section's name
     * @param header  the header line
     * @param lines   the lines under it
     * @param flags   each thing it flags, each one issue
     * @return the section
     */
    public static GooAuditSection of(String section, String header, List<String> lines, List<String> flags) {
        return new GooAuditSection(section, header, List.copyOf(lines), flags.size(), List.copyOf(flags));
    }

    /**
     * Reads a section a writer appended as header, separator, lines and a closing blank line, every line under
     * the header a flag where the section counts issues.
     *
     * @param appended the appended lines
     * @param issues   the issues it counts
     * @return the section
     */
    static GooAuditSection withIssueFlags(List<String> appended, int issues) {
        List<String> lines = linesUnder(appended);
        return new GooAuditSection(nameOf(appended.getFirst()), appended.getFirst(), lines, issues,
                issues == 0 ? List.of() : lines.stream().map(String::trim).toList());
    }

    /**
     * Reads a section a writer appended, the lines holding a mark its flags.
     *
     * @param appended the appended lines
     * @param issues   the issues it counts
     * @param mark     the text a flagged line holds
     * @return the section
     */
    static GooAuditSection withFlagsMarked(List<String> appended, int issues, String mark) {
        List<String> lines = linesUnder(appended);
        return new GooAuditSection(nameOf(appended.getFirst()), appended.getFirst(), lines, issues,
                lines.stream().filter(line -> line.contains(mark)).map(String::trim).toList());
    }

    private static List<String> linesUnder(List<String> appended) {
        int end = appended.size() > HEADER_AND_SEPARATOR && appended.getLast().isEmpty()
                ? appended.size() - 1 : appended.size();
        return List.copyOf(appended.subList(Math.min(HEADER_AND_SEPARATOR, end), end));
    }

    /**
     * A header names its section before the count in parentheses: "PHANTOM IDS (2 identifiers ...)".
     */
    private static String nameOf(String header) {
        int count = header.indexOf(COUNT_OPEN);
        return count < 0 ? header.trim() : header.substring(0, count).trim();
    }
}
