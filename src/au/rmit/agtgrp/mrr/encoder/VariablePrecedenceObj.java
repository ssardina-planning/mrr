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
