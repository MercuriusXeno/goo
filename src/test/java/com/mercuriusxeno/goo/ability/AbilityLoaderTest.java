package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.LingerStep;
import com.mercuriusxeno.goo.ability.program.PlaceBlockStep;
import com.mercuriusxeno.goo.ability.program.Step;
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
import java.util.Set;
import java.util.stream.Collectors;
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
    /** The abilities whose whole design was a per-stack shape. */
    /** The world abilities that stay after their blob lands. */
    private static final List<String> LINGERING_ABILITIES = List.of("crystal_cloud", "metal_spikes",
            "nether_black_hole", "unstable_proximity_mine", "glow_crystal");
    /** The world ability whose program ends the tick it lands. */
    private static final String BLAST = "unstable_instant_detonation";
    private static final List<String> STACK_SHAPE_ABILITIES = List.of("blaze_flat", "blaze_tunnel",
            "frost_flat", "frost_tunnel", "frost_sphere", "rock_flat", "rock_tunnel");

    private static Map<Identifier, AbilityDefinition> scanShipped(List<Path> files) {
        return IdentifiedJsonScan.scan(managerListing(files), FileToIdConverter.json(DIRECTORY), JsonOps.INSTANCE,
                AbilityDefinition::codecFor);
    }

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
            Map.entry("blaze_kindle", List.of("magma_cream")),
            Map.entry("leaf_barkskin", List.of("oak_log")),
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

        Map<Identifier, AbilityDefinition> scanned = scanShipped(files);

        assertEquals(files.size(), scanned.size(), "every ability file decodes");
        for (Path file : files) {
            Identifier fileId = AbilityJson.idOf(file.getFileName().toString());
            assertEquals(fileId, scanned.get(fileId).id(), file.getFileName().toString());
        }
    }

    // decision splat-runs-the-program-no-fuse
    @Test
    void stackShapeAbilitiesLeaveTheRegistry() {
        Map<Identifier, AbilityDefinition> scanned = scanShipped(AbilityJson.files());

        assertFalse(scanned.isEmpty(), "No ability scanned");
        for (String name : STACK_SHAPE_ABILITIES) {
            assertFalse(scanned.containsKey(Identifier.fromNamespaceAndPath(Goo.MODID, name)), name + " still loads");
        }
    }

    // decision splat-runs-the-program-no-fuse
    @Test
    void noShippedProgramReadsAStackCount() {
        for (AbilityDefinition ability : scanShipped(AbilityJson.files()).values()) {
            Set<String> read = ability.behaviors().stream()
                    .flatMap(AbilityLoaderTest::withDescendants)
                    .flatMap(Step::expressions)
                    .flatMap(expr -> expr.variables().stream())
                    .collect(Collectors.toSet());
            assertFalse(read.contains("stacks") || read.contains("max_stacks"), ability.id() + " reads " + read);
        }
    }

    // decision lingering-abilities-place-their-own-thing
    @Test
    void eachLingeringAbilityPlacesItsOwnThingAndBlastPlacesNothing() {
        Map<Identifier, AbilityDefinition> scanned = scanShipped(AbilityJson.files());
        for (String name : LINGERING_ABILITIES) {
            AbilityDefinition ability = scanned.get(Identifier.fromNamespaceAndPath(Goo.MODID, name));
            assertTrue(placesItsOwnThing(ability), name + " names no step placing its own thing");
        }
        AbilityDefinition blast = scanned.get(Identifier.fromNamespaceAndPath(Goo.MODID, BLAST));
        assertFalse(placesItsOwnThing(blast), BLAST + " places a thing of its own");
    }

    private static boolean placesItsOwnThing(AbilityDefinition ability) {
        return ability.behaviors().stream().flatMap(AbilityLoaderTest::withDescendants)
                .anyMatch(step -> step instanceof LingerStep || step instanceof PlaceBlockStep);
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(AbilityLoaderTest::withDescendants));
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
