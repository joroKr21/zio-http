/*
 * Copyright 2021 - 2023 Sporta Technologies PVT LTD & the ZIO HTTP contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package zio.http.netty

import java.nio.charset.Charset

import scala.jdk.CollectionConverters._

import zio.http.QueryParams

import io.netty.handler.codec.http.{QueryStringDecoder, QueryStringEncoder}

private[http] object NettyQueryParamEncoding {
  final def decode(queryStringFragment: String, charset: Charset): QueryParams = {
    if (queryStringFragment == null || queryStringFragment.isEmpty) {
      QueryParams.empty
    } else {
      val decoder = new QueryStringDecoder(queryStringFragment, charset, false)
      QueryParams(decoder.parameters())
    }
  }

  final def encode(baseUri: String, queryParams: QueryParams, charset: Charset): String = {
    val encoder = new QueryStringEncoder(baseUri, charset)
    queryParams.seq.foreach { entry =>
      val key    = entry.getKey
      val values = entry.getValue.asScala
      if (key != "") {
        if (values.isEmpty) {
          encoder.addParam(key, "")
        } else
          values.foreach(value => encoder.addParam(key, value))
      }
    }

    encoder.toString
  }
}
