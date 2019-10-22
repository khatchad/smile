/*******************************************************************************
 * Copyright (c) 2010-2019 Haifeng Li
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation, either version 3 of
 * the License, or (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 *******************************************************************************/
 
package smile.benchmark

import java.util
import java.util.stream.IntStream
import org.apache.commons.csv.CSVFormat
import smile.base.mlp.{Layer, OutputFunction}
import smile.data._
import smile.classification._
import smile.data.`type`.{DataTypes, StructField}
import smile.data.formula.Formula
import smile.feature.Standardizer
import smile.read
import smile.math.MathEx
import smile.math.kernel.GaussianKernel
import smile.validation._
import smile.util._

/**
 *
 * @author Haifeng Li
 */
object USPS {

  def main(args: Array[String]): Unit = {
    benchmark
  }

  def benchmark(): Unit = {
    println("USPS")

    val fields = new util.ArrayList[StructField]
    fields.add(new StructField("class", DataTypes.ByteType))
    (1 to 256).foreach(i => fields.add(new StructField("V" + i, DataTypes.DoubleType)))
    val schema = DataTypes.struct(fields)

    val formula = Formula.lhs("class")
    val train = read.csv(Paths.getTestData("usps/zip.train").toString, delimiter = ' ', header = false, schema = schema)
    val test = read.csv(Paths.getTestData("usps/zip.test").toString, delimiter = ' ', header = false, schema = schema)
    val x = formula.x(train).toArray
    val y = formula.y(train).toIntArray
    val testx = formula.x(test).toArray
    val testy = formula.y(test).toIntArray
    val k = 10

    // Random Forest
    println("Training Random Forest of 200 trees...")
    val forest = randomForest(formula, train, ntrees = 200)
    println("OOB error rate = %.2f%%" format (100.0 * forest.error()))
    val p1 = forest.predict(test)
    println("Test accuracy = %.2f%%" format (100.0 * Accuracy.apply(testy, p1)))

    // Gradient Tree Boost
    println("Training Gradient Tree Boost of 200 trees...")
    val boost = gbm(formula, train, ntrees = 200)
    val p2 = boost.predict(test)
    println("Test accuracy = %.2f%%" format (100.0 * Accuracy.apply(testy, p2)))

    // SVM
    println("Training SVM, one epoch...")
    val kernel = new GaussianKernel(8.0)
    //val svm = OneVersusOne.fit(x, y, (xi: Array[Array[Double]], yi: Array[Int]) => SVM.fit(xi, yi, kernel, 5, 1E-3))
    //val p3 = svm.predict(testx)
    //println("Test accuracy = %.2f%%" format (100.0 * Accuracy.apply(testy, p3)))

    // RBF Network
    println("Training RBF Network...")
    val rbf = rbfnet(x, y, 200)
    val p4 = rbf.predict(testx)
    println("Test accuracy = %.2f%%" format (100.0 * Accuracy.apply(testy, p4)))

    // Logistic Regression
    println("Training Logistic regression...")
    val logistic = logit(x, y, 0.3, 1E-3, 1000)
    val p5 = logistic.predict(testx)
    println("Test accuracy = %.2f%%" format (100.0 * Accuracy.apply(testy, p5)))

    // Neural Network
    val scaler = Standardizer.fit(x)
    val x2 = scaler.transform(x)
    val testx2 = scaler.transform(testx)

    println("Training Neural Network, 10 epoch...")
    val net = new MLP(256,
      Layer.sigmoid(768),
      Layer.sigmoid(192),
      Layer.sigmoid(30),
      Layer.mle(k, OutputFunction.SIGMOID)
    )
    net.setLearningRate(0.1)
    net.setMomentum(0.0)

    (0 until 10).foreach(epoch => {
      println("----- epoch %d -----%n", epoch)
      MathEx.permutate(x2.length).foreach(i => net.update(x2(i), y(i)))
      val prediction = Validation.test(net, testx2)
      val error = Error.apply(testy, prediction)
      println("Test Error = %d", error)
    })
  }
}
