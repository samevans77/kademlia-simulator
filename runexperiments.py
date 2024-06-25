import json
import os
import subprocess
import time

# Path to the JSON config file
config_file = 'experiments_config.json'

run_file = 'run.sh'

# Load the JSON config
with open(config_file, 'r') as f:
    experiments = json.load(f)

# Function to update configurations
def update_config(experiment):
    # Extract values from the JSON config
    attack_time = experiment['KademliaCommonConfig']['ATTACK_TIME']
    security_active = experiment['KademliaCommonConfig']['SECURITY_ACTIVE']
    max_fails = experiment['KademliaCommonConfig']['MAX_FAILURES']
    evil_node_ratio = experiment['dasprotocolevil0.25']['evilNodeRatioValidator']
    sim_time = experiment['dasprotocolevil0.25']['sim_time']

    # Update KademliaCommonConfig.java
    java_file = 'simulator/src/main/java/peersim/kademlia/das/KademliaCommonConfigDas.java'
    with open(java_file, 'r') as f:
        java_lines = f.readlines()

    with open(java_file, 'w') as f:
        for line in java_lines:
            if 'public static long ATTACK_TIME' in line:
                line = f'    public static long ATTACK_TIME = {attack_time};\n'
            elif 'public static boolean SECURITY_ACTIVE' in line:
                line = f'    public static boolean SECURITY_ACTIVE = {"true" if security_active else "false"};\n'
            elif 'public static boolean SECURITY_ACTIVE' in line:
                line = f'    public static int MAX_RATED_LEVEL = {max_fails};\n'
            f.write(line)

    # Update dasprotocolevil0.25.cfg
    cfg_file = 'simulator/config/malicious/dasprotocolevil0.25.cfg'
    with open(cfg_file, 'r') as f:
        cfg_lines = f.readlines()

    with open(cfg_file, 'w') as f:
        for line in cfg_lines:
            if 'protocol.7evildasprotocol.attackTime' in line:
                line = f'protocol.7evildasprotocol.attackTime {attack_time}\n'
            elif 'init.1uniqueNodeID.evilNodeRatioValidator' in line:
                line = f'init.1uniqueNodeID.evilNodeRatioValidator {evil_node_ratio}\n'
            elif 'SIM_TIME' in line:
                if 'simulation.endtime' not in line:
                    line = f'SIM_TIME {sim_time}\n'
            elif 'control.4.logfolder' in line:
                line = f"control.4.logfolder {experiment['experiment_name']}"
            f.write(line)

    print(f"Configuration files for experiment '{experiment['experiment_name']}' updated successfully.")

def package_changes(target_directory):
    try:
        # Check if the directory exists
        if not os.path.isdir(target_directory):
            raise FileNotFoundError(f"The directory {target_directory} does not exist.")

        # Execute the mvn package command in the target directory
        result = subprocess.run(["mvn", "package"], capture_output=True, text=True, cwd=target_directory)

        # Print the output
        print(result.stdout)
        print(result.stderr)
    except Exception as e:
        print(f"An error occurred: {e}")

# Iterate over each experiment configuration and update configurations
for experiment in experiments:
    print("Updating " + experiment["experiment_name"])
    update_config(experiment)
    os.system("./test.sh")
    print("Waiting 5 seconds for files to populate...")
    time.sleep(5)
    print("Running data collection...")
    os.system(f"./dataTreatment/run.sh {experiment['experiment_name']} {12000} {experiment['KademliaCommonConfig']['ATTACK_TIME']} {experiment['experiment_name']}")
    
print("All experiments done!")