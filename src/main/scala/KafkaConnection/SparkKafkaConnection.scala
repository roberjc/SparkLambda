package KafkaConnection

import java.nio.charset.StandardCharsets

import org.apache.spark.sql.{Dataset, SparkSession}
import org.apache.spark.SparkConf
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions.{from_json, get_json_object}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.streaming.Trigger
import org.apache.spark.sql.expressions.scalalang.typed


  object SparkKafkaConnection extends App {

    val path = "./"
    val topic = "testtopic"
    val kafkaBroker = "http://localhost"
    val kafkaPort = "9092"
    val logLevel = "WARN"

    // Init Spark config
    val sc = new SparkConf()
      .setMaster("local[2]")
      .setAppName("Spark-Kafka-conn")

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
      .option("kafka.bootstrap.servers", "http://localhost:9092")
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
      .select(get_json_object(($"value").cast("string"), "$.device_id").alias("device_id").cast("Int")
      , get_json_object(($"value").cast("string"), "$.event_time").alias("event_time").cast("String")
      , get_json_object(($"value").cast("string"), "$.measures.temperature").alias("temperature").cast("Double")
      , get_json_object(($"value").cast("string"), "$.measures.humidity").alias("humidity").cast("Int"))
      //.withColumn("event_time", to_timestamp(from_unixtime(unix_timestamp($"event_time", "dd-MM-yyyy HH:mm:ss"), "dd-MM-yyyy HH:mm:ss")))
      .withColumn("event_time", from_unixtime(unix_timestamp($"event_time", "dd-MM-yyyy HH:mm:ss"), "dd-MM-yyyy HH:mm:ss"))
      .withColumn("time", from_unixtime(unix_timestamp($"event_time", "dd-MM-yyyy HH:mm:ss"), "HH:mm").cast(TimestampType)).alias("time")
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

    val query = ds
      .writeStream
      .format("csv")
      //.format("com.databricks.spark.avro")
      .option("checkpointLocation", path+"checkpointData")
      .option("path", path+"data/ficherito.csv")
      .trigger(Trigger.ProcessingTime("20 seconds"))
      .start()

    val wds = ds
      .withColumn("time", to_timestamp($"time"))
      .withWatermark("time", "30 seconds")
      .groupBy(
        window($"time", "20 seconds"), $"device_id")
        .agg(
          mean("temperature").alias("Mean"),
          count(lit(1)).alias("Num Of Records")
        )
      .select("window.start", "window.end", "device_id", "Mean", "Num Of Records")

    /*
    wds
      .writeStream
      .format("json")        // can be "orc", "json", "csv", etc.
      .option("checkpointLocation", path+"checkpointAgg")
      .option("path", path+"agg")
      .trigger(Trigger.ProcessingTime("20 seconds"))
      .start()
    */

    wds
      .writeStream
      .format("console")
      .outputMode("complete")
      .start()

    // Wait until workflow end
    query.awaitTermination()

}