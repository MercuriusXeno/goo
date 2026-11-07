package com.mercuriusxeno.goo.ability.program;

import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Places a Quantum portal on the face the blob struck, for its caster, and
 * finishes. With the ability in hand, right clicking one of the caster's
 * portals chooses its partner instead of throwing. Quantum's Portal is
 * {@code quantum_portal}.
 * portals-link-by-looking-through-portal-vision
 */
public record PortalStep() implements TargetGatedStep {

    private static final String NAME = "quantum_portal";

    /**
     * Codec for the step: it takes no params.
     */
    public static final MapCodec<PortalStep> CODEC = MapCodec.unit(PortalStep::new);

    /**
     * The registered type.
     */
    public static final StepType<PortalStep> TYPE = new StepType<>(NAME, CODEC);

    @Override
    public StepType<PortalStep> type() {
        return TYPE;
    }

    @Override
    public boolean tick(StepContext context) {
        context.hostAs(PortalHost.class).placePortal();
        return true;
    }

    /**
     * A throw at a face no frame fits on is refused before it flies, costing
     * nothing and showing nothing.
     *
     * @param thrower the throwing player
     * @param struck  the block the throw would strike
     * @param face    the face it would strike
     * @return true where a frame fits on the face
     */
    @Override
    public boolean admitsFace(ServerPlayer thrower, BlockPos struck, Direction face) {
        return Portal.frameOn(thrower.level(), struck, face, thrower.getDirection()).isPresent();
    }

    /**
     * Whether an ability places portals, so a right click on one of its
     * caster's portals chooses a partner rather than throwing.
     *
     * @param behaviors an ability's top-level steps
     * @return true where a portal step stands among them
     */
    public static boolean placesPortals(List<Step> behaviors) {
        return behaviors.stream().anyMatch(PortalStep.class::isInstance);
    }

    @Override
    public Stream<Expr> expressions() {
        return Stream.empty();
    }

    @Override
    public Set<HostCapability> requires() {
        return Set.of(HostCapability.PORTAL);
    }
}
