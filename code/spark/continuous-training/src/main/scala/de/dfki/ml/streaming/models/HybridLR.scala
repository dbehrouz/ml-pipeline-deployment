package de.dfki.ml.streaming.models

import de.dfki.ml.classification.LogisticRegressionWithSGD
import de.dfki.ml.optimization.SquaredL2Updater
import org.apache.spark.mllib.classification.LogisticRegressionModel
import org.apache.spark.mllib.optimization.Updater

/**
  * @author bede01.
  */
class HybridLR(private var stepSize: Double,
               private var numIterations: Int,
               private var regParam: Double,
               private var miniBatchFraction: Double,
               private var updater: Updater)
  extends HybridModel[LogisticRegressionModel, LogisticRegressionWithSGD] {


  /**
    * Construct a StreamingLogisticRegression object with default parameters:
    * {stepSize: 0.1, numIterations: 50, miniBatchFraction: 1.0, regParam: 0.0}.
    * Initial weights must be set before using trainOn or predictOn
    * (see `StreamingLinearAlgorithm`)
    */
  def this() = this(0.1, 50, 0.0, 1.0, new SquaredL2Updater)

  protected val algorithm = new LogisticRegressionWithSGD(stepSize, numIterations, regParam, miniBatchFraction, updater)

  protected var model: Option[LogisticRegressionModel] = _
}
