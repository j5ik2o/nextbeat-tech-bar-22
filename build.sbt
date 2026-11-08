ThisBuild / scalaVersion := "3.3.9"
ThisBuild / organization := "com.example"
ThisBuild / version := "0.1.0-SNAPSHOT"

lazy val root = (project in file("."))
  .aggregate(scala3, scala2)
  .settings(
    name := "nextbeat-tech-bar-22",
    publish / skip := true
  )

lazy val scala3 = (project in file("scala3"))
  .settings(
    name := "due-date-scala3",
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.6" % Test
  )

lazy val scala2 = (project in file("scala2"))
  .settings(
    name := "due-date-scala2",
    scalaVersion := "2.13.18",
    libraryDependencies += "org.scalameta" %% "munit" % "1.3.6" % Test
  )
