Things that must be fixed before opensourcing:

[ ] fix security token that was removed from console and permlink forms
[ ] Find way to cleanly inject internal-to-superblock logic

Small tasks to do before releasing:

[ ] run links checker to test correctness
[ ] test api console
[ ] Remove all linkedin urls from frontend/conf/application.conf
[ ] Collapse the commit history so nothing leaks out
[ ] Validate all config by building locally
[ ] Check in all config/ changes BEFORE committing rest-search_trunk code

Things that need to be improved, but can be done after initial opensourcing:

[ ] Add extension point so that the mixin loader can be used internally and a UI module for it can be plugged in (or just drop mixin support, it's a bit hairy)
[ ] Remove duplicated static content if possible (change where static content is loaded from?)
[ ] try directly using multi-product sbt to avoid having to define the oss projects multiple times
[ ] release pegasus to maven central
[ ] release rest.li-sbt-plugin to maven central, try to just publish it into same maven grouping as rest of pegasus
[ ] include a binary and documentation about go/restli on the website and wiki

[ ] Clean up root page
[ ] Stop using JavaConversions every, switch to JavaConverters
[ ] Opensource the play rest.li plugin and use it in D2IdlFetcher and UrlIdlFetcher
