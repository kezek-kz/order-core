name := "order-core"

version := "0.1"

scalaVersion := "2.13.1"

lazy val akkaVersion      = "2.6.8"
lazy val akkaHttpVersion  = "10.2.1"
lazy val swaggerVersion   = "2.1.2"
lazy val circeVersion = "0.13.0"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-protobuf-v3" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "org.mongodb.scala" %% "mongo-scala-driver" % "4.2.0",
  //
  "javax.ws.rs"                  % "javax.ws.rs-api"       % "2.1.1",
  "com.github.swagger-akka-http" %% "swagger-akka-http"    % "2.0.5",
  "com.github.swagger-akka-http" %% "swagger-scala-module" % "2.1.0",
  "io.swagger.core.v3"           % "swagger-core"          % swaggerVersion,
  "io.swagger.core.v3"           % "swagger-annotations"   % swaggerVersion,
  "io.swagger.core.v3"           % "swagger-models"        % swaggerVersion,
  "io.swagger.core.v3"           % "swagger-jaxrs2"        % swaggerVersion,
  //
  "de.heikoseeberger" %% "akka-http-circe"      % "1.31.0",
  "io.circe"          %% "circe-core"           % circeVersion,
  "io.circe"          %% "circe-generic"        % circeVersion,
  "io.circe"          %% "circe-parser"         % circeVersion,
  "io.circe"          %% "circe-literal"        % circeVersion,
  "io.circe"          %% "circe-generic-extras" % circeVersion,
  "io.circe"          %% "circe-jawn"           % circeVersion,
  "io.circe"          %% "circe-bson"           % "0.4.0",
  "joda-time"         % "joda-time"             % "2.10.5",
  "ch.qos.logback"    % "logback-classic"       % "1.2.3" % Runtime,
  //
  "io.scalaland" %% "chimney" % "0.6.1"
)

enablePlugins(JavaAppPackaging)

// heroku deployment configs
herokuAppName in Compile := Map(
  "prod"  -> "prod-kezek-order-core",
  "dev" -> "dev-kezek-order-core",
)(sys.props.getOrElse("env", "dev"))

herokuJdkVersion in Compile := "1.8"