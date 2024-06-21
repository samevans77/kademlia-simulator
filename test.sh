#!/bin/bash

# Define the arguments for each run.sh
SIMULATOR_ARGS="config/malicious/dasprotocolevil0.25.cfg" # replace with actual arguments for simulator
DATATREATMENT_ARGS="logsDasEvil0.25" # replace with actual arguments for dataTreatment

# Run the script in the simulator directory
echo "Packaging changes with Maven..."
cd ~/RP2/kademlia-simulator/simulator
mvn package
wait
echo "Running simulator script..."
./run.sh $SIMULATOR_ARGS

# Wait for the simulator script to complete
wait

# Run the script in the dataTreatment directory
echo "Running dataTreatment script..."
cd ~/RP2/kademlia-simulator/dataTreatment
./run.sh $DATATREATMENT_ARGS