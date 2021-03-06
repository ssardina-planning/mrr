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

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import au.rmit.agtgrp.mrr.fol.Substitution;
import au.rmit.agtgrp.mrr.fol.function.Constant;
import au.rmit.agtgrp.mrr.fol.predicate.Atom;
import au.rmit.agtgrp.mrr.fol.predicate.Literal;
import au.rmit.agtgrp.mrr.fol.predicate.Predicate;
import au.rmit.agtgrp.mrr.fol.symbol.Term;
import au.rmit.agtgrp.mrr.fol.symbol.Variable;
import au.rmit.agtgrp.mrr.pddl.Operator;

public class ThreatMap {

	public static ThreatMap getThreatMap(List<Operator<Variable>> freeSteps) {
		Map<Boolean, Map<Predicate, Set<Threat>>> threatMap = new HashMap<Boolean, Map<Predicate, Set<Threat>>>();
		threatMap.put(true, new HashMap<Predicate, Set<Threat>>());
		threatMap.put(false, new HashMap<Predicate, Set<Threat>>());
		
		for (Operator<Variable> threatOp : freeSteps) {
			for (Literal<Variable> threatLit : threatOp.getPostconditions()) {
				// produces p, is a threat to -p
				Set<Threat> negs = threatMap.get(!threatLit.getValue()).get(threatLit.getAtom().getSymbol());
				if (negs == null) {
					negs = new HashSet<Threat>();
					threatMap.get(!threatLit.getValue()).put(threatLit.getAtom().getSymbol(), negs);
				}
				negs.add(new Threat(threatOp, threatLit.getNegated()));
			}
		}

		return new ThreatMap(threatMap);
	}

	private Map<Boolean, Map<Predicate, Set<Threat>>> threatMap = new HashMap<Boolean, Map<Predicate, Set<Threat>>>();



	public ThreatMap(Map<Boolean, Map<Predicate, Set<Threat>>> threatMap) {
		this.threatMap = threatMap;
	}

	public Set<Threat> getNegations(Consumer cons) {
		Set<Threat> negs = threatMap.get(cons.literal.getValue()).get(cons.literal.getAtom().getSymbol());
		if (negs == null) {
			negs = new HashSet<Threat>();
			threatMap.get(cons.literal.getValue()).put(cons.literal.getAtom().getSymbol(), negs);
		}
		return negs;
	}
	
	public Set<Threat> getNegations(PcLink pcLink) {
		Set<Threat> negs = threatMap.get(pcLink.getProducer().literal.getValue()).get(pcLink.getProducer().literal.getAtom().getSymbol());
		if (negs == null) {
			negs = new HashSet<Threat>();
			threatMap.get(pcLink.getProducer().literal.getValue()).put(pcLink.getProducer().literal.getAtom().getSymbol(), negs);
		}
		return negs;
	}

	// pclink: a(x) -> a(y), negation: a(z)
	public boolean isThreat(PcLink pcLink, Threat negation, Substitution<Constant> sub) {

		// same predicate
		if (!pcLink.getProducer().literal.getAtom().getSymbol().equals(negation.literal.getAtom().getSymbol()))
			return false;

		// opposite value
		if (pcLink.getProducer().literal.getValue() != negation.literal.getValue())
			return false;

		// consumer cannot be a threat to itself
		if (negation.operator.equals(pcLink.getConsumer().operator))
			return false;

		// assignable
		if (!assignable(negation.literal.getAtom(), pcLink.getConsumer().literal.getAtom()))
			return false;

		// is undone in uninstantiated form (p(x), -p(x))
		if (negation.operator.isUndone(negation.literal.getNegated()))
			return false;
				
		// is undone in ground form (p(x), -p(y), x=1, y=1)
		if (sub != null && 
			negation.operator.applySubstitution(sub).isUndone(negation.literal.applySubstitution(sub).getNegated()))
			return false;
		
		// is a negative postcon threatening a pos postcon in the same operator
		if (pcLink.getProducer().operator.equals(negation.operator) && negation.literal.getValue()) 
			return false;

		// if ground, check bindings and return
		if (sub != null) {
			return sub.apply(pcLink.getConsumer().literal.getAtom().getVariables()).equals(sub.apply(negation.literal.getAtom().getVariables()));
		}

		return true;
	}

	public Set<Threat> getNonGroundThreats(PcLink pcLink) {
		return getGroundThreats(pcLink, null);
	}

	public Set<Threat> getGroundThreats(PcLink pcLink, Substitution<Constant> sub) {
		Set<Threat> threats = new HashSet<Threat>();
		for (Threat threat : getNegations(pcLink)) {
			if (isThreat(pcLink, threat, sub)) {
				threats.add(threat);
			}
		}
		return threats;
	}

	private static boolean assignable(Atom<? extends Term> prod, Atom<? extends Term> cons) {
		for (int i = 0; i < prod.getParameters().size(); i++) {
			if (!cons.getParameters().get(i).getType().hasSubtype(prod.getParameters().get(i).getType())
					&& !prod.getParameters().get(i).getType().hasSubtype(cons.getParameters().get(i).getType())) {
				return false;
			}
		}

		return true;
	}

}
