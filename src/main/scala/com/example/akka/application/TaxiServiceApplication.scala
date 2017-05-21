package com.example.akka.application

import akka.actor.{ActorIdentity, ActorPath, ActorSystem, Identify, PoisonPill, Props}
import akka.cluster.Cluster
import akka.cluster.singleton.{ClusterSingletonManager, ClusterSingletonManagerSettings}
import akka.pattern.ask
import akka.persistence.journal.leveldb.{SharedLeveldbJournal, SharedLeveldbStore}
import akka.util.Timeout
import com.example.akka.actor.{Customer, TaxiDispatcher, TaxiDriver}
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.language.postfixOps


object TaxiServiceApplication extends App {

  private val logger = LoggerFactory getLogger TaxiServiceApplication.getClass


  private val portNumbers = Seq("2551", "2552", "0")

  startNode(portNumbers)

  def startNode(ports: Seq[String]): Unit = {

    ports foreach { port =>

      logger debug s"Starting a node at port $port..."

      logger debug s"Overriding the configuration of node's port to $port ..."
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).withFallback(ConfigFactory.load("application"))

      logger debug "Creating a cluster system ..."
      val system = ActorSystem("ClusterSystem", config)

      startupSharedJournal(system, startStore = port == "2551",
        path = ActorPath.fromString("akka.tcp://ClusterSystem@127.0.0.1:2551/user/store"))

      logger debug s"Creating a cluster singleton manager in the node at port $port ..."
      val taxiDispatcher = system.actorOf(
        ClusterSingletonManager.props(singletonProps = Props[TaxiDispatcher],
          terminationMessage = PoisonPill,
          settings = ClusterSingletonManagerSettings(system).withRole(None)
        ), name = "taxiDispatcher")


      Cluster(system) registerOnMemberUp {
        logger debug s"Creating a taxi driver actor in the node at port $port as the cluster member is up"
        system.actorOf(TaxiDriver.props, name = "taxiDriver")
      }

      if (port != "2551" && port != "2552") {
        Cluster(system) registerOnMemberUp {
          logger debug s"Creating a custer actor in the node at port $port as the cluster member is up"
          system.actorOf(Customer.props, name = "customer")
        }
      }
    }

    def startupSharedJournal(system: ActorSystem, startStore: Boolean, path: ActorPath): Unit = {

      logger debug "Starting the non distributed, shared journal on node at port 2551 ..."

      if (startStore) {
        logger debug "Creating a 'store' actor on node 2551 for holding the shared journal ..."
        system.actorOf(Props[SharedLeveldbStore], "store")


        // register the shared journal
        import system.dispatcher
        implicit val timeout = Timeout(15 seconds)
        val identifyResult = system.actorSelection(path) ? Identify(None)
        identifyResult.foreach {
          case ActorIdentity(_, Some(ref)) => SharedLeveldbJournal.setStore(ref, system)
          case _ =>
            logger error s"Failed to start shared journal at $path"
            logger info s"Terminating the system: ${system.name} ..."
            system terminate
        }
        identifyResult.failed.foreach {
          _ =>
            logger error s"Lookup of shared journal at $path timed out"
            logger info s"Terminating the system: ${system.name} ..."
            system terminate
        }
      }
    }



  }

}
