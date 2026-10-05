package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooCreativeTabs;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.DamageResistant;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.Collection;
import java.util.Objects;

/**
 * Gametests for the generic goo items (decision generic-goo-items): a goo
 * created for a type throws as that type and lands that type's ability, and
 * the creative tab offers gooStacks, a one-goo one among them, and a bucket
 * for every type the registry holds, a datapack's included
 * (decision thousands-become-gooStacks).
 */
public final class GooItemTests {

    private static final BlockPos CRYSTAL_WALL = new BlockPos(1, 1, 1);
    private static final BlockPos METAL_WALL = new BlockPos(3, 1, 1);
    private static final BlockPos PLAYER_POS = new BlockPos(2, 1, 5);
    private static final Direction THROW_FACE = Direction.SOUTH;
    private static final int NO_TARGET_ENTITY = -1;
    private static final String CRYSTAL_CLOUD = "goo:crystal_cloud";
    private static final String METAL_SPIKES = "goo:metal_spikes";
    private static final String REMOVAL = "removal";
    /**
     * Ticks past the arc from the player to either wall, four blocks at one
     * and a half blocks a tick, and short of the marker's fuse.
     */
    private static final int ARRIVAL_TICKS = 10;

    private static final String TEST_PACK_NAMESPACE = "gootest";
    private static final ResourceKey<GooTypeDefinition> SEVENTEENTH = ResourceKey.create(
            GooTypes.REGISTRY, Identifier.fromNamespaceAndPath(TEST_PACK_NAMESPACE, "seventeenth"));

    private static final String GOO_NOT_SPENT = "Throwing should spend the goo of the thrown type: ";
    private static final String NO_MARKER = "Thrown goo should land a chain marker beside the wall at ";
    private static final String WRONG_MARKER_TYPE = "Chain marker should carry the thrown goo's type at ";
    private static final String EXO_NOT_FIRE_RESISTANT = "A fresh exo gauntlet should resist fire damage";
    private static final String TAB_LACKS_THOUSAND = "Creative tab should offer a one-goo of the datapack type";
    private static final String TAB_LACKS_GOO = "Creative tab should offer a goo of the datapack type";
    private static final String TAB_LACKS_BUCKET = "Creative tab should offer a bucket of the datapack type";

    private GooItemTests() {
    }

    /**
     * A player holding a glove, with one crystal goo and one metal goo made
     * through GooStacks, throws each at its own stone wall; each throw spends
     * that goo and lands a chain marker of that type beside its wall.
     *
     * @param helper the gametest helper
     */
    public static void thrownGooLandOwnType(GameTestHelper helper) {
        throwGooFrom(helper, GooItems.GOO_GLOVE.get());
    }

    /**
     * The exo gauntlet keeps the goo gauntlet's benefits (decision
     * exo-gauntlet-smithed-with-exorite): a fresh one resists fire, and a
     * player holding it throws thousands that land their own type's chain
     * markers the way the glove's do.
     *
     * @param helper the gametest helper
     */
    public static void exoGauntletKeepsBenefits(GameTestHelper helper) {
        DamageResistant resistant = new ItemStack(GooItems.EXO_GAUNTLET.get()).get(DataComponents.DAMAGE_RESISTANT);
        helper.assertTrue(resistant != null && resistant.types().unwrapKey().filter(DamageTypeTags.IS_FIRE::equals).isPresent(), EXO_NOT_FIRE_RESISTANT);
        throwGooFrom(helper, GooItems.EXO_GAUNTLET.get());
    }

    /**
     * A mock player holding the thrower throws one crystal and one metal goo,
     * each at its own stone wall, then leaves the level once the markers are
     * read, so a later thrower's tracking broadcast never reaches its mock
     * connection, which never negotiated the mod's channels.
     *
     * @param helper  the gametest helper
     * @param thrower the glove or gauntlet the player holds
     */
    @SuppressWarnings(REMOVAL) // vanilla marks the mock server player helper for removal and names no replacement
    private static void throwGooFrom(GameTestHelper helper, Item thrower) {
        helper.setBlock(CRYSTAL_WALL, Blocks.STONE);
        helper.setBlock(METAL_WALL, Blocks.STONE);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos stand = helper.absolutePos(PLAYER_POS);
        player.setPos(stand.getX(), stand.getY(), stand.getZ());
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(thrower));
        ItemStack crystal = GooStacks.createForOutput(GooTypes.CRYSTAL, GooStacks.THOUSAND);
        ItemStack metal = GooStacks.createForOutput(GooTypes.METAL, GooStacks.THOUSAND);
        player.getInventory().add(crystal);
        player.getInventory().add(metal);

        GooThrowHandler.execute(player, throwAt(helper, GooTypes.CRYSTAL, CRYSTAL_CLOUD, CRYSTAL_WALL));
        GooThrowHandler.execute(player, throwAt(helper, GooTypes.METAL, METAL_SPIKES, METAL_WALL));
        helper.assertTrue(crystal.isEmpty(), GOO_NOT_SPENT + GooTypes.id(GooTypes.CRYSTAL));
        helper.assertTrue(metal.isEmpty(), GOO_NOT_SPENT + GooTypes.id(GooTypes.METAL));

        helper.runAfterDelay(ARRIVAL_TICKS, () -> {
            assertMarker(helper, CRYSTAL_WALL.relative(THROW_FACE), GooTypes.CRYSTAL);
            assertMarker(helper, METAL_WALL.relative(THROW_FACE), GooTypes.METAL);
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.succeed();
        });
    }

    /**
     * The goo creative tab, built against the level's registries, holds a
     * one-goo, a goo and a bucket whose GOO_TYPE component names the
     * seventeenth type the test datapack adds.
     *
     * @param helper the gametest helper
     */
    public static void creativeTabOffersDatapackType(GameTestHelper helper) {
        CreativeModeTab tab = GooCreativeTabs.GOO_TAB.get();
        tab.buildContents(new CreativeModeTab.ItemDisplayParameters(
                helper.getLevel().enabledFeatures(), false, helper.getLevel().registryAccess()));
        Collection<ItemStack> shown = tab.getDisplayItems();
        helper.assertTrue(shown.stream().anyMatch(stack -> stack.is(GooItems.GOO.get())
                && SEVENTEENTH.equals(GooStacks.keyOf(stack))
                && GooStacks.volumeOf(stack) == GooStacks.THOUSAND), TAB_LACKS_THOUSAND);
        helper.assertTrue(holdsTyped(shown, GooItems.GOO.get(), SEVENTEENTH), TAB_LACKS_GOO);
        helper.assertTrue(holdsTyped(shown, GooItems.GOO_BUCKET.get(), SEVENTEENTH), TAB_LACKS_BUCKET);
        helper.succeed();
    }

    private static boolean holdsTyped(Collection<ItemStack> stacks, Item item, ResourceKey<GooTypeDefinition> key) {
        return stacks.stream().anyMatch(stack -> stack.is(item) && key.equals(stack.get(GooDataComponents.GOO_TYPE.get())));
    }

    private static GooThrowPayload throwAt(GameTestHelper helper, ResourceKey<GooTypeDefinition> type,
                                            String abilityId, BlockPos wall) {
        return new GooThrowPayload(GooTypes.id(type), NO_TARGET_ENTITY, helper.absolutePos(wall),
                THROW_FACE.ordinal(), false, abilityId, Vec3.ZERO);
    }

    private static void assertMarker(GameTestHelper helper, BlockPos pos, ResourceKey<GooTypeDefinition> type) {
        helper.assertTrue(helper.getBlockState(pos).is(GooBlocks.CHAIN_MARKER.get()), NO_MARKER + pos);
        ChainMarkerBlockEntity marker = helper.getBlockEntity(pos, ChainMarkerBlockEntity.class);
        helper.assertTrue(Objects.equals(type, marker.getGooType()), WRONG_MARKER_TYPE + pos);
    }
}
