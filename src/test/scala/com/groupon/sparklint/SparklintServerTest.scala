/*
 * Copyright 2016 Groupon, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.groupon.sparklint

import java.io.File

import com.groupon.sparklint.common.TestUtils._
import com.groupon.sparklint.common.{CliSparklintConfig, ScheduledTask, SchedulerLike}
import com.groupon.sparklint.events.{EventSourceIdentifier, RootEventSourceManager}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

import scala.collection.mutable.ArrayBuffer

/**
  * @author swhitear 
  * @since 8/18/16.
  */
class SparklintServerTest extends FlatSpec with BeforeAndAfterEach with Matchers {

  private val TEMP_FILE_CONTENT =
    """{"Event":"SparkListenerApplicationStart","App Name":"MyAppName","App ID":"temp_addded_in_test","Timestamp":1466087746466,"User":"johndoe"}|"""

  private var server            : SparklintServer        = _
  private var eventSourceManager: RootEventSourceManager = _
  private var dirname           : String                 = _
  private var tempFile          : File                   = _
  private var scheduler         : StubScheduler          = _
  private var config            : CliSparklintConfig     = _

  override protected def beforeEach(): Unit = {
    scheduler = new StubScheduler()
    dirname = resource("directory_source")
    tempFile = resetTempFile(dirname)
    config = CliSparklintConfig(exitOnError = false)
    server = new SparklintServer(scheduler, config)
    eventSourceManager = server.eventSourceManager
  }

  override protected def afterEach(): Unit = {
    server.shutdownUI()
  }

  it should "load expected buffer from a file when configured" in {
    val filename = resource("spark_event_log_example")
    val args = Seq("-f", filename).toArray
    config.parseCliArgs(args)
    server.addEventSourcesFromCommandLineArguments()
    server.startUI()

    eventSourceManager.eventSourceManagers.map(_.sourceCount).sum shouldEqual 1
    scheduler.scheduledTasks.isEmpty shouldBe true

    val es = eventSourceManager.eventSourceManagers.head.getScrollingSource(EventSourceIdentifier("GraphXTest", None).toString)
    es.meta.appName shouldEqual "spark_event_log_example"
    es.hasNext shouldEqual true
    es.hasPrevious shouldEqual false
  }

  it should "load expected buffer from a file and replay when configured" in {
    val filename = resource("spark_event_log_example")
    val args = Seq("-f", filename, "-r").toArray
    config.parseCliArgs(args)
    server.addEventSourcesFromCommandLineArguments()
    server.startUI()

    eventSourceManager.eventSourceManagers.map(_.sourceCount).sum shouldEqual 1
    scheduler.scheduledTasks.isEmpty shouldBe true

    val es = eventSourceManager.eventSourceManagers.head.getScrollingSource("application_1462781278026_205691")
    es.meta.appName shouldEqual "spark_event_log_example"
    es.hasNext shouldEqual false
    es.hasPrevious shouldEqual true
  }


  it should "load expected buffer from a directory when configured" in {
    val dirname = resource("directory_source")
    val args = Seq("-d", dirname).toArray
    config.parseCliArgs(args)
    server.addEventSourcesFromCommandLineArguments()
    server.startUI()

    eventSourceManager.eventSourceManagers.map(_.sourceCount).sum shouldEqual 0
    scheduler.scheduledTasks.size shouldEqual 1

    // fire the timed event to load from directory
    scheduler.scheduledTasks.head.run()
    eventSourceManager.eventSourceManagers.map(_.sourceCount).sum shouldEqual 2

    var es = eventSourceManager.eventSourceManagers.head.getScrollingSource("application_1462781278026_205691")
    es.meta.appName shouldEqual "event_log_0"
    es.hasNext shouldEqual true
    es.hasPrevious shouldEqual false

    es = eventSourceManager.eventSourceManagers.head.getScrollingSource("application_1472176676028_116806")
    es.meta.appName shouldEqual "event_log_1"
    es.hasNext shouldEqual true
    es.hasPrevious shouldEqual false
  }

  it should "load expected buffer from a directory and replay when configured" in {
    val args = Seq("-d", dirname, "-r").toArray
    config.parseCliArgs(args)
    server.addEventSourcesFromCommandLineArguments()
    server.startUI()

    eventSourceManager.eventSourceManagers.map(_.sourceCount).sum shouldEqual 0
    scheduler.scheduledTasks.size shouldEqual 1

    // fire the timed event to load from directory
    scheduler.scheduledTasks.head.run()
    eventSourceManager.eventSourceManagers.map(_.sourceCount).sum shouldEqual 2

    var es = eventSourceManager.eventSourceManagers.head.getScrollingSource("application_1462781278026_205691")
    es.meta.appName shouldEqual "event_log_0"
    es.hasNext shouldEqual false
    es.hasPrevious shouldEqual true

    es = eventSourceManager.eventSourceManagers.head.getScrollingSource("application_1472176676028_116806")
    es.meta.appName shouldEqual "event_log_1"
    es.hasNext shouldEqual false
    es.hasPrevious shouldEqual true
  }

  private def resetTempFile(dirname: String): File = {
    // will add this new file, so delete if exists
    val newFile: File = new File(dirname, "temp_addded_in_test")
    if (newFile.exists()) newFile.delete() shouldBe true
    newFile
  }

  private def addInTempFile(file: File, content: String = TEMP_FILE_CONTENT): File = {
    val pw = new java.io.PrintWriter(file)
    try pw.write(content) finally pw.close()
    file
  }

  private def cleanupTempFile(file: File) = {
    if (file.exists()) file.delete() shouldBe true
  }
}

//class StubEventSourceManager(override val eventSourceDetails: ArrayBuffer[EventSourceLike] = ArrayBuffer[EventSourceLike]())
//  extends FileEventSourceManager

class StubScheduler(val scheduledTasks: ArrayBuffer[ScheduledTask[_]] = ArrayBuffer[ScheduledTask[_]]())
  extends SchedulerLike {

  override def scheduleTask[T](task: ScheduledTask[T]): Unit = scheduledTasks += task

  override def cancelAll(): Unit = ???
}
