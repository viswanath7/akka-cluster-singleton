package com.example.akka.actor

import akka.actor.{ActorLogging, ActorRef}
import akka.persistence.{PersistentActor, SnapshotOffer}
import com.example.akka.actor.TaxiDispatcher._
import com.example.akka.actor.TaxiDriver.{Drive, UnAuthorised, WorkUnavailable}
import com.example.akka.model.TaxiRide
import org.slf4j.LoggerFactory

object TaxiDispatcher {

  sealed trait Command
  case object RegisterDriverCommand extends Command
  case object CheckRideRequestCommand extends Command
  case class RequestTaxiCommand(taxiRide: TaxiRide) extends Command

  sealed trait Event
  case class DriverRegisteredEvent(taxiDriver: ActorRef) extends Event
  case class RideAllocatedEvent(taxiDriver: ActorRef, rideRequests: List[RideRequest]) extends Event
  case class TaxiRequestReceivedEvent(rideRequest: RideRequest) extends Event

  case class RideRequest(customer:ActorRef, taxiRide:TaxiRide)
  case class State(taxiDrivers: Set[ActorRef], rideRequests: List[RideRequest])

}

class TaxiDispatcher extends PersistentActor with ActorLogging {

  private val logger = LoggerFactory getLogger TaxiDispatcher.getClass

  private[this] var taxiDrivers: Set[ActorRef] = Set.empty
  /**
    * Ride requests represent 'received orders' namely
    * a list of pairs of customers who placed the ride requests
    * along with their taxi ride details.
    */
  private[this] var rideRequests: List[RideRequest] = List.empty

  /**
    * Every message in the journal needs an identifier for its persistent entity.
    * There should be exactly one persistent actor with persistent identifier running at a given time.
    *
    * @return identifier of persistent entity
    */
  override def persistenceId: String = self.path.parent.name + "-" + self.path.name

  override def receiveCommand: Receive = {
    case RegisterDriverCommand =>
      logger debug s"Received a command to register driver: ${sender.path.name}"
      persist(DriverRegisteredEvent(sender)) {
        driverRegisteredEvent =>  updateState(driverRegisteredEvent)
      }
    case CheckRideRequestCommand if !(taxiDrivers contains sender)=>
      logger debug s"Received a command from ${sender.path.name} to check ride requests."
      logger warn s"Taxi driver: ${sender.path.name} is unregistered."
      sender ! UnAuthorised
    case CheckRideRequestCommand if rideRequests.isEmpty =>
      logger debug s"Received a command from ${sender.path.name} to check ride requests."
      logger info s"No ride requests are available at the moment ${sender.path.name}"
      sender ! WorkUnavailable
    case CheckRideRequestCommand =>
      logger debug s"Received a command from ${sender.path.name} to check ride requests."
      persist(RideAllocatedEvent(sender, rideRequests)) {
        rideAllocatedEvent => updateState(rideAllocatedEvent)
      }
    case RequestTaxiCommand(taxiRide) =>
      logger debug s"Received a command from ${sender.path.name} to ride a taxi"
      persist(TaxiRequestReceivedEvent(RideRequest(sender, taxiRide)) ) {
        taxiRequestReceivedEvent => updateState(taxiRequestReceivedEvent)
      }
  }

  override def receiveRecover: Receive = {
    case taxiDispatcherEvent: Event => updateState(taxiDispatcherEvent)
    case SnapshotOffer(_, snapshot: State) =>
      logger debug "Restoring the state of the actor from snapshot ..."
      taxiDrivers = snapshot.taxiDrivers
      rideRequests = snapshot.rideRequests
  }

  private def updateState(taxiDispatcherEvent: Event) = taxiDispatcherEvent match {
    case DriverRegisteredEvent(taxiDriver) => logger debug s"Handling DriverRegisteredEvent(${taxiDriver.path})"
      logger debug s"Registering the taxi driver ${taxiDriver.path.name} ..."
      taxiDrivers = taxiDrivers + taxiDriver
      logger debug s"Number of registered taxi-drivers are ${taxiDrivers.size}"
    case RideAllocatedEvent(taxiDriver, ridesAvailable) => logger debug s"Handling RideAllocatedEvent for ${taxiDriver.path}"
      taxiDriver ! Drive(ridesAvailable.head.customer, ridesAvailable.head.taxiRide)
      logger debug s"Removing the allocated ride: ${ridesAvailable.head}"
      rideRequests = ridesAvailable.tail
      logger debug s"Number of pending ride request: ${rideRequests.size}"
    case TaxiRequestReceivedEvent(rideRequest) => logger debug s"Handling TaxiRequestReceivedEvent for ${rideRequest.customer.path}"
      logger debug s"Registering the ride request $rideRequest"
      rideRequests = rideRequests :+ rideRequest
      logger debug s"Number of pending ride request: ${rideRequests.size}"
  }

}


