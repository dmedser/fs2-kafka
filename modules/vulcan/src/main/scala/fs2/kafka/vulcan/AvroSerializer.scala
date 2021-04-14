/*
 * Copyright 2018-2021 OVO Energy Limited
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package fs2.kafka.vulcan

import _root_.vulcan.Codec
import cats.effect.Sync
import cats.implicits._
import fs2.kafka.{RecordSerializer, Serializer}

final class AvroSerializer[A] private[vulcan] (
  private val codec: Codec[A]
) extends AnyVal {
  def using[F[_]](
    settings: AvroSettings[F]
  )(implicit F: Sync[F]): RecordSerializer[F, A] = {
    val createSerializer: Boolean => F[Serializer[F, A]] =
      settings.createAvroSerializer(_).map {
        case (serializer, _) =>
          Serializer.instance { (topic, _, a) =>
            F.defer {
              codec.encode(a) match {
                case Right(value) => F.pure(serializer.serialize(topic, value))
                case Left(error)  => F.raiseError(error.throwable)
              }
            }
          }
      }

    RecordSerializer.instance(
      forKey = createSerializer(true).map(_.forKey),
      forValue = createSerializer(false).map(_.forValue)
    )
  }

  override def toString: String =
    "AvroSerializer$" + System.identityHashCode(this)
}

object AvroSerializer {
  def apply[A](implicit codec: Codec[A]): AvroSerializer[A] =
    new AvroSerializer(codec)
}
