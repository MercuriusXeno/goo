package com.mercuriusxeno.goo.tools.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Writes the shape findings ({@code findings.md}), the architectural document
 * a sweep reads first, from one scan of goo's compiled classes (decision
 * one-command-regenerates-architectural-documentation). The
 * {@code architectureFindings} Gradle task runs it from the repository root,
 * which the source paths resolve against.
 */
public final class ArchitectureFindings {

    /** The findings document's file name. */
    public static final String FINDINGS_FILE = "findings.md";
    /** The packages the findings read past: game tests and the build's own tools. */
    public static final List<String> READ_PAST = List.of(
            NodeLabels.ROOT_PACKAGE + ".gametest",
            NodeLabels.ROOT_PACKAGE + ".tools");
    private static final String USAGE = "usage: ArchitectureFindings <classes dir> <source root> <output dir>";
    private static final int ARGUMENT_COUNT = 3;
    private static final int OUTPUT_ARGUMENT = 2;

    /**
     * Holds only static members.
     */
    private ArchitectureFindings() {
    }

    /**
     * Scans the classes and writes the findings.
     *
     * @param args the classes directory, the source root relative to the repository, and the output directory
     * @throws IOException when a class, a source file or the document cannot be read or written
     */
    public static void main(String[] args) throws IOException {
        if (args.length != ARGUMENT_COUNT) {
            throw new IllegalArgumentException(USAGE);
        }
        List<ScannedType> types = new ClassScanner(Path.of(args[0]), args[1]).scan();
        ArchitectureGraphs.writeDocument(Path.of(args[OUTPUT_ARGUMENT]).resolve(FINDINGS_FILE),
                FindingsDocument.render(types, READ_PAST, countSourceLines(types)));
    }

    /**
     * Counts the lines of each scanned type's source file.
     *
     * @param types the scan
     * @return each source path that exists mapped to its line count
     * @throws IOException when a source file cannot be read
     */
    private static Map<String, Integer> countSourceLines(List<ScannedType> types) throws IOException {
        Map<String, Integer> lines = new HashMap<>();
        for (ScannedType type : types) {
            Path source = Path.of(type.sourcePath());
            if (Files.isRegularFile(source)) {
                try (Stream<String> sourceLines = Files.lines(source)) {
                    lines.put(type.sourcePath(), (int) sourceLines.count());
                }
            }
        }
        return lines;
    }
}
