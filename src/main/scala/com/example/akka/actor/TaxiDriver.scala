package com.example.akka.actor

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.singleton.{ClusterSingletonProxy, ClusterSingletonProxySettings}
import com.example.akka.actor.TaxiDispatcher.{CheckRideRequestCommand, RegisterDriverCommand}
import com.example.akka.actor.TaxiDriver.{Drive, UnAuthorised, WorkUnavailable}
import com.example.akka.model.TaxiRide
import com.example.akka.util.SampleLocations
import org.slf4j.LoggerFactory

import scala.concurrent.duration._
import scala.language.postfixOps

object TaxiDriver {

  private val logger = LoggerFactory getLogger TaxiDriver.getClass

  sealed trait RequestMessage
  case class Drive(customer:ActorRef, taxiRide: TaxiRide) extends RequestMessage
  case object WorkUnavailable extends RequestMessage
  case object UnAuthorised extends RequestMessage

  def props: Props = Props(new TaxiDriver)
}

class TaxiDriver extends Actor with ActorLogging {

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

  context.system.scheduler.schedule(initialDelay = 3 second, interval = 20 seconds, receiver = taxiDispatcherSingletonProxy, message = RegisterDriverCommand)
  context.system.scheduler.schedule(initialDelay = 5 second, interval = 2 seconds, receiver = taxiDispatcherSingletonProxy, message = CheckRideRequestCommand)

  override def receive: Receive = {
    case msg:Drive =>
      val pickUpLocation = SampleLocations.getName(msg.taxiRide.pickUpLocation)
      if(msg.taxiRide.dropOffLocation.isDefined) {
        val dropOffLocation = SampleLocations.getName(msg.taxiRide.dropOffLocation.get)
        log info s"Customer shall be picked up from $pickUpLocation and dropped off at $dropOffLocation"
      } else {
        log info s"Customer shall be picked up from $pickUpLocation"
      }
    case WorkUnavailable =>
      log info "Taxi driver has been asked to sit back and relax for the moment as there's no work to be done."
    case UnAuthorised =>
      log info "Notification for taxi driver: Unauthorised to perform the operation."
    case m =>
      log warning s"Received an unknown message $m ! The message shall be ignored."
  }
}
