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

package com.expedia.www.haystack.trace.reader.readers.validators

import com.expedia.open.tracing.api.Trace
import com.expedia.www.haystack.trace.reader.exceptions.InvalidTraceException

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

/**
  * validates if the traceId is non empty and consistent for all the spans
  */
class TraceIdValidator extends TraceValidator {
  override def validate(trace: Trace): Try[Trace] =
    if (trace.getTraceId.isEmpty) {
      Failure(new InvalidTraceException("invalid traceId"))
    } else if (!trace.getChildSpansList.asScala.forall(_.getTraceId == trace.getTraceId)) {
      Failure(new InvalidTraceException(s"span with different traceId are not allowed for traceId=${trace.getTraceId}"))
    } else {
      Success(trace)
    }
}
