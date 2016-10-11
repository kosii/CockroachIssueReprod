import java.util.concurrent.CyclicBarrier

import model._
import scalikejdbc._
import scalikejdbc.config.DBs
import utils._

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Random, Try}


object Main extends App {

  DBs.setupAll()
  DB.localTx { implicit session =>
    BlacklistEntry.createDatabase
    BlacklistEntry.createTable
  }

  cockroachReplay("fixture")( implicit session =>
    for {
      key <- 1 to 500
    } {
        val created = 1000 * Random.nextGaussian() + System.currentTimeMillis()
        val expires = created + Random.nextDouble() * 1000
        sql"UPSERT INTO ttl (namespace, key, created, expires) values ('click', ${key.toString},${created.toLong}, ${expires.toLong})".update.apply()
    }
  )

  import scala.concurrent.ExecutionContext.Implicits.global
  val num = 5

  val a: List[Future[Try[Long]]] = (for {
    i <- 1 to num
  } yield {
    Future(cockroachReplay(s"CLEANER #$i")(implicit session => BlacklistEntry.clean))
  }) toList



  val polluter = Future(for {
    key <- Iterator.from(501)
  } {
    val created = 1000*Random.nextGaussian() + System.currentTimeMillis()
    val expires = created + Random.nextDouble()*1000
    cockroachReplay("INSERTER")({ implicit session =>
      sql"UPSERT INTO ttl (namespace, key, created, expires) values ('click', ${key.toString},${created.toLong}, ${expires.toLong})".update.apply()
    })
  })

  println(Await.result(Future.any[Any, List](a :+ polluter), Duration.Inf))
}
