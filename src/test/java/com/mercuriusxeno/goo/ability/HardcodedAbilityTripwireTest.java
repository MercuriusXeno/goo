package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    private static final Set<String> NOT_YET_MIGRATED = Set.of();

    @Test
    void everyAbilityBehaviorIsAProgramOrAwaitingMigration() {
        List<Path> files = AbilityJson.files();
        assertFalse(files.isEmpty(), "No ability JSON found under " + AbilityJson.ABILITIES_DIR);
        List<String> offenders = new ArrayList<>();
        for (Path file : files) {
            for (AbilityDefinition.BehaviorEntry entry : AbilityJson.decode(file).behaviors()) {
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
