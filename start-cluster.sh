#!/bin/bash
###################################################################
#Script Name    :    start-cluster.sh
#Description    :    Start all services of the cluster
#Author         :    roberjc
###################################################################

function runCommand(){
        clush -g $1 $2 & pid=$!
        wait ${pid}
}

function stdout(){
        echo
        printf "##Starting $1\n"
}

# Zookeeper config
# -----------------------------
# Start Zookeeper services
# -----------------------------
stdout "Zookeeper service"
runCommand "all" "$KAFKA_HOME/bin/zookeeper-server-start.sh -daemon $KAFKA_HOME/config/zookeeper.properties"

# Kafka config
# -----------------------------
# Start Kafka brokers
# -----------------------------
stdout "Kafka brokers"
runCommand "all" "$KAFKA_HOME/bin/kafka-server-start.sh -daemon $KAFKA_HOME/config/server.properties"

# HDFS config
# -----------------------------
# Start HDFS services
# -----------------------------
stdout "HDFS"
start-dfs.sh

# Yarn config
# -----------------------------
# Start Yarn services
# -----------------------------
stdout "Yarn"
start-yarn.sh

# Mosquitto config
# -----------------------------
# Start Mosquitto broker
# -----------------------------
stdout "Mosquitto"
runCommand "master" "mosquitto -d"

# Kafka Connect config
# -----------------------------
# Start Kafka Connect service
# -----------------------------
stdout "Kafka Connect with mqtt connector\n"
runCommand "master" "$KAFKA_HOME/bin/connect-standalone.sh -daemon $KAFKA_HOME/config/connect-standalone.properties $KAFKA_HOME/config/mqtt.properties"

wait

printf "Servicios en nodo Master:\n"
runCommand "master" "jps"
echo
printf "Servicios en nodo Slave1:\n"
runCommand "slave1" "jps"
echo
printf "Servicios en nodo Slave2:\n"