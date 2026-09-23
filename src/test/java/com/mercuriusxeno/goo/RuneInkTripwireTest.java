package com.mercuriusxeno.goo;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tripwire for decision rune-ink-stays-out: no data file under data/goo names rune_ink in its path or body.
 */
class RuneInkTripwireTest {

    private static final String DATA_DIR = "data/goo";
    private static final String RUNE_INK = "rune_ink";

    private static Path dataRoot() throws URISyntaxException {
        URL dir = RuneInkTripwireTest.class.getClassLoader().getResource(DATA_DIR);
        if (dir == null) {
            fail("Classpath holds no " + DATA_DIR);
        }
        return Path.of(dir.toURI());
    }

    private static boolean namesRuneInk(Path root, Path file) throws IOException {
        String relative = root.relativize(file).toString().toLowerCase(Locale.ROOT);
        String body = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1).toLowerCase(Locale.ROOT);
        return relative.contains(RUNE_INK) || body.contains(RUNE_INK);
    }

    @Test
    void runeInkHasNoRecipe() throws IOException, URISyntaxException {
        Path root = dataRoot();
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                if (namesRuneInk(root, file)) {
                    offenders.add(DATA_DIR + "/" + root.relativize(file).toString().replace('\\', '/'));
                }
            }
        }
        assertTrue(offenders.isEmpty(), () -> "Rune ink is not an item, recipe or upgrade"
                + " (decision rune-ink-stays-out); these data files name " + RUNE_INK + ":\n"
                + String.join("\n", offenders));
    }
}
