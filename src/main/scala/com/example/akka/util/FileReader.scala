package com.example.akka.util

import java.io.FileNotFoundException

import org.slf4j.LoggerFactory

import scala.io.{BufferedSource, Source}
import scala.util.{Failure, Success, Try}


object FileReader {

  private val logger = LoggerFactory getLogger FileReader.getClass

  def readClasspathFile(fileName: String):List[String] = {
    logger debug s"Reading classpath resource with name $fileName"
    val bufferedSource: BufferedSource = Source fromResource fileName
    Try( bufferedSource.getLines.toList ) match {
      case Success(lines) => lines
      case Failure(f) => logger error (s"Unable to read classpath resource $fileName", f.getCause)
        Nil
    }
  }

  def tryToReadClasspathFile(fileName: String): Try[List[String]] = {
    Try(Source.fromResource(fileName).getLines.toList)
      .recover(throw new FileNotFoundException(fileName))
  }

}