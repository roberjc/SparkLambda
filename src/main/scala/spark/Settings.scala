package spark

import com.typesafe.config._
import model.ProcessedData
import play.api.libs.json.{JsNumber, JsObject, JsString}
import spark.Driver.settings

import scalaj.http.{Http, HttpOptions}

class Settings(config: Config) {

  config.checkValid(ConfigFactory.defaultReference(), "config")

  def this() {
    this(ConfigFactory.load())
  }

  // Spark config
  lazy val master = config.getString("config.spark.master")
  lazy val appName = config.getString("config.spark.app_name")
  lazy val logLevel = config.getString("config.spark.log_level")

  lazy val readFormat = config.getString("config.spark.read.format")
  lazy val optBootstrapServers = config.getString("config.spark.read.option_bootstrap_servers")
  lazy val optSubscribe = config.getString("config.spark.read.option_subscribe")
  lazy val optStartingOffsets = config.getString("config.spark.read.option_starting_offsets")

  lazy val urlWriteServer = config.getString("config.spark.write.server.url")
  lazy val connTimeoutWriteServer = config.getInt("config.spark.write.server.conn_timeout")
  lazy val readTimeoutWriteServer = config.getInt("config.spark.write.server.read_timeout")
  lazy val triggerProcessingTime = config.getString("config.spark.write.trigger_processing_time")
  lazy val outputMode = config.getString("config.spark.write.output_mode")

  // Kafka config
  lazy val bootstrapServers = config.getString("config.kafka.bootstrap_servers")
  lazy val topic = config.getString("config.kafka.topic")
  lazy val startingOffsets = config.getString("config.kafka.starting_offsets")

}