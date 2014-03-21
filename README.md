Rest.li API Hub
===============

API Hub is a web UI for browsing and searching a catalog of [rest.li](github.com/linkedin/rest.li) APIs.

Features:

* Explorable catalog of all Rest.li resources
* Lucene powered full text search across all resources and data schemas

<p align="center"><img width="600" src=https://github.com/linkedin/rest.li-api-hub/wiki/search-screenshot.png></p>

* Detailed resource pages including all available RESTful methods

<p align="center"><img width="600" src=https://github.com/linkedin/rest.li-api-hub/wiki/resource-screenshot.png></p>

* Detailed data schema pages
* All resources and data schemas documentation is displayed and nicely formatted
* Generated example HTTP requests and responses

<p align="center"><img width="600" src=https://github.com/linkedin/rest.li-api-hub/wiki/http-example-screenshot.png></p>

* Interactive Console for composing and sending requests to live REST resources

<p align="center"><img width="600" src=https://github.com/linkedin/rest.li-api-hub/wiki/console-screenshot.png></p>

* Requests composed in console and be saved to a paste service (gist by default) and shared via a link

Requirements
------------

* SBT                - 0.13.0+
* Play               - 2.2.1+
* rest.li            - 1.15.4+
* rest.li-sbt-plugin - 0.0.2+

Configuration
-------------

* Edit the data loader strategy configuration properties in  `frontend/conf/application.conf` 
  to include URLs to all your resources.

```
   dataLoadStrategy=crawlerFilesystemCached
   filesystemCacheDir=/tmp/apihub
   resourceUrls=[                                                                                                                                                                    
     "http://localhost:1338/fortunes",
     "http://localhost:1338/greetings"
   ]
```

How to run
----------

* `play run`
* In your browser, hit `http://localhost:9000/apihub`

How to debug
------------

* `play debug run`
* Connect IDE debugger to port 9999
