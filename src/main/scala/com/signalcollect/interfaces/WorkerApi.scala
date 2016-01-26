/*
 *  @author Philip Stutz
 *
 *  Copyright 2010 University of Zurich
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

package com.signalcollect.interfaces

import com.signalcollect.Edge
import com.signalcollect.GraphEditor
import com.signalcollect.Vertex

trait WorkerApi[Id, Signal] {
  def addVertex(vertex: Vertex[Id, _, Id, Signal]): Unit
  def addEdge(sourceId: Id, edge: Edge[Id]): Unit
  def removeVertex(vertexId: Id): Unit
  def removeEdge(edgeId: EdgeId[Id]): Unit
  def processSignalWithSourceId(signal: Signal, targetId: Id, sourceId: Id): Unit
  def processSignalWithoutSourceId(signal: Signal, targetId: Id): Unit
  def modifyGraph(graphModification: GraphEditor[Id, Signal] => Unit, vertexIdHint: Option[Id] = None): Unit
  def loadGraph(graphModifications: Iterator[GraphEditor[Id, Signal] => Unit], vertexIdHint: Option[Id] = None): Unit

  def setSignalThreshold(signalThreshold: Double): Unit
  def setCollectThreshold(collectThreshold: Double): Unit

  def recalculateScores(): Unit
  def recalculateScoresForVertexWithId(vertexId: Id): Unit

  def forVertexWithId[VertexType <: Vertex[Id, _, Id, Signal], ResultType](vertexId: Id, f: VertexType => ResultType): ResultType
  def foreachVertex(f: Vertex[Id, _, Id, Signal] => Unit): Unit
  def foreachVertexWithGraphEditor(f: GraphEditor[Id, Signal] => Vertex[Id, _, Id, Signal] => Unit): Unit

  def aggregateOnWorker[WorkerResult](aggregationOperation: ComplexAggregation[WorkerResult, _]): WorkerResult
  def aggregateAll[WorkerResult, EndResult](aggregationOperation: ComplexAggregation[WorkerResult, EndResult]): EndResult

  def pauseComputation(): Unit
  def startComputation(): Unit

  def signalStep(): Boolean // always returns true, just to make it blocking.
  def collectStep(): Boolean

  def getWorkerStatistics(): WorkerStatistics
  def getIndividualWorkerStatistics(): List[WorkerStatistics]

  def reset(): Unit
  def shutdown(): Unit

  def initializeIdleDetection(): Unit

  //TODO: Implement system information accessors on node instead.
  def getNodeStatistics(): NodeStatistics
  def getIndividualNodeStatistics(): List[NodeStatistics]

  /**
   * Creates a snapshot of all the vertices in all workers.
   * Does not store the toSignal/toCollect collections or pending messages.
   * Should only be used when the workers are idle.
   * Overwrites any previous snapshot that might exist.
   */
  def snapshot(): Unit

  /**
   * Restores the last snapshot of all the vertices in all workers.
   * Does not store the toSignal/toCollect collections or pending messages.
   * Should only be used when the workers are idle.
   */
  def restore(): Unit

  /**
   * Deletes the worker snapshots if they exist.
   */
  def deleteSnapshot(): Unit

}
