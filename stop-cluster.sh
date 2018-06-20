#!/bin/bash
###################################################################
#Script Name    :    stop-cluster.sh
#Description    :    Stop all services of the cluster
#Author         :    roberjc
###################################################################

function runCommand(){
        clush -g $1 $2 & pid=$!
        wait ${pid}
}

function stdout(){
        echo
        printf "##Stopping $1\n"
}

# Zookeeper config
# -----------------------------
# Stop Zookeeper services
# -----------------------------
stdout "Zookeeper service"
runCommand "all" "$KAFKA_HOME/bin/zookeeper-server-stop.sh"

# Kafka config
# -----------------------------
# Stop Kafka services
# -----------------------------
stdout "Kafka brokers"

# Master
if jps | grep "Kafka";then
    kill -9 `jps | grep "Kafka" | cut -d " " -f 1`
fi

# Slave1
if clush -w @slave1 jps | grep "Kafka" ;then
    kafkaPid=`clush -w @slave1 jps | grep "Kafka" | cut -d " " -f 2`
    clush -w @slave1 kill -9 ${kafkaPid}
fi

# Slave2
if clush -w @slave2 jps | grep "Kafka" ;then
    kafkaPid=`clush -w @slave2 jps | grep "Kafka" | cut -d " " -f 2`
    clush -w @slave2 kill -9 ${kafkaPid}
fi

# Kafka config
# -----------------------------
# Stop HDFS services
# -----------------------------
stdout "HDFS"
stop-dfs.sh

# Yarn config
# -----------------------------
# Stop Yarn services
# -----------------------------
stdout "Yarn"
stop-yarn.sh

# Mosquitto config
# -----------------------------
# Stop Mosquitto broker
# -----------------------------
stdout "Mosquitto"
service mosquitto stop

# Kafka Connect config
# -----------------------------
# Stop Kafka Connect service
# -----------------------------
stdout "Kafka Connect with mqtt connector\n"

if jps | grep "ConnectStandalone";then
    kill -9 `jps | grep "ConnectStandalone" | cut -d " " -f 1`
fi

wait

printf "Servicios en nodo Master:\n"
runCommand "master" "jps"
echo
printf "Servicios en nodo Slave1:\n"
runCommand "slave1" "jps"
echo
printf "Servicios en nodo Slave2:\n"
runCommand "slave2" "jps"