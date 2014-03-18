Things that must be fixed before opensourcing:

[ ] Find a way to avoid having restli-resource-explorer files monkey patched into data-templates (see the .pdsc files in data-templates/app/pegasus)
[ ] Add extension point so that the mixin loader can be used internally and a UI module for it can be plugged in (or just drop mixin support, it's a bit hairy)
[ ] Find way to cleanly inject internal-to-superblock logic


Small tasks to do before releasing:

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
