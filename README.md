Rest.li API Hub
===============

API Hub is a web UI for browsing and searching a catalog of rest.li APIs.

Features:

* Explorable catalog of all Rest.li resources
* Full text search across all resources and data schemas
* Detailed resource pages including all available RESTful methods
* Detailed data schema pages
* All resources and data schemas documentation is displayed and nicely formatted
* Generated example HTTP requests and responses
* Interactive Console for composing and sending requests to live REST resources
* Requests composed in console and be saved to a pastebin (gist by default) and shared via a link

Screenshots below.

Requirements
------------

* SBT                - 0.13.0+
* Play               - 2.2.1+
* rest.li            - 1.15.3+
* rest.li-sbt-plugin - 0.0.1+

Building
--------

* If restli-sbt-plugin is not yet available in maven central.  Build it locally first.

    clone https://github.com/linkedin/rest.li-sbt-plugin
    cd rest.li-sbt-plugin
    ./gradlew install

* If the needed version of rest.li is not yet available in maven central.  Build it locally first.

    clone https://github.com/linkedin/rest.li.git
    cd rest.li
    ./gradlew install

* Edit project/Build.scala, setting version numbers to match what was installed by the above calls to "./gradlew install".

* Build the project.

    play clean compile

Configuration
-------------

* Edit the data loader strategy configuration properties in  'frontend/conf/application.conf' 
  to include URLs to all your resources.

How to run
----------

* `play run`
* In your browser, hit `http://localhost:9000/apihub`

How to debug
------------

* `play debug run`
* Connect IDE debugger to port 9999

Screenshots
===========

Search
------
![](https://raw.githubusercontent.com/wiki/linkedin/rest.li-api-hub/search-screenshot.png)

Resource Details Pages
----------------------
![](https://raw.githubusercontent.com/wiki/linkedin/rest.li-api-hub/resource-screenshot.png)

Generated Examples
------------------
![](https://raw.githubusercontent.com/wiki/linkedin/rest.li-api-hub/http-example-screenshot.png)

Send Requests from the Console
------------------------------
![](https://raw.githubusercontent.com/wiki/linkedin/rest.li-api-hub/console-screenshot.png)