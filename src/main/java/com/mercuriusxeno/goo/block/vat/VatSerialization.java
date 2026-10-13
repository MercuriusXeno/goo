package com.mercuriusxeno.goo.block.vat;

import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.registry.GooDataComponents;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * NBT and data-component serialization logic for VatBlockEntity.
 */
final class VatSerialization {

    /**
     * NBT key for goo contents.
     */
    static final String TAG_CONTENTS = "Contents";
    /**
     * NBT key for the player-assigned label.
     */
    static final String TAG_LABEL = "Label";
    /**
     * NBT key for stream goo type ordinal.
     */
    static final String TAG_STREAM_TYPE = "StreamType";
    /**
     * NBT key for stream transfer rate.
     */
    static final String TAG_STREAM_RATE = "StreamRate";
    /**
     * NBT key for stream start tick.
     */
    static final String TAG_STREAM_TICK = "StreamTick";
    /**
     * Sentinel value indicating an invalid NBT ordinal.
     */
    static final String NO_STREAM_TYPE = "";
    /**
     * NBT key for the water tank's volume.
     */
    static final String TAG_WATER = "Water";
    /**
     * NBT key for whether the stream pours water.
     */
    static final String TAG_STREAM_WATER = "StreamWater";

    private VatSerialization() {
    }

    /**
     * Captures the current stream state from the vat's fluid handler.
     *
     * @param be the vat block entity
     */
    static void snapshotStream(VatBlockEntity be) {
        long tick = be.getLevel() != null ? be.getLevel().getGameTime() : 0L;
        be.vatStreamType = be.fluidHandler.getStreamType(tick);
        be.vatStreamRate = be.fluidHandler.getStreamRate(tick);
        be.vatStreamTick = be.fluidHandler.getStreamTick();
        be.vatStreamWater = be.fluidHandler.isStreamWater(tick);
    }

    /**
     * Triggers vat stack redistribution if the vat is stacked and not already redistributing.
     *
     * @param be the vat block entity
     */
    static void tryRedistribute(VatBlockEntity be) {
        Level level = be.getLevel();
        if (be.redistributing || !isServerLevel(level)) {
            return;
        }
        if (isStacked(be.getBlockState())) {
            VatStackRedistributor.redistribute(level, be.getBlockPos());
        }
    }

    /**
     * Returns true if the level is a non-null server level.
     *
     * @param level the level to check, or null
     * @return true if the level exists and is server-side
     */
    private static boolean isServerLevel(@Nullable Level level) {
        return level != null && !level.isClientSide();
    }

    /**
     * Returns true if the vat has a neighbor above or below.
     *
     * @param state the vat block state
     * @return true if VAT_ABOVE or VAT_BELOW is set
     */
    private static boolean isStacked(BlockState state) {
        return state.getValue(VatBlock.VAT_ABOVE) || state.getValue(VatBlock.VAT_BELOW);
    }

    /**
     * Writes all vat fields to the value output.
     *
     * @param be     the vat block entity
     * @param output the value output
     */
    static void saveFields(VatBlockEntity be, ValueOutput output) {
        saveContentsAndLabel(be, output);
        saveStreamFields(be, output);
    }

    /**
     * Writes goo contents and label to the value output.
     *
     * @param be     the vat block entity
     * @param output the value output
     */
    private static void saveContentsAndLabel(VatBlockEntity be, ValueOutput output) {
        GooContents contents = be.fluidHandler.toGooContents();
        if (!contents.isEmpty()) {
            output.store(TAG_CONTENTS, GooContents.CODEC, contents);
        }
        int water = be.fluidHandler.waterVolume();
        if (water > 0) {
            output.putInt(TAG_WATER, water);
        }
        if (be.label != null) {
            output.putString(TAG_LABEL, be.label);
        }
    }

    /**
     * Writes stream-state fields to the value output.
     *
     * @param be     the vat block entity
     * @param output the value output
     */
    private static void saveStreamFields(VatBlockEntity be, ValueOutput output) {
        if (be.vatStreamType == null && !be.vatStreamWater) {
            return;
        }
        if (be.vatStreamType != null) {
            output.putString(TAG_STREAM_TYPE, GooTypes.id(be.vatStreamType));
        }
        output.putBoolean(TAG_STREAM_WATER, be.vatStreamWater);
        output.putInt(TAG_STREAM_RATE, be.vatStreamRate);
        output.putLong(TAG_STREAM_TICK, be.vatStreamTick);
    }

    /**
     * Reads all vat fields from the value input.
     *
     * @param be    the vat block entity
     * @param input the value input
     */
    static void loadFields(VatBlockEntity be, ValueInput input) {
        loadContentsAndLabel(be, input);
        loadStreamFields(be, input);
    }

    /**
     * Reads goo contents and label from the value input.
     *
     * @param be    the vat block entity
     * @param input the value input
     */
    private static void loadContentsAndLabel(VatBlockEntity be, ValueInput input) {
        be.fluidHandler.loadFrom(input.read(TAG_CONTENTS, GooContents.CODEC).orElse(GooContents.EMPTY));
        be.fluidHandler.loadWater(input.getIntOr(TAG_WATER, 0));
        be.label = input.getString(TAG_LABEL).orElse(null);
    }

    /**
     * Reads stream-state fields from the value input.
     *
     * @param be    the vat block entity
     * @param input the value input
     */
    private static void loadStreamFields(VatBlockEntity be, ValueInput input) {
        be.vatStreamType = resolveStreamType(input.getStringOr(TAG_STREAM_TYPE, NO_STREAM_TYPE));
        be.vatStreamWater = input.getBooleanOr(TAG_STREAM_WATER, false);
        be.vatStreamRate = input.getIntOr(TAG_STREAM_RATE, 0);
        be.vatStreamTick = input.getLongOr(TAG_STREAM_TICK, 0);
    }

    /**
     * Resolves stream type from a saved id.
     *
     * @param id the saved type id, or empty for none
     * @return the goo type key, or null for none or an id no identifier accepts
     */
    @Nullable
    static ResourceKey<GooTypeDefinition> resolveStreamType(String id) {
        return id.isEmpty() ? null : GooTypes.byId(id);
    }

    /**
     * Writes goo contents to the data component builder.
     *
     * @param builder  the component builder
     * @param contents the goo contents snapshot
     */
    static void collectContents(DataComponentMap.Builder builder, GooContents contents) {
        if (!contents.isEmpty()) {
            builder.set(GooDataComponents.GOO_CONTENTS.get(), contents);
        }
    }

    /**
     * Reads goo contents from placed item's data components.
     *
     * @param getter the data component getter
     * @return the goo contents, or EMPTY
     */
    static GooContents applyContents(DataComponentGetter getter) {
        return getter.getOrDefault(GooDataComponents.GOO_CONTENTS.get(), GooContents.EMPTY);
    }

}
