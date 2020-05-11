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

package au.rmit.agtgrp.mrr.utils.collections;

public class Triple<S, T, W> extends Pair<S, T> {

	private static final long serialVersionUID = 1L;

	public static <S, T, W> Triple<S, T, W> instance(S first, T second, W third) {
		return new Triple<S, T, W>(first, second, third);
	}
	
	private final W third;
	
	public Triple(S first, T second, W third) {
		super(first, second);
		this.third = third;
	}
		
	public W getThird() {
		return third;
	}

	public Triple<S, T, W> intern() {
		return Pair.getCached(this);
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = super.hashCode();
		result = prime * result + ((third == null) ? 0 : third.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (!super.equals(obj))
			return false;
		if (getClass() != obj.getClass())
			return false;
		Triple<?, ?, ?> other = (Triple<?, ?, ?>) obj;
		if (third == null) {
			if (other.third != null)
				return false;
		} else if (!third.equals(other.third))
			return false;
		return true;
	}

}
