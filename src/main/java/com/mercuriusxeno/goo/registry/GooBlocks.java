package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.block.ability.AbilityBlock;
import com.mercuriusxeno.goo.block.ability.CellBinding;
import com.mercuriusxeno.goo.block.ability.FungalBudBlock;
import com.mercuriusxeno.goo.block.ability.GlowCrystalBlock;
import com.mercuriusxeno.goo.block.ability.HostCellBlock;
import com.mercuriusxeno.goo.block.ability.InfestCellBlock;
import com.mercuriusxeno.goo.block.ability.LightRailBlock;
import com.mercuriusxeno.goo.block.ability.MagickedIceBlock;
import com.mercuriusxeno.goo.block.ability.MoltenCellBlock;
import com.mercuriusxeno.goo.block.ability.NectarCellBlock;
import com.mercuriusxeno.goo.block.ability.PrismBlock;
import com.mercuriusxeno.goo.block.ability.RimeCellBlock;
import com.mercuriusxeno.goo.block.ability.SignalCellBlock;
import com.mercuriusxeno.goo.block.ability.SpikesCellBlock;
import com.mercuriusxeno.goo.block.ability.UpdraftCellBlock;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.block.ability.WispBlock;
import com.mercuriusxeno.goo.block.ability.ZapPulseBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlock;
import com.mercuriusxeno.goo.block.gasket.ChoralGasketBlock;
import com.mercuriusxeno.goo.block.hub.HubBlock;
import com.mercuriusxeno.goo.block.plexer.PlexerBlock;
import com.mercuriusxeno.goo.block.reactor.ReactorBlock;
import com.mercuriusxeno.goo.block.statue.StatueBlock;
import com.mercuriusxeno.goo.block.vat.VatBlock;
import com.mercuriusxeno.goo.block.zone.DimensionDoorBlock;
import com.mercuriusxeno.goo.fluid.GooFluidBlock;
import com.mercuriusxeno.goo.fluid.MoltenIronBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.IceBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TintedGlassBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
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
    /** The hardness no hand breaks, bedrock's own. */
    private static final float UNBREAKABLE = -1.0F;
    /** Bedrock's blast resistance. */
    private static final float UNBLASTABLE = 3_600_000.0F;
    /** The faint light voidstone gives. */
    private static final int VOIDSTONE_GLOW = 3;
    /** The light the dimension door gives, enough to find it in the dark. */
    private static final int DOOR_GLOW = 10;

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
    /**
     * A wisp of light Wisps leaves in dark air: lit, replaceable, without
     * shape or collision, fading on its own (decision radiant-wisps-where-light-is-low).
     */
    public static final DeferredBlock<WispBlock> WISP = BLOCKS.registerBlock(
            "wisp", WispBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .noCollision()
                    .noOcclusion()
                    .noLootTable()
                    .replaceable()
                    .instabreak()
                    .sound(SoundType.AMETHYST_CLUSTER)
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .lightLevel(WispBlock::lightLevel));
    /**
     * One cell of the light rail between linked reflector prisms: lit,
     * replaceable, shapeless and without collision, burning the undead that
     * cross it (decision reflector-rails-carry-the-brightest-light).
     */
    public static final DeferredBlock<LightRailBlock> LIGHT_RAIL = BLOCKS.registerBlock(
            "light_rail", LightRailBlock::new,
            () -> BlockBehaviour.Properties.of()
                    .noCollision()
                    .noOcclusion()
                    .noLootTable()
                    .replaceable()
                    .instabreak()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .lightLevel(LightRailBlock::lightLevel));
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
                    .lightLevel(state -> GlowCrystalBlock.LIGHT_LEVEL));
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
     * Slime coat: the volume cell bound to Weird's slime, the layered green
     * slime Goop lays on a block's top, one to fifteen layers, bouncing what
     * lands on it, standing until destroyed and then dropping the weird goo
     * its layers cost through VolumeCellBlock.getDrops, no loot table
     * (decisions coat-is-a-green-slime-film-over-the-blocks-own-texture,
     * slime-and-slick-are-one-volume-cell-tech, breaking-slime-drops-weird-goo-worth-its-layers).
     */
    public static final DeferredBlock<VolumeCellBlock> SLIME_COAT = BLOCKS.registerBlock(
            "slime_coat", properties -> new VolumeCellBlock(properties, CellBinding.SLIME),
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GRASS)
                    .friction(CellBinding.SLIME.surface().friction())
                    .sound(SoundType.SLIME_BLOCK)
                    .instabreak()
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY));
    /**
     * Slick: the volume cell bound to Kinetic's slick, the electric blue and
     * white goo Slick lays on a block's top, one to fifteen layers, slippery
     * as ice and ramping what moves on it, dropping the kinetic goo its layers
     * cost through VolumeCellBlock.getDrops, no loot table
     * (decisions slick-coats-the-ground-with-kinetic-speed, kinetic-is-electric-blue-and-white,
     * slime-and-slick-are-one-volume-cell-tech, breaking-slick-drops-kinetic-goo-worth-its-film).
     */
    public static final DeferredBlock<VolumeCellBlock> KINETIC_SLICK = BLOCKS.registerBlock(
            "kinetic_slick", properties -> new VolumeCellBlock(properties, CellBinding.SLICK),
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_BLUE)
                    .friction(CellBinding.SLICK.surface().friction())
                    .sound(SoundType.SLIME_BLOCK)
                    .instabreak()
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY));
    /**
     * Molten: the volume cell bound to Blaze's molten goo, the orange goo
     * Molten sprays onto a block's top or a lava source's surface, one to
     * fifteen layers, ordinary ground underfoot that burns a living thing
     * touching it as lava does, dropping the blaze goo its layers cost through
     * VolumeCellBlock.getDrops, no loot table
     * (decisions molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava,
     * molten-burns-mobs-as-hard-as-lava-and-a-hit-spends-a-layer,
     * each-goo-type-ships-a-world-goo-on-the-volume-cell).
     */
    public static final DeferredBlock<MoltenCellBlock> BLAZE_MOLTEN = BLOCKS.registerBlock(
            "blaze_molten", properties -> new MoltenCellBlock(properties, CellBinding.MOLTEN),
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.FIRE)
                    .friction(CellBinding.MOLTEN.surface().friction())
                    .sound(SoundType.SLIME_BLOCK)
                    .instabreak()
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY));
    /**
     * Shatter: the volume cell bound to Crystal's shatter goo, the pale blue
     * goo Shatter sprays onto a block's top, one to fifteen layers, ordinary
     * ground underfoot with glass shards hanging over it, dropping the crystal
     * goo its layers cost through VolumeCellBlock.getDrops, no loot table
     * (decisions shatter-is-splattered-goo-and-shards-form-over-the-splatters,
     * each-goo-type-ships-a-world-goo-on-the-volume-cell).
     */
    public static final DeferredBlock<VolumeCellBlock> CRYSTAL_SHATTER = BLOCKS.registerBlock(
            "crystal_shatter", properties -> new VolumeCellBlock(properties, CellBinding.SHATTER),
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .friction(CellBinding.SHATTER.surface().friction())
                    .sound(SoundType.SLIME_BLOCK)
                    .instabreak()
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY));
    /**
     * Host: the volume cell bound to Deep's sculky slime, the dark teal goo
     * Host sprays onto a block's top, one to fifteen layers, ordinary ground
     * underfoot that sounds as goo does, dropping the deep goo its layers
     * cost through VolumeCellBlock.getDrops, no loot table
     * (decisions host-is-a-sculky-slime-that-sculk-spreads-eagerly-through,
     * each-goo-type-ships-a-world-goo-on-the-volume-cell).
     */
    public static final DeferredBlock<HostCellBlock> DEEP_HOST = BLOCKS.registerBlock(
            "deep_host", properties -> new HostCellBlock(properties, CellBinding.HOST),
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .friction(CellBinding.HOST.surface().friction())
                    .sound(SoundType.SLIME_BLOCK)
                    .instabreak()
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY));
    /**
     * Rime: the volume cell bound to Frost's endothermic goo, the pale blue
     * goo Rime sprays onto a block's top, one to fifteen layers, as slippery
     * as ice underfoot and sounding as goo does, dropping the frost goo its
     * layers cost through VolumeCellBlock.getDrops, no loot table
     * (decisions iceborn-is-dropped-for-rime-a-frost-goo-that-keeps-what-is-around-it-frozen,
     * rime-goo-is-slippery-to-walk-on, each-goo-type-ships-a-world-goo-on-the-volume-cell).
     */
    public static final DeferredBlock<RimeCellBlock> FROST_RIME = BLOCKS.registerBlock(
            "frost_rime", properties -> new RimeCellBlock(properties, CellBinding.RIME),
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.ICE)
                    .friction(CellBinding.RIME.surface().friction())
                    .sound(SoundType.SLIME_BLOCK)
                    .instabreak()
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY));
    /**
     * Wisps: the volume cell bound to Glow's goo, the golden goo Wisps sprays
     * onto a block's top, one to fifteen layers, ordinary ground underfoot,
     * shining at a light level equal to its layers, dropping the glow goo its
     * layers cost through VolumeCellBlock.getDrops, no loot table
     * (decisions glow-world-goo-is-named-wisps-and-takes-the-holds-place,
     * wisps-goo-glows-at-a-light-level-equal-to-its-layers,
     * each-goo-type-ships-a-world-goo-on-the-volume-cell).
     */
    public static final DeferredBlock<VolumeCellBlock> GLOW_WISPS = BLOCKS.registerBlock(
            "glow_wisps", properties -> new VolumeCellBlock(properties, CellBinding.WISPS),
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .friction(CellBinding.WISPS.surface().friction())
                    .sound(SoundType.SLIME_BLOCK)
                    .instabreak()
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY)
                    .lightLevel(state -> state.getValue(VolumeCellBlock.LAYERS)));
    /**
     * Leech: the volume cell bound to Hex's goo, the violet goo Leech sprays
     * onto a block's top, one to fifteen layers, ordinary ground underfoot
     * that slows what it catches and sounds as goo does, dropping the
     * hex goo its layers cost through VolumeCellBlock.getDrops, no loot table
     * (decisions leech-goo-slows-what-it-catches-and-the-spirits-damage-it,
     * each-goo-type-ships-a-world-goo-on-the-volume-cell).
     */
    public static final DeferredBlock<VolumeCellBlock> HEX_LEECH = BLOCKS.registerBlock(
            "hex_leech", properties -> new VolumeCellBlock(properties, CellBinding.LEECH),
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .friction(CellBinding.LEECH.surface().friction())
                    .sound(SoundType.SLIME_BLOCK)
                    .instabreak()
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY));
    /**
     * Updraft: the volume cell bound to Typhoon's goo, the pale green goo
     * Updraft sprays onto a block's top, one to fifteen layers, ordinary
     * ground underfoot and sounding as goo does, lifting what stands above it
     * a block for each layer, dropping the typhoon goo its layers cost
     * through VolumeCellBlock.getDrops, no loot table
     * (decisions updraft-is-typhoons-world-goo-that-lifts-the-entities-above-it,
     * updraft-goo-lifts-one-block-per-layer, each-goo-type-ships-a-world-goo-on-the-volume-cell).
     */
    public static final DeferredBlock<UpdraftCellBlock> TYPHOON_UPDRAFT = BLOCKS.registerBlock(
            "typhoon_updraft", properties -> new UpdraftCellBlock(properties, CellBinding.UPDRAFT),
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .friction(CellBinding.UPDRAFT.surface().friction())
                    .sound(SoundType.SLIME_BLOCK)
                    .instabreak()
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY));
    /**
     * Nectar: the volume cell bound to Jelly's goo, the orange goo Nectar
     * sprays onto a block's top, one to fifteen layers, ordinary ground
     * underfoot and sounding as goo does, a food the mobs about it eat a
     * layer at a time or fight over, dropping the jelly goo its layers cost
     * through VolumeCellBlock.getDrops, no loot table
     * (decisions nectar-is-sprayed-goo-with-a-guided-throw-near-a-mob,
     * nectar-goo-keeps-feeds-effects-as-a-universal-food,
     * each-goo-type-ships-a-world-goo-on-the-volume-cell).
     */
    public static final DeferredBlock<NectarCellBlock> JELLY_NECTAR = BLOCKS.registerBlock(
            "jelly_nectar", properties -> new NectarCellBlock(properties, CellBinding.NECTAR),
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_ORANGE)
                    .friction(CellBinding.NECTAR.surface().friction())
                    .sound(SoundType.SLIME_BLOCK)
                    .instabreak()
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY));
    /**
     * Signal: the volume cell bound to Pulse's goo, the red goo Signal sprays
     * onto a block's top, one to fifteen layers, ordinary ground underfoot
     * and sounding as goo does, sending a redstone signal equal to its layers
     * as a redstone block sends its own, carried by no block it lies against,
     * dropping the pulse goo its layers cost through VolumeCellBlock.getDrops,
     * no loot table (decisions extend-is-dropped-for-signal-an-in-world-pulse-goo,
     * signal-goo-sends-a-redstone-signal-equal-to-its-layers,
     * each-goo-type-ships-a-world-goo-on-the-volume-cell).
     */
    public static final DeferredBlock<SignalCellBlock> PULSE_SIGNAL = BLOCKS.registerBlock(
            "pulse_signal", properties -> new SignalCellBlock(properties, CellBinding.SIGNAL),
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .friction(CellBinding.SIGNAL.surface().friction())
                    .sound(SoundType.SLIME_BLOCK)
                    .instabreak()
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .isRedstoneConductor((state, level, pos) -> false)
                    .pushReaction(PushReaction.DESTROY));
    /**
     * Spikes: the volume cell bound to Metal's goo, the silver goo Spikes
     * sprays onto a block's top, one to fifteen layers, ordinary ground
     * underfoot that impales what walks on it and sounds as goo does,
     * dropping the metal goo its layers cost through VolumeCellBlock.getDrops,
     * no loot table
     * (decisions spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it,
     * each-goo-type-ships-a-world-goo-on-the-volume-cell).
     */
    public static final DeferredBlock<SpikesCellBlock> METAL_SPIKES = BLOCKS.registerBlock(
            "metal_spikes", properties -> new SpikesCellBlock(properties, CellBinding.SPIKES),
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .friction(CellBinding.SPIKES.surface().friction())
                    .sound(SoundType.SLIME_BLOCK)
                    .instabreak()
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY));
    /**
     * Infest: the volume cell bound to Nether's goo, the dark red goo Infest
     * sprays onto a block's top, one to fifteen layers, ordinary ground
     * underfoot and sounding as goo does, gnawing what it lies on and biting
     * what stands in it, dropping the nether goo its layers cost through
     * VolumeCellBlock.getDrops, no loot table
     * (decisions infest-is-nethers-goo-the-decay-effect-localized-around-the-goo,
     * infest-gnaws-the-blocks-it-lies-on-and-bites-what-stands-in-it,
     * each-goo-type-ships-a-world-goo-on-the-volume-cell).
     */
    public static final DeferredBlock<InfestCellBlock> NETHER_INFEST = BLOCKS.registerBlock(
            "nether_infest", properties -> new InfestCellBlock(properties, CellBinding.INFEST),
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.NETHER)
                    .friction(CellBinding.INFEST.surface().friction())
                    .sound(SoundType.SLIME_BLOCK)
                    .instabreak()
                    .noOcclusion()
                    .replaceable()
                    .noLootTable()
                    .pushReaction(PushReaction.DESTROY));
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
                    .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)
                    .lightLevel(PrismBlock::lightLevel));
    /**
     * Voidstone: the unbreakable block at the center of each Zone room,
     * the one block there nothing can take away (decision
     * outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform).
     */
    public static final DeferredBlock<Block> VOIDSTONE = BLOCKS.registerSimpleBlock("voidstone",
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(UNBREAKABLE, UNBLASTABLE)
                    .noLootTable().pushReaction(PushReaction.BLOCK).lightLevel(state -> VOIDSTONE_GLOW));

    /**
     * The dimension door standing in each Zone room, which leads back
     * where the player came in from (decision
     * outsider-is-a-channeled-pocket-with-a-door-voidstone-and-a-spiral-platform).
     */
    public static final DeferredBlock<DimensionDoorBlock> DIMENSION_DOOR = BLOCKS.registerBlock(
            "dimension_door", DimensionDoorBlock::new,
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_PURPLE).strength(UNBREAKABLE, UNBLASTABLE)
                    .noCollision().noOcclusion().noLootTable().pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> DOOR_GLOW));

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
     * Rime ice: magicked ice Rime goo leaves on the water in its reach, its
     * own block so the level's record knows it still stands frozen when it
     * sends it back to water once no Rime goo stands in reach
     * (decision rime-freezes-water-and-lava-and-freezes-and-damages-the-shoeless).
     */
    public static final DeferredBlock<MagickedIceBlock> RIME_ICE = BLOCKS.registerBlock(
            "rime_ice", MagickedIceBlock::new,
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

    // --- Lunar matter (decision lunar-matter-is-anorthosite-basalt-regolith-and-an-iron-core) ---
    /** Stone's hardness and blast resistance, which anorthosite mines like. */
    private static final float STONE_HARDNESS = 1.5F;
    private static final float STONE_RESISTANCE = 6.0F;
    /** Sand's hardness, which regolith digs like. */
    private static final float SAND_HARDNESS = 0.5F;
    /** An iron block's hardness and blast resistance, which the iron core mines like. */
    private static final float IRON_BLOCK_HARDNESS = 5.0F;
    private static final float IRON_BLOCK_RESISTANCE = 6.0F;
    /** Glass's hardness, which impact glass breaks like. */
    private static final float GLASS_HARDNESS = 0.3F;
    /** Lava's light and its blast resistance. */
    private static final int MOLTEN_IRON_LIGHT = 15;
    private static final float MOLTEN_IRON_RESISTANCE = 100.0F;

    /** Anorthosite: lunar's pale feldspar highland stone. */
    public static final DeferredBlock<Block> ANORTHOSITE = BLOCKS.registerSimpleBlock("anorthosite",
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).requiresCorrectToolForDrops()
                    .strength(STONE_HARDNESS, STONE_RESISTANCE).sound(SoundType.STONE));

    /** Regolith: lunar's grey glass dust over the stone. */
    public static final DeferredBlock<Block> REGOLITH = BLOCKS.registerSimpleBlock("regolith",
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_GRAY).strength(SAND_HARDNESS)
                    .sound(SoundType.SAND));

    /** The iron core: the solid iron at lunar's heart, mined like an iron block. */
    public static final DeferredBlock<Block> IRON_CORE = BLOCKS.registerSimpleBlock("iron_core",
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops()
                    .strength(IRON_BLOCK_HARDNESS, IRON_BLOCK_RESISTANCE).sound(SoundType.METAL));

    /**
     * Meteoric iron: the iron-nickel of fallen meteorites, veined through lunar's stone, mined like a raw iron block.
     * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
     */
    public static final DeferredBlock<Block> METEORIC_IRON = BLOCKS.registerSimpleBlock("meteoric_iron",
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.RAW_IRON).requiresCorrectToolForDrops()
                    .strength(IRON_BLOCK_HARDNESS, IRON_BLOCK_RESISTANCE).sound(SoundType.METAL));

    /**
     * Impact glass: lunar's regolith melted where meteorites struck, dark glass that holds back light as tinted
     * glass does, mined like glass.
     * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
     */
    public static final DeferredBlock<TintedGlassBlock> IMPACT_GLASS = BLOCKS.registerBlock("impact_glass",
            TintedGlassBlock::new, () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK)
                    .strength(GLASS_HARDNESS).sound(SoundType.GLASS).noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false).isRedstoneConductor(GooBlocks::never)
                    .isSuffocating(GooBlocks::never).isViewBlocking(GooBlocks::never));

    /**
     * Breccia: rubble an impact fused into rock, anorthosite and basalt shards set in a dark matrix, ringing lunar's
     * crater rims; mined like stone.
     * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
     */
    public static final DeferredBlock<Block> BRECCIA = BLOCKS.registerSimpleBlock("breccia",
            () -> BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).requiresCorrectToolForDrops()
                    .strength(STONE_HARDNESS, STONE_RESISTANCE).sound(SoundType.TUFF));

    /**
     * Crater ice: the ice lying in the floors of lunar's deepest craters, its only water; it melts and breaks to water
     * as vanilla ice does.
     * decision lunar-ground-gains-meteoric-iron-impact-glass-breccia-and-crater-ice
     */
    public static final DeferredBlock<IceBlock> CRATER_ICE = BLOCKS.registerBlock("crater_ice", IceBlock::new,
            () -> BlockBehaviour.Properties.ofFullCopy(Blocks.ICE));

    /** Molten iron's liquid block, glowing as lava does. */
    public static final DeferredBlock<LiquidBlock> MOLTEN_IRON = BLOCKS.registerBlock(GooFluids.MOLTEN_IRON_PATH,
            p -> new MoltenIronBlock(GooFluids.MOLTEN_IRON_SOURCE.get(), p),
            () -> BlockBehaviour.Properties.of()
                    .mapColor(MapColor.FIRE)
                    .replaceable()
                    .noCollision()
                    .strength(MOLTEN_IRON_RESISTANCE)
                    .pushReaction(PushReaction.DESTROY)
                    .noLootTable()
                    .liquid()
                    .sound(SoundType.EMPTY)
                    .lightLevel(state -> MOLTEN_IRON_LIGHT));

    /**
     * A state predicate answering false at every position, as vanilla's glass reads for its view and its weight.
     *
     * @param state the block state
     * @param level the level holding it
     * @param pos   its position
     * @return false
     */
    private static boolean never(BlockState state, BlockGetter level, BlockPos pos) {
        return false;
    }
}
