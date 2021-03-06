import org.apache.spark.SparkContext
import org.apache.spark.mllib.stat.{MultivariateStatisticalSummary, Statistics}
import org.apache.spark.mllib.linalg.{Vector,Vectors}
import org.apache.spark.rdd.RDD
import org.apache.spark.mllib.regression.LabeledPoint
import org.apache.spark.mllib.regression.LinearRegressionWithSGD
import org.apache.spark.SparkConf

object Chapter0901 {
  //
  def getCurrentDirectory = new java.io.File( "." ).getCanonicalPath
      //
      def parseCarData(inpLine : String) : Array[Double] = {
    val values = inpLine.split(',')
        val mpg = values(0).toDouble
        val displacement = values(1).toDouble
        val hp = values(2).toInt
        val torque = values(3).toInt
        val CRatio = values(4).toDouble
        val RARatio = values(5).toDouble // Rear Axle Ratio
        val CarbBarrells = values(6).toInt
        val NoOfSpeed = values(7).toInt
        val length = values(8).toDouble
        val width = values(9).toDouble
        val weight = values(10).toDouble
        val automatic = values(11).toInt
        return Array(mpg,displacement,hp,
            torque,CRatio,RARatio,CarbBarrells,
            NoOfSpeed,length,width,weight,automatic)
  }
  //
  def carDataToLP(inpArray : Array[Double]) : LabeledPoint = {
    return new LabeledPoint( inpArray(0),Vectors.dense ( inpArray(1), inpArray(2),inpArray(3),
        inpArray(4), inpArray(5),inpArray(6),inpArray(7), inpArray(8),inpArray(9),
        inpArray(10), inpArray(11) ) )
  }
  //
  def main(args: Array[String]) {
    println(getCurrentDirectory)
    val conf = new SparkConf(false) // skip loading external settings
    .setMaster("local") // could be "local[4]" for 4 threads
    .setAppName("Chapter 9")
    .set("spark.logConf", "true")
    val sc = new SparkContext(conf) // ("local","Chapter 9") if using directly
    println(s"Running Spark Version ${sc.version}")
    //
    val dataFile = sc.textFile("/Users/ksankar/fdps-vii/data/car-milage-no-hdr.csv")
    val carRDD = dataFile.map(line => parseCarData(line))
    //
    // Let us find summary statistics
    //
    val vectors: RDD[Vector] = carRDD.map(v => Vectors.dense(v))
    val summary = Statistics.colStats(vectors)
    carRDD.foreach(ln=> {ln.foreach(no => print("%6.2f | ".format(no))); println()})
    print("Count :");println(summary.count)
    print("Max :");println(summary.max)
    print("Min :");println(summary.min)
    print("Mean :");println(summary.mean)
    //
    // correlations
    //
    val hp = vectors.map(x => x(2))
    val weight = vectors.map(x => x(10))
    var corP = Statistics.corr(hp,weight,"pearson") // default
    println("hp to weight : Pearson Correlation = %2.4f".format(corP))
    var corS = Statistics.corr(hp,weight,"spearman") // Need to specify
    println("hp to weight : Spearman Correlation = %2.4f".format(corS)) 
    //
    val raRatio = vectors.map(x => x(5))
    val width = vectors.map(x => x(9))
    corP = Statistics.corr(raRatio,width,"pearson") // default
    println("Rear Axle Ratio to width : Pearson Correlation = %2.4f".format(corP))
    corS = Statistics.corr(raRatio,width,"spearman") // Need to specify
    println("Rear Axle Ratio to width : Spearman Correlation = %2.4f".format(corS)) 
   //
    // Linear Regression
    //
    val carRDDLP = carRDD.map(x => carDataToLP(x)) // create a labeled point RDD
    println(carRDDLP.count())
    println(carRDDLP.first().label)
    println(carRDDLP.first().features)
    //
    // Let us split the data set into training & test set using a very simple filter
    //
    val carRDDLPTrain = carRDDLP.filter( x => x.features(9) <= 4000)
    val carRDDLPTest = carRDDLP.filter( x => x.features(9) > 4000)
    println("Training Set : " + "%3d".format(carRDDLPTrain.count()))
    println("Training Set : " + "%3d".format(carRDDLPTest.count()))
    //
    // Train a Linear Regression Model
    // numIterations = 100, stepsize = 0.000000001
    // without such a small step size the algorithm will diverge
    //
    val mdlLR = LinearRegressionWithSGD.train(carRDDLPTrain,100,0.000000001)
    println(mdlLR.intercept)
    println(mdlLR.weights)
    //
    // Now let us use the model to predict our test set
    //
    val valuesAndPreds = carRDDLPTest.map(p => (p.label, mdlLR.predict(p.features)))
    val mse = valuesAndPreds.map( vp => math.pow( (vp._1 - vp._2),2 ) ).
    reduce(_+_) / valuesAndPreds.count()
    println("Mean Squared Error = " + mse)
    // Let us print what the model predicted
    valuesAndPreds.take(20).foreach(println)
  }
}