Things that must be fixed before opensourcing:

[ ] figure out how to correctly publish pegasus into local repo so I can test it
[ ] Add extension point so that the mixin loader can be used internally and a UI module for it can be plugged in (or just drop mixin support, it's a bit hairy)
[ ] Find way to cleanly inject internal-to-superblock logic
[ ] try directly using multi-product sbt to avoid having to define the oss projects multiple times
[ ] Remove duplicated static content if possible (change where static content is loaded from?)

Small tasks to do before releasing:

[ ] commit pegasus changes (RB 276699), publish new pegasus version, depend on it
[ ] commit restli-resource-explorer changes (RB 276707), publish new version, depend on it, clean up rewrap TODOs in ExplorerDatasetLoader
[ ] remove local-repo from local repositories for opensource project
[ ] run links checker to test correctness
[ ] test api console
[ ] Remove all linkedin urls from frontend/conf/application.conf
[ ] Collapse the commit history so nothing leaks out
[ ] Validate all config by building locally
[ ] Check in all config/ changes BEFORE committing rest-search_trunk code

Things that need to be improved:

[ ] Clean up root page
[ ] Stop using JavaConversions every, switch to JavaConverters
[ ] Opensource the play rest.li plugin and use it in D2IdlFetcher and UrlIdlFetcher
