package com.example.akka.util

import com.example.akka.model.Location
import org.slf4j.LoggerFactory

import scala.collection.immutable.ListMap
import scala.util.Random
import scala.util.parsing.json._

object SampleLocations {

  private val logger = LoggerFactory getLogger SampleLocations.getClass

  private[this] val lines = FileReader readClasspathFile "locations.json"
  private[this] val rawJSON = lines mkString "\n"
  private type nestedValues = Map[String, Map[String, _]]
  private val sampleDataSet: Map[String, Location] = initialiseSampleData
  private val random = new Random

  private def extractLocation(input: Map[String, _]): Option[Location] = {

    def coordinatesExtractor[T](t: T): Option[Location] = t match {
      case t:Map[String, Double] =>
        val coordinates: List[Double] = sortAlphabetically(t.filterKeys(k => k == "Latitude" || k == "Longitude"))
          .mapValues(_.toString.toDouble).values.toList
        if(coordinates.size==2)
          Some(Location(coordinates(1), coordinates.head))
        else None
      case _ => None
    }

    input.filterKeys(k=> k.contentEquals("Position"))
      .mapValues(coordinatesExtractor).values.toList.head
  }

  private def initialiseSampleData: Map[String, Location] = {
    logger debug "Initialising sample data-set ..."
    JSON.parseFull(rawJSON) match {
      case Some(json: nestedValues) => sortAlphabetically(json mapValues extractLocation)
          .filter(e => e._2.isDefined).mapValues(_.get)
      case _ => Map.empty
    }
  }

  private def sortAlphabetically[T](input: Map[String, T]) = {
    ListMap(input.toSeq.sortWith(_._1 < _._1): _*)
  }

  private def getRandomLocation: (String, Location) = {
    val locationName: String = sampleDataSet.keys.toList(random.nextInt(10))
    (locationName, sampleDataSet(locationName))
  }

  def getRandomLocationPair: (Location, Location) = {
    val firstLocation = getRandomLocation

    def generate: (Location, Location) = {
      var secondLocation = getRandomLocation
      if (firstLocation != secondLocation) {
        logger debug s"Randomly chosen locations are ${firstLocation._1} and ${secondLocation._1}"
        (firstLocation._2, secondLocation._2)
      } else generate
    }

    generate
  }

  /**
    * @param location
    * @return
    */
  def getName(location: Location): String = {
    val searchResult = sampleDataSet.find(_._2 == location)
    if(searchResult.isDefined) searchResult.get._1
    else "Unknown"
  }

}
