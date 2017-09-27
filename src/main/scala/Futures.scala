import Main.system
import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.HostConnectionPool
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.settings.ConnectionPoolSettings
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

object Futures {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  def processForSource(source: Source[(HttpRequest, Int), NotUsed]): Unit = {
    try {
      val settings = ConnectionPoolSettings(system).withMaxOpenRequests(64)
      val poolClientFlow: Flow[(HttpRequest, Int), (Try[HttpResponse], Int), HostConnectionPool] =
        Http().cachedHostConnectionPoolHttps[Int]("www.scala-lang.org", 443, Http().defaultClientHttpsContext, settings)

      val responses = source
        .via(poolClientFlow)
        .mapAsync(4){ r =>
          if(1 == 1) {
            Future.failed(MyException(s"hardcoded exception: ${r._2}%"))
          } else {
            Future.successful(r)
          }
        }
        .map { r =>
          r._1.map(_.discardEntityBytes())
          r
        }
        //        .map( r => if (0 == 0) throw MyException(s"hardcoded exception: ${r._2}%") else r)
        .runWith(Sink.seq)

      responses.onComplete {
        case Success(res) =>
          println("Completed all with success: " + res)
        case Failure(ex) =>
          println("Failure: " + ex)
      }
    } catch {
      case e => println("Some exception caught: " + e)
    }

  }

  def main(args: Array[String]): Unit = {
    val httpReq = HttpRequest(uri = "/")
    val requests = List.fill(100)(HttpRequest(uri = "/")).zipWithIndex
    val source: Source[(HttpRequest, Int), NotUsed] = Source(requests)

    for {
      i <- 0 to 100
    } {
      processForSource(Source.single((httpReq, i)))
    }
    println("after the loop")

  }
}
