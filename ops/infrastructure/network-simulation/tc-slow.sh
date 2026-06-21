#!/bin/bash

# Simulate slow network using Linux traffic control
# Usage: ./tc-slow.sh <interface> <bandwidth> <latency> <packet-loss>

INTERFACE=${1:-eth0}
BANDWIDTH=${2:-1mbit}
LATENCY=${3:-100ms}
LOSS=${4:-1%}

# Clear existing rules
tc qdisc del dev $INTERFACE root 2>/dev/null || true

# Add new rules
tc qdisc add dev $INTERFACE root handle 1: htb default 30
tc class add dev $INTERFACE parent 1: classid 1:1 htb rate $BANDWIDTH
tc class add dev $INTERFACE parent 1:1 classid 1:10 htb rate $BANDWIDTH ceil $BANDWIDTH
tc qdisc add dev $INTERFACE parent 1:10 handle 10: netem delay $LATENCY loss $LOSS
tc filter add dev $INTERFACE protocol ip parent 1:0 prio 1 u32 match ip dst 0.0.0.0/0 flowid 1:10

echo "Network simulation applied: $BANDWIDTH bandwidth, $LATENCY latency, $LOSS packet loss"
