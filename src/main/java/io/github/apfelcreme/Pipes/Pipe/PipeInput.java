package io.github.apfelcreme.Pipes.Pipe;

import org.bukkit.block.Dispenser;
import org.bukkit.inventory.InventoryHolder;

/**
 * Copyright (C) 2016 Lord36 aka Apfelcreme
 * <p>
 * This program is free software;
 * you can redistribute it and/or modify it under the terms of the GNU General
 * Public License as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * <p>
 * You should have received a copy of the GNU General Public License along with
 * this program; if not, see <http://www.gnu.org/licenses/>.
 *
 * @author Lord36 aka Apfelcreme
 */
public class PipeInput {

    private final Dispenser dispenser;

    public PipeInput(Dispenser dispenser) {
        this.dispenser = dispenser;
    }

    /**
     * returns the dispenser
     *
     * @return the dispenser
     */
    public Dispenser getDispenser() {
        return dispenser;
    }

    @Override
    public String toString() {
        return "Dispenser:" + dispenser.getX() + "," + dispenser.getY() + "," + dispenser.getZ();
    }
}
