ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.16"

version := "1.0"

lazy val circeVersion = "0.14.14"

libraryDependencies ++= Seq(
  ws,
  filters,
  
  // Testing dependencies
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test
)

// If you use Flyway for DB migrations
libraryDependencies += "org.flywaydb" % "flyway-core" % "11.11.0"

lazy val circeDeps = Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
)

lazy val domainDeps = circeDeps


lazy val workerDeps = Seq(
  "com.typesafe.akka" %% "akka-stream" % "2.6.20",
  "com.typesafe.akka" %% "akka-stream-kafka" % "3.0.0",
  "com.typesafe.akka" %% "akka-actor-typed" % "2.6.20",
  guice
) ++ circeDeps

lazy val libDeps = Seq(
  "org.postgresql" % "postgresql" % "42.7.7",
  "com.typesafe.slick" %% "slick" % "3.6.1",
  "com.typesafe.slick" %% "slick-hikaricp" % "3.6.1",
  "com.github.tminglei" %% "slick-pg" % "0.23.1",
  "io.prometheus" % "simpleclient" % "0.16.0",
  "io.prometheus" % "simpleclient_hotspot" % "0.16.0",
  "io.prometheus" % "simpleclient_httpserver" % "0.16.0",
  "org.apache.kafka" % "kafka-clients" % "4.0.0",
  "redis.clients" % "jedis" % "4.3.1",
  guice
) ++ circeDeps

lazy val domain = (project in file("domain"))
  .settings(
    name := "AsyncTesterDomain",
    libraryDependencies ++= domainDeps
  )

lazy val library = (project in file("library"))
  .settings(
    name := "AsyncTesterLib",
    libraryDependencies ++= libDeps
  ).dependsOn(domain)


lazy val workers = (project in file("workers"))
  .settings(
    name := "AsyncTesterWorkers",
    libraryDependencies ++= workerDeps
  ).dependsOn(library)


lazy val root = (project in file("."))
  .settings(
    name := "AsyncTester"
  ).dependsOn(library).enablePlugins(PlayScala)
