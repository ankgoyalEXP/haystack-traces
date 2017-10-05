/*
 *  Copyright 2017 Expedia, Inc.
 *
 *       Licensed under the Apache License, Version 2.0 (the "License");
 *       you may not use this file except in compliance with the License.
 *      You may obtain a copy of the License at
 *
 *           http://www.apache.org/licenses/LICENSE-2.0
 *
 *       Unless required by applicable law or agreed to in writing, software
 *       distributed under the License is distributed on an "AS IS" BASIS,
 *       WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *       See the License for the specific language governing permissions and
 *       limitations under the License.
 */

package com.expedia.www.haystack.trace.reader.readers

import com.expedia.open.tracing.Span
import com.expedia.open.tracing.api.{FieldNames, _}
import com.expedia.www.haystack.trace.reader.config.entities.TraceTransformersConfiguration
import com.expedia.www.haystack.trace.reader.exceptions.SpanNotFoundException
import com.expedia.www.haystack.trace.reader.metrics.MetricsSupport
import com.expedia.www.haystack.trace.reader.readers.transformers.{TraceTransformationHandler, TraceValidationHandler}
import com.expedia.www.haystack.trace.reader.stores.TraceStore
import org.slf4j.{Logger, LoggerFactory}

import scala.collection.JavaConversions._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

class TraceReader(traceStore: TraceStore, transformersConfig: TraceTransformersConfiguration)(implicit val executor: ExecutionContextExecutor)
  extends TraceTransformationHandler(transformersConfig.transformers)
    with TraceValidationHandler
    with MetricsSupport {
  private val LOGGER: Logger = LoggerFactory.getLogger(s"${classOf[TraceReader]}.search.trace.rejection")
  private val traceRejectedCounter = metricRegistry.meter("search.trace.rejection")

  def getTrace(request: TraceRequest): Future[Trace] = {
    traceStore
      .getTrace(request.getTraceId)
      .flatMap(transformTrace(_) match {
        case Success(span) => Future.successful(span)
        case Failure(ex) => Future.failed(ex)
      })
  }

  private def transformTrace(trace: Trace): Try[Trace] = {
    validate(trace) match {
      case Success(_) => Success(transform(trace))
      case Failure(ex) => Failure(ex)
    }
  }

  def getRawTrace(request: TraceRequest): Future[Trace] = {
    traceStore.getTrace(request.getTraceId)
  }

  def getRawSpan(request: SpanRequest): Future[Span] = {
    traceStore
      .getTrace(request.getTraceId)
      .flatMap(trace => {
        val spanOption = trace.getChildSpansList
          .find(span => span.getSpanId.equals(request.getSpanId))

        spanOption match {
          case Some(span) => Future.successful(span)
          case None => Future.failed(new SpanNotFoundException)
        }
      })
  }

  def searchTraces(request: TracesSearchRequest): Future[TracesSearchResult] = {
    traceStore
      .searchTraces(request)
      .map(
        traces => {
          TracesSearchResult
            .newBuilder()
            .addAllTraces(traces.flatMap(transformTraceIgnoringInvalidSpans))
            .build()
        })
  }

  private def transformTraceIgnoringInvalidSpans(trace: Trace): Option[Trace] = {
    validate(trace) match {
      case Success(_) => Some(transform(trace))
      case Failure(ex) => {
        LOGGER.warn(s"invalid trace rejected", ex)
        traceRejectedCounter.mark()
        None
      }
    }
  }

  def getFieldNames(): Future[FieldNames] = {
    traceStore
      .getFieldNames()
      .map(
        FieldNames
          .newBuilder()
          .addAllNames(_)
          .build())
  }

  def getFieldValues(request: FieldValuesRequest): Future[FieldValues] = {
    traceStore
      .getFieldValues(request)
      .map(
        FieldValues
          .newBuilder()
          .addAllValues(_)
          .build())
  }
}