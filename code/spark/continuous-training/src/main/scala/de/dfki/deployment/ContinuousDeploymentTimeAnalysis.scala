package de.dfki.deployment

import de.dfki.core.streaming.BatchFileInputDStream
import de.dfki.ml.pipelines.Pipeline
import org.apache.hadoop.io.{LongWritable, Text}
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat
import org.apache.spark.rdd.RDD
import org.apache.spark.streaming.StreamingContext

import scala.collection.mutable.ListBuffer

/**
  * no optimization
  *
  * @author behrouz
  */
class ContinuousDeploymentTimeAnalysis(val history: String,
                                       val streamBase: String,
                                       val evaluationPath: String,
                                       val resultPath: String,
                                       val samplingRate: Double = 0.1,
                                       val daysToProcess: Array[Int] = Array(1, 2, 3, 4, 5),
                                       val slack: Int = 10) extends Deployment {

  override def deploy(streamingContext: StreamingContext, pipeline: Pipeline) = {
    // create rdd of the initial data that the pipeline was trained with
    val data = streamingContext.sparkContext
      .textFile(history)
      .cache()
    data.count()

    val testData = streamingContext.sparkContext.textFile(evaluationPath)
    val streamingSource = new BatchFileInputDStream[LongWritable, Text, TextInputFormat](streamingContext, streamBase, days = daysToProcess)

    var processedRDD: ListBuffer[RDD[String]] = new ListBuffer[RDD[String]]()
    processedRDD += data

    pipeline.model.setMiniBatchFraction(1.0)
    pipeline.model.setNumIterations(5)
    var time = 1

    evaluateStream(pipeline, testData, resultPath)

    while (!streamingSource.allFileProcessed()) {

      val rdd = streamingSource.generateNextRDD().get.map(_._2.toString)
      //pipeline.update(rdd)
      rdd.cache()
      rdd.count()

      processedRDD += rdd

      // update and store update time
      var start = System.currentTimeMillis()
      pipeline.update(rdd)
      var end = System.currentTimeMillis()
      val updateTime = end - start

      storeTrainingTimes(updateTime, resultPath, "update")

      if (time % slack == 0) {

        // transform and store transform time
        start = System.currentTimeMillis()
        val nextBatch = historicalDataRDD(processedRDD, samplingRate, slack, streamingContext.sparkContext)
        val transformed = pipeline.transform(nextBatch)
        transformed.cache()
        transformed.count()
        end = System.currentTimeMillis()
        val transformTime = end - start
        storeTrainingTimes(transformTime, resultPath, "transform")

        // train and store train time
        start = System.currentTimeMillis()
        pipeline.train(transformed)
        end = System.currentTimeMillis()
        storeTrainingTimes(transformTime, resultPath, "train")

        transformed.unpersist(true)
        evaluateStream(pipeline, testData, resultPath)
      }
      time += 1
    }
    processedRDD.foreach {
      r => r.unpersist(true)
    }
  }

}