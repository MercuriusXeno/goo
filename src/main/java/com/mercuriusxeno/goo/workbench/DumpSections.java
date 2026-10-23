package com.mercuriusxeno.goo.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the audit and clique dumps section by section, the headers coming from the dumps themselves: a
 * header is a line underlined by a line of dashes or equals signs, and the text above the first one is a
 * section headed by the file's first line. The least value and the clique tables lead, since they are what a
 * change to base_values.json moves (decision workbench-reruns-the-audit-headless).
 */
final class DumpSections {
    private static final String LEADING_AUDIT_SECTION = "LEAST VALUE PER TYPE";
    private static final int UNDERLINE_AT_LEAST = 3;
    private static final String DUMP = "dump";
    private static final String HEADER = "header";
    private static final String LINES = "lines";
    private static final String WINDOWS_LINE_END = "\r\n";
    private static final String LINE_END = "\n";
    // String.split drops trailing empty lines unless its limit is negative.
    private static final int KEEP_TRAILING_EMPTY = -1;

    private DumpSections() {
    }

    /**
     * A dump's section.
     *
     * @param dump   the dump's file name
     * @param header the section's header line
     * @param lines  the lines under it
     */
    record Section(String dump, String header, List<String> lines) {
    }

    /**
     * @param dump the dump's file name
     * @param text the dump's text
     * @return its sections, in file order
     */
    static List<Section> parse(String dump, String text) {
        List<String> lines = List.of(text.replace(WINDOWS_LINE_END, LINE_END).split(LINE_END, KEEP_TRAILING_EMPTY));
        List<Section> sections = new ArrayList<>();
        String header = null;
        List<String> body = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            boolean headed = i + 1 < lines.size() && isUnderline(lines.get(i + 1)) && !line.isBlank();
            if (headed || header == null && !line.isBlank()) {
                if (header != null) {
                    sections.add(new Section(dump, header, trimmed(body)));
                }
                header = line.trim();
                body = new ArrayList<>();
                if (headed) {
                    i++;
                }
                continue;
            }
            body.add(line);
        }
        if (header != null) {
            sections.add(new Section(dump, header, trimmed(body)));
        }
        return sections;
    }

    /**
     * @param audit   the audit dump's sections
     * @param cliques the clique dump's sections
     * @return the least value section, then the clique sections, then the rest of the audit
     */
    static List<Section> ordered(List<Section> audit, List<Section> cliques) {
        List<Section> ordered = new ArrayList<>();
        audit.stream().filter(section -> section.header().startsWith(LEADING_AUDIT_SECTION)).forEach(ordered::add);
        ordered.addAll(cliques);
        audit.stream().filter(section -> !section.header().startsWith(LEADING_AUDIT_SECTION)).forEach(ordered::add);
        return ordered;
    }

    /**
     * @param auditDump  the audit dump's file
     * @param cliqueDump the clique dump's file
     * @return both dumps' sections in the order the page shows them, each absent dump answering none
     * @throws IOException where a standing dump cannot be read
     */
    static JsonArray read(Path auditDump, Path cliqueDump) throws IOException {
        List<Section> ordered = ordered(sectionsOf(auditDump), sectionsOf(cliqueDump));
        JsonArray sections = new JsonArray();
        for (Section section : ordered) {
            JsonObject json = new JsonObject();
            json.addProperty(DUMP, section.dump());
            json.addProperty(HEADER, section.header());
            JsonArray lines = new JsonArray();
            section.lines().forEach(lines::add);
            json.add(LINES, lines);
            sections.add(json);
        }
        return sections;
    }

    private static List<Section> sectionsOf(Path dump) throws IOException {
        return Files.isRegularFile(dump) ? parse(dump.getFileName().toString(), Files.readString(dump)) : List.of();
    }

    private static boolean isUnderline(String line) {
        String trimmed = line.trim();
        if (trimmed.length() < UNDERLINE_AT_LEAST) {
            return false;
        }
        char first = trimmed.charAt(0);
        return (first == '-' || first == '=') && trimmed.chars().allMatch(c -> c == first);
    }

    private static List<String> trimmed(List<String> body) {
        int start = 0;
        int end = body.size();
        while (start < end && body.get(start).isBlank()) {
            start++;
        }
        while (end > start && body.get(end - 1).isBlank()) {
            end--;
        }
        return List.copyOf(body.subList(start, end));
    }
}
