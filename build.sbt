ThisBuild / version := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "2.13.16"

version := "1.0"

// Global dependency resolution strategy
ThisBuild / conflictManager := ConflictManager.latestRevision
ThisBuild / updateOptions := updateOptions.value.withGigahorse(false)

// Handle version conflicts with dependency schemes
ThisBuild / libraryDependencySchemes ++= Seq(
  "com.typesafe.slick" %% "slick" % VersionScheme.Always,
  "com.typesafe.slick" %% "slick-hikaricp" % VersionScheme.Always,
  "com.fasterxml.jackson.core" % "jackson-core" % VersionScheme.Always,
  "com.fasterxml.jackson.core" % "jackson-databind" % VersionScheme.Always,
  "com.fasterxml.jackson.core" % "jackson-annotations" % VersionScheme.Always,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % VersionScheme.Always
)

lazy val circeVersion = "0.14.14"
lazy val jacksonVersion = "2.14.3"
lazy val slickVersion = "3.4.1" // Compatible with Play Slick 5.1.0

// Force Jackson version compatibility
libraryDependencies ++= Seq(
  ws,
  filters,

  // Database dependencies for main app
  "com.typesafe.play" %% "play-slick" % "5.1.0",
  "com.typesafe.play" %% "play-slick-evolutions" % "5.1.0",

  // Force compatible Jackson versions
  "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
  "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
  "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,


// Testing dependencies
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test,
)

// If you use Flyway for DB migrations
libraryDependencies += "org.flywaydb" % "flyway-core" % "11.11.0"


lazy val circeDeps = Seq(
  "io.circe" %% "circe-core" % circeVersion,
  "io.circe" %% "circe-generic" % circeVersion,
  "io.circe" %% "circe-parser" % circeVersion,
  "com.github.jwt-scala" %% "jwt-circe" % "11.0.3"
)

lazy val domainDeps = circeDeps ++ Seq(
  "org.scalatest" %% "scalatest" % "3.2.15" % Test
)


lazy val akkaVersion = "2.6.20"

lazy val workerDeps = Seq(
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream-kafka" % "3.0.0",
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.4.14", // SLF4J implementation
  "com.typesafe.play" %% "play-ahc-ws-standalone" % "2.2.11", // WSClient for HTTP requests
  "com.typesafe.play" %% "play-ws-standalone-json" % "2.2.11", // JSON support for WS
  guice
) ++ circeDeps

lazy val libDeps = Seq(
  "org.postgresql" % "postgresql" % "42.7.7",
  "com.typesafe.slick" %% "slick" % slickVersion,
  "com.typesafe.slick" %% "slick-hikaricp" % slickVersion,
  "com.github.tminglei" %% "slick-pg" % "0.21.1",
  "io.prometheus" % "simpleclient" % "0.16.0",
  "io.prometheus" % "simpleclient_hotspot" % "0.16.0",
  "io.prometheus" % "simpleclient_httpserver" % "0.16.0",
  "org.apache.kafka" % "kafka-clients" % "4.0.0",
  "redis.clients" % "jedis" % "4.3.1",
  "com.github.jwt-scala" %% "jwt-play-json" % "11.0.2",
  "com.sun.mail" % "jakarta.mail" % "2.0.1",
  "org.mindrot" % "jbcrypt" % "0.4",
  guice,
  ws
) ++ circeDeps

lazy val domain = (project in file("domain"))
  .settings(
    name := "AsyncTesterDomain",
    libraryDependencies ++= domainDeps
  )

lazy val library = (project in file("library"))
  .settings(
    name := "AsyncTesterLib",
    libraryDependencies ++= libDeps,
    dependencyOverrides ++= Seq(
      "com.typesafe.slick" %% "slick" % slickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % slickVersion
    )
  ).dependsOn(domain)


lazy val workers = (project in file("workers"))
  .settings(
    name := "AsyncTesterWorkers",
    libraryDependencies ++= workerDeps,
    dependencyOverrides ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
      "com.typesafe.slick" %% "slick" % slickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % slickVersion
    )
  ).dependsOn(library)


lazy val root = (project in file("."))
  .settings(
    name := "AsyncTester",
    // Force version resolution for root project
    dependencyOverrides ++= Seq(
      "com.fasterxml.jackson.core" % "jackson-core" % jacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-databind" % jacksonVersion,
      "com.fasterxml.jackson.core" % "jackson-annotations" % jacksonVersion,
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % jacksonVersion,
      "com.typesafe.slick" %% "slick" % slickVersion,
      "com.typesafe.slick" %% "slick-hikaricp" % slickVersion
    )
  ).dependsOn(library).enablePlugins(PlayScala)
