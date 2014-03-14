Go/restli development guidelines:

https://iwww.corp.linkedin.com/wiki/cf/pages/viewpage.action?pageId=82944168

Setup:
* Run `./checkout-configs.sh`, this checks out config,  config is checkout using svn and must be checked in using svn if changed

How to run:

* `./start-frontend.sh`
* In your browser, hit `http://localhost:9000`

How to test:

* `mint test` (PCL runs this)

How to clean:
* `./clean`

How to deploy using mint:

* `mint build`
* `mint build-cfg QEI2`
* `mint release`
* `mint release-cfg`
* `mint deploy --config QEI2`

How to setup eclipse:

* Recommended eclipse version: Scala IDE - http://scala-ide.org/ or http://typesafe.com/technology/scala-ide
* Run `./sbt` eclipse to create project
* Add `data-templates/src/main/codegen`, `frontend/app` and `frontend/target/scala-2.9.2/src_managed/main` to the java build path

How to debug:

* `./debug-frontend.sh`
* Connect IDE debugger to port 9999

Alternatively,

* `export SBT_MODE=debug`
* `./start-frontend.sh` or `./sbt`
* Connect IDE debugger to port 5005
