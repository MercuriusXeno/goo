package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.CellBinding;
import com.mercuriusxeno.goo.block.ability.FluidFooting;
import com.mercuriusxeno.goo.data.GooGroups;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Blaze's Molten block step: Blaze's molten goo lands where each of the
 * stream's drips strikes, one layer for each drip, through the volume cell's
 * one landing, as Goop lays its slime and Slick its slick; a drip striking a
 * lava source lays its layer on the lava's surface, where the goo pools. Its
 * melts_into map names what the goo melts: each key is the block a melt
 * leaves, and its array what melts into it, loose blocks, #groups and #tags:
 * {@code molten melts_into={minecraft:lava: [minecraft:obsidian, minecraft:magma_block],
 * goo:molten_iron: [goo:iron_core]} heat_ticks=40 damage=4 burn_seconds=15}.
 * A cell melting a block heats it red hot for the heat's ticks before it
 * becomes the key's source block. A living thing touching a cell takes the
 * step's fire damage a hit and burns for its seconds, as vanilla lava's 4
 * damage and 15 seconds.
 * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
 * molten-melts-only-obsidian-magma-and-iron-core-one-layer-each
 * molten-melts-by-a-meltable-group-and-its-parallel-melted-group
 * stone-goes-red-hot-before-molten-turns-it-to-lava
 * melt-blob-absorbs-into-lava
 * molten-burns-mobs-as-hard-as-lava-and-a-hit-spends-a-layer
 * flames-melts-obsidian-magma-and-iron-core-as-molten-does
 * slime-and-slick-are-one-volume-cell-tech
 * drip-creates-a-volume-cell-immediately
 * drip-on-a-standing-cell-increases-it
 *
 * @param meltsInto   each block a melt leaves, with the loose blocks, #groups and #tags that melt into it
 * @param heatTicks   the ticks a melting block stands red hot before it melts
 * @param damage      the fire damage a cell's hit deals a living thing touching it
 * @param burnSeconds the seconds a cell's hit sets the thing alight for
 */
public record MoltenStep(Map<Identifier, List<String>> meltsInto, int heatTicks, float damage, int burnSeconds)
        implements TakesNoEntity, DripLanding {

    private static final String NAME = "molten";
    private static final String FIELD_MELTS_INTO = "melts_into";
    private static final String FIELD_HEAT_TICKS = "heat_ticks";
    private static final String FIELD_DAMAGE = "damage";
    private static final String FIELD_BURN_SECONDS = "burn_seconds";
    private static final String MARK = "#";

    /**
     * Codec for the step's params.
     */
    public static final MapCodec<MoltenStep> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.unboundedMap(Identifier.CODEC, Codec.STRING.listOf()).fieldOf(FIELD_MELTS_INTO)
                    .forGetter(MoltenStep::meltsInto),
            Codec.intRange(1, Integer.MAX_VALUE).fieldOf(FIELD_HEAT_TICKS).forGetter(MoltenStep::heatTicks),
            Codec.floatRange(0, Float.MAX_VALUE).fieldOf(FIELD_DAMAGE).forGetter(MoltenStep::damage),
            Codec.intRange(0, Integer.MAX_VALUE).fieldOf(FIELD_BURN_SECONDS).forGetter(MoltenStep::burnSeconds)
    ).apply(inst, MoltenStep::new));

    /**
     * The registered type.
     */
    public static final StepType<MoltenStep> TYPE = new StepType<>(NAME, CODEC);

    /**
     * The molten step a program carries, the first where it carries several.
     *
     * @param program the ability's steps
     * @return the molten step, empty where the program lays no molten goo
     */
    public static Optional<MoltenStep> in(List<Step> program) {
        return program.stream().filter(MoltenStep.class::isInstance).map(MoltenStep.class::cast).findFirst();
    }

    /**
     * The block a block melts into: the source block of the key whose array names it.
     *
     * @param block the block the goo stands over
     * @return its melted form, empty where nothing melts it
     */
    public Optional<BlockState> meltedFormOf(BlockState block) {
        return meltedFormOf(meltsInto, BuiltInRegistries.BLOCK.getKey(block.getBlock()),
                tag -> block.is(TagKey.create(Registries.BLOCK, tag)), GooGroups.current())
                .flatMap(BuiltInRegistries.BLOCK::getOptional).map(Block::defaultBlockState);
    }

    /**
     * The melts the loaded Molten ability names, the ones Flames melts too, so
     * a datapack changing Molten's melts_into changes Flames with it.
     * flames-melts-obsidian-magma-and-iron-core-as-molten-does
     *
     * @param level the level whose server loads the abilities
     * @return the block each block melts into, empty for every block where Molten does not load
     */
    public static Function<Block, Optional<Block>> meltsIn(Level level) {
        AbilityDefinition molten = AbilityRegistry.of(level).getAbility(CellBinding.MOLTEN_ABILITY);
        return molten == null ? raw -> Optional.empty()
                : in(molten.behaviors()).map(MoltenStep::melts).orElse(raw -> Optional.empty());
    }

    /**
     * The block each block melts into under this step's map, its key's source block.
     *
     * @return the block a block melts into, empty where nothing melts it
     */
    public Function<Block, Optional<Block>> melts() {
        return meltsOf(meltsInto, BuiltInRegistries.BLOCK::getKey,
                (raw, tag) -> raw.defaultBlockState().is(TagKey.create(Registries.BLOCK, tag)), GooGroups.current(),
                BuiltInRegistries.BLOCK::getOptional);
    }

    /**
     * The block each block melts into under a melts_into map, read by block id.
     *
     * @param meltsInto each block a melt leaves, with what melts into it
     * @param idOf      a block's id
     * @param inTag     whether a block stands in a block tag, by the tag's id
     * @param groups    the base values' custom groups by name
     * @param blockOf   the block an id names, empty where none is registered
     * @return the block a block melts into, empty where no array names it
     */
    static Function<Block, Optional<Block>> meltsOf(Map<Identifier, List<String>> meltsInto,
                                                    Function<Block, Identifier> idOf,
                                                    BiPredicate<Block, Identifier> inTag,
                                                    Map<String, List<Identifier>> groups,
                                                    Function<Identifier, Optional<Block>> blockOf) {
        return raw -> meltedFormOf(meltsInto, idOf.apply(raw), tag -> inTag.test(raw, tag), groups)
                .flatMap(blockOf);
    }

    /**
     * The key whose array names a block: as a loose block, a member of a #group the base values hold, or a member of
     * a #tag, a #name no group holds reading as a block tag.
     *
     * @param meltsInto each block a melt leaves, with what melts into it
     * @param block     the block's id
     * @param inTag     whether the block stands in a block tag, by the tag's id
     * @param groups    the base values' custom groups by name
     * @return the block it melts into, empty where no array names it
     */
    static Optional<Identifier> meltedFormOf(Map<Identifier, List<String>> meltsInto, Identifier block,
                                             Predicate<Identifier> inTag, Map<String, List<Identifier>> groups) {
        return meltsInto.entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(named -> names(named, block, inTag, groups)))
                .map(Map.Entry::getKey).findFirst();
    }

    private static boolean names(String named, Identifier block, Predicate<Identifier> inTag,
                                 Map<String, List<Identifier>> groups) {
        if (!named.startsWith(MARK)) {
            return Identifier.parse(named).equals(block);
        }
        String name = named.substring(MARK.length());
        List<Identifier> group = groups.get(name);
        return group != null ? group.contains(block) : inTag.test(Identifier.parse(name));
    }

    @Override
    public StepType<MoltenStep> type() {
        return TYPE;
    }

    /**
     * Runs nothing on the hold's tick: the stream's drips lay the molten goo as they strike.
     *
     * @param context the step's context
     * @return true, the step never fails
     */
    @Override
    public boolean tick(StepContext context) {
        return true;
    }

    @Override
    public Optional<CellFed> cellFed(ChannelHost host, CoatRay.BlockStrike strike) {
        return CellLanding.cellFed(host, strike, GooBlocks.BLAZE_MOLTEN.get());
    }

    @Override
    public void feed(ChannelHost host, CellFed fed) {
        CellLanding.feed(host, fed, GooBlocks.BLAZE_MOLTEN.get());
    }

    /**
     * Molten's drips strike a lava source at its surface, where the goo pools.
     * molten-is-a-goopy-spray-that-turns-stone-below-it-to-lava
     */
    @Override
    public FluidFooting fluidFooting() {
        return CellBinding.MOLTEN.fluidFooting();
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.CHANNEL);
    }
}
