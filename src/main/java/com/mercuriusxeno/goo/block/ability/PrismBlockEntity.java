package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.PrismCombos;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.Step;
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
    private static final String TAG_CHARGE = "Charge";
    private static final String TAG_COMBO_SINCE = "ComboSince";

    private final MarkerProgramState programState = new MarkerProgramState();
    private ResourceKey<GooTypeDefinition> gooType = GooTypes.CRYSTAL;
    private String combo = NO_COMBO;
    /** The goo an oculus holds, which pays for blinks to it (decision oculus-prism-becomes-a-hovering-eye). */
    private int charge;
    /** The game time the combo took, which its transformation plays from. */
    private long comboSince;
    /** The combo's program while it runs; null once it ends or before any combo. */
    private @Nullable ProgramBehavior behavior;
    /**
     * Whether a combo that was running when the prism saved waits to rebuild
     * its program: a chunk loads the prism before it has a level, when no
     * ability can be read, so the program comes back on its first server tick.
     */
    private boolean resumesCombo;

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
        if (prism.resumesCombo) {
            prism.resumesCombo = false;
            prism.behavior = prism.comboProgram();
        }
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
        comboSince = server.getGameTime();
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
     * @return the goo the prism's combo holds as its charge, in mB
     */
    public int charge() {
        return charge;
    }

    /**
     * Sets the goo the prism's combo holds as its charge, then saves and syncs.
     * decision oculus-prism-becomes-a-hovering-eye
     *
     * @param mb the charge in mB
     */
    public void setCharge(int mb) {
        charge = mb;
        setChanged();
        BlockEntitySync.markDirtyAndSync(this);
    }

    /**
     * @return the game time the prism's combo took
     */
    public long comboSince() {
        return comboSince;
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
        charge = input.getIntOr(TAG_CHARGE, 0);
        comboSince = input.getLongOr(TAG_COMBO_SINCE, 0L);
        programState.load(input);
        boolean running = input.getBooleanOr(TAG_RUNNING, false);
        behavior = running ? comboProgram() : null;
        if (behavior != null) {
            behavior.loadAdditional(input);
        }
        // a chunk reads the prism before its level is set: the running combo comes back on the first server tick
        resumesCombo = running && behavior == null && level == null;
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
        output.putInt(TAG_CHARGE, charge);
        output.putLong(TAG_COMBO_SINCE, comboSince);
        programState.save(output);
        output.putBoolean(TAG_RUNNING, behavior != null || resumesCombo);
        if (behavior != null) {
            behavior.saveAdditional(output);
        }
    }
}
