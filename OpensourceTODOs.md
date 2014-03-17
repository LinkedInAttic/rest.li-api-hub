Figure out how to make the internal go/restli code embed this opensource project code
  This is basically working.  See: https://iwww.corp.linkedin.com/wiki/cf/display/ENGS/Opensourcing+plan

Look for way to avoid having restli-resource-explorer files monkey patched into data-templates (see the .pdsc files in data-templates/app/pegasus)

Remove linkedin specific links from main.scala.html, make them pluggable
Remove all linkedin urls from frontend/conf/application.conf

Clean up frontend/conf/logger.xml to not contain linkedin logging stuff
Fix ZK crawler, it is currently commented out.  See D2IdlFetcher.
Add extension point so that the mixin loader can be used internally and a UI module for it can be plugged in
Remove all references to EI, EI2, ...

Opensource the play rest.li plugin and use it in D2IdlFetcher and UrlIdlFetcher

Replace paste-in stuff with gist and/or make pluggables