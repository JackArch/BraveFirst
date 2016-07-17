package org.brave.spark.streaming

import kafka.serializer.StringDecoder
import org.apache.spark.SparkConf
import org.apache.spark.storage.StorageLevel
import org.apache.spark.streaming._
import org.apache.spark.streaming.kafka._

/**
  * Created by JasonHong on 2016/7/16.
  */
object KafkaSparkStreaming {

  def main(args: Array[String]) {
    if(args.length<5){
      System.err.print(s"""
                          |Usage: KafkaSparkStreaming <maxRate> <batchDuration> <concurrentJobs> <topics> <kParams>
        """.stripMargin)
      System.exit(1)
    }

    val maxRate = args(0) //1000
    val batchDuration = args(1).toInt //60
    val concurrentJobs = args(2) //5
    val iTopic = args(3) //sparkmovie2
    val kParams = {
      var kvm: Map[String,String] = Map()

      for {kv <- args(4).split(";")
           kva = kv.split("=")}
      {
        kvm += (kva(0) -> kva(1))
      }
      kvm
    }

    val checkpointDirectory = "/checkpoint/KafkaStream"
    val ssc = StreamingContext.getOrCreate(checkpointDirectory,
      () => {
        createContext( checkpointDirectory,maxRate,batchDuration,concurrentJobs,iTopic,kParams)
      })

    ssc.start()
    ssc.awaitTermination()

  }

  def createContext(checkpointDirectory: String,maxRate: String,batchDuration:Int,concurrentJobs:String,iTopic:String,kParams:Map[String,String]): StreamingContext = {

    // If you do not see this printed, that means the StreamingContext has been loaded
    // from the new checkpoint
    println("Creating new KafkaStreaming context")

    val sparkConf = new SparkConf().setAppName("KafkaStreaming")
//      .set("spark.streaming.backpressure.enabled","true")
      .set("spark.scheduler.mode","FAIR")
      .set("spark.shuffle.consolidateFiles", "true")
      .set("spark.streaming.receiver.maxRate",maxRate)
      .set("spark.serializer","org.apache.spark.serializer.KryoSerializer")
      .set("spark.streaming.concurrentJobs",concurrentJobs)
      .set("spark.scheduler.mode","FAIR")
      .set("spark.cleaner.ttl","480")
    //      .set("spark.streaming.receiver.writeAheadLog.enable","true")
    val ssc = new StreamingContext(sparkConf, Seconds(batchDuration))
    //        ssc.remember(Minutes(5))
    val kafkaStream = KafkaUtils.createStream[String, String, StringDecoder, StringDecoder](ssc, kParams, Map(iTopic -> 1), StorageLevel.MEMORY_AND_DISK_SER).map(_._2)


    kafkaStream.foreachRDD { rdd =>
      rdd.saveAsTextFile("/data/movieStream")

    }

    ssc
  }


}