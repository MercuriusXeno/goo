package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooType;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.command.GooTypesCommand;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import java.util.List;

/**
 * Gametests for the {@code goo:goo_type} datapack registry: the bundled
 * types resolve after datapack load, and a folder datapack the gametest
 * server copies into its world (build.gradle passes {@code --packs}) adds a
 * type the registry and the {@code /goo types} listing both hold.
 */
public final class GooTypeRegistryTests {

    /**
     * Namespace of the test-resources datapack under src/test/resources/datapacks.
     */
    private static final String TEST_PACK_NAMESPACE = "gootest";
    /**
     * Id of the type that datapack adds.
     */
    private static final String SEVENTEENTH_PATH = "seventeenth";
    private static final ResourceKey<GooTypeDefinition> SEVENTEENTH = ResourceKey.create(
            GooTypes.REGISTRY, Identifier.fromNamespaceAndPath(TEST_PACK_NAMESPACE, SEVENTEENTH_PATH));
    private static final String MISSING_BUNDLED = "Bundled goo type missing from registry: ";
    private static final String MISSING_BRIDGE = "Enum holder() bridge resolves a different entry than GooTypes for ";
    private static final String MISSING_SEVENTEENTH = "Datapack type missing from registry: ";
    private static final String UNLISTED_SEVENTEENTH = "Datapack type missing from /goo types listing: ";

    private GooTypeRegistryTests() {
    }

    /**
     * Every GooTypes key resolves from the level's registry access, and the
     * enum's holder() bridge lands on the same entry.
     *
     * @param helper the gametest helper
     */
    public static void bundledTypesResolve(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        for (ResourceKey<GooTypeDefinition> key : GooTypes.BUNDLED) {
            helper.assertTrue(registries.get(key).isPresent(), MISSING_BUNDLED + key.identifier());
        }
        for (GooType type : GooType.values()) {
            helper.assertTrue(type.holder(registries).key().equals(type.key()),
                    MISSING_BRIDGE + type.getId());
        }
        helper.succeed();
    }

    /**
     * The seventeenth type the test datapack adds is in the registry and in
     * the ids {@code /goo types} lists.
     *
     * @param helper the gametest helper
     */
    public static void datapackTypeListed(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        helper.assertTrue(registries.get(SEVENTEENTH).isPresent(),
                MISSING_SEVENTEENTH + SEVENTEENTH.identifier());
        HolderLookup.RegistryLookup<GooTypeDefinition> registry = registries.lookupOrThrow(GooTypes.REGISTRY);
        List<String> listed = GooTypesCommand.sortedIds(registry.listElementIds());
        helper.assertTrue(listed.contains(SEVENTEENTH.identifier().toString()),
                UNLISTED_SEVENTEENTH + SEVENTEENTH.identifier());
        helper.succeed();
    }
}
