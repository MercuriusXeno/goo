package com.mercuriusxeno.goo.tools.architecture;

import de.elnarion.util.plantuml.generator.classdiagram.PlantUMLClassDiagramGenerator;
import de.elnarion.util.plantuml.generator.classdiagram.config.PlantUMLClassDiagramConfig;
import de.elnarion.util.plantuml.generator.classdiagram.config.PlantUMLClassDiagramConfigBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Writes one PlantUML class-diagram slice of goo's compiled classes. The
 * {@code umlDiagrams<Name>} Gradle tasks run it as a forked JavaExec, so the
 * classes it scans load in this JVM and never in the build's
 * (decision uml-generator-runs-in-tools).
 */
public final class UmlSliceWriter {

    private static final String USAGE = "usage: UmlSliceWriter <output dir> <slice file> <package>...";
    private static final String WROTE_LINE = "Wrote %s (%d bytes)%n";
    private static final int FIRST_PACKAGE_ARGUMENT = 2;

    /**
     * Holds only static members.
     */
    private UmlSliceWriter() {
    }

    /**
     * Generates the slice and writes it.
     *
     * @param args the output directory, the slice's file name, then each package the slice draws
     * @throws IOException when the slice cannot be written
     */
    public static void main(String[] args) throws IOException {
        if (args.length <= FIRST_PACKAGE_ARGUMENT) {
            throw new IllegalArgumentException(USAGE);
        }
        List<String> packages = Arrays.asList(args).subList(FIRST_PACKAGE_ARGUMENT, args.length);
        Path out = Path.of(args[0]).resolve(args[1]);
        Files.createDirectories(out.getParent());
        Files.writeString(out, generate(packages), StandardCharsets.UTF_8);
        System.out.printf(WROTE_LINE, out, Files.size(out));
    }

    /**
     * Renders the diagram of the classes directly inside the given packages.
     *
     * @param packages the packages the slice draws
     * @return the PlantUML text
     */
    public static String generate(List<String> packages) {
        PlantUMLClassDiagramConfig config = new PlantUMLClassDiagramConfigBuilder(packages, directChildrenOf(packages))
                .withClassLoader(UmlSliceWriter.class.getClassLoader())
                .build();
        return new PlantUMLClassDiagramGenerator(config).generateDiagramText();
    }

    /**
     * ClassGraph scans subpackages too, so a slice listing {@code client} would
     * draw {@code client.ber}; this whitelist keeps classes whose package is one listed.
     *
     * @param packages the packages the slice draws
     * @return the whitelist regex
     */
    static String directChildrenOf(List<String> packages) {
        String alternatives = packages.stream()
                .map(name -> name.replace(".", "\\."))
                .collect(Collectors.joining("|"));
        return "^(" + alternatives + ")\\.[^.]+$";
    }
}
