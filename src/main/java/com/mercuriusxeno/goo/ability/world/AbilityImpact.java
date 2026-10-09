package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.PrismCombos;
import com.mercuriusxeno.goo.ability.program.ExplodeStep;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.LandingHost;
import com.mercuriusxeno.goo.ability.program.LingerStep;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.ProgramLoadException;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.network.ChainBurnoutPayload;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * What an ability goo does when it lands on a block: it lands in the cell
 * {@link LandingSpot} decides, announces its burnout, and runs its program
 * on that landing the tick it splats. The landing places no block of its
 * own; a program ending that tick leaves only its effect, and one that
 * lingers stands its own block through its linger step (decisions
 * splat-runs-the-program-no-fuse, lingering-abilities-place-their-own-thing).
 * A goo landing on a prism lands in the prism instead, running its combo
 * (decision prism-hosts-the-combos).
 */
public final class AbilityImpact {

    private static final String LOG_PROGRAM_REFUSED = "Ability {} program refused for the landing host: {}";
    private static final String LOG_COMBO_REFUSED = "Prism combo {} program refused for the marker host: {}";

    private AbilityImpact() {
    }

    /**
     * Lands an ability goo on a block.
     *
     * @param level   the server level
     * @param pos     the struck block
     * @param type    the goo type thrown
     * @param face    the struck face
     * @param ability the ability the goo names
     */
    public static void land(ServerLevel level, BlockPos pos, ResourceKey<GooTypeDefinition> type,
                            Direction face, AbilityDefinition ability) {
        land(level, pos, type, face, ability, null);
    }

    /**
     * Lands an ability goo on a block, its world actions anchored at the
     * aimed point where it names one, and at the landing cell's center
     * otherwise, so a free ability resolves where it was aimed.
     * aim-point-follows-the-cursor
     *
     * @param level   the server level
     * @param pos     the struck block
     * @param type    the goo type thrown
     * @param face    the struck face
     * @param ability the ability the goo names
     * @param point   the aimed point the ability resolves at, or null for the cell's center
     */
    public static void land(ServerLevel level, BlockPos pos, ResourceKey<GooTypeDefinition> type,
                            Direction face, AbilityDefinition ability, @Nullable Vec3 point) {
        if (level.getBlockEntity(pos) instanceof PrismBlockEntity prism) {
            landOnPrism(level, prism, type, ability);
            return;
        }
        Optional<LandingSpot> spot = point == null ? LandingSpot.resolve(level, pos, face)
                : LandingSpot.resolve(level, pos, face, point);
        if (spot.isEmpty()) {
            return;
        }
        BlockPos cell = spot.get().cell();
        LandingHost host = new LandingHost(level, cell, face, spot.get().waterlogged(), type,
                ability.id().toString(), point == null ? Vec3.atCenterOf(cell) : point);
        AbilitySplat.resolve(new Landing(host, ability));
    }

    /**
     * Lands a goo in a prism: the landing ability's own prism reaction, or its
     * type's prism ability, runs as the prism's combo on a marker host at the
     * prism. A type with neither leaves the prism as it is, and a prism
     * already holding a combo refuses a second.
     * decision prism-hosts-the-combos
     *
     * @param level   the server level
     * @param prism   the struck prism
     * @param type    the goo type thrown
     * @param ability the ability the goo names
     */
    private static void landOnPrism(ServerLevel level, PrismBlockEntity prism, ResourceKey<GooTypeDefinition> type,
                                    AbilityDefinition ability) {
        // timekeeper-prism-banks-ticks-forward-only: goo of a banking prism's own type feeds its bank
        if (prism.feed(type, ability.cost())) {
            return;
        }
        AbilityDefinition source = PrismCombos.comboSource(ability, AbilityRegistry.of(level).prismAbilityFor(type));
        if (source == null || prism.hasCombo()) {
            return;
        }
        try {
            prism.runCombo(type, source.id().toString(), PrismCombos.comboSteps(source));
        } catch (ProgramLoadException e) {
            Goo.LOGGER.error(LOG_COMBO_REFUSED, source.id(), e.getMessage());
        }
    }

    /**
     * Whether an ability's program stands its own block that explodes after
     * the splat, so its burnout plays when that block explodes rather than as
     * the blob lands. A lingering program that never explodes, Razor's cloud,
     * plays its burnout at the landing.
     * decision elemental-explosion-per-type
     * decision diagnose-then-restore-the-razor-dome
     *
     * @param ability the landing ability
     * @return true when a top-level linger step's body explodes
     */
    static boolean explodesLater(AbilityDefinition ability) {
        return ability.behaviors().stream().filter(LingerStep.class::isInstance)
                .flatMap(AbilityImpact::withDescendants)
                .anyMatch(ExplodeStep.class::isInstance);
    }

    private static Stream<Step> withDescendants(Step step) {
        return Stream.concat(Stream.of(step), step.children().flatMap(AbilityImpact::withDescendants));
    }

    /**
     * A blob's world actions as it lands.
     *
     * @param host    the landing host
     * @param ability the ability the blob names
     */
    private record Landing(LandingHost host, AbilityDefinition ability) implements AbilitySplat {

        @Override
        public void announceBurnout() {
            new ChainBurnoutPayload(host.cell(), host.face().ordinal(), GooTypes.id(host.gooType()),
                    host.abilityId()).sendToTracking(host.level());
        }

        @Override
        public boolean explodesLater() {
            return AbilityImpact.explodesLater(ability);
        }

        @Override
        public void runProgram() {
            try {
                ProgramBehavior.forHost(ability.behaviors(), HostKind.LANDING).tick(host);
            } catch (ProgramLoadException e) {
                Goo.LOGGER.error(LOG_PROGRAM_REFUSED, ability.id(), e.getMessage());
            }
        }
    }
}
