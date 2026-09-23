package com.mercuriusxeno.goo.ability;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Tripwire for decision no-new-hardcoded-abilities: every ability JSON on
 * the classpath decodes, and every behavior entry is a {@code program} or
 * one of the hand-written types still awaiting migration. A new ability
 * lands as a program; a new hand-written type fails here naming its file.
 */
class HardcodedAbilityTripwireTest {

    /**
     * The hand-written behavior types still standing. This set only loses
     * members: each migration thread under data-driven-goo deletes its own
     * type here in the commit that deletes the Java behind it.
     */
    private static final Set<String> NOT_YET_MIGRATED = Set.of(
            "black_hole");

    private static final String ABILITIES_DIR = "data/goo/goo_abilities";
    private static final String JSON_SUFFIX = ".json";

    private static List<Path> abilityFiles() throws IOException, URISyntaxException {
        URL dir = HardcodedAbilityTripwireTest.class.getClassLoader().getResource(ABILITIES_DIR);
        if (dir == null) {
            fail("Classpath holds no " + ABILITIES_DIR);
        }
        try (Stream<Path> files = Files.list(Path.of(dir.toURI()))) {
            return files.filter(p -> p.getFileName().toString().endsWith(JSON_SUFFIX)).sorted().toList();
        }
    }

    private static AbilityDefinition decode(Path file) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            return AbilityDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                    .getOrThrow(message -> new IllegalStateException(file.getFileName() + ": " + message));
        }
    }

    @Test
    void everyAbilityBehaviorIsAProgramOrAwaitingMigration() throws IOException, URISyntaxException {
        List<Path> files = abilityFiles();
        assertFalse(files.isEmpty(), "No ability JSON found under " + ABILITIES_DIR);
        List<String> offenders = new ArrayList<>();
        for (Path file : files) {
            for (AbilityDefinition.BehaviorEntry entry : decode(file).behaviors()) {
                if (!isAllowed(entry.type())) {
                    offenders.add(file.getFileName() + " names behavior type '" + entry.type() + "'");
                }
            }
        }
        assertTrue(offenders.isEmpty(), () -> "Hardcoded ability behavior outside the program runtime:\n"
                + String.join("\n", offenders)
                + "\nA new ability is a \"program\" in goo_abilities; the allowlist only loses members.");
    }

    @Test
    void migratedTypesAreNotOnTheAllowlist() {
        assertFalse(NOT_YET_MIGRATED.contains(ProgramBehavior.TYPE_NAME));
        assertFalse(NOT_YET_MIGRATED.contains("explosion"), "explosion migrated to a program; remove it");
    }

    private static boolean isAllowed(String type) {
        return ProgramBehavior.TYPE_NAME.equals(type) || NOT_YET_MIGRATED.contains(type);
    }
}
