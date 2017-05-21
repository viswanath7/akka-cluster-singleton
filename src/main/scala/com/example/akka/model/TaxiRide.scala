package com.example.akka.model

/**
  * A ride consists of mandatory pick-up location and an optional drop-off location
  *
  * @param pickUpLocation
  * @param dropOffLocation
  */
case class TaxiRide(pickUpLocation:Location, dropOffLocation:Option[Location])
