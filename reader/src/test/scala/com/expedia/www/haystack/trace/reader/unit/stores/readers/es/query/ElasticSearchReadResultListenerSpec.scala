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

package com.expedia.www.haystack.trace.reader.unit.stores.readers.es.query

import com.codahale.metrics.{Meter, Timer}
import com.expedia.www.haystack.trace.reader.exceptions.ElasticSearchClientError
import com.expedia.www.haystack.trace.reader.stores.readers.es.ElasticSearchReadResultListener
import com.expedia.www.haystack.trace.reader.unit.BaseUnitTestSpec
import io.searchbox.core.{Search, SearchResult}
import org.easymock.EasyMock

import scala.concurrent.Promise

class ElasticSearchReadResultListenerSpec extends BaseUnitTestSpec {

  describe("ElasticSearch Read Result Listener") {
    it("should invoke successful promise with search result") {
      val promise = mock[Promise[SearchResult]]
      val timer = mock[Timer.Context]
      val failureMeter = mock[Meter]
      val searchResult = mock[SearchResult]
      val request = mock[Search]

      expecting {
        timer.close().once()
        searchResult.getResponseCode.andReturn(200).atLeastOnce()
        promise.success(searchResult).andReturn(promise).once()
      }

      whenExecuting(request, promise, timer, failureMeter, searchResult) {
        val listener = new ElasticSearchReadResultListener(request, promise, timer, failureMeter)
        listener.completed(searchResult)
      }
    }

    it("should invoke failed promise with exception object if response code is not 2xx ") {
      val promise = mock[Promise[SearchResult]]
      val timer = mock[Timer.Context]
      val failureMeter = mock[Meter]
      val searchResult = mock[SearchResult]
      val request = mock[Search]

      expecting {
        timer.close().once()
        searchResult.getResponseCode.andReturn(500).atLeastOnce()
        searchResult.getJsonString.andReturn("json-string").once()
        failureMeter.mark()
        promise.failure(EasyMock.anyObject(classOf[ElasticSearchClientError])).andReturn(promise).once()
      }

      whenExecuting(request, promise, timer, failureMeter, searchResult) {
        val listener = new ElasticSearchReadResultListener(request, promise, timer, failureMeter)
        listener.completed(searchResult)
      }
    }

    it("should invoke failed promise with exception object if failure is generated") {
      val promise = mock[Promise[SearchResult]]
      val timer = mock[Timer.Context]
      val failureMeter = mock[Meter]
      val expectedException = new Exception
      val request = mock[Search]

      expecting {
        timer.close().once()
        failureMeter.mark()
        promise.failure(expectedException).andReturn(promise).once()
      }

      whenExecuting(request, promise, timer, failureMeter) {
        val listener = new ElasticSearchReadResultListener(request, promise, timer, failureMeter)
        listener.failed(expectedException)
      }
    }
  }
}
