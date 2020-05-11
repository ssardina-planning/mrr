/*******************************************************************************
 * MRR - Minimum Reinstantiated Reorder
 *
 * Copyright (C) 2020 
 * Max Waters (max.waters@rmit.edu.au)
 * RMIT University, Melbourne VIC 3000
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/

package au.rmit.agtgrp.mrr.encoder;

import au.rmit.agtgrp.mrr.fol.symbol.Variable;
import au.rmit.agtgrp.mrr.utils.collections.Pair;

public class VariablePrecedenceObj extends Pair<Variable, Variable>{

	private static final long serialVersionUID = 1L;

	public VariablePrecedenceObj(Variable first, Variable second) {
		super(first, second);
	}
	
	public VariablePrecedenceObj intern() {
		return Pair.getCached(this);
	}

}
