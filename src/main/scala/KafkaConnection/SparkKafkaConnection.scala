package KafkaConnection

import java.nio.charset.StandardCharsets

import org.apache.spark.sql.{Dataset, ForeachWriter, Row, SparkSession}
import org.apache.spark.SparkConf
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions.{from_json, get_json_object}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.expressions.scalalang.typed
import java.io.File
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Calendar

import play.api.libs.json.{JsNumber, JsObject, JsString, JsValue}

import scalaj.http.{Http, HttpOptions}


  object SparkKafkaConnection extends App {

    val path = "./"
    val topic = "testtopic"
    val kafkaBroker = "http://localhost"
    val kafkaPort = "9092"
    val logLevel = "WARN"

    // Init Spark config
    val sc = new SparkConf()
      .setMaster("local[2]")
      //.setMaster("yarn")
      .setAppName("Sensor-Streaming")

    // Init Spark session
    val ss = SparkSession
      .builder
      .config(sc)
      .getOrCreate()

    import ss.implicits._

    // Set log level to Warning messages
    ss.sparkContext.setLogLevel("WARN")

    // Streaming read from Kafka source
    var df = ss
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "192.168.0.147:9092,192.168.0.148:9093,192.168.0.149:9094")
      .option("subscribe", "testtopic")
      .option("startingOffsets", "latest") //earliest
      .load()

    // Schema for Kafka topics
    val schema = new StructType()
      .add("schema", StringType)
      .add("payload", BinaryType)

    // Defining typed dataframe (dataset of DeviceData) for using typed APIs
    val ds = df.selectExpr("cast (value as string) as kafkaJson")
      .select(from_json($"kafkaJson", schema=schema).as("data")).select($"data.payload".as[Array[Byte]])
      .map({data =>
        new String(data, StandardCharsets.UTF_8)
      })
      .select(get_json_object(($"value").cast("string"), "$.sector_id").alias("sector_id").cast("Int")
      , get_json_object(($"value").cast("string"), "$.device_id").alias("device_id").cast("Int")
      , get_json_object(($"value").cast("string"), "$.event_time").alias("event_time").cast("String")
      , get_json_object(($"value").cast("string"), "$.measures.temperature").alias("temperature").cast("Double")
      , get_json_object(($"value").cast("string"), "$.measures.humidity").alias("humidity").cast("Int"))
      //.withColumn("event_time", to_timestamp(from_unixtime(unix_timestamp($"event_time", "dd-MM-yyyy HH:mm:ss"), "dd-MM-yyyy HH:mm:ss")))
      .withColumn("event_time", from_unixtime(unix_timestamp($"event_time", "dd-MM-yyyy HH:mm:ss")).cast(TimestampType))
      //.withColumn("time", from_unixtime(unix_timestamp($"event_time", "dd-MM-yyyy HH:mm:ss"), "HH:mm:ss").cast(TimestampType)).alias("time")
      .as[DeviceData]

/*
    wds
      .writeStream
      .format("console")
        .outputMode("complete")
      .start()
*/

      //.groupBy($"device_id").count()

    // Print schema for dataframe
    println(ds.printSchema())

    // Streaming write with no aggregation (counts) to console
/*
val query = ds
    .writeStream
    .format("console")
    //.outputMode("complete")
    .start()
*/
    // Save data from Kafka topic
    /*
    val query = ds
      .writeStream
      .format("json")
      //.format("com.databricks.spark.avro")
      .option("checkpointLocation", path+"checkpointData")
      .option("path", path+"data")
      .trigger(Trigger.ProcessingTime("30 seconds"))
      .start()
      */
    val query = ds
      .writeStream
      .format("console")
      .outputMode("append")
      .start()

    // Process data to calculate aggregation
    val wds = ds
      .withColumn("event_time", to_timestamp($"event_time"))
      .withWatermark("event_time", "40 seconds")
      .groupBy(
        window($"event_time", "30 seconds"), $"sector_id")
        .agg(
          mean("temperature").alias("mean"),
          count(lit(1)).alias("records")
        )
      .select("window.start", "window.end", "sector_id", "mean", "records")

    // Show aggregation on console
    /*
    wds
      .writeStream
      .format("console")
      .outputMode("complete")
      .start()
    */

    val accum = ss.sparkContext.collectionAccumulator[Int]
    var content = "{\"results\":{\"properties\":["
    var filePath = "/home/result/db.json"
    var times = 0
    //val url = "http://192.168.0.196:3000/properties/"

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
    def save(url: String, json: String): Unit ={
      Http(url).postData(json)
        .header("Content-Type", "application/json")
        .header("Charset", "UTF-8")
        .option(HttpOptions.connTimeout(1000000))
        .option(HttpOptions.readTimeout(5000000)).asString
    }

    wds
      .writeStream
      .foreach(new ForeachWriter[Row] {

        override def process(row: Row): Unit = {
          println(s">> Procesando ${row}")
          val url = "http://192.168.0.196:3000/properties/"

          var sectorId = BigDecimal(row.getAs("sector_id").toString)
          var mean = BigDecimal(row.getAs("mean").toString).setScale(1, BigDecimal.RoundingMode.HALF_UP).toDouble
          var records = BigDecimal(row.getAs("records").toString)

          val sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
          val cal = Calendar.getInstance
          cal.setTime(sdf.parse(row.getTimestamp(0).toString))
          var calendarTime = cal.getTime()

          val date = new SimpleDateFormat("dd-MM-yyyy").format(calendarTime)
          val time = new SimpleDateFormat("HH:mm").format(calendarTime)

          save(url, json = buildProperties(sectorId, mean, records, date, time))

          /*
          if(content.endsWith("]}}")){
            content = content.substring(0, content.length() - 3).concat(",")
        }

          content = content.concat(buildProperties(sectorId, mean, records)).concat("]}}")

          val writer = new PrintWriter(new File(filePath))

          writer.write(content)
          writer.close()
          */

          //accum.add(row.getAs("sector_id").asInstanceOf[Int])
        }

        override def close(errorOrNull: Throwable): Unit = {}

        override def open(partitionId: Long, version: Long): Boolean = true
      })
      .trigger(Trigger.ProcessingTime("30 seconds"))
      .outputMode("update")
      .start()

    // Wait until workflow end
    query.awaitTermination()

}