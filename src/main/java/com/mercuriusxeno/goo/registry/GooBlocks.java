package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.ChainMarkerBlock;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.block.ability.MagickedIceBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.gasket.ChoralGasketBlock;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.plexer.PlexerBlock;
import com.mercuriusxeno.goo.block.reactor.ReactorBlock;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.fluid.GooFluidBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import java.util.function.Supplier;

/**
 * Block registry for all goo mod blocks, including machine blocks and fluid blocks.
 */
public class GooBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Goo.MODID);

    /**
     * Chain marker: short-lived fuse block for chain world effects.
     */
    public static final DeferredBlock<ChainMarkerBlock> CHAIN_MARKER = BLOCKS.registerBlock(
            "chain_marker", ChainMarkerBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .noCollision()
                    .instabreak()
                    .noLootTable()
                    .noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    // --- Machine blocks ---
    /**
     * Glow crystal: permanent light source left by glow chain detonation.
     */
    public static final DeferredBlock<GlowCrystalBlock> GLOW_CRYSTAL = BLOCKS.registerBlock(
            "glow_crystal", GlowCrystalBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .noCollision()
                    .instabreak()
                    .noLootTable()
                    .noOcclusion()
                    .sound(SoundType.GLASS)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .lightLevel(GlowCrystalBlock::lightLevel));
    /**
     * Magicked ice: a non-melting mod variant of vanilla ice, placed
     * permanently by the frost cold snap. Visually, audibly, and
     * mechanically identical to {@code minecraft:ice}. Silk-touch drops
     * vanilla ice; the block is not obtainable in its true form.
     */
    public static final DeferredBlock<MagickedIceBlock> MAGICKED_ICE = BLOCKS.registerBlock(
            "magicked_ice", MagickedIceBlock::new,
            () -> BlockBehaviour.Properties.ofFullCopy(Blocks.ICE)
                    .overrideLootTable(Blocks.ICE.getLootTable()));
    /**
     * Indestructible strength value for fluid blocks (matches bedrock).
     */
    private static final float INDESTRUCTIBLE = -1.0F;
    private static final Supplier<BlockBehaviour.Properties> CRUCIBLE_PROPERTY_SUPPLIER = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.NETHER).strength(1.5F).sound(SoundType.NETHER_BRICKS)
            .noOcclusion();
    public static final DeferredBlock<CrucibleBlock> CRUCIBLE = BLOCKS.registerBlock("crucible",
            CrucibleBlock::new, CRUCIBLE_PROPERTY_SUPPLIER);
    private static final Supplier<BlockBehaviour.Properties> HUB_PROPERTY_SUPPLIER = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.NETHER).strength(1.5F).sound(SoundType.NETHER_BRICKS)
            .noOcclusion();
    public static final DeferredBlock<HubBlock> HUB = BLOCKS.registerBlock("hub",
            HubBlock::new, HUB_PROPERTY_SUPPLIER);
    private static final Supplier<BlockBehaviour.Properties> PLEXER_PROPERTY_SUPPLIER = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.NETHER).strength(1.5F).sound(SoundType.NETHERITE_BLOCK)
            .noOcclusion();
    public static final DeferredBlock<PlexerBlock> PLEXER = BLOCKS.registerBlock("plexer",
            PlexerBlock::new, PLEXER_PROPERTY_SUPPLIER);
    private static final Supplier<BlockBehaviour.Properties> REACTOR_PROPERTY_SUPPLIER = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.NETHER).strength(1.5F).sound(SoundType.NETHERITE_BLOCK)
            .noOcclusion();
    /**
     * Reactor: consumes goo from corner canisters, produces output into front hollow.
     */
    public static final DeferredBlock<ReactorBlock> REACTOR = BLOCKS.registerBlock("reactor",
            ReactorBlock::new, REACTOR_PROPERTY_SUPPLIER);
    private static final Supplier<BlockBehaviour.Properties> VAT_PROPERTY_SUPPLIER = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.NETHER).strength(1.5F).sound(SoundType.NETHER_BRICKS)
            .noOcclusion();
    public static final DeferredBlock<VatBlock> VAT = BLOCKS.registerBlock("vat",
            VatBlock::new, VAT_PROPERTY_SUPPLIER);
    private static final Supplier<BlockBehaviour.Properties> TAP_PROPERTY_SUPPLIER = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_ORANGE).strength(1.5F).sound(SoundType.COPPER)
            .noOcclusion();
    public static final DeferredBlock<TapBlock> TAP = BLOCKS.registerBlock("tap",
            TapBlock::new, TAP_PROPERTY_SUPPLIER);
    private static final Supplier<BlockBehaviour.Properties> CANISTER_PROPERTY_SUPPLIER = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.METAL).instabreak().sound(SoundType.METAL)
            .noOcclusion();

    // --- Effect blocks ---
    public static final DeferredBlock<CanisterBlock> CANISTER = BLOCKS.registerBlock("canister",
            CanisterBlock::new, CANISTER_PROPERTY_SUPPLIER);
    private static final Supplier<BlockBehaviour.Properties> CHORAL_GASKET_PROPERTY_SUPPLIER =
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).instabreak().sound(SoundType.AMETHYST)
                    .noOcclusion().noCollision();
    public static final DeferredBlock<ChoralGasketBlock> CHORAL_GASKET_BLOCK = BLOCKS.registerBlock(
            "choral_gasket", ChoralGasketBlock::new, CHORAL_GASKET_PROPERTY_SUPPLIER);
    /**
     * The one goo liquid block; its map color reads the type stamped on its
     * block entity, and STONE stands where nothing is stamped.
     */
    public static final DeferredBlock<GooFluidBlock> GOO_FLUID = BLOCKS.registerBlock(GooFluids.GOO_PATH,
            p -> new GooFluidBlock(GooFluids.SOURCE.get(), p),
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .liquid()
                    .noCollision()
                    .strength(INDESTRUCTIBLE)
                    .noLootTable());
}
