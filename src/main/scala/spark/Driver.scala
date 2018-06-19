package spark

import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Calendar

import org.apache.spark.SparkConf
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.types._
import org.apache.spark.sql.{ForeachWriter, Row, SparkSession}
import play.api.libs.json.{JsNumber, JsObject, JsString}

import scalaj.http.{Http, HttpOptions}
import model.{DeviceData, ProcessedData}


object Driver extends App {

  val settings = new Settings()

  // Init Spark config
  val sc = new SparkConf()
    .setMaster(settings.master)
    .setAppName(settings.appName)

  // Init Spark session
  val ss = SparkSession
    .builder
    .config(sc)
    .getOrCreate()

  import ss.implicits._

  // Set log level to Warning messages
  ss.sparkContext.setLogLevel(settings.logLevel)

  // Streaming read from Kafka source
  var readStream = ss
    .readStream
    .format(settings.readFormat)
    .option(settings.optBootstrapServers, settings.bootstrapServers)
    .option(settings.optSubscribe, settings.topic)
    .option(settings.optStartingOffsets, settings.startingOffsets)
    .load()

  // Schema for Kafka topics
  val schema = new StructType()
    .add("schema", StringType)
    .add("payload", BinaryType)

  // Defining typed dataframe (dataset of DeviceData) for using typed APIs
  val parse = readStream.selectExpr("cast (value as string) as kafkaJson")
    .select(from_json($"kafkaJson", schema=schema).as("data")).select($"data.payload".as[Array[Byte]])
    .map({data =>
      new String(data, StandardCharsets.UTF_8)
    })
    .select(get_json_object(($"value").cast("string"), "$.sector_id").alias("sector_id").cast("Int")
    , get_json_object(($"value").cast("string"), "$.device_id").alias("device_id").cast("Int")
    , get_json_object(($"value").cast("string"), "$.event_time").alias("event_time").cast("String")
    , get_json_object(($"value").cast("string"), "$.measures.temperature").alias("temperature").cast("Double")
    , get_json_object(($"value").cast("string"), "$.measures.humidity").alias("humidity").cast("Int"))
    .withColumn("event_time", from_unixtime(unix_timestamp($"event_time", "dd-MM-yyyy HH:mm:ss")).cast(TimestampType))
    .as[DeviceData]

  // Print schema for dataframe
  println(">> Data Schema:")
  println(s"${parse.printSchema()}")

  val writeRaw = parse
    .writeStream
    .outputMode("append")
    .option("path", "hdfs://master:9000/user/data/raw.db/device")
    .option("checkpointLocation", "hdfs://master:9000/user/data/checkpoint/device")
    .format("json")
    .start()

  // Process data to calculate aggregation
  val processed = parse
    .withColumn("event_time", to_timestamp($"event_time"))
    .withWatermark("event_time", "40 seconds")
    .groupBy(
      window($"event_time", "30 seconds"), $"sector_id")
      .agg(
        mean("temperature").alias("mean"),
        count(lit(1)).alias("records")
      )
    .select("window.start", "window.end", "sector_id", "mean", "records")

  def buildProperties(sectorId: BigDecimal, mean: BigDecimal, records: BigDecimal, date: String, time: String): String ={
    JsObject(
      Seq(
        "id" -> JsNumber(sectorId),
        "mean" -> JsNumber(mean),
        "records" -> JsNumber(records),
        "date" -> JsString(date),
        "time" -> JsString(time))
    ).toString()
  }
  def save(json: String): Unit ={
    val url = settings.urlWriteServer

    Http(url).postData(json)
      .header("Content-Type", "application/json")
      .header("Charset", "UTF-8")
      .option(HttpOptions.connTimeout(settings.connTimeoutWriteServer))
      .option(HttpOptions.readTimeout(settings.readTimeoutWriteServer)).asString
  }

  val writeProcessed = processed
    .writeStream
    .foreach(new ForeachWriter[Row] {

      override def process(row: Row): Unit = {

        var sectorId = BigDecimal(row.getAs("sector_id").toString)
        var mean = BigDecimal(row.getAs("mean").toString).setScale(1, BigDecimal.RoundingMode.HALF_UP).toDouble
        var records = BigDecimal(row.getAs("records").toString)
        val date = new SimpleDateFormat("dd-MM-yyyy").format(row.getTimestamp(0))
        val time = new SimpleDateFormat("HH:mm").format(row.getTimestamp(0))

        println(s">> Processing data: ${sectorId} | ${mean} | ${records} | ${date} | ${time}")

        save(json = buildProperties(sectorId, mean, records, date, time))
      }

      override def close(errorOrNull: Throwable): Unit = {}

      override def open(partitionId: Long, version: Long): Boolean = true
    })
    .trigger(Trigger.ProcessingTime(settings.triggerProcessingTime))
    .outputMode(settings.outputMode)
    .start()

  // Wait until workflow end
  writeProcessed.awaitTermination()

}