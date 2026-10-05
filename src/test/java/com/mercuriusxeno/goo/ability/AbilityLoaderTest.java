package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.data.IdentifiedJsonScan;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    /** The items each gated ability requires, as the task's how maps the operator's lists. */
    private static final Map<String, List<String>> GATES = Map.ofEntries(
            Map.entry("ender_blink", List.of("ender_pearl")),
            Map.entry("ender_teleport", List.of("popped_chorus_fruit")),
            Map.entry("hex_charm", List.of("honey_bottle", "cake", "cookie")),
            Map.entry("unstable_explode", List.of("gunpowder")),
            Map.entry("unstable_proximity_mine", List.of("tnt")),
            Map.entry("glow_laser", List.of("spectral_arrow")),
            Map.entry("glow_crystal", List.of("glowstone")),
            Map.entry("crystal_cloud", List.of("glass", "sand")),
            Map.entry("crystal_flechettes", List.of("amethyst_shard")),
            Map.entry("blaze_spitfire", List.of("torchflower")),
            Map.entry("blaze_ignite", List.of("flint")),
            Map.entry("aeon_time_stop", List.of("clock")),
            Map.entry("leaf_entangle", List.of("vine")),
            Map.entry("typhoon_levitate", List.of("shulker_shell")),
            Map.entry("typhoon_propel", List.of("phantom_membrane")),
            Map.entry("rock_petrify", List.of("pointed_dripstone")),
            Map.entry("pulse_short_circuit", List.of("redstone")));

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
     * Each shipped ability names the items the operator's lists gate it behind,
     * and an ungated one names none (decision ability-hidden-until-recipes-known).
     */
    @Test
    void everyShippedAbilityRequiresTheItemsItsGateNames() {
        List<Path> files = AbilityJson.files();
        Map<Identifier, AbilityDefinition> scanned = IdentifiedJsonScan.scan(
                managerListing(files), FileToIdConverter.json(DIRECTORY), JsonOps.INSTANCE,
                AbilityDefinition::codecFor);

        for (Path file : files) {
            Identifier fileId = AbilityJson.idOf(file.getFileName().toString());
            List<Identifier> expected = GATES.getOrDefault(fileId.getPath(), List.of()).stream()
                    .map(Identifier::withDefaultNamespace).toList();
            assertEquals(expected, scanned.get(fileId).requires(), fileId.toString());
        }
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
