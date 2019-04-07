scalaVersion := "2.12.7"
organization := "com.example"

lazy val `persistence` = (project in file("."))
    .settings(
        name := "Persistence"
    )

Test / fork := true

resolvers += "dnvriend" at "http://dl.bintray.com/dnvriend/maven"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % "2.5.21",
  "com.typesafe.akka" %% "akka-persistence" % "2.5.21",
  "org.fusesource.leveldbjni" % "leveldbjni-all" % "1.8",
  "com.typesafe.akka" %% "akka-testkit" % "2.5.21" % Test,
  "org.scalatest" %% "scalatest" % "3.0.5" % Test,
  "org.mockito" % "mockito-scala_2.12" % "1.3.0" % Test,
  "com.typesafe.akka" %% "akka-http" % "10.1.8",
  "com.github.dnvriend" %% "akka-persistence-inmemory" % "2.5.15.1"
)
