UI for browsing and searching documentation of rest.li APIs.

Requirements:

* SBT                - 0.13.0+
* Play               - 2.2.1+
* rest.li            - 1.15.0+
* rest.li-sbt-plugin - 0.0.1+

How to build:

* If restli-sbt-plugin is not yet available in maven central.  Build it locally first.

    clone https://github.com/linkedin/rest.li-sbt-plugin
    cd rest.li-sbt-plugin
    ./gradlew install

* If the needed version of rest.li is not yet available in maven central.  Build it locally first.

    clone https://github.com/linkedin/rest.li.git
    cd rest.li
    ./gradlew install

* Edit project/Build.scala, setting version numbers to match what was installed by the above calls to "./gradlew install".
* play clean compile

How to configure:

* Modify the 'resourceUrls' property in 'frontend/conf/application.conf' to include URLs to all your resources.
* OR, if you are running D2.  Set the 'zkHost' and 'zkPort' to point to your D2 zookeeper

How to run:

* play run
* In your browser, hit `http://localhost:9000/rest-search-frontend`

How to debug:

* play debug run
* Connect IDE debugger to port 9999
