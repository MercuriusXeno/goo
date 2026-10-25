package com.mercuriusxeno.goo.client.workbench;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * The Audit tab's state with no Screen: the sections goo_audit.json or the text dumps carry, flagged sections first,
 * the latest lines of a dump run in flight, and the line its result leaves, as index.html's audit section holds them
 * (decisions goo-workbench-opens-the-in-game-manager-screen, workbench-shows-each-roots-remainders-and-items-solved,
 * workbench-lists-type-to-type-paths-worst-first, workbench-traces-each-undercut-manual-value-to-its-roots).
 */
public final class AuditTab {
    /** The test server logs tens of thousands of lines a run; the latest few show its progress. */
    public static final int PROGRESS_LINES_SHOWN = 300;
    private static final String SECTIONS = "sections";
    private static final String HEADER = "header";
    private static final String FLAGS = "flags";
    private static final String LINES = "lines";
    private static final String DUMP = "dump";
    private static final String EXIT_CODE = "exitCode";
    private static final String DURATION = "durationSeconds";
    private static final String AUDIT_JSON = "goo_audit.json";
    private static final String ONE_FLAG = " flag";
    private static final String FLAGS_SUFFIX = " flags";
    private static final String REFRESHED = "dumps refreshed";
    private static final String FAILED = "the run failed, exit ";
    private static final String SEPARATOR = " · ";
    private static final String MINUTES = " m ";
    private static final String SECONDS = " s";
    private static final String NO_TEXT = "";
    private static final int SECONDS_PER_MINUTE = 60;

    private final List<AuditSection> sections = new ArrayList<>();
    private final Deque<String> progress = new ArrayDeque<>();
    private boolean running;
    private String runLine = NO_TEXT;

    /**
     * One section of the audit.
     *
     * @param header the section's header
     * @param flags  what the section's rule flags
     * @param source the file the section came from
     * @param lines  the section's body
     * @param badge  its flag count, empty for a quiet section
     */
    public record AuditSection(String header, List<String> flags, String source, List<String> lines, String badge) {
    }

    /**
     * @param sectionsJson the audit, standing dumps or dump run reply carrying sections
     */
    public void readSections(String sectionsJson) {
        readSectionsOf(JsonParser.parseString(sectionsJson).getAsJsonObject());
    }

    /**
     * @param sectionsJson a reply carrying sections
     * @return whether it carries none, as the audit answers before any run wrote goo_audit.json
     */
    public static boolean holdsNoSections(String sectionsJson) {
        return JsonParser.parseString(sectionsJson).getAsJsonObject().getAsJsonArray(SECTIONS).isEmpty();
    }

    private void readSectionsOf(JsonObject reply) {
        List<AuditSection> flagged = new ArrayList<>();
        List<AuditSection> quiet = new ArrayList<>();
        for (JsonElement element : reply.getAsJsonArray(SECTIONS)) {
            JsonObject section = element.getAsJsonObject();
            List<String> flags = strings(section, FLAGS);
            JsonElement dump = section.get(DUMP);
            AuditSection read = new AuditSection(section.get(HEADER).getAsString(), flags,
                    dump == null || dump.isJsonNull() ? AUDIT_JSON : dump.getAsString(), strings(section, LINES),
                    flags.isEmpty() ? NO_TEXT : flags.size() + (flags.size() == 1 ? ONE_FLAG : FLAGS_SUFFIX));
            (flags.isEmpty() ? quiet : flagged).add(read);
        }
        sections.clear();
        sections.addAll(flagged);
        sections.addAll(quiet);
    }

    private static List<String> strings(JsonObject json, String field) {
        JsonElement value = json.get(field);
        if (value == null || !value.isJsonArray()) {
            return List.of();
        }
        JsonArray array = value.getAsJsonArray();
        return array.asList().stream().map(JsonElement::getAsString).toList();
    }

    /**
     * @return the sections, flagged ones first
     */
    public List<AuditSection> sections() {
        return List.copyOf(sections);
    }

    /**
     * A run starts: its progress clears.
     */
    public void startRun() {
        running = true;
        progress.clear();
        runLine = NO_TEXT;
    }

    /**
     * @param line one line the run printed
     */
    public void progress(String line) {
        progress.addLast(line);
        while (progress.size() > PROGRESS_LINES_SHOWN) {
            progress.removeFirst();
        }
    }

    /**
     * @param resultJson the dump run's reply: its exit code, its duration and both dumps' sections
     */
    public void finishRun(String resultJson) {
        JsonObject result = JsonParser.parseString(resultJson).getAsJsonObject();
        int exit = result.get(EXIT_CODE).getAsInt();
        long seconds = Math.round(result.get(DURATION).getAsDouble());
        runLine = (exit == 0 ? REFRESHED : FAILED + exit) + SEPARATOR + seconds / SECONDS_PER_MINUTE + MINUTES
                + seconds % SECONDS_PER_MINUTE + SECONDS;
        readSectionsOf(result);
        running = false;
    }

    /**
     * @param refusal why the run did not answer
     */
    public void failRun(String refusal) {
        runLine = refusal;
        running = false;
    }

    /**
     * @return whether a run is in flight
     */
    public boolean running() {
        return running;
    }

    /**
     * @return the run's latest lines in arrival order
     */
    public List<String> progressLines() {
        return List.copyOf(progress);
    }

    /**
     * @return what the last run's result says, empty before any run finished
     */
    public String runLine() {
        return runLine;
    }
}
