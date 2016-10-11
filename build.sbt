
name := "CockroachIssueReprod"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.3.0",
  "com.beust" % "jcommander" % "1.48",
  "com.google.guava" % "guava" % "19.0",
  "org.scalikejdbc"    %% "scalikejdbc"        % "2.4.2",
  "org.scalikejdbc"    %% "scalikejdbc-config" % "2.4.2",
  "org.postgresql"     %  "postgresql"         % "9.4.1208",
  "org.slf4j" % "slf4j-simple" % "1.7.21"
)
