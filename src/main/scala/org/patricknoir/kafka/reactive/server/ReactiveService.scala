package org.patricknoir.kafka.reactive.server

import io.circe.{ Error, Decoder, Encoder }
import io.circe.syntax._
import io.circe.parser._
import cats.std.all._
import cats.data._
import scala.concurrent.{ ExecutionContext, Future }

case class ReactiveService[-In: Decoder, +Out: Encoder](id: String)(f: In => Future[Error Xor Out]) {
  def apply(in: In): Future[Error Xor Out] = f(in)

  def unsafeApply(jsonStr: String)(implicit ec: ExecutionContext): Future[Error Xor String] = (for {
    in <- XorT(Future.successful(decode[In](jsonStr)))
    out <- XorT(f(in)).map(_.asJson.noSpaces)
  } yield out).value
}

object ReactiveService {
  implicit def reactiveServiceToRoute(service: ReactiveService[_, _]) = ReactiveRoute().add(service)
}

case class ReactiveRoute(services: Map[String, ReactiveService[_, _]] = Map.empty[String, ReactiveService[_, _]]) {
  def add[In, Out](reactiveService: ReactiveService[In, Out]): ReactiveRoute =
    this.copy(services = services + (reactiveService.id -> reactiveService))
  def ~(reactiveRoute: ReactiveRoute) = ReactiveRoute(this.services ++ reactiveRoute.services)
}

object ReactiveRoute {

  //TODO: Replace this with the 'Magnet' Pattern

  def request[In: Decoder, Out: Encoder](id: String)(f: In => Future[Error Xor Out]): ReactiveRoute =
    ReactiveRoute().add(ReactiveService[In, Out](id)(f))

  def request[In: Decoder, Out: Encoder](id: String)(f: In => Future[Out])(implicit ec: ExecutionContext): ReactiveRoute =
    ReactiveRoute().add(ReactiveService[In, Out](id)(in => f(in).map(out => Xor.right[Error, Out](out))))

  def requestFuture[In: Decoder, Out: Encoder](id: String)(f: In => Out)(implicit ec: ExecutionContext): ReactiveRoute =
    ReactiveRoute().add(ReactiveService[In, Out](id) { in => Future(Xor.right(f(in))) })
}