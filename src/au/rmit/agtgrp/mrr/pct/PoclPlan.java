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
package au.rmit.agtgrp.mrr.pct;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import au.rmit.agtgrp.mrr.fol.Substitution;
import au.rmit.agtgrp.mrr.fol.function.Constant;
import au.rmit.agtgrp.mrr.fol.symbol.Variable;
import au.rmit.agtgrp.mrr.pddl.Operator;
import au.rmit.agtgrp.mrr.pddl.PddlDomain;
import au.rmit.agtgrp.mrr.pddl.PddlProblem;
import au.rmit.agtgrp.mrr.pddl.Plan;
import au.rmit.agtgrp.mrr.pddl.PlanFactory;
import au.rmit.agtgrp.mrr.utils.FormattingUtils;

public class PoclPlan {
	
	private static final long serialVersionUID = 1L;
	
	protected final PddlProblem problem;
	protected final Set<Operator<Variable>> operators;
	protected final Operator<Variable> init;
	protected final Operator<Variable> goal;
	
	protected final Substitution<Constant> initSub;
	protected final Substitution<Constant> goalSub;

	protected final List<Operator<Variable>> planSteps;
	protected final Substitution<Constant> originalSub;

	protected final CausalStructure constraints;
	
	public PoclPlan(PddlProblem problem, List<Operator<Variable>> planSteps, Substitution<Constant> sub, CausalStructure constraints) {

		if (problem == null || planSteps == null || sub == null || constraints == null) 		
			throw new NullPointerException("Arguments cannot be null");

		this.problem = problem;
		this.planSteps = planSteps;
		this.originalSub = sub;
		this.constraints = constraints;

		operators = new HashSet<Operator<Variable>>(this.planSteps);
		goal = getOpByName(operators, Plan.GOAL_OP_NAME);
		init = getOpByName(operators, Plan.INIT_OP_NAME);

		if (init == null || goal == null)
			throw new IllegalArgumentException("No init or goal operator");

		if (!PlanFactory.hasUniqueVariableNames(operators)) {
			StringBuilder sb = new StringBuilder();
			for (Operator<Variable> step : operators) 
				sb.append(step.getName() + "(" + FormattingUtils.toString(step.getVariables(), ",") + ")\n");

			throw new IllegalArgumentException("Operator/variable name error:\n" + sb.toString());
		}
		
		this.goalSub = Substitution.trim(sub, goal.getVariables());
		this.initSub = Substitution.trim(sub, init.getVariables());	
	}

	private Operator<Variable> getOpByName(Collection<Operator<Variable>> operators, String name) {
		for (Operator<Variable> op : operators) {
			if (op.getName().equals(name))
				return op;
		}
		
		return null;
	}
	
	public PddlProblem getProblem() {
		return problem;
	}

	public PddlDomain getDomain() {
		return problem.getDomain();
	}
	
	public Set<Operator<Variable>> getOperators() {
		return operators;
	}

	public Operator<Variable> getInitAction() {
		return init;
	}

	public Operator<Variable> getGoalAction() {
		return goal;
	}
	
	public Substitution<Constant> getInitSub() {
		return initSub;
	}

	public Substitution<Constant> getGoalSub() {
		return goalSub;
	}

	public List<Operator<Variable>> getPlanSteps() {
		return planSteps;
	}
	
	public Substitution<Constant> getOriginalSub() {
		return originalSub;
	}
	
	public CausalStructure getConstraints() {
		return constraints;
	}

}
