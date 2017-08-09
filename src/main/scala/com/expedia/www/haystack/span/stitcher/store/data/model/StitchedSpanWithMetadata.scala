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
 *     WITHOUT WARRANTIES OR CONDITIONS OF ASpanGroupWithTimestampNY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 */
package com.expedia.www.haystack.span.stitcher.store.data.model

import com.expedia.open.tracing.stitch.StitchedSpan

/**
  * @param builder protobuf builder for building stitched span object.
  * @param firstSpanSeenAt timestamp when the first span of a given traceId is seen
  */
case class StitchedSpanWithMetadata(builder: StitchedSpan.Builder, firstSpanSeenAt: Long)

