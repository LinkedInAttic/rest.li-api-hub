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

Install and Run
---------------

Requirements:
* Java 1.6+

Download latest stable build: http://rest.li/releases/apihub/restli-apihub-0.0.2.zip

Unzip the build, e.g.:
```sh
unzip restli-apihub-0.0.2.zip
```

Start the application:
```sh
Linux/OSX:
cd restli-apihub-0.0.2/bin
./restli-apihub

Windows:
cd restli-apihub-0.0.2\bin
restli-apihub.bat
```

In your browser, hit `http://localhost:9000/apihub`

Configuration
-------------

Edit the data loader strategy configuration properties in  `conf/application.conf`.


* Comment out the test data loader:
```
   #dataLoadStrategy=resource
   #filesystemCacheDir=int-test-dataset.json
```

* Uncomment the crawling loader, modify the `resourceUrls` list to include URLs to all your rest.li resources.
```
   dataLoadStrategy=crawlerFilesystemCached
   filesystemCacheDir=/tmp/apihub
   resourceUrls=[                                                                                                                                                                    
     "http://localhost:1338/fortunes",
     "http://localhost:1338/greetings"
   ]
```

Building From Source
--------------------

Requirements:
* SBT                - 0.13.0+
* Play               - 2.2.1+

How to run:
* `play run`
* In your browser, hit `http://localhost:9000/apihub`

How to debug:
* `play debug run`
* Connect IDE debugger to port 9999
