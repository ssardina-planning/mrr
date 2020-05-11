package au.rmit.agtgrp.mrr.encoder;

import au.rmit.agtgrp.mrr.fol.function.Constant;
import au.rmit.agtgrp.mrr.fol.symbol.Variable;
import au.rmit.agtgrp.mrr.utils.collections.Pair;

public class VariableAssignmentObj extends Pair<Variable, Constant>{

	private static final long serialVersionUID = 1L;

	public VariableAssignmentObj(Variable first, Constant second) {
		super(first, second);
	}

	public VariableAssignmentObj intern() {
		return Pair.getCached(this);
	}
	
	@Override
	public String toString() {
		return super.getFirst() + " = " + super.getSecond();
	}
	
}