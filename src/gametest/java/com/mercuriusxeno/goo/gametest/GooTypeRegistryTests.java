package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for the {@code goo:goo_type} datapack registry: the bundled
 * types resolve after datapack load, and a folder datapack the gametest
 * server copies into its world (build.gradle passes {@code --packs}) adds a
 * type the registry and the {@code /goo types} listing both hold.
 */
@RegistersGameTests
public final class GooTypeRegistryTests {
    private static final String TYPES_BUNDLED_RESOLVE = "types_bundled_resolve";
    private static final String TYPES_GLOVE_SELECTION_RELOADS = "types_glove_selection_reloads";
    private static final String SELECTION_TYPE_LOST = "A glove's selection should read back its type after the stack's save and load";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, TYPES_BUNDLED_RESOLVE, GooTypeRegistryTests::bundledTypesResolve);
        GooTestFunctions.registerTest(registrar, TYPES_GLOVE_SELECTION_RELOADS,
                GooTypeRegistryTests::gloveSelectionReloadsItsType);
    }


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
    private static final String CAPTURE_STALE = "GooTypes.order() should be the registry's keys captured at reload";
    private static final String CAPTURE_LACKS_SEVENTEENTH = "GooTypes.order() should hold the datapack type";
    private static final Identifier METAL_SPIKES = Identifier.fromNamespaceAndPath(Goo.MODID, "metal_spikes");

    private GooTypeRegistryTests() {
    }

    /**
     * Every GooTypes key resolves from the level's registry access, and the
     * order the server captured at reload is the registry's, the datapack
     * type included (decision datapack-goo-registry).
     *
     * @param helper the gametest helper
     */
    public static void bundledTypesResolve(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        for (ResourceKey<GooTypeDefinition> key : GooTypes.BUNDLED) {
            helper.assertTrue(registries.get(key).isPresent(), MISSING_BUNDLED + key.identifier());
        }
        helper.assertTrue(GooTypes.order().equals(GooTypes.all(registries)), CAPTURE_STALE);
        helper.assertTrue(GooTypes.order().contains(SEVENTEENTH), CAPTURE_LACKS_SEVENTEENTH);
        helper.succeed();
    }

    /**
     * A real glove stack's selection survives the trip through the item's
     * persistent components: the stack saves to NBT through the level's
     * registry ops and parses back with its goo type selected.
     *
     * @param helper the gametest helper
     */
    public static void gloveSelectionReloadsItsType(GameTestHelper helper) {
        ItemStack glove = new ItemStack(GooItems.GOO_GLOVE.get());
        GooGloveItem.setSelection(glove, GloveSelection.ofAbility(GooTypes.METAL, METAL_SPIKES));
        RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
        Tag saved = ItemStack.CODEC.encodeStart(ops, glove).getOrThrow();
        GloveSelection selection = GooGloveItem.getSelection(ItemStack.CODEC.parse(ops, saved).getOrThrow());
        helper.assertTrue(selection != null && GooTypes.METAL.equals(selection.getGooType()), SELECTION_TYPE_LOST);
        helper.succeed();
    }
}