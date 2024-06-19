from os import listdir, getcwd, makedirs
from os.path import isfile, join, isdir, join, basename
import sys
import pandas as pd
from datetime import datetime

def get_parent_dir(directory):
    import os
    return os.path.dirname(directory)

if __name__ == "__main__":
    directory = sys.argv[1]

    current_dirs_parent = get_parent_dir(getcwd())
    target_dir = join(current_dirs_parent, 'simulator', directory)

    if not(isdir(target_dir)):
        print("[FAIL] Usage: ./run.sh [directory]")
        exit(1)
    df = None
    files = [f for f in listdir(target_dir) if isfile(join(target_dir,f))]
    for file in files:
        if file == "operation.csv":
            df = pd.read_csv(join(target_dir, file))

    if df is None or df.empty:
        print(f"[FAIL] operation.csv not found in {target_dir}")
        exit(2)

    filtered_df = df[df['type'] == 'ValidatorSamplingOperation']

    completed_counts = filtered_df['completed'].value_counts()
    completed_yes = completed_counts.get('yes', 0) # Defaults to zero
    completed_no = completed_counts.get('no', 0) # Defaults to zero
    total_completions = completed_yes + completed_no
    average_completion_time = filtered_df['completion_time'].mean()
    percentage_yes = (completed_yes / total_completions) * 100 if total_completions > 0 else 0
    percentage_no = (completed_no / total_completions) * 100 if total_completions > 0 else 0

    results_dir = join(getcwd(), 'results')
    if not isdir(results_dir):
        makedirs(results_dir)

    timestamp = datetime.now().strftime('%Y%m%d%H%M%S')
    target_name = basename(target_dir)
    output_file = join(results_dir, f"{target_name}_{timestamp}.txt")

    with open(output_file, 'w') as f:
        f.write(f"Number of entries with type 'ValidatorSamplingOperation': {len(filtered_df)} \n\n")
        f.write(f"Number of 'completed' values that are 'yes': {completed_yes}\n")
        f.write(f"Number of 'completed' values that are 'no': {completed_no}\n\n")
        f.write(f"Percentage of 'completed' values that are 'yes': {percentage_yes:.2f}%\n")
        f.write(f"Percentage of 'completed' values that are 'no': {percentage_no:.2f}%\n\n")
        f.write(f"Average 'completion_time' for 'ValidatorSamplingOperation': {average_completion_time:.2f}")

    print(f"Results written to {output_file}")