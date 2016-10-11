import java.sql.{Connection, Savepoint}
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent._

import org.postgresql.util.PSQLException
import org.slf4j.LoggerFactory
import scalikejdbc.{ConnectionPool, DB, DBSession, _}
import scalikejdbc.config.DBs

import scala.annotation.tailrec
import scala.util.{Failure, Success, Try}

package object model {
  type Namespace = String
  type Key = String


  case class BlacklistEntry(namespace: Namespace, key: Key, created: Long, expires: Long)

  object BlacklistEntry {
    def createDatabase(implicit session: DBSession) = {
      sql"CREATE DATABASE IF NOT EXISTS ivt".execute().apply()
    }

    def createTable(implicit session: DBSession):Boolean = {
      sql"""CREATE TABLE IF NOT EXISTS TTL (
        namespace STRING NOT NULL,
        key STRING NOT NULL,
        created INT NOT NULL,
        expires INT NOT NULL,
        PRIMARY KEY (namespace, key)
        )""".execute().apply()
    }


    def clean(implicit session: DBSession): Long = {
      val list = sql"SELECT namespace, max(created) AS created FROM ttl GROUP BY namespace"
        .map(rs => (rs.string("namespace"), rs.long("created")))
        .list()
        .apply()

      list.map { case (namespace, maxCreatedTime) =>
        sql"DELETE FROM ttl WHERE expires < ${maxCreatedTime} and namespace = ${namespace}".update.apply()
      }
      .sum
    }
  }


  def cockroachReplay[A](name: String)(v: => ((DBSession) => A)): Try[A] = {
    val LOG = LoggerFactory.getLogger("cockroachReplay")

    @tailrec
    def core(spt: Savepoint, conn: Connection, session: DBSession, retryNumber: Int = 0): Try[A] = {
      Try {
        val res = v(session)
        conn.releaseSavepoint(spt)
        conn.commit()
        res
      } match {
        case success@Success(_) =>
          LOG.info(s"${name}: Successful execution after ${retryNumber} failed attempt")
          success
        case f@Failure(ex: PSQLException) if ex.getMessage.contains("restart transaction") =>
            LOG.warn(s"${name}: Retry #${retryNumber}: Rolling back to savepoint due to retriable error ${ex.getMessage}")
            conn.rollback(spt)
            core(spt, conn, session, retryNumber + 1)
        case f@Failure(ex) =>
          LOG.error(s"${name}: Fatal error caught, aborting transaction", ex)
          conn.rollback()
          f
      }
    }
    using(ConnectionPool.borrow()) {
      conn =>
        conn.setAutoCommit(false)
        conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED)
        using(DB(conn)) {
          implicit db =>
            val savepoint = conn.setSavepoint("cockroach_restart")
            db.withinTx({
              implicit session =>
                core(savepoint, conn, session)
            })
        }
    }
  }
}
