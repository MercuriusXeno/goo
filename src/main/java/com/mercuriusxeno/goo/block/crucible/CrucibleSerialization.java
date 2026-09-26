package com.mercuriusxeno.goo.block.crucible;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import java.util.List;

/**
 * Static helpers for crucible NBT serialization: melting state (PMI + heat).
 * Gasket fields are now handled by GasketState. Keeps framework save/load
 * overrides in CrucibleBlockEntity.
 */
final class CrucibleSerialization {

    private CrucibleSerialization() { }

    /** Saves the melting item stack, its melt queue, the heat ticks and the fuel that bought them.
     *
     * @param be     the crucible block entity
     * @param output the value output to write to
     */
    static void saveMeltingState(CrucibleBlockEntity be, ValueOutput output) {
        if (!be.meltingItem.isEmpty()) {
            output.store(CrucibleBlockEntity.TAG_MELTING_ITEM, ItemStack.CODEC, be.meltingItem);
        }
        if (!be.meltQueue.isEmpty()) {
            output.store(CrucibleBlockEntity.TAG_MELT_QUEUE, CrucibleMeltQueue.CODEC, be.meltQueue.entries());
        }
        FuelGrade grade = be.heat.grade();
        if (be.heat.heatTicks() > 0 && grade != null) {
            output.putInt(CrucibleBlockEntity.TAG_HEAT_TICKS, be.heat.heatTicks());
            output.store(CrucibleBlockEntity.TAG_HEAT_FUEL, GooTypes.KEY_CODEC, grade.fuel());
        }
    }

    /** Loads the melting item stack, its melt queue and the heat, at the configured grade of the fuel that bought it.
     *
     * @param be    the crucible block entity
     * @param input the value input to read from
     */
    static void loadMeltingState(CrucibleBlockEntity be, ValueInput input) {
        be.meltingItem = input.read(CrucibleBlockEntity.TAG_MELTING_ITEM, ItemStack.CODEC)
            .orElse(ItemStack.EMPTY);
        be.meltQueue.loadFrom(input.read(CrucibleBlockEntity.TAG_MELT_QUEUE, CrucibleMeltQueue.CODEC)
            .orElse(List.of()));
        int ticks = input.getIntOr(CrucibleBlockEntity.TAG_HEAT_TICKS, 0);
        ResourceKey<GooTypeDefinition> fuel = input.read(CrucibleBlockEntity.TAG_HEAT_FUEL, GooTypes.KEY_CODEC)
            .orElse(GooTypes.BLAZE);
        be.heat.set(ticks, gradeOf(fuel));
    }

    /** Returns the configured grade burning the given fuel, blaze's when none does.
     *
     * @param fuel the fuel goo type
     * @return the grade
     */
    private static FuelGrade gradeOf(ResourceKey<GooTypeDefinition> fuel) {
        for (FuelGrade grade : FuelGrade.configured()) {
            if (grade.fuel().equals(fuel)) {
                return grade;
            }
        }
        return FuelGrade.configuredBlaze();
    }
}
