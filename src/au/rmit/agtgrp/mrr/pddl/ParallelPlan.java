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

import java.util.ArrayList;
import java.util.List;

import au.rmit.agtgrp.mrr.fol.function.Constant;
import au.rmit.agtgrp.mrr.fol.symbol.Variable;

public class ParallelPlan extends Plan {

	private static List<Operator<Constant>> concatenate(List<List<Operator<Constant>>> lists) {
		List<Operator<Constant>> concat = new ArrayList<Operator<Constant>>();
		for (List<Operator<Constant>> list : lists)
			concat.addAll(list);
		return concat;
	}
	
	private final List<List<Operator<Variable>>> parallelSteps;
	
	public ParallelPlan(PddlProblem problem, List<List<Operator<Constant>>> planSteps) {
		super(problem, concatenate(planSteps));
		
		parallelSteps = new ArrayList<List<Operator<Variable>>>();		
		int n = 0;
		for (List<Operator<Constant>> step : planSteps) {
			parallelSteps.add(super.getPlanSteps().subList(n, n+step.size()));
			n+=step.size();
		}			
	}

	public int getMakespan() {
		return parallelSteps.size();
	}

	public List<List<Operator<Variable>>> getParallelSteps() {
		return parallelSteps;
	}
	
	public int getWidth() {
		int w = 0;
		for (List<Operator<Variable>> pstep : parallelSteps) {
			if (pstep.size() > w)
				w = pstep.size();
		}
		return w;
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < parallelSteps.size(); i++) {
			for (Operator<Variable> op : parallelSteps.get(i))
				sb.append(op.applySubstitution(super.getSubstitution()).formatParameters() + " ");
			if (i < parallelSteps.size() - 1)
				sb.append("\n");
		}
		return sb.toString();
	}
	
}
