import os
import re
import matplotlib.pyplot as plt
import numpy as np

# Directory containing the result files
results_dir = 'results'

# Initialize data structures
data_time = {}
data_rate_yes = {}
data_rate_no = {}

# Regular expression to parse filenames
filename_re = re.compile(r'(\d+)pcEvilSec(True(\d+)Fail|False)_(\d+).txt')

# Function to read and parse a file
def parse_file(filepath):
    avg_time = None
    perc_yes = None
    perc_no = None
    with open(filepath, 'r') as file:
        lines = file.readlines()
        for line in lines:
            if "Average 'completion_time'" in line:
                avg_time = float(line.split(':')[-1].strip())
            if "Percentage of 'completed' values that are 'yes'" in line:
                perc_yes = float(line.split(':')[-1].strip().replace('%', ''))
            if "Percentage of 'completed' values that are 'no'" in line:
                perc_no = float(line.split(':')[-1].strip().replace('%', ''))
    return avg_time, perc_yes, perc_no

# Read filenames and extract data
for filename in os.listdir(results_dir):
    match = filename_re.match(filename)
    if match:
        pc_value = int(match.group(1))
        fail_count = int(match.group(3)) if match.group(2) != 'False' else 0
        avg_completion_time, perc_yes, perc_no = parse_file(os.path.join(results_dir, filename))
        
        if pc_value not in data_time:
            data_time[pc_value] = {}
            data_rate_yes[pc_value] = {}
            data_rate_no[pc_value] = {}
        data_time[pc_value][fail_count] = avg_completion_time
        data_rate_yes[pc_value][fail_count] = perc_yes
        data_rate_no[pc_value][fail_count] = perc_no

# Sort the data by pc_value
sorted_data_time = sorted(data_time.items())
sorted_data_rate_yes = sorted(data_rate_yes.items())
sorted_data_rate_no = sorted(data_rate_no.items())

# Plotting average completion times
for pc_value, times in sorted_data_time:
    fail_counts = sorted(times.keys())
    avg_times = [times[fail] for fail in fail_counts]
    
    plt.figure()
    plt.bar(fail_counts, avg_times, tick_label=[f'{fail}Fail' if fail != 0 else 'Baseline' for fail in fail_counts])
    plt.xlabel('Failure Count')
    plt.ylabel('Average Completion Time')
    plt.title(f'Average Completion Time for {pc_value}pcEvilSec')
    plt.show()

# Plotting average completion rates (Yes)
for pc_value, rates in sorted_data_rate_yes:
    fail_counts = sorted(rates.keys())
    avg_rates_yes = [rates[fail] for fail in fail_counts]
    
    plt.figure()
    plt.bar(fail_counts, avg_rates_yes, tick_label=[f'{fail}Fail' if fail != 0 else 'Baseline' for fail in fail_counts])
    plt.xlabel('Failure Count')
    plt.ylabel('Average Completion Rate (%) - Yes')
    plt.title(f'Average Completion Rate (Yes) for {pc_value}pcEvilSec')
    plt.show()

# Plotting average completion rates (No)
for pc_value, rates in sorted_data_rate_no:
    fail_counts = sorted(rates.keys())
    avg_rates_no = [rates[fail] for fail in fail_counts]
    
    plt.figure()
    plt.bar(fail_counts, avg_rates_no, tick_label=[f'{fail}Fail' if fail != 0 else 'Baseline' for fail in fail_counts])
    plt.xlabel('Failure Count')
    plt.ylabel('Average Completion Rate (%) - No')
    plt.title(f'Average Completion Rate (No) for {pc_value}pcEvilSec')
    plt.show()
