#!/bin/bash
###################################################################
#Script Name    :    run.sh
#Description    :    Run streaming application
#Author         :    roberjc
###################################################################

spark-submit --packages org.apache.spark:spark-sql-kafka-0-10_2.11:2.3.0 --jars typesafe-config-1.3.2.jar,play-json_2.11-2.4.6.jar,scalaj-http_2.11-2.3.0.jar --deploy-mode client --master yarn --class spark.Driver tfm_2.11-0.1.jar
