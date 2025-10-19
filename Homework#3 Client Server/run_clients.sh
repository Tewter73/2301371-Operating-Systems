#!/bin/bash

# Number of concurrent clients to simulate.
NUM_CLIENTS=100

for i in $(seq 1 $NUM_CLIENTS)
do
   # Run the client script in the background using '&'.
   python3 client.py &
done

echo "$NUM_CLIENTS client processes started."
