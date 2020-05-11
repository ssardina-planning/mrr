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

package au.rmit.agtgrp.mrr.pddl;

import java.util.List;

import au.rmit.agtgrp.mrr.fol.symbol.ParameterisedSymbol;
import au.rmit.agtgrp.mrr.fol.symbol.Term;
import au.rmit.agtgrp.mrr.fol.symbol.Type;

public class OperatorSymbol extends ParameterisedSymbol implements Term {

	private static final long serialVersionUID = 1L;

	public OperatorSymbol(String name, List<Type> types) {
		super(name, types);
	}

	@Override
	public Type getType() {
		return Type.OPERATOR_TYPE;
	}
	
	@Override
	public OperatorSymbol rename(String newName) {
		return new OperatorSymbol(newName, super.types);
	}
	
}
