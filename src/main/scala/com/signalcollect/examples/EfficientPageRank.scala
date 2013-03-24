/*
 *  @author Philip Stutz
 *  @author Daniel Strebel
 *  
 *  Copyright 2013 University of Zurich
 *      
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *  
 *         http://www.apache.org/licenses/LICENSE-2.0
 *  
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *  
 */

package com.signalcollect.examples

import java.io._
import scala.collection.mutable.ArrayBuffer
import com.signalcollect._
import com.signalcollect.factory.messagebus.BulkAkkaMessageBusFactory
import com.signalcollect.configuration.ExecutionMode._

/**
 * Use GraphSplitter to download the graph and generate the splits.
 *
 * Run with JVM parameters:
 * -Xmx2000m -Xms2000m
 *
 * Computation ran in as little as 1978 milliseconds (best run) on a notebook
 * with a 2.3GHz Core i7 (1 processor, 4 cores, 8 splits for 8 hyper-threads).
 */
object EfficientPageRankLoader extends App {
  val g = new GraphBuilder[Int, Float].withMessageBusFactory(new BulkAkkaMessageBusFactory(512, false)).build
  val numberOfSplits = Runtime.getRuntime.availableProcessors
  val splits = {
    val s = new Array[DataInputStream](numberOfSplits)
    for (i <- 0 until numberOfSplits) {
      s(i) = new DataInputStream(new FileInputStream(s"web-split-$i"))
    }
    s
  }
  for (i <- 0 until numberOfSplits) {
    g.modifyGraph(loadSplit(i), Some(i))
  }
  print("Loading graph ...")
  g.awaitIdle
  println("done.")
  print("Running computation ...")
  val stats = g.execute(ExecutionConfiguration.withExecutionMode(PureAsynchronous).withSignalThreshold(0.01))
  println("done.")
  println(stats)
  val top100 = g.aggregate(new TopKFinder[Float](100))
  top100 foreach (println(_))
  g.shutdown

  def loadSplit(splitIndex: Int)(ge: GraphEditor[Int, Float]) {
    val in = splits(splitIndex)
    var vertexId = CompactIntSet.readUnsignedVarInt(in)
    while (vertexId >= 0) {
      val numberOfEdges = CompactIntSet.readUnsignedVarInt(in)
      var edges = new ArrayBuffer[Int]
      while (edges.length < numberOfEdges) {
        val nextEdge = CompactIntSet.readUnsignedVarInt(in)
        edges += nextEdge
      }
      val vertex = new EfficientPageRankVertex(vertexId)
      vertex.setTargetIds(edges.length, CompactIntSet.create(edges.toArray))
      ge.addVertex(vertex)
      vertexId = CompactIntSet.readUnsignedVarInt(in)
    }
  }
}

/**
 * A version of PageRank that performs faster and uses less memory than the standard version.
 * This version signals only the deltas and collects upon signal delivery.
 */
class EfficientPageRankVertex(val id: Int) extends Vertex[Int, Float] {
  var state = 0.15f
  var lastSignalState = 0f
  var outEdges = 0.0
  def setState(s: Float) {
    state = s
  }
  protected var targetIdArray: Array[Byte] = null
  def setTargetIds(numberOfEdges: Int, compactIntSet: Array[Byte]) = {
    outEdges = numberOfEdges
    targetIdArray = compactIntSet
  }
  def deliverSignal(signal: Any, sourceId: Option[Any]): Boolean = {
    val s = signal.asInstanceOf[Float]
    state = (state + 0.85 * s).toFloat
    true
  }
  override def executeSignalOperation(graphEditor: GraphEditor[Any, Any]) {
    if (outEdges != 0) {
      val signal = ((state.toDouble - lastSignalState.toDouble) / outEdges).toFloat
      CompactIntSet.foreach(targetIdArray, graphEditor.sendSignal(signal, _, None))
    }
    lastSignalState = state
  }
  override def scoreSignal: Double = state - lastSignalState
  def scoreCollect = 0 // Because signals are collected upon delivery.
  def edgeCount = outEdges.toInt
  override def toString = s"${this.getClass.getName}(state=$state)"
  def executeCollectOperation(graphEditor: GraphEditor[Any, Any]) {}
  def afterInitialization(graphEditor: GraphEditor[Any, Any]) = {}
  def beforeRemoval(graphEditor: GraphEditor[Any, Any]) = {}
  override def addEdge(e: Edge[_], graphEditor: GraphEditor[Any, Any]): Boolean = throw new UnsupportedOperationException("Use setTargetIds(...)")
  override def removeEdge(targetId: Any, graphEditor: GraphEditor[Any, Any]): Boolean = throw new UnsupportedOperationException
  override def removeAllEdges(graphEditor: GraphEditor[Any, Any]): Int = throw new UnsupportedOperationException
}