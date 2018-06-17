name := "tfm"

version := "0.1"

scalaVersion := "2.11.11"

val sparkVersion = "2.3.0"

libraryDependencies ++= Seq(
  "org.scalaj" % "scalaj-http_2.11" % "2.3.0",
  "org.apache.spark" % "spark-core_2.11" % sparkVersion,
  "org.apache.spark" % "spark-sql_2.11" % sparkVersion,
  "org.apache.spark" % "spark-streaming_2.11" % sparkVersion,
  "org.apache.spark" % "spark-sql-kafka-0-10_2.11" % sparkVersion,
  "org.apache.spark" % "spark-streaming-kafka-0-10_2.11" % sparkVersion,
  "com.typesafe.play" % "play-json_2.11" % "2.4.6",
  "org.eclipse.paho" % "org.eclipse.paho.client.mqttv3" % "1.2.0",
  "org.scalaj" %% "scalaj-http" % "2.3.0",
  "com.databricks" %% "spark-avro" % "4.0.0",
  "org.apache.kafka" % "kafka_2.11" % "0.11.0.0",
  "org.apache.kafka" % "connect-api" % "1.1.0"
)

assemblyMergeStrategy in assembly := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x => MergeStrategy.first
}