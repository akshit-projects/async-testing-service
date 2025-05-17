ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.16"  // Update to a later version

version := "1.0"

lazy val circeVersion = "0.14.12"

libraryDependencies ++= Seq(
  guice,
  ws,
  filters,
  
  // MongoDB official driver
  "org.mongodb.scala" %% "mongo-scala-driver" % "5.4.0",
  
  // Circe for JSON handling
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  
  // Prometheus metrics
  "io.prometheus" % "simpleclient" % "0.16.0",
  "io.prometheus" % "simpleclient_hotspot" % "0.16.0",
  "io.prometheus" % "simpleclient_httpserver" % "0.16.0",
  
  // Kafka for async messaging
  "org.apache.kafka" % "kafka-clients" % "4.0.0",
  
  // Redis for distributed locks and caching
  "redis.clients" % "jedis" % "4.3.1",
  
  // Testing dependencies
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test
)

lazy val root = (project in file("."))
  .settings(
    name := "AsyncTester"
  ).enablePlugins(PlayScala)
