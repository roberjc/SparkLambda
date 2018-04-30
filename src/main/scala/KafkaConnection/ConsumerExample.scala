package KafkaConnection

import java.util

import org.apache.kafka.clients.consumer.KafkaConsumer

import scala.collection.JavaConverters._

object ConsumerExample{

  def main(args: Array[String]): Unit = {


  import java.util.Properties

  val TOPIC="testtopic"

  val  props = new Properties()
  props.put("bootstrap.servers", "http://172.17.0.2:9092")

  props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
  props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer")
  props.put("group.id", "something")

  val consumer = new KafkaConsumer[String, String](props)

  consumer.subscribe(util.Collections.singletonList(TOPIC))

  while(true){
    val records=consumer.poll(100)
    for (record<-records.asScala){
      println(record)
    }

    }
  }
}
