package KafkaConnection

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.sql.SparkSession
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.streaming._
import org.apache.spark.streaming.dstream.DStream
import org.apache.spark.streaming.kafka010._
import org.apache.spark.sql.functions.{explode, split}
import org.apache.spark.sql.types._
import org.apache.spark.sql.functions.{from_json, get_json_object, json_tuple}
import org.apache.spark.sql.streaming.ProcessingTime

  object SparkKafkaConnection extends App {

    val path = "./"

    // Create context with 2 second batch interval
    val sc = new SparkConf()
      .setMaster("local[2]")
      .setAppName("Spark-Kafka-conn")

    val ss = SparkSession
      .builder
      .config(sc)
      .getOrCreate()

    import ss.implicits._

    ss.sparkContext.setLogLevel("WARN")

    /*
    val mySchema = StructType(Array(
      StructField("id", IntegerType),
      StructField("name", StringType),
      StructField("year", IntegerType),
      StructField("rating", DoubleType),
      StructField("duration", IntegerType)
    ))
    */

    // Construct a streaming DataFrame that reads from topic
    var df = ss
      .readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", "http://localhost:9092")
      .option("subscribe", "testtopic")
      .option("startingOffsets", "earliest")
      .load()

    df.selectExpr("CAST(key AS STRING)", "CAST(value AS STRING)")

    val query =
      df
        .writeStream
        .format("console")
        .start()

    query.awaitTermination()

  /*
    val query =
      df
        .writeStream
        .format("console")
        .outputMode("complete")
        .start()

    val query = df1.writeStream
      .option("checkpointLocation", path + "/checkpointData")
      .format("com.databricks.spark.avro")
      .start(path + "/dataAvro")

    val query = df1.writeStream
      .outputMode("append")
      .queryName("table")
      .format("console")
      .start()

    query.awaitTermination()


    ss.stop()



    df.printSchema()

    var streamingSelectDF =
      df
        .select(get_json_object(($"value").cast("string"), "$.payload").alias("zip"))
        .groupBy($"zip")
        .count()


    val query =
      streamingSelectDF
        .writeStream
        .format("console")
        .outputMode("complete")
        .start()

    df.writeStream
      .format("console")
      .start()
      .awaitTermination()

     val ssc = new StreamingContext(sc, Seconds(2))


      // Create direct kafka stream with brokers and topics
      val topicSet = topics.split(",").toSet
      val kafkaParams = Map[String, Object](
        ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG -> broker,
        ConsumerConfig.GROUP_ID_CONFIG -> groupId,
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer],
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG -> classOf[StringDeserializer])
      val directKafkaStream = KafkaUtils.createDirectStream[String, String](
        ssc,
        LocationStrategies.PreferConsistent,
        ConsumerStrategies.Subscribe[String, String](topicSet, kafkaParams))

      // Get the lines, split them into words, count the words and print
      val inputJsonStream: DStream[String] = directKafkaStream.map(_.value)

      val strings: List[String] = inputJsonStream.foreachRDD(x -> )

      strings.forEach(x -> {
        Dataset<Row> inputDataset = spark.read().option("multiLine",true).option("mode", "PERMISSIVE").json(inputRDD);
        inputDataset.printSchema();
      });

      println("MENSAJES RECIBIDOS")
      //val words = lines.flatMap(_.split("},"))
      //val wordCounts = words.map(x => (x, 1L)).reduceByKey(_ + _)
      //wordCounts.print()
      lines.print()


      // Start the computation

      ssc.start()
      ssc.awaitTermination()

*/


  }