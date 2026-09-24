package com.mercuriusxeno.goo.lab;

import java.util.List;

/**
 * Everything one lab build sets, in the order {@link LabBuilder} sets it,
 * with the plots that name which ground belongs to which machine.
 *
 * @param placements every block the build sets, relative to the lab origin
 * @param plots      one plot per {@link LabMachine}
 * @param bounds     the box holding every placement and plot
 */
public record LabPlan(List<LabPlacement> placements, List<LabPlot> plots, LabBox bounds) {

    /**
     * Copies the lists so a plan stays fixed once made.
     *
     * @param placements every block the build sets
     * @param plots      one plot per machine
     * @param bounds     the box holding the plan
     */
    public LabPlan {
        placements = List.copyOf(placements);
        plots = List.copyOf(plots);
    }
}
