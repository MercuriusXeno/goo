package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.*;
import com.mercuriusxeno.goo.ability.AbilityDefinition.ChainConfig;
import com.mercuriusxeno.goo.ability.program.FieldEffectState;
import com.mercuriusxeno.goo.ability.program.PhasedState;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.GooSyncedBlockEntity;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.network.ChainBurnoutPayload;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Ticking block entity for chain effects. Owns only the shared state:
 * goo type, stack count and placed face. Its work is the marker's ability
 * program, a {@link ProgramBehavior} loaded for the marker host and run
 * from the tick its blob splats (decision splat-runs-the-program-no-fuse).
 */
public class ChainMarkerBlockEntity extends GooSyncedBlockEntity {

    private static final String TAG_GOO_TYPE = "goo_type";
    private static final String TAG_STACK_COUNT = "StackCount";
    private static final String TAG_MAX_STACKS = "MaxStacks";
    private static final String TAG_PLACED_FACE = "PlacedFace";
    /**
     * Default goo type id when loading from NBT.
     */
    private static final String DEFAULT_GOO_TYPE = "rock";
    /**
     * Default face name when loading from NBT.
     */
    private static final String DEFAULT_FACE = "up";
    private static final String TAG_LAST_STACK_TICK = "LastStackTick";
    private static final String TAG_ABILITY_ID = "AbilityId";
    private static final String TAG_CONSUMED_GOO = "ConsumedGoo";

    /**
     * Where a client-side marker reads its ability's steps; client setup
     * installs the synced abilities' source.
     */
    private static MarkerStepSource clientSteps = MarkerStepSource.NONE;

    private ResourceKey<GooTypeDefinition> gooType = GooTypes.ROCK;
    private final ChainMarkerStacks stacks = new ChainMarkerStacks();
    private Direction placedFace = Direction.UP;
    /**
     * Game tick when the last stack was added (for client pulse animation).
     */
    private long lastStackTick;
    /**
     * State a running field effect keeps through the marker host: strikes
     * in flight, cooldown and charges spent, read back by the spike visual.
     */
    private final FieldEffectState fieldEffect = new FieldEffectState();
    /**
     * Phase cursor a running phased step keeps through the marker host,
     * read back by the black-hole visual.
     */
    private final PhasedState phased = new PhasedState();
    /**
     * Goo a running black hole consumed from the blocks around it, dropped
     * as goo when it pops or when the marker is broken first.
     */
    private GooContents consumedGoo = GooContents.EMPTY;
    /**
     * The running ability program; null until the blob splats or when this
     * side holds no such ability. Nulled out implicitly when the BE removes
     * itself.
     */
    @Nullable
    private ProgramBehavior behavior;
    /**
     * Id of the ability the marker runs as its blob splats (decision
     * no-throw-without-ability); a marker loaded without one runs nothing.
     */
    private String abilityId = "";

    /**
     * Creates a chain marker block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public ChainMarkerBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.CHAIN_MARKER.get(), pos, state);
    }

    /**
     * Server tick: runs the program one tick, removing the marker once it
     * ends or when no program runs.
     *
     * @param level the current level
     * @param pos   the block position
     * @param state the block state
     * @param be    the block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ChainMarkerBlockEntity be) {
        ServerLevel server = (ServerLevel) level;
        if (be.behavior == null) {
            server.removeBlock(pos, false);
            return;
        }
        be.behavior.serverTick(server, pos, be);
        if (!be.behavior.isActive()) {
            server.removeBlock(pos, false);
            return;
        }
        be.setChanged();
        BlockEntitySync.markDirtyAndSync(be);
    }

    /**
     * Configures this marker from a data-driven ability definition.
     *
     * @param type    the goo type
     * @param face    the placed face
     * @param ability the ability definition
     */
    public void initChainFromAbility(ResourceKey<GooTypeDefinition> type, Direction face, AbilityDefinition ability) {
        ChainConfig chain = ability.chain();
        this.gooType = type;
        this.placedFace = face;
        stacks.arm(chain);
        this.abilityId = ability.id().toString();
        setChanged();
    }

    /**
     * Resolves the marker the tick its blob splats: announces the burnout,
     * runs the program's first tick, and removes the marker when the program
     * ends that tick (decision splat-runs-the-program-no-fuse).
     */
    public void splat() {
        if (level instanceof ServerLevel server) {
            ChainMarkerSplat.resolve(new SplattingMarker(server, worldPosition));
        }
    }

    /**
     * Attempts to increment the stack count under the ability's stack
     * ceiling, while the running program takes a top-off; the program reads
     * the new count through {@code stacks}.
     *
     * @return true if the stack count was incremented
     */
    public boolean tryStack() {
        ChainConfig chain = abilityChain();
        if (refusesTopOff() || chain == null || !stacks.addStack(chain)) {
            return false;
        }
        lastStackTick = level != null ? level.getGameTime() : 0;
        setChanged();
        BlockEntitySync.markDirtyAndSync(this);
        return true;
    }

    /**
     * Returns true when a standing behavior takes no more goo.
     *
     * @return true if a behavior stands and refuses a top-off
     */
    private boolean refusesTopOff() {
        return behavior != null && !behavior.allowsTopOff();
    }

    /**
     * Decrements the stack count by one, for a program step that spends
     * stacks as charges. Syncs to client.
     */
    public void decrementStack() {
        if (stacks.stackCount() > 0) {
            stacks.spendStack();
            setChanged();
            BlockEntitySync.markDirtyAndSync(this);
        }
    }

    /**
     * Restores the state a marker carried through a fall, its running
     * program included. Called by {@link ChainMarkerFallScheduler} after the
     * flight animation completes.
     *
     * @param snapshot the state taken when the support broke
     */
    public void restoreFromFall(ChainMarkerSnapshot snapshot) {
        if (level == null) {
            return;
        }
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(problemPath(),
                Goo.LOGGER)) {
            loadCustomOnly(TagValueInput.create(reporter, level.registryAccess(), snapshot.saved()));
        }
        setChanged();
        BlockEntitySync.markDirtyAndSync(this);
    }

    /**
     * Reads the chain block of the ability this marker runs.
     *
     * @return the chain block, or null when the registry holds no such ability
     */
    private @Nullable ChainConfig abilityChain() {
        AbilityDefinition def = ability();
        return def != null ? def.chain() : null;
    }

    /**
     * Resolves the ability this marker runs through the registry.
     *
     * @return the ability, or null when the id names none the registry holds
     */
    private @Nullable AbilityDefinition ability() {
        Identifier id = Identifier.tryParse(abilityId);
        return id != null && level != null ? AbilityRegistry.of(level).getAbility(id) : null;
    }

    /**
     * Returns the game tick when the last goo was stacked.
     *
     * @return the game tick of the last stack event
     */
    public long getLastStackTick() {
        return lastStackTick;
    }

    /**
     * Returns the state a running field effect keeps on this marker; the
     * field-effect step mutates it through the marker host each tick, and
     * the active behavior's tick marks and syncs the entity afterwards.
     *
     * @return the live field-effect state
     */
    public FieldEffectState getFieldEffect() {
        return fieldEffect;
    }

    /**
     * Returns the phase cursor a running phased step keeps on this marker;
     * the phased step mutates it through the marker host each tick, and
     * the active behavior's tick marks and syncs the entity afterwards.
     *
     * @return the live phased state
     */
    public PhasedState getPhased() {
        return phased;
    }

    /**
     * Returns the goo consumed from the blocks around this marker and not
     * yet dropped.
     *
     * @return the consumed goo
     */
    public GooContents getConsumedGoo() {
        return consumedGoo;
    }

    /**
     * Adds goo consumed from the blocks around this marker to its total.
     *
     * @param consumed the goo just consumed
     */
    public void addConsumedGoo(GooContents consumed) {
        consumedGoo = consumedGoo.mergeWith(consumed);
    }

    /**
     * Empties the consumed goo total, handing back what it held.
     *
     * @return the goo consumed so far
     */
    public GooContents takeConsumedGoo() {
        GooContents taken = consumedGoo;
        consumedGoo = GooContents.EMPTY;
        return taken;
    }

    /**
     * Removes the marker block, when the block at its position is still a marker.
     *
     * @param level the server level
     * @param pos   the marker position
     */
    private static void removeMarkerBlock(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(GooBlocks.CHAIN_MARKER.get())) {
            level.removeBlock(pos, false);
        }
    }

    /**
     * This marker's world actions as its blob splats.
     */
    private final class SplattingMarker implements ChainMarkerSplat {

        private final ServerLevel level;
        private final BlockPos pos;

        /**
         * @param level the server level
         * @param pos   the marker position
         */
        SplattingMarker(ServerLevel level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        @Override
        public void announceBurnout() {
            ChainBurnoutPayload burnout = burnoutPayload(pos);
            // A listener that never negotiated the mod's channels, a gametest's mock player, gets no burnout.
            for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(level.getChunkAt(pos).getPos(), false)) {
                if (player.connection.hasChannel(burnout)) {
                    PacketDistributor.sendToPlayer(player, burnout);
                }
            }
        }

        @Override
        public boolean loadProgram() {
            behavior = createBehavior();
            return behavior != null;
        }

        @Override
        public boolean runFirstTick() {
            behavior.onSplat(level, pos, ChainMarkerBlockEntity.this);
            return behavior.isActive();
        }

        @Override
        public void removeMarker() {
            removeMarkerBlock(level, pos);
        }

        @Override
        public void syncRunningProgram() {
            setChanged();
            BlockEntitySync.markDirtyAndSync(ChainMarkerBlockEntity.this);
        }
    }

    /**
     * The burnout this marker announces as its blob splats.
     *
     * @param pos the marker position
     * @return the burnout payload
     */
    private ChainBurnoutPayload burnoutPayload(BlockPos pos) {
        return new ChainBurnoutPayload(pos, placedFace.ordinal(), GooTypes.id(gooType), abilityId,
                stacks.stackCount());
    }


    /**
     * Installs the source a client-side marker reads its ability's steps
     * from. Called from client setup, so this class links no client class.
     *
     * @param source the client's step source
     */
    public static void installClientSteps(MarkerStepSource source) {
        clientSteps = source;
    }

    /**
     * Loads the marker's ability program from the steps this side holds.
     *
     * @return the ability's program, or null when this side holds no such ability
     */
    private @Nullable ProgramBehavior createBehavior() {
        if (level == null) {
            return null;
        }
        return MarkerStepSource.forSide(level.isClientSide(), clientSteps, AbilityRegistry.of(level)).program(abilityId);
    }

    /**
     * Returns the goo type driving this chain effect.
     *
     * @return the goo type
     */
    public ResourceKey<GooTypeDefinition> getGooType() {
        return gooType;
    }

    /**
     * Returns the id of the ability this marker runs.
     *
     * @return the ability id
     */
    public String getAbilityId() {
        return abilityId;
    }

    /**
     * Returns the current stack count (number of goo absorbed).
     *
     * @return the stack count
     */
    public int getStackCount() {
        return stacks.stackCount();
    }

    /**
     * Returns the stack ceiling the ability's chain block sets.
     *
     * @return the max stacks
     */
    public int getMaxStacks() {
        return stacks.maxStacks();
    }

    /**
     * Returns the block face this marker was placed on.
     *
     * @return the placed face
     */
    public Direction getPlacedFace() {
        return placedFace;
    }

    /**
     * Returns the running ability program, or null before the blob splats.
     *
     * @return the active chain behavior, or null
     */
    @Nullable
    public ProgramBehavior getBehavior() {
        return behavior;
    }


    /**
     * Restores chain state from persistent storage.
     *
     * @param input the value input to read from
     */
    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        loadSharedFields(input);
        placedFace = loadFace(input);
        reconstituteBehavior(input);
    }

    /**
     * Restores goo type, stack count and max stacks from persistent data.
     *
     * @param input the value input to read from
     */
    private void loadSharedFields(ValueInput input) {
        ResourceKey<GooTypeDefinition> loaded = GooTypes.byId(input.getStringOr(TAG_GOO_TYPE, DEFAULT_GOO_TYPE));
        gooType = loaded != null ? loaded : GooTypes.ROCK;
        stacks.restore(input.getIntOr(TAG_STACK_COUNT, 1), input.getIntOr(TAG_MAX_STACKS, 1));
        lastStackTick = input.getLongOr(TAG_LAST_STACK_TICK, 0);
        abilityId = input.getStringOr(TAG_ABILITY_ID, abilityId);
        fieldEffect.load(input);
        phased.load(input);
        consumedGoo = input.read(TAG_CONSUMED_GOO, GooContents.CODEC).orElse(GooContents.EMPTY);
    }

    /**
     * Re-creates the running program from the ability and lets it reload its
     * own state from the same value stream. Called after the shared fields
     * have been loaded.
     *
     * @param input the value input to read from
     */
    private void reconstituteBehavior(ValueInput input) {
        behavior = createBehavior();
        if (behavior == null) {
            return;
        }
        behavior.loadAdditional(input);
    }

    /**
     * Loads the placed face direction, defaulting to UP if unrecognized.
     *
     * @param input the value input to read from
     * @return the placed face direction
     */
    private Direction loadFace(ValueInput input) {
        String faceName = input.getStringOr(TAG_PLACED_FACE, DEFAULT_FACE);
        Direction dir = Direction.byName(faceName);
        return dir != null ? dir : Direction.UP;
    }

    /**
     * Writes chain state to persistent storage.
     *
     * @param output the value output to write to
     */
    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.putString(TAG_GOO_TYPE, GooTypes.id(gooType));
        output.putInt(TAG_STACK_COUNT, stacks.stackCount());
        output.putInt(TAG_MAX_STACKS, stacks.maxStacks());
        output.putString(TAG_PLACED_FACE, placedFace.getName());
        output.putLong(TAG_LAST_STACK_TICK, lastStackTick);
        output.putString(TAG_ABILITY_ID, abilityId);
        fieldEffect.save(output);
        phased.save(output);
        if (!consumedGoo.isEmpty()) {
            output.store(TAG_CONSUMED_GOO, GooContents.CODEC, consumedGoo);
        }
        if (behavior != null) {
            behavior.saveAdditional(output);
        }
    }
}
