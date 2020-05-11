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
import au.rmit.agtgrp.mrr.pct.PoclPlan;
import au.rmit.agtgrp.mrr.pddl.Operator;
import au.rmit.agtgrp.mrr.sat.WeightedSatFormula;

public class PcToWcnfEncoder extends PcToCnfEncoder {

	public PcToWcnfEncoder(CnfEncoderOptions options) {
		super(options);
	}
	
	@Override
	public WeightedSatFormula encodeConstraints(PoclPlan plan) {
		super.encodeConstraints(plan);
		return (WeightedSatFormula) super.satFormula;
	}
	
	@Override
	protected void encode() {
		super.encode();
		buildSoftOrderingConstraints();
	}
	
	@Override
	protected WeightedSatFormula initSatFormula() {
		return new WeightedSatFormula(Integer.MAX_VALUE);
	}

	private void buildSoftOrderingConstraints() {
		WeightedSatFormula weightedSat = (WeightedSatFormula) super.satFormula;
		for (int i = 0; i < plan.getPlanSteps().size(); i++) {
			Operator<Variable> op1 = plan.getPlanSteps().get(i);
			for (int j = 0; j < plan.getPlanSteps().size(); j++) {
				Operator<Variable> op2 = plan.getPlanSteps().get(j);

				if (i == j || propMap.getPrecedenceProposition(op1, op2) == null)
					continue;

				int p12 = propMap.getPrecedenceProposition(op1, op2);
				weightedSat.addWeightedClause(1, -p12);

			}
		}
	}
}
