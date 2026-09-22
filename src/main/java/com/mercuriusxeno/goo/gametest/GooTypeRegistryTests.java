package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.command.GooTypesCommand;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
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
    private static final String CAPTURE_STALE = "GooTypes.order() should be the registry's keys captured at reload";
    private static final String CAPTURE_LACKS_SEVENTEENTH = "GooTypes.order() should hold the datapack type";
    private static final String MARKER_TYPE_LOST = "Chain marker should reload with the type it saved";
    private static final String SELECTION_TYPE_LOST = "Glove selection should reload with the datapack type it saved";
    private static final BlockPos MARKER_POS = new BlockPos(1, 1, 1);
    private static final String MISSING_SEVENTEENTH = "Datapack type missing from registry: ";
    private static final String UNLISTED_SEVENTEENTH = "Datapack type missing from /goo types listing: ";

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
     * A chain marker saves its type as an id and loads it back by key: the
     * marker's tag, read into a fresh marker, carries the type it was built
     * with.
     *
     * @param helper the gametest helper
     */
    public static void chainMarkerReloadsType(GameTestHelper helper) {
        helper.setBlock(MARKER_POS, GooBlocks.CHAIN_MARKER.get());
        ChainMarkerBlockEntity marker = helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class);
        marker.initChain(GooTypes.FROST, Direction.UP);
        CompoundTag saved = marker.getUpdateTag(helper.getLevel().registryAccess());

        helper.destroyBlock(MARKER_POS);
        helper.setBlock(MARKER_POS, GooBlocks.CHAIN_MARKER.get());
        ChainMarkerBlockEntity restored = helper.getBlockEntity(MARKER_POS, ChainMarkerBlockEntity.class);
        try (var reporter = new ProblemReporter.ScopedCollector(restored.problemPath(), Goo.LOGGER)) {
            restored.loadCustomOnly(TagValueInput.create(reporter, helper.getLevel().registryAccess(), saved));
        }
        helper.assertTrue(GooTypes.FROST.equals(restored.getGooType()), MARKER_TYPE_LOST);
        helper.succeed();
    }

    /**
     * A glove selection of the datapack type survives the trip through the
     * item's persistent components: the stack saves to NBT and parses back
     * with the seventeenth type selected.
     *
     * @param helper the gametest helper
     */
    public static void gloveSelectionReloadsType(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        ItemStack glove = new ItemStack(GooItems.GOO_GLOVE.get());
        GooGloveItem.setSelection(glove, GloveSelection.ofType(SEVENTEENTH));
        RegistryOps<Tag> ops = registries.createSerializationContext(NbtOps.INSTANCE);
        Tag saved = ItemStack.CODEC.encodeStart(ops, glove).getOrThrow();
        ItemStack loaded = ItemStack.CODEC.parse(ops, saved).getOrThrow();
        GloveSelection selection = GooGloveItem.getSelection(loaded);
        helper.assertTrue(selection != null && SEVENTEENTH.equals(selection.getGooType()), SELECTION_TYPE_LOST);
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
