package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.PrismCombos;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.pulse.RedstoneBeat;
import com.mercuriusxeno.goo.block.BlockEntitySync;
import com.mercuriusxeno.goo.block.GooSyncedBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlockEntities;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import java.util.List;

/**
 * The prism's block entity: the marker anchor a combo's program runs on, and
 * the record of which combo the prism holds, saved and synced so the prism
 * renders by its combo. A prism holds one combo; a second is refused.
 * decision prism-hosts-the-combos
 */
public class PrismBlockEntity extends GooSyncedBlockEntity implements MarkerAnchor {

    /** The combo a plain prism holds: none. */
    public static final String NO_COMBO = "";

    private static final String TAG_COMBO = "Combo";
    private static final String TAG_GOO_TYPE = "goo_type";
    private static final String TAG_RUNNING = "ComboRunning";
    private static final String TAG_PREVIOUS_EDGE = "BeatPreviousEdge";
    private static final String TAG_LAST_EDGE = "BeatLastEdge";
    private static final String TAG_HEARD = "BeatHeard";

    private final MarkerProgramState programState = new MarkerProgramState();
    private ResourceKey<GooTypeDefinition> gooType = GooTypes.CRYSTAL;
    private String combo = NO_COMBO;
    /** The combo's program while it runs; null once it ends or before any combo. */
    private @Nullable ProgramBehavior behavior;
    /** The redstone beat the prism has heard (decision metronome-prism-pulses-at-the-learned-rate). */
    private RedstoneBeat beat = RedstoneBeat.SILENT;

    /**
     * Creates the prism's block entity.
     *
     * @param pos   the prism's position
     * @param state the prism's block state
     */
    public PrismBlockEntity(BlockPos pos, BlockState state) {
        super(GooBlockEntities.PRISM.get(), pos, state);
    }

    /**
     * Server tick: runs the combo's program one tick while it runs. The prism
     * stands once the program ends; only the program stops.
     *
     * @param level the current level
     * @param pos   the prism's position
     * @param state the prism's block state
     * @param prism the prism's block entity
     */
    public static void serverTick(Level level, BlockPos pos, BlockState state, PrismBlockEntity prism) {
        if (prism.behavior == null) {
            return;
        }
        prism.behavior.serverTick((ServerLevel) level, pos, prism);
        prism.settle();
    }

    /**
     * Runs a combo on the prism: records it, then runs the first tick of its
     * program on a marker host at the prism. A prism already holding a combo
     * refuses the second and runs nothing.
     *
     * @param type    the goo type that landed
     * @param comboId the id of the ability whose program is the combo
     * @param steps   the combo's program
     * @return true when the combo took, false when the prism already held one
     */
    public boolean runCombo(ResourceKey<GooTypeDefinition> type, String comboId, List<Step> steps) {
        if (hasCombo() || !(level instanceof ServerLevel server)) {
            return false;
        }
        gooType = type;
        combo = comboId;
        behavior = ProgramBehavior.forHost(steps, HostKind.MARKER);
        behavior.onSplat(server, worldPosition, this);
        settle();
        return true;
    }

    /**
     * Drops the program once it ends, then saves and syncs the prism.
     */
    private void settle() {
        if (behavior != null && !behavior.isActive()) {
            behavior = null;
        }
        setChanged();
        BlockEntitySync.markDirtyAndSync(this);
    }

    /**
     * Reads the prism's redstone input as a neighbor changes, moving its beat
     * on when a signal starts.
     *
     * @param powered whether a signal reaches the prism now
     * @param now     the game time
     */
    public void hearSignal(boolean powered, long now) {
        RedstoneBeat after = beat.hear(powered, now);
        if (after != beat) {
            beat = after;
            setChanged();
        }
    }

    /**
     * @return the redstone beat the prism has heard
     */
    public RedstoneBeat beat() {
        return beat;
    }

    /**
     * @return true when the prism holds a combo
     */
    public boolean hasCombo() {
        return !combo.isEmpty();
    }

    /**
     * @return the id of the ability whose program is the prism's combo, {@link #NO_COMBO} for a plain prism
     */
    public String getCombo() {
        return combo;
    }

    /**
     * @return the combo's program while it runs, null otherwise
     */
    public @Nullable ProgramBehavior getBehavior() {
        return behavior;
    }

    @Override
    public Direction getPlacedFace() {
        return getBlockState().getValue(PrismBlock.FACING);
    }

    @Override
    public ResourceKey<GooTypeDefinition> getGooType() {
        return gooType;
    }

    @Override
    public String getAbilityId() {
        return combo;
    }

    @Override
    public MarkerProgramState programState() {
        return programState;
    }

    @Override
    protected void loadAdditional(@NonNull ValueInput input) {
        super.loadAdditional(input);
        ResourceKey<GooTypeDefinition> loaded = GooTypes.byId(input.getStringOr(TAG_GOO_TYPE, GooTypes.id(gooType)));
        gooType = loaded != null ? loaded : GooTypes.CRYSTAL;
        combo = input.getStringOr(TAG_COMBO, NO_COMBO);
        programState.load(input);
        beat = new RedstoneBeat(input.getLongOr(TAG_PREVIOUS_EDGE, RedstoneBeat.NEVER),
                input.getLongOr(TAG_LAST_EDGE, RedstoneBeat.NEVER), input.getBooleanOr(TAG_HEARD, false));
        behavior = input.getBooleanOr(TAG_RUNNING, false) ? comboProgram() : null;
        if (behavior != null) {
            behavior.loadAdditional(input);
        }
    }

    /**
     * Rebuilds the running combo's program from the ability its combo names,
     * on a server that holds that ability.
     *
     * @return the program, or null on the client or for an ability the server no longer holds
     */
    private @Nullable ProgramBehavior comboProgram() {
        Identifier id = Identifier.tryParse(combo);
        if (!(level instanceof ServerLevel) || id == null) {
            return null;
        }
        AbilityDefinition source = AbilityRegistry.of(level).getAbility(id);
        return source == null ? null : ProgramBehavior.forHost(PrismCombos.comboSteps(source), HostKind.MARKER);
    }

    @Override
    protected void saveAdditional(@NonNull ValueOutput output) {
        super.saveAdditional(output);
        output.putString(TAG_GOO_TYPE, GooTypes.id(gooType));
        output.putString(TAG_COMBO, combo);
        programState.save(output);
        output.putLong(TAG_PREVIOUS_EDGE, beat.previousEdge());
        output.putLong(TAG_LAST_EDGE, beat.lastEdge());
        output.putBoolean(TAG_HEARD, beat.heard());
        output.putBoolean(TAG_RUNNING, behavior != null);
        if (behavior != null) {
            behavior.saveAdditional(output);
        }
    }
}
