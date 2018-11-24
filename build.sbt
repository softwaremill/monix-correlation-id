lazy val commonSettings = commonSmlBuildSettings ++ ossPublishSettings ++ Seq(
  organization := "com.softwaremill.xxx",
  scalaVersion := "2.12.7"
)

val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5" % "test"
val http4sVersion = "0.20.0-M3"
val sttpVersion = "1.5.0"
val doobieVersion = "0.6.0"

lazy val rootProject = (project in file("."))
  .settings(commonSettings: _*)
  .settings(publishArtifact := false, name := "root")
  .aggregate(core)

lazy val core: Project = (project in file("core"))
  .settings(commonSettings: _*)
  .settings(
    name := "core",
    libraryDependencies ++= Seq(
      "io.monix" %% "monix" % "3.0.0-RC2",
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-blaze-server" % http4sVersion,
      "com.softwaremill.sttp" %% "async-http-client-backend-cats" % sttpVersion,
      "org.flywaydb" % "flyway-core" % "5.2.1",
      "org.tpolecat" %% "doobie-core" % doobieVersion,
      "org.tpolecat" %% "doobie-hikari" % doobieVersion,
      "org.tpolecat" %% "doobie-h2" % doobieVersion,
      "ch.qos.logback" % "logback-classic" % "1.2.3",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0",
      scalaTest
    )
  )

