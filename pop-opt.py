import argparse
import os
import platform
import subprocess
import time
from pathlib import Path

JAVA_MAIN = "au.rmit.agtgrp.mrr.main.MrrMain"
JAVA_VM_ARGS = "-Xmx4G"
JAVA_CLASSPATH = "./lib/mrr-0.0.1.jar:./lib/args4j-2.33.jar:./lib/pddl4j-3.5.0.jar"

SEP = "************************************************"

def print_header(header: str):
    print("\n{}\n** {}\n{}".format(SEP, header, SEP))


MAXSAT_SAT = "SATISFIABLE"
MAXSAT_UNSAT = "UNSATISFIABLE"
MAXSAT_OPT = "OPTIMAL"
MAXSAT_TO = "TIMEOUT"
MAXSAT_ERR = "MAXSAT_ERROR"
ENCODING_ERR = "ENCODING_ERROR"

OUT_DIR = "out"

def check_file(file_name: str, exit_on_no: bool = False) -> bool:
    exists = Path(file_name).is_file()
    if not exists:
        print("File does not exist: {}".format(file_name))
        if exit_on_no:
            exit(1)
    return exists


def make_dir(dir_name: str):
    path = Path(dir_name)
    if path.is_file():
        print("{} is a file".format(dir_name))
        exit(1)
    if not Path(dir_name).is_dir():
        print("Making directory: {}".format(dir_name))
        path.mkdir(parents=True, exist_ok=True)


def parse_maxpre_result(maxpre_result: str) -> str:
    maxpre_result = maxpre_result.split(" ")[1]  # remove s
    if maxpre_result == "OPTIMUM" or maxpre_result == "OPTIMAL":
        return MAXSAT_OPT
    if maxpre_result == "SATISFIABLE":
        return MAXSAT_SAT
    if maxpre_result == "UNSATISFIABLE":
        return MAXSAT_UNSAT
    if maxpre_result == "TIMEOUT" or maxpre_result == "UNKNOWN":
        return MAXSAT_TO
    if maxpre_result == "ERROR":
        return MAXSAT_ERR

    print("Cannot decode MaxSAT result: {}".format(maxpre_result))
    return ""

def clean():
    if os.path.isdir(OUT_DIR):
        files = os.listdir(OUT_DIR)
        for file in files:
            os.remove(os.path.join(OUT_DIR, file))

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--domain", help="Domain file", required=True)
    parser.add_argument("--problem", help="Problem file", required=True)
    parser.add_argument("--plan", help="Plan file", required=True)
    parser.add_argument("--encoder", help="MaxSAT encoder", required=True)
    parser.add_argument("--verbose", help="Verbose mode", action='store_true')

    args = parser.parse_args()

    domain_file = args.domain
    problem_file = args.problem
    plan_file = args.plan
    alg = args.encoder
    verbose = args.verbose

    print("Domain file:  {}".format(domain_file))
    print("Problem file: {}".format(problem_file))
    print("Plan file:    {}".format(plan_file))
    print("Encoder:      {}".format(alg))
    print("Verbose:      {}".format(verbose))

    check_file(domain_file, exit_on_no=True)
    check_file(problem_file, exit_on_no=True)
    check_file(plan_file, exit_on_no=True)

    wcnf_file = "{}/encoded.wcnf".format(OUT_DIR)
    pp_wcnf_file = "{}/preprocessed.wcnf".format(OUT_DIR)
    pp_map_file = "{}/preprocessed.wcnf.map".format(OUT_DIR)
    pp_wcnf_model = "{}/pp-model.dimacs".format(OUT_DIR)
    wcnf_model = "{}/model.dimacs".format(OUT_DIR)
    pop_file = "{}/optimised.pop".format(OUT_DIR)

    maxsat_out = "{}/maxsat-preprocessor-out.log".format(OUT_DIR)
    pp_out = "{}/maxsat-solver-out.log".format(OUT_DIR)

    #
    # clean previous
    #
    clean()

    #
    # encode WCNF
    #
    print_header("Encoding WCNF")
    args = ["java", JAVA_VM_ARGS, "-cp", JAVA_CLASSPATH, JAVA_MAIN,
            "--domain", domain_file,
            "--problem", problem_file,
            "--plan", plan_file,
            "--enc", alg,
            "--wcnf", wcnf_file,
            "--action", "ENCODE"]
    if verbose:
        args.append("--verbose")

    print(" ".join(args))
    subprocess.call(args)

    if not check_file(wcnf_file):
        print("Encoding failed")
        exit(1)

    #
    # preprocess WCNF
    #
    print_header("Preprocessing WCNF")
    args = ["maxpre", wcnf_file, "preprocess", "-mapfile={}".format(pp_map_file)]
    print(" ".join(args))
    subprocess.call(args, stdout=open(pp_wcnf_file, "w"))

    if not check_file(pp_wcnf_file):
        print("Preprocessing failed")
        exit(1)

    #
    # solve MaxSAT
    #
    print_header("Solving MaxSAT")  
    args = ["loandra", "-print-model", pp_wcnf_file]
    print(" ".join(args))
    completed = subprocess.run(args, capture_output=True)
    std_out = completed.stdout.decode("utf-8")
    print(std_out)

    model_found = False
    maxsat_result = MAXSAT_ERR
    for line in std_out.split("\n"):
        if line.startswith("v"):
            with open(pp_wcnf_model, "w") as f:
                f.write("s OPTIMUM\n{}\n".format(line))
            print("Model written to {}".format(pp_wcnf_model))
            model_found = True
        if line.startswith("s"):
            maxsat_result = parse_maxpre_result(line)
            print(maxsat_result)

    if not model_found or (maxsat_result != MAXSAT_SAT and maxsat_result != MAXSAT_OPT):
        print("MaxSAT failed")
        exit(1)

    #
    # undo preprocessing
    #
    print_header("Undoing preprocessing")
    args = ["maxpre", pp_wcnf_model, "reconstruct", "-mapfile={}".format(pp_map_file)]
    print(" ".join(args))
    completed = subprocess.run(args, capture_output=True)
    std_out = completed.stdout.decode("utf-8")
    print(std_out)

    model_found = False
    for line in std_out.split("\n"):
        if line.startswith("v"):
            with open(wcnf_model, "w") as f:
                f.write(line)
            print("Model written to {}".format(wcnf_model))
            model_found = True

    if not model_found:
        print("Failed to undo preprocessing")
        exit(1)

    #
    # evaluate model
    #
    print_header("Decoding MaxSAT model")
    args = ["java", JAVA_VM_ARGS, "-cp", JAVA_CLASSPATH, JAVA_MAIN,
            "--domain", domain_file,
            "--problem", problem_file,
            "--plan", plan_file,
            "--action", "DECODE",
            "--model", wcnf_model,
            "--wcnf", wcnf_file,
            "--pop", pop_file]
    if verbose:
        args.append("--verbose")

    print(" ".join(args))
    subprocess.call(args)


if __name__ == "__main__":
    main()
