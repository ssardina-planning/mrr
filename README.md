# MRR: Minimum Reinsantiated Reorder

MRR is an implementation of a MaxSAT-based technique for finding *minimum deorderings and reorderings* of a partial order plan. It can be configured to either find a *minimum deorder* or *minimum reorder* as per [Muise et al.](https://www.jair.org/index.php/jair/article/view/11024), or a *minimum reinstantiated deorder* or *minimum reiinstantiated reorder* as per Waters et al.


## Dependencies

Download the following .jar files to the `lib` directory:

* [`args4j-2.33.jar`](https://github.com/kohsuke/args4j)
* [`pddl4j-3.5.0.jar`](https://github.com/pellierd/pddl4j)

The following programs must be installed:

* [Loandra](https://github.com/jezberg/loandra)
* [MaxPre](https://github.com/Laakeri/maxpre)
* Python 3
* Java SDK 1.8
* [ant](http://ant.apache.org)

The programs `loandra` and `maxpre` must be in the `PATH`.


## Compiling MRR

From the root directory run:
```
ant build
```
MRR will be compiled into `lib/mrr-0.0.1.jar`.


## Running MRR

Run MRR with the following command:
```
usage: pop-opt.py [-h] --domain DOMAIN --problem PROBLEM --plan PLAN --encoder ENCODER [--verbose]
			  	 
```
Required arguments:

* `--domain DOMAIN`: The location of the PDDL domain file.
* `--problem PROBLEM`: The location of the problem instance PDDL file.
* `--plan PLAN`: The location of the plan which solves `PROBLEM`.
* `--encoder CSP_ENCODER`: The encoder used to convert the input plan into a MaxSAT instance. Options are: 
	* `MD`: Finds a minimum deorder as per Muise et al.
	* `MR`: Finds a minimum reorder as per Muise et al.
	* `MR_OPSB`: Finds a minimum reorder, also encodes operator symmetry breaking constraints.
	* `MRD`: Finds a minimum reinstantiated deorder.
	* `MRR`: Finds a minimum reinstantiated reorder.
	* `MRR_OPSB`: Finds a minimum reinstantiated reorder, also encodes operator symmetry breaking constraints.
	
Options:

* `--verbose`: Verbose output.


### Plan format

Plan files with a `.m` extension are assumed to be [Madagascar](https://research.ics.aalto.fi/software/sat/madagascar/) parallel plans (e.g., `example/p01.pddl.m`), otherwise plans are assumed to be standard IPC format plans (e.g., e.g., `example/p01.pddl.bfws`).


### Example

The `example` directory contains a small planning instance from the IPC `rovers` domain, and three different plans. Run MRR over the example with the following commmand:

```
./mktr.sh --domain example/domain.pddl --problem example/p01.pddl
		  --plan example/p01.pddl.m --encoder MRR
			  	 
```


## Supported PDDL fragments

All features of basic `STRIPS` are supported, and some aspects of `ADL`, namely `equality`, `typing` and `negative preconditions`.


## Contact

Max Waters (max.waters@rmit.edu.au).
 

## License

This project is using the GPLv3 for open source licensing for information and the license visit GNU website (https://www.gnu.org/licenses/gpl-3.0.en.html).

This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for more details.

You should have received a copy of the GNU General Public License along with this program. If not, see http://www.gnu.org/licenses/.
