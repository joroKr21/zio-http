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

package zio.http.netty.server

import java.util.concurrent.TimeUnit

import zio._
import zio.stacktracer.TracingImplicits.disableAutoTrace

import zio.http.Server
import zio.http.Server.RequestStreaming
import zio.http.netty.model.Conversions
import zio.http.netty.{HybridContentLengthHandler, Names}

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel._
import io.netty.handler.codec.http.HttpObjectDecoder.DEFAULT_MAX_CHUNK_SIZE
import io.netty.handler.codec.http._
import io.netty.handler.flush.FlushConsolidationHandler
import io.netty.handler.timeout.ReadTimeoutHandler

/**
 * Initializes the netty channel with default handlers
 */
@Sharable
private[zio] final case class ServerChannelInitializer(
  cfg: Server.Config,
  reqHandler: ChannelInboundHandler,
) extends ChannelInitializer[Channel] {

  override def initChannel(channel: Channel): Unit = {
    // !! IMPORTANT !!
    // Order of handlers are critical to make this work
    val pipeline = channel.pipeline()
    // SSL
    // Add SSL Handler if CTX is available
    cfg.sslConfig.foreach { sslCfg =>
      pipeline.addFirst(Names.SSLHandler, new ServerSSLDecoder(sslCfg, cfg))
    }

    cfg.idleTimeout.foreach { timeout =>
      pipeline.addLast(Names.ReadTimeoutHandler, new ReadTimeoutHandler(timeout.toMillis, TimeUnit.MILLISECONDS))
    }

    // ServerCodec
    // Instead of ServerCodec, we should use Decoder and Encoder separately to have more granular control over performance.
    pipeline.addLast(
      Names.HttpRequestDecoder,
      new HttpRequestDecoder(
        new HttpDecoderConfig()
          .setMaxInitialLineLength(cfg.maxInitialLineLength)
          .setMaxHeaderSize(cfg.maxHeaderSize)
          .setMaxChunkSize(DEFAULT_MAX_CHUNK_SIZE)
          .setValidateHeaders(false),
      ),
    )
    pipeline.addLast(Names.HttpResponseEncoder, new HttpResponseEncoder())

    // HttpContentDecompressor
    if (cfg.requestDecompression.enabled)
      pipeline.addLast(Names.HttpRequestDecompression, new HttpContentDecompressor(cfg.requestDecompression.strict, 0))

    cfg.responseCompression.foreach(ops => {
      pipeline.addLast(
        Names.HttpResponseCompression,
        new HttpContentCompressor(ops.contentThreshold, ops.options.map(Conversions.compressionOptionsToNetty): _*),
      )
    })

    // ObjectAggregator
    cfg.requestStreaming match {
      case RequestStreaming.Enabled                         =>
      case RequestStreaming.Disabled(maximumContentLength)  =>
        pipeline.addLast(Names.HttpObjectAggregator, new HttpObjectAggregator(maximumContentLength))
      case RequestStreaming.Hybrid(aggregatedContentLength) =>
        pipeline.addLast(Names.HybridContentLengthHandler, new HybridContentLengthHandler(aggregatedContentLength))
        pipeline.addLast(Names.HttpObjectAggregator, new HttpObjectAggregator(aggregatedContentLength))
    }

    // ExpectContinueHandler
    // Add expect continue handler is settings is true
    if (cfg.acceptContinue) pipeline.addLast(Names.HttpServerExpectContinue, new HttpServerExpectContinueHandler())

    // KeepAliveHandler
    // Add Keep-Alive handler is settings is true
    if (cfg.keepAlive) pipeline.addLast(Names.HttpKeepAliveHandler, new HttpServerKeepAliveHandler)

    pipeline.addLast(Names.HttpServerFlushConsolidation, new FlushConsolidationHandler())

    // RequestHandler
    // Always add ZIO Http Request Handler
    pipeline.addLast(Names.HttpRequestHandler, reqHandler)
    ()
  }

}

object ServerChannelInitializer {
  implicit val trace: Trace = Trace.empty

  val layer: ZLayer[SimpleChannelInboundHandler[HttpObject] with Server.Config, Nothing, ServerChannelInitializer] =
    ZLayer.fromZIO {
      for {
        cfg     <- ZIO.service[Server.Config]
        handler <- ZIO.service[SimpleChannelInboundHandler[HttpObject]]
      } yield ServerChannelInitializer(cfg, handler)
    }
}
