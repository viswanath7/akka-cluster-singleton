package com.example.akka.actor

import akka.actor.{Actor, ActorLogging, Props}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.example.akka.actor.Customer.OrderRide
import com.example.akka.actor.TaxiDispatcher.RequestTaxiCommand
import com.example.akka.model.TaxiRide
import com.example.akka.util.SampleLocations
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.language.postfixOps

object Customer {

  private val logger = LoggerFactory getLogger Customer.getClass

  val props: Props = Props[Customer]

  sealed trait RequestMessage
  case object OrderRide extends RequestMessage
}

class Customer extends Actor with ActorLogging {

  import context.dispatcher

  /**
    *  Cluster Singleton actor instance is started and maintained by the ClusterSingletonManager actor on each of the cluster nodes
    *  with the specified role for the singleton. ClusterSingletonManager maintains at most one singleton instance on the oldest node
    *  of the cluster with the specified role at any point in time. Should the oldest node (could be the 1st seed node) fail, the next
    *  oldest node will be elected.
    *
    *  To access the cluster singleton actor, ClusterSingletonProxy is used which is present on all nodes with the specified role.
    *  ClusterSingletonProxy routes all messages to the current instance of the singleton. It will keep track of the oldest node
    *  in the cluster and resolve the singleton's ActorRef.
    */
  private val taxiDispatcherSingletonProxy = context.actorOf(props = ClusterSingletonProxy.props(singletonManagerPath = "/user/taxiDispatcher",
    settings = ClusterSingletonProxySettings(context.system).withRole(None)),
    name = "taxiDispatcherSingletonProxy")

  // Order a taxi every 3 seconds
  context.system.scheduler.schedule(initialDelay = 5 second, interval = 3 seconds, receiver = self, message = OrderRide)

  override def receive: Receive = {
    case OrderRide => log debug "Sending a message to taxi dispatcher singleton's proxy to request a ride."
      val randomLocationPair = SampleLocations.getRandomLocationPair
      taxiDispatcherSingletonProxy ! RequestTaxiCommand(TaxiRide(randomLocationPair._1, Some(randomLocationPair._2)))
  }

}


