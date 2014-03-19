//NOTE: we are not using SafeModuleDependencies to avoid adding the sbt-infrastrcture in the project/project/plugins.sbt because it is not needed for this module

resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

libraryDependencies += "com.linkedin.pegasus" %% "sbt-plugin" % "0.0.1"

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.6.0")

addSbtPlugin("com.typesafe.play" % "sbt-plugin" % "2.2.1")