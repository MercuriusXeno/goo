package com.mercuriusxeno.goo.tools.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes the package topology graph ({@code topology.md}) and the type
 * dependency graph ({@code dependencies.md}) from one scan of goo's compiled
 * classes (decision one-command-regenerates-architectural-documentation).
 * The {@code architectureGraphs} Gradle task runs it.
 */
public final class ArchitectureGraphs {

    /** The package topology document's file name. */
    public static final String TOPOLOGY_FILE = "topology.md";
    /** The type dependency document's file name. */
    public static final String DEPENDENCIES_FILE = "dependencies.md";
    private static final String TOPOLOGY_TITLE = "goo package topology";
    private static final String DEPENDENCIES_TITLE = "goo type dependency graph";
    private static final String PACKAGES_NOUN = "packages";
    private static final String TYPES_NOUN = "types";
    private static final String USAGE = "usage: ArchitectureGraphs <classes dir> <source root> <output dir>";
    private static final String WROTE_LINE = "Wrote %s%n";
    private static final int ARGUMENT_COUNT = 3;
    private static final int OUTPUT_ARGUMENT = 2;

    /**
     * Holds only static members.
     */
    private ArchitectureGraphs() {
    }

    /**
     * Scans the classes and writes both graphs.
     *
     * @param args the classes directory, the source root relative to the repository, and the output directory
     * @throws IOException when a document cannot be written
     */
    public static void main(String[] args) throws IOException {
        if (args.length != ARGUMENT_COUNT) {
            throw new IllegalArgumentException(USAGE);
        }
        List<ScannedType> types = new ClassScanner(Path.of(args[0]), args[1]).scan();
        write(Path.of(args[OUTPUT_ARGUMENT]), types);
    }

    /**
     * Writes both graphs of a scan under a directory.
     *
     * @param outputDir the directory, created when absent
     * @param types     the scan
     * @throws IOException when a document cannot be written
     */
    public static void write(Path outputDir, List<ScannedType> types) throws IOException {
        DirectedGraph packages = DirectedGraph.ofPackages(types);
        writeDocument(outputDir.resolve(TOPOLOGY_FILE), MermaidGraphDocument.render(TOPOLOGY_TITLE, PACKAGES_NOUN,
                packages, NodeLabels.forPackages(packages.adjacency().keySet())));
        writeDocument(outputDir.resolve(DEPENDENCIES_FILE), MermaidGraphDocument.render(DEPENDENCIES_TITLE,
                TYPES_NOUN, DirectedGraph.ofTypes(types), NodeLabels.forTypes(types)));
    }

    /**
     * Writes one document, creating its directory.
     *
     * @param file the document's path
     * @param text its text
     * @throws IOException when it cannot be written
     */
    static void writeDocument(Path file, String text) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        System.out.printf(WROTE_LINE, file);
    }
}
