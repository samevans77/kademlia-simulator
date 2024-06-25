from os import listdir, getcwd, makedirs
from os.path import isfile, join, isdir, join, basename
import sys
import pandas as pd
from datetime import datetime

def get_parent_dir(directory):
    import os
    return os.path.dirname(directory)

if __name__ == "__main__":

    if len(sys.argv) != 4:
        print("[FAIL] Usage: ./run.sh [directory] [step] [attackTime]")
        exit(3)

    directory = sys.argv[1]

    try:
        step_time = int(sys.argv[2])
        attackTime = int(sys.argv[3])
    except:
        print("[FAIL] Steps or attacktime not given as integers. Usage: ./run.sh [directory] [step] [attackTime]")
        exit(4)

    # current_dirs_parent = get_parent_dir(getcwd())
    target_dir = join('simulator', directory)

    if not(isdir(target_dir)):
        print("[FAIL] Directory: "+ target_dir +" does not exist. Usage: ./run.sh [directory] [step] [attackTime]")
        exit(1)
    df = None
    files = [f for f in listdir(target_dir) if isfile(join(target_dir,f))]
    for file in files:
        if file == "operation.csv":
            df = pd.read_csv(join(target_dir, file))

    if df is None or df.empty:
        print(f"[FAIL] operation.csv not found in {target_dir}")
        exit(2)

    # Initialize variables
    step_count = 0
    find_operation_passed = False

    totalValidatorSamplingOperations = 0
    afterAttackValidatorSuccessSamples = 0
    afterAttackValidatorFailedSamples = 0
    totalCompletionTime = 0

    # Output list to store results
    output_lines = []

    # Iterate through the DataFrame
    for index, row in df.iterrows():
        # Check the type of operation
        if row['type'] == 'FindOperation':
            find_operation_seen_last = True
            simulated_time = (step_count * step_time) + row['completion_time']
        elif find_operation_seen_last and row['type'] in ['ValidatorSamplingOperation', 'RandomSamplingOperation']:
            # Increment step count only once after FindOperation is passed
            step_count += 1
            find_operation_seen_last = False  # Reset the flag
            # Calculate the true simulated completion time
            simulated_time = (step_count * step_time) + row['completion_time']
        elif row['type'] in ['ValidatorSamplingOperation', 'RandomSamplingOperation']:
            simulated_time = (step_count * step_time) + row['completion_time']
        else:
            print("found unusual entry. This shouldn't happen")
            print(list(row))
            exit(2)

        if simulated_time > attackTime and row['type'] == 'ValidatorSamplingOperation':
            # We've found an entry of interest
            totalValidatorSamplingOperations += 1
            totalCompletionTime += row['completion_time']
            if row['completed'] == 'yes':
                afterAttackValidatorSuccessSamples += 1
            elif row['completed'] == 'no':
                afterAttackValidatorFailedSamples += 1
            else:
                print("this shouldn't happen")
                print(list(row))
                exit(1)

    print(f"Number of entries with type 'ValidatorSamplingOperation': {totalValidatorSamplingOperations} \n\n")
    print(f"Number of 'completed' values that are 'yes': {afterAttackValidatorSuccessSamples}\n")
    print(f"Number of 'completed' values that are 'no': {afterAttackValidatorFailedSamples}\n\n")
    print(f"Percentage of 'completed' values that are 'yes': {afterAttackValidatorSuccessSamples/totalValidatorSamplingOperations}%\n")
    print(f"Percentage of 'completed' values that are 'no': {afterAttackValidatorFailedSamples/totalValidatorSamplingOperations}%\n\n")
    print(f"Average 'completion_time' for 'ValidatorSamplingOperation': {totalCompletionTime/totalValidatorSamplingOperations}")

    results_dir = join(getcwd(), 'results')
    if not isdir(results_dir):
        makedirs(results_dir)

    timestamp = datetime.now().strftime('%Y%m%d%H%M%S')
    target_name = basename(target_dir)
    output_file = join(results_dir, f"{target_name}_{timestamp}.txt")

    with open(output_file, 'w') as f:
        f.write(f"Number of entries with type 'ValidatorSamplingOperation': {totalValidatorSamplingOperations} \n\n")
        f.write(f"Number of 'completed' values that are 'yes': {afterAttackValidatorSuccessSamples}\n")
        f.write(f"Number of 'completed' values that are 'no': {afterAttackValidatorFailedSamples}\n\n")
        f.write(f"Percentage of 'completed' values that are 'yes': {afterAttackValidatorSuccessSamples/totalValidatorSamplingOperations}%\n")
        f.write(f"Percentage of 'completed' values that are 'no': {afterAttackValidatorFailedSamples/totalValidatorSamplingOperations}%\n\n")
        f.write(f"Average 'completion_time' for 'ValidatorSamplingOperation': {totalCompletionTime/totalValidatorSamplingOperations}")

    print(f"Results written to {output_file}")