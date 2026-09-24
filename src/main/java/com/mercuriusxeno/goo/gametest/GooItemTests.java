package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.item.BlobStacks;
import com.mercuriusxeno.goo.network.BlobThrowHandler;
import com.mercuriusxeno.goo.network.BlobThrowPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooCreativeTabs;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import java.util.Collection;
import java.util.Objects;

/**
 * Gametests for the generic goo items (decision generic-goo-items): a blob
 * created for a type throws as that type and lands that type's ability, and
 * the creative tab offers a blob, omniblobs and a bucket for every type the
 * registry holds, a datapack's included.
 */
public final class GooItemTests {

    private static final BlockPos BLAZE_WALL = new BlockPos(1, 1, 1);
    private static final BlockPos ROCK_WALL = new BlockPos(3, 1, 1);
    private static final BlockPos PLAYER_POS = new BlockPos(2, 1, 5);
    private static final Direction THROW_FACE = Direction.SOUTH;
    private static final int NO_TARGET_ENTITY = -1;
    private static final String BLAZE_TUNNEL = "goo:blaze_tunnel";
    private static final String ROCK_TUNNEL = "goo:rock_tunnel";
    private static final String REMOVAL = "removal";
    /**
     * Ticks past the arc from the player to either wall, four blocks at one
     * and a half blocks a tick, and short of the marker's fuse.
     */
    private static final int ARRIVAL_TICKS = 10;

    private static final String TEST_PACK_NAMESPACE = "gootest";
    private static final ResourceKey<GooTypeDefinition> SEVENTEENTH = ResourceKey.create(
            GooTypes.REGISTRY, Identifier.fromNamespaceAndPath(TEST_PACK_NAMESPACE, "seventeenth"));

    private static final String BLOB_NOT_SPENT = "Throwing should spend the blob of the thrown type: ";
    private static final String NO_MARKER = "Thrown blob should land a chain marker beside the wall at ";
    private static final String WRONG_MARKER_TYPE = "Chain marker should carry the thrown blob's type at ";
    private static final String TAB_LACKS_BLOB = "Creative tab should offer a blob of the datapack type";
    private static final String TAB_LACKS_OMNIBLOB = "Creative tab should offer an omniblob of the datapack type";
    private static final String TAB_LACKS_BUCKET = "Creative tab should offer a bucket of the datapack type";

    private GooItemTests() {
    }

    /**
     * A player holding a glove, with one blaze blob and one rock blob made
     * through BlobStacks, throws each at its own stone wall; each throw spends
     * that blob and lands a chain marker of that type beside its wall.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    public static void thrownBlobsLandOwnType(GameTestHelper helper) {
        helper.setBlock(BLAZE_WALL, Blocks.STONE);
        helper.setBlock(ROCK_WALL, Blocks.STONE);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos stand = helper.absolutePos(PLAYER_POS);
        player.setPos(stand.getX(), stand.getY(), stand.getZ());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        ItemStack blaze = BlobStacks.createBlobStack(GooTypes.BLAZE, 1);
        ItemStack rock = BlobStacks.createBlobStack(GooTypes.ROCK, 1);
        player.getInventory().add(blaze);
        player.getInventory().add(rock);

        BlobThrowHandler.execute(player, throwAt(helper, GooTypes.BLAZE, BLAZE_TUNNEL, BLAZE_WALL));
        BlobThrowHandler.execute(player, throwAt(helper, GooTypes.ROCK, ROCK_TUNNEL, ROCK_WALL));
        helper.assertTrue(blaze.isEmpty(), BLOB_NOT_SPENT + GooTypes.id(GooTypes.BLAZE));
        helper.assertTrue(rock.isEmpty(), BLOB_NOT_SPENT + GooTypes.id(GooTypes.ROCK));

        helper.runAfterDelay(ARRIVAL_TICKS, () -> {
            assertMarker(helper, BLAZE_WALL.relative(THROW_FACE), GooTypes.BLAZE);
            assertMarker(helper, ROCK_WALL.relative(THROW_FACE), GooTypes.ROCK);
            helper.succeed();
        });
    }

    /**
     * The goo creative tab, built against the level's registries, holds a
     * blob, an omniblob and a bucket whose GOO_TYPE component names the
     * seventeenth type the test datapack adds.
     *
     * @param helper the gametest helper
     */
    public static void creativeTabOffersDatapackType(GameTestHelper helper) {
        CreativeModeTab tab = GooCreativeTabs.GOO_TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), false, helper.getLevel().registryAccess()));
        Collection<ItemStack> shown = tab.getDisplayItems();
        helper.assertTrue(holdsTyped(shown, GooItems.GOO_BLOB.get(), SEVENTEENTH), TAB_LACKS_BLOB);
        helper.assertTrue(holdsTyped(shown, GooItems.GOO_OMNIBLOB.get(), SEVENTEENTH), TAB_LACKS_OMNIBLOB);
        helper.assertTrue(holdsTyped(shown, GooItems.GOO_BUCKET.get(), SEVENTEENTH), TAB_LACKS_BUCKET);
        helper.succeed();
    }

    private static boolean holdsTyped(Collection<ItemStack> stacks, Item item, ResourceKey<GooTypeDefinition> key) {
        return stacks.stream().anyMatch(stack -> stack.is(item) && key.equals(stack.get(GooDataComponents.GOO_TYPE.get())));
    }

    private static BlobThrowPayload throwAt(GameTestHelper helper, ResourceKey<GooTypeDefinition> type,
                                            String abilityId, BlockPos wall) {
        return new BlobThrowPayload(GooTypes.id(type), NO_TARGET_ENTITY, helper.absolutePos(wall),
                THROW_FACE.ordinal(), false, abilityId);
    }

    private static void assertMarker(GameTestHelper helper, BlockPos pos, ResourceKey<GooTypeDefinition> type) {
        helper.assertTrue(helper.getBlockState(pos).is(GooBlocks.CHAIN_MARKER.get()), NO_MARKER + pos);
        ChainMarkerBlockEntity marker = helper.getBlockEntity(pos, ChainMarkerBlockEntity.class);
        helper.assertTrue(Objects.equals(type, marker.getGooType()), WRONG_MARKER_TYPE + pos);
    }
}
