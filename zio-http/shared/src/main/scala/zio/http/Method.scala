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

package zio.http

import zio.http.codec.PathCodec

/**
 * Represents an HTTP method, such as GET, PUT, POST, or DELETE.
 */
sealed trait Method { self =>

  /**
   * A right-biased way of combining two methods. If either method is default,
   * the other will be returned. Otherwise, the right method will be returned.
   */
  def ++(that: Method): Method =
    if (that == Method.ANY) self
    else that

  def /[A](that: PathCodec[A]): RoutePattern[A] =
    if (that == PathCodec.empty) RoutePattern.fromMethod(self).asInstanceOf[RoutePattern[A]]
    else RoutePattern.fromMethod(self) / that

  def matches(that: Method): Boolean =
    if (self == Method.ANY) true
    else if (that == Method.ANY) true
    else self == that

  /**
   * The name of the method, as it appears in the HTTP request.
   */
  val name: String

  def render: String = if (self == Method.ANY) "*" else self.name

  override def toString: String = render
}

object Method {

  def fromString(method: String): Method =
    method.toUpperCase match {
      case POST.name    => Method.POST
      case GET.name     => Method.GET
      case OPTIONS.name => Method.OPTIONS
      case HEAD.name    => Method.HEAD
      case PUT.name     => Method.PUT
      case PATCH.name   => Method.PATCH
      case DELETE.name  => Method.DELETE
      case TRACE.name   => Method.TRACE
      case CONNECT.name => Method.CONNECT
      case _            => Method.CUSTOM(method)
    }

  final case class CUSTOM(name: String) extends Method

  case object OPTIONS extends Method { val name = "OPTIONS" }
  case object GET     extends Method { val name = "GET"     }
  case object HEAD    extends Method { val name = "HEAD"    }
  case object POST    extends Method { val name = "POST"    }
  case object PUT     extends Method { val name = "PUT"     }
  case object PATCH   extends Method { val name = "PATCH"   }
  case object DELETE  extends Method { val name = "DELETE"  }
  case object TRACE   extends Method { val name = "TRACE"   }
  case object CONNECT extends Method { val name = "CONNECT" }

  case object ANY extends Method { val name = "GET" }
}
