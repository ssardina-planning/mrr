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
