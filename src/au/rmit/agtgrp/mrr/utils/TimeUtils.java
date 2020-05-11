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
package au.rmit.agtgrp.mrr.utils;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

public class TimeUtils {
	
	public static long getCpuTime() {
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		return bean.isCurrentThreadCpuTimeSupported() ? bean.getCurrentThreadCpuTime() : 0L;
	}

	/** Get user time in nanoseconds. */
	public static long getUserTime() {
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		return bean.isCurrentThreadCpuTimeSupported() ? bean.getCurrentThreadUserTime() : 0L;
	}

	/** Get system time in nanoseconds. */
	public static long getSystemTime() {
		ThreadMXBean bean = ManagementFactory.getThreadMXBean();
		return bean.isCurrentThreadCpuTimeSupported()
				? (bean.getCurrentThreadCpuTime() - bean.getCurrentThreadUserTime()) : 0L;
	}
	
	
	private TimeUtils() { }

}
