package com.bisphone.cassandra

import com.bisphone.cassandra
import com.bisphone.util.{AsyncResult, Convertor, SimpleError, ValueExtractor}
import com.bisphone.std._
import scala.concurrent.ExecutionContextExecutor

/**
  * Created by moein on 4/23/17.
  */
object Util {

  private val regex = {
    val raw = """(.*):(\d*)"""
    raw.r
  }

  implicit val tupleOfStringAndInt = Convertor[String, (String, Int)]("String => (String, Int)") {
    case regex(host,port) => (host, port.toInt)
    case x => throw new RuntimeException(s"Invalid String for 'host:port': '${x}'")
  }

  implicit val cassandraConsistencyLevel =
    Convertor[String, cassandra.ConsistencyLevel]("String => cassandra.ConsistencyLevel") { value =>
      cassandra.ConsistencyLevel.get(value) match {
        case Some(rsl) => rsl
        case None =>
          val msg = s"Invalid value for cassandra-consistencylevel: ${value}. Valid values: ${cassandra.ConsistencyLevel.values.mkString(",")}"
          throw new RuntimeException(msg)
      }
    }

  def cassandraConfig[T <: ValueExtractor](extractor: T)(
    implicit ex: ExecutionContextExecutor
  ): AsyncResult[SimpleError, cassandra.Config] = {
    for {
      seeds <- extractor.nelist[(String, Int)]("seeds")(tupleOfStringAndInt, ex)
      keyspace <- extractor.required[String]("keyspace")
      readCL <- extractor.required[cassandra.ConsistencyLevel]("read-consistency-level")
      writeCL <- extractor.required[cassandra.ConsistencyLevel]("write-consistency-level")
    } yield Config(seeds, keyspace, readCL, writeCL)
  }

  def cassandraConnection[T <: ValueExtractor](extractor: T)(
    implicit ex: ExecutionContextExecutor
  ): AsyncResult[SimpleError, cassandra.Connection] = {

    for {
      config <- cassandraConfig(extractor)

      conn = new cassandra.Connection(config)

    } yield conn
  }

  class ConfigParser {

    private val regex = {
      val raw = """(.*):(\d*)"""
      raw.r
    }

    private def parseHostAndPort(value: String) = value match {
      case regex(host,port) => (host, port.toInt)
      case x => throw new IllegalArgumentException(s"Invalid String for 'host:port': '${x}'")
    }

    private def parseConsitencyLevel(value: String) =
      cassandra.ConsistencyLevel.get(value) match {
        case Some(rsl) => rsl
        case None =>
          val msg = s"Invalid value for cassandra-consistencylevel: ${value}. Valid values: ${cassandra.ConsistencyLevel.values.mkString(",")}"
          throw new IllegalArgumentException(msg)
      }

    def parse(config: com.typesafe.config.Config) = {

      import scala.collection.JavaConverters._

      val seeds = config.getStringList("seeds").asScala.map(parseHostAndPort).toList
      val keyspace = config.getString("keyspace")
      val readCL = parseConsitencyLevel(config.getString("read-consistency-level"))
      val writeCL = parseConsitencyLevel(config.getString("write-consistency-level"))

      Config(seeds, keyspace, readCL, writeCL)
    }

  }
}
