package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.*;
import com.mercuriusxeno.goo.ability.program.FieldEffectState;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.PhasedState;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.GooSyncedBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * Ticking block entity a lingering ability stands where its blob lands
 * (decision lingering-abilities-place-their-own-thing). Owns only the shared
 * state: goo type, placed face and ability id. Its work is the body of the
 * ability's linger step, a {@link ProgramBehavior} run on the block's host
 * from the tick the blob splats until it ends, when the block goes.
 */
public class AbilityBlockEntity extends GooSyncedBlockEntity implements MarkerAnchor {

    private static final String TAG_GOO_TYPE = "goo_type";
    private static final String TAG_PLACED_FACE = "PlacedFace";
    /**
     * Default goo type id when loading from NBT.
     */
    private static final String DEFAULT_GOO_TYPE = "rock";
    /**
     * Default face name when loading from NBT.
     */
    private static final String DEFAULT_FACE = "up";
    private static final String TAG_ABILITY_ID = "AbilityId";

    /**
     * Where a client-side marker reads its ability's steps; client setup
     * installs the synced abilities' source.
     */
    private static MarkerStepSource clientSteps = MarkerStepSource.NONE;

    private ResourceKey<GooTypeDefinition> gooType = GooTypes.ROCK;
    private Direction placedFace = Direction.UP;
    /**
     * State the running program keeps through the marker host: the field
     * effect read back by the spike visual, the phase cursor read back by the
     * black-hole visual, and the goo a black hole consumed, dropped when it
     * pops or when the marker is broken first.
     */
    private final MarkerProgramState programState = new MarkerProgramState();
    /**
     * The running body of the ability's linger step; null when this side
     * holds no such ability. Nulled out implicitly when the BE removes
     * itself.
     */
    @Nullable
    private ProgramBehavior behavior;
    /**
     * Id of the ability that stood this block (decision
     * no-throw-without-ability); a marker loaded without one runs nothing.
     */
    private String abilityId = "";

    /**
     * Creates a ability block block entity at the given position.
     *
     * @param pos   the block position
     * @param state the block state
     */
    public AbilityBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.ABILITY_BLOCK.get(), pos, state);
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
                                  AbilityBlockEntity be) {
        if (be.behavior == null) {
            level.removeBlock(pos, false);
            return;
        }
        be.behavior.serverTick((ServerLevel) level, pos, be);
        if (!be.behavior.isActive()) {
            level.removeBlock(pos, false);
            return;
        }
        be.setChanged();
        BlockEntitySync.markDirtyAndSync(be);
    }

    /**
     * Stands the block for a lingering ability and runs the first tick of the
     * steps its linger step hands it, in the tick the blob splats; steps
     * ending that tick take the block with them (decisions
     * splat-runs-the-program-no-fuse, lingering-abilities-place-their-own-thing).
     *
     * @param type      the goo type
     * @param face      the placed face
     * @param ability   the id of the ability that lingers
     * @param steps     the body of the ability's linger step
     * @param size      the size the cast was dragged to, which the program reads,
     *                  zero for one naming none (decision black-hole-leaves-a-compression-sphere)
     */
    public void stand(ResourceKey<GooTypeDefinition> type, Direction face, String ability, List<Step> steps,
                      double size) {
        this.gooType = type;
        this.placedFace = face;
        this.abilityId = ability;
        programState.setCastSize(size);
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        behavior = ProgramBehavior.forHost(steps, HostKind.MARKER);
        behavior.onSplat(server, worldPosition, this);
        if (!behavior.isActive()) {
            removeMarkerBlock(server, worldPosition);
            return;
        }
        setChanged();
        BlockEntitySync.markDirtyAndSync(this);
    }

    /**
     * Restores the state a marker carried through a fall, its running
     * program included. Called by {@link AbilityBlockFallScheduler} after the
     * flight animation completes.
     *
     * @param snapshot the state taken when the support broke
     */
    public void restoreFromFall(AbilityBlockSnapshot snapshot) {
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
     * Returns the state a running field effect keeps on this marker; the
     * field-effect step mutates it through the marker host each tick, and
     * the active behavior's tick marks and syncs the entity afterwards.
     *
     * @return the live field-effect state
     */
    public FieldEffectState getFieldEffect() {
        return programState.fieldEffect();
    }

    /**
     * Returns the phase cursor a running phased step keeps on this marker;
     * the phased step mutates it through the marker host each tick, and
     * the active behavior's tick marks and syncs the entity afterwards.
     *
     * @return the live phased state
     */
    public PhasedState getPhased() {
        return programState.phased();
    }

    @Override
    public MarkerProgramState programState() {
        return programState;
    }

    /**
     * Removes the marker block, when the block at its position is still a marker.
     *
     * @param level the server level
     * @param pos   the marker position
     */
    private static void removeMarkerBlock(ServerLevel level, BlockPos pos) {
        if (level.getBlockState(pos).is(GooBlocks.ABILITY_BLOCK.get())) {
            level.removeBlock(pos, false);
        }
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
    @Override
    public ResourceKey<GooTypeDefinition> getGooType() {
        return gooType;
    }

    /**
     * Returns the id of the ability this marker runs.
     *
     * @return the ability id
     */
    @Override
    public String getAbilityId() {
        return abilityId;
    }

    /**
     * Returns the block face this marker was placed on.
     *
     * @return the placed face
     */
    @Override
    public Direction getPlacedFace() {
        return placedFace;
    }

    /**
     * Returns the running body of the ability's linger step, or null when this side holds none.
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
     * Restores goo type, ability id and program state from persistent data.
     *
     * @param input the value input to read from
     */
    private void loadSharedFields(ValueInput input) {
        ResourceKey<GooTypeDefinition> loaded = GooTypes.byId(input.getStringOr(TAG_GOO_TYPE, DEFAULT_GOO_TYPE));
        gooType = loaded != null ? loaded : GooTypes.ROCK;
        abilityId = input.getStringOr(TAG_ABILITY_ID, abilityId);
        programState.load(input);
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
        output.putString(TAG_PLACED_FACE, placedFace.getName());
        output.putString(TAG_ABILITY_ID, abilityId);
        programState.save(output);
        if (behavior != null) {
            behavior.saveAdditional(output);
        }
    }
}
