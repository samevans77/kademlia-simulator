from os import listdir, getcwd
from os.path import isfile, join, isdir, join
import sys
import pandas as pd

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
    completed_yes = completed_counts.get('yes', 0)
    completed_no = completed_counts.get('no', 0)
    total_completions = completed_yes + completed_no
    average_completion_time = filtered_df['completion_time'].mean()
    percentage_yes = (completed_yes / total_completions) * 100 if total_completions > 0 else 0
    percentage_no = (completed_no / total_completions) * 100 if total_completions > 0 else 0

    print(f"Number of entries with type 'ValidatorSamplingOperation': {len(filtered_df)} \n")

    print(f"Number of 'completed' values that are 'yes': {completed_yes}")
    print(f"Number of 'completed' values that are 'no': {completed_no} \n")

    print(f"Percentage of 'completed' values that are 'yes': {percentage_yes:.2f}%")
    print(f"Percentage of 'completed' values that are 'no': {percentage_no:.2f}% \n")

    print(f"Average 'completion_time' for 'ValidatorSamplingOperation': {average_completion_time:.2f}")