#!/bin/bash

# Define the arguments for each run.sh
SIMULATOR_ARGS="config/malicious/dasprotocolevil0.25.cfg" # replace with actual arguments for simulator

# Run the script in the simulator directory
echo "Packaging changes with Maven..."
cd ~/RP2/kademlia-simulator/simulator
mvn package
wait
echo "Running simulator script..."
./run.sh $SIMULATOR_ARGS

# Wait for the simulator script to complete
wait