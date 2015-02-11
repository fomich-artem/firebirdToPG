package org.comsoft

import javax.sql.DataSource

import akka.actor.SupervisorStrategy.Stop
import akka.actor._
import akka.routing.FromConfig
import com.zaxxer.hikari.HikariDataSource
import org.comsoft.Protocol._
import scalikejdbc.{ConnectionPool, DataSourceConnectionPool}
import scalikejdbc.config.DBs

class MainActor extends Actor with ActorLogging {

  val manager = context.actorOf(WorkManager.props, "operator")
  val allTables = context.actorOf(TableRetriever.props, "tr")
  var pgtime = 0l
  var fbtime = 0l
  context.watch(manager)
  override def supervisorStrategy: SupervisorStrategy = {
    AllForOneStrategy(){
      case msg => log.info(s"!!!!!! $msg"); Stop
    }
  }

  override def receive: Receive = {
    case Collect => allTables ! Collect
    case WorkComplete =>
      log.info("all work completed")
      log.info(s"pg time $pgtime")
      log.info(s"fb time $fbtime")
      shutdown
    case msg: DoExport => manager ! msg
    case Terminated(manager) => shutdown
    case PGTime(time) => pgtime = time
    case FBTime(time) => fbtime = time
  }

  def shutdown = {
    log.info("shutting down")
    DBs.closeAll()
    context.system.shutdown()
  }
}

object Main extends App {
  val dataSource: DataSource = {
    val ds = new HikariDataSource()
    ds.setDataSourceClassName("org.postgresql.ds.PGSimpleDataSource")
    ds.setUsername("postgres")
    ds.setPassword("postgres")
    ds.addDataSourceProperty("databaseName", "aisbd_new")
    ds.addDataSourceProperty("serverName", "localhost")

    ds
  }
  ConnectionPool.add('pg, new DataSourceConnectionPool(dataSource))

  DBs.setupAll()
  val system = ActorSystem("example")

  sys.addShutdownHook {
    DBs.closeAll()
    system.shutdown()
  }

  val main = system.actorOf(Props[MainActor], "main")

  main ! Collect
}