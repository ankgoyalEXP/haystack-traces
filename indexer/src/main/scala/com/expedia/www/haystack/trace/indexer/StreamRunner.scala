/*
 *  Copyright 2017 Expedia, Inc.
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */

package com.expedia.www.haystack.trace.indexer

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{Executors, TimeUnit}

import com.expedia.www.haystack.trace.commons.config.entities.WhitelistIndexFieldConfiguration
import com.expedia.www.haystack.trace.commons.health.HealthController
import com.expedia.www.haystack.trace.indexer.config.entities._
import com.expedia.www.haystack.trace.indexer.processors.StreamTaskState.StreamTaskState
import com.expedia.www.haystack.trace.indexer.processors.supplier.SpanIndexProcessorSupplier
import com.expedia.www.haystack.trace.indexer.processors.{StateListener, StreamTaskRunnable, StreamTaskState}
import com.expedia.www.haystack.trace.indexer.store.SpanBufferMemoryStoreSupplier
import com.expedia.www.haystack.trace.indexer.writers.TraceWriter
import com.expedia.www.haystack.trace.indexer.writers.cassandra.CassandraWriter
import com.expedia.www.haystack.trace.indexer.writers.es.ElasticSearchWriter
import com.expedia.www.haystack.trace.indexer.writers.kafka.KafkaWriter
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory

import scala.collection.mutable

class StreamRunner(kafkaConfig: KafkaConfiguration,
                   accumulatorConfig: SpanAccumulatorConfiguration,
                   esConfig: ElasticSearchConfiguration,
                   cassandraConfig: CassandraWriteConfiguration,
                   indexConfig: WhitelistIndexFieldConfiguration) extends AutoCloseable with StateListener {

  implicit private val executor = scala.concurrent.ExecutionContext.Implicits.global

  private val LOGGER = LoggerFactory.getLogger(classOf[StreamRunner])

  private val isClosing = new AtomicBoolean(false)
  private val streamThreadExecutor = Executors.newFixedThreadPool(kafkaConfig.numStreamThreads)
  private val taskRunnables = mutable.ListBuffer[StreamTaskRunnable]()

  private val writers: Seq[TraceWriter] = {
    val writers = mutable.ListBuffer[TraceWriter]()
    writers += new CassandraWriter(cassandraConfig)(executor)
    writers += new ElasticSearchWriter(esConfig, indexConfig)

    if(StringUtils.isNotEmpty(kafkaConfig.produceTopic)) {
      writers += new KafkaWriter(kafkaConfig.producerProps, kafkaConfig.produceTopic)
    }
    writers
  }

  def start(): Unit = {
    LOGGER.info("Starting the span indexing stream..")

    val storeSupplier = new SpanBufferMemoryStoreSupplier(accumulatorConfig.minTracesPerCache, accumulatorConfig.maxEntriesAllStores)
    val streamProcessSupplier = new SpanIndexProcessorSupplier(accumulatorConfig, storeSupplier, writers)

    for(streamId <- 0 until kafkaConfig.numStreamThreads) {
      val task = new StreamTaskRunnable(streamId, kafkaConfig, streamProcessSupplier)
      task.setStateListener(this)
      taskRunnables += task
      streamThreadExecutor.execute(task)
    }
  }

  override def close(): Unit = {
    if(!isClosing.getAndSet(true)) {
      val shutdownThread = new Thread() {
        closeStreamTasks()
        closeWriters()
        waitAndTerminate()
      }
      shutdownThread.setDaemon(true)
      shutdownThread.run()
    }
  }

  override def onTaskStateChange(state: StreamTaskState): Unit = {
    if(state == StreamTaskState.FAILED) {
      LOGGER.error("Thread state has changed to 'FAILED', so tearing down the app")
      HealthController.setUnhealthy()
    }
  }

  private def closeStreamTasks(): Unit = {
    LOGGER.info("Closing all the stream tasks..")
    taskRunnables foreach { _.close() }
  }

  private def closeWriters(): Unit = {
    LOGGER.info("Closing all the writers now..")
    writers foreach { _.close }
  }

  private def waitAndTerminate(): Unit = {
    LOGGER.info("Shutting down the stream executor service")
    streamThreadExecutor.shutdown()
    streamThreadExecutor.awaitTermination(kafkaConfig.consumerCloseTimeoutInMillis, TimeUnit.MILLISECONDS)

    // bluntly shutdown the app
    if(kafkaConfig.exitJvmAfterClose) System.exit(1)
  }
}