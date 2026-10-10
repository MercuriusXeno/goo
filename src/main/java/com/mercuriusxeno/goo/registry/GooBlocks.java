package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.AbilityBlock;
import com.mercuriusxeno.goo.block.ability.FungalBudBlock;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.block.ability.MagickedIceBlock;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.block.ability.ZapPulseBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.crystallizer.CrystallizerBlock;
import com.mercuriusxeno.goo.block.gasket.ChoralGasketBlock;
import com.mercuriusxeno.goo.block.gate.DragonGateBlock;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.plexer.PlexerBlock;
import com.mercuriusxeno.goo.block.reactor.ReactorBlock;
import com.mercuriusxeno.goo.block.statue.StatueBlock;
import com.mercuriusxeno.goo.block.tap.TapBlock;
import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.fluid.GooFluidBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IronBarsBlock;
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

    /** Obsidian's hardness. */
    private static final float EXORITE_BARS_HARDNESS = 50.0F;
    /** Obsidian's blast resistance. */
    private static final float EXORITE_BARS_BLAST_RESISTANCE = 1200.0F;
    /** An amethyst cluster's hardness. */
    private static final float PRISM_HARDNESS = 1.5F;
    /** Cobblestone's hardness and blast resistance, which a statue mines like. */
    private static final float COBBLESTONE_HARDNESS = 2.0F;
    private static final float COBBLESTONE_RESISTANCE = 6.0F;

    /**
     * Ability block: short-lived block a world ability runs its program from.
     */
    public static final DeferredBlock<AbilityBlock> ABILITY_BLOCK = BLOCKS.registerBlock(
            "ability_block", AbilityBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .noCollision()
                    .instabreak()
                    .noLootTable()
                    .noOcclusion()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));

    /**
     * Exorite bars: iron bars retextured to exorite and obsidian tier, hardness
     * 50 and blast resistance 1200, dropping only to a diamond pickaxe or better
     * (decision exorite-bars-retextured-iron-bars).
     */
    public static final DeferredBlock<IronBarsBlock> EXORITE_BARS = BLOCKS.registerBlock(
            "exorite_bars", IronBarsBlock::new,
            () -> BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BARS)
                    .strength(EXORITE_BARS_HARDNESS, EXORITE_BARS_BLAST_RESISTANCE)
                    .requiresCorrectToolForDrops());

    // --- Machine blocks ---
    /**
     * Glow crystal: permanent light source a glow ability block places.
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
     * Fungal bud: the colony bud Mycosis leaves on a sprayed floor, ripening
     * on random ticks into a mushroom (decision mycosis-spore-stream-buds-and-poisons).
     */
    public static final DeferredBlock<FungalBudBlock> FUNGAL_BUD = BLOCKS.registerBlock(
            "fungal_bud", FungalBudBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BROWN)
                    .noCollision()
                    .instabreak()
                    .randomTicks()
                    .noLootTable()
                    .noOcclusion()
                    .sound(SoundType.FUNGUS)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));
    /**
     * Zap pulse: the moment of full power Zap stands beside a block with no
     * toggle of its own (decision zap-ticks-the-device-and-stuns).
     */
    public static final DeferredBlock<ZapPulseBlock> ZAP_PULSE = BLOCKS.registerBlock(
            "zap_pulse", ZapPulseBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .noCollision()
                    .instabreak()
                    .noLootTable()
                    .noOcclusion()
                    .replaceable()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));
    /**
     * Prism: the milky quartz crystal Crystal's Prism grows, the host every
     * prism combo grows on (decision prism-blob-becomes-a-milky-quartz-crystal).
     */
    /**
     * Statue: a petrified mob, mined like cobblestone for cobblestone and the
     * mob's experience (decision petrify-stone-encasement-and-calcify-map).
     */
    public static final DeferredBlock<StatueBlock> STATUE = BLOCKS.registerBlock(
            "statue", StatueBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .requiresCorrectToolForDrops()
                    .strength(COBBLESTONE_HARDNESS, COBBLESTONE_RESISTANCE)
                    .noOcclusion()
                    .sound(SoundType.STONE));
    public static final DeferredBlock<PrismBlock> PRISM = BLOCKS.registerBlock(
            "prism", PrismBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ)
                    .strength(PRISM_HARDNESS)
                    .noLootTable()
                    .noOcclusion()
                    .sound(SoundType.AMETHYST_CLUSTER)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));
    /**
     * Dragon Gate: an end portal laid over a block for a while, carrying what
     * steps in to its partner gate; unbreakable, uncollidable and dropping
     * nothing (decision dragon-gate-banishes-blocks-and-opens-a-portal).
     */
    public static final DeferredBlock<DragonGateBlock> DRAGON_GATE = BLOCKS.registerBlock(
            "dragon_gate", DragonGateBlock::new,
            () -> BlockBehaviour.Properties.ofFullCopy(Blocks.END_PORTAL).noLootTable());
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
     * Iceborn's ice: magicked ice an Iceborn player leaves on water, its own
     * block so the level's record knows it still stands frozen when it sends
     * it back to water (decision iceborn-frozen-hearts-thaw-on-fire).
     */
    public static final DeferredBlock<MagickedIceBlock> ICEBORN_ICE = BLOCKS.registerBlock(
            "iceborn_ice", MagickedIceBlock::new,
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
    private static final Supplier<BlockBehaviour.Properties> CRYSTALLIZER_PROPERTY_SUPPLIER = () -> BlockBehaviour.Properties.of()
            .mapColor(MapColor.COLOR_PURPLE).strength(1.5F).sound(SoundType.AMETHYST).noOcclusion();
    /** Phases goo into chrysm (decision crystallizer-emits-chrysm). */
    public static final DeferredBlock<CrystallizerBlock> CRYSTALLIZER = BLOCKS.registerBlock("crystallizer",
            CrystallizerBlock::new, CRYSTALLIZER_PROPERTY_SUPPLIER);
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
