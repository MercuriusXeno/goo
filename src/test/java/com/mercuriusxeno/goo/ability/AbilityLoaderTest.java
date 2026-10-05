package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.ability.program.AilmentOverlayStep;
import com.mercuriusxeno.goo.ability.program.PotionStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.data.IdentifiedJsonScan;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Every shipped ability file scanned the way {@link AbilityLoader} scans it, through a
 * mocked resource manager, decodes into a definition carrying its file id from
 * construction (decision delete-dead-fold-mirrors).
 */
class AbilityLoaderTest {

    private static final String DIRECTORY = "goo_abilities";
    private static final Identifier GLOWING = Identifier.parse("minecraft:glowing");

    @Test
    void everyScannedAbilityCarriesItsFileId() {
        List<Path> files = AbilityJson.files();
        assertFalse(files.isEmpty(), "No ability JSON found under " + AbilityJson.ABILITIES_DIR);

        Map<Identifier, AbilityDefinition> scanned = IdentifiedJsonScan.scan(
                managerListing(files), FileToIdConverter.json(DIRECTORY), JsonOps.INSTANCE,
                AbilityDefinition::codecFor);

        assertEquals(files.size(), scanned.size(), "every ability file decodes");
        for (Path file : files) {
            Identifier fileId = AbilityJson.idOf(file.getFileName().toString());
            assertEquals(fileId, scanned.get(fileId).id(), file.getFileName().toString());
        }
    }

    /**
     * Hex charm and aeon's stasis show their ailment through the overlay
     * step, and neither applies vanilla glowing any more
     * (decision ailment-overlay-shader-per-ailment).
     */
    @ParameterizedTest
    @CsvSource({"hex_charm, HEX", "aeon_time_stop, STASIS"})
    void ailmentAbilitiesWearTheOverlayInPlaceOfGlowing(String name, AilmentKind kind) {
        List<Step> steps = AbilityJson.decode(name).behaviors().stream()
                .flatMap(AbilityLoaderTest::stepTree).toList();

        assertEquals(List.of(kind), steps.stream().filter(AilmentOverlayStep.class::isInstance)
                .map(step -> ((AilmentOverlayStep) step).kind()).toList(), name);
        assertTrue(steps.stream().filter(PotionStep.class::isInstance)
                .noneMatch(step -> GLOWING.equals(((PotionStep) step).effect())), name + " still applies glowing");
    }

    /** A step and every step it holds, depth first. */
    private static Stream<Step> stepTree(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(AbilityLoaderTest::stepTree));
    }

    /**
     * A resource manager whose listing under the ability directory is the given files.
     */
    private static ResourceManager managerListing(List<Path> files) {
        PackResources pack = mock(PackResources.class);
        Map<Identifier, Resource> listing = new HashMap<>();
        for (Path file : files) {
            Identifier path = Identifier.fromNamespaceAndPath(Goo.MODID, DIRECTORY + "/" + file.getFileName());
            listing.put(path, new Resource(pack, () -> Files.newInputStream(file)));
        }
        ResourceManager manager = mock(ResourceManager.class);
        when(manager.listResources(eq(DIRECTORY), any())).thenReturn(listing);
        return manager;
    }
}
