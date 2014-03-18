Things that must be fixed before opensourcing:

[ ] Find a way to avoid having restli-resource-explorer files monkey patched into data-templates (see the .pdsc files in data-templates/app/pegasus)
[ ] Add extension point so that the mixin loader can be used internally and a UI module for it can be plugged in
[ ] Remove all references to EI, EI2, ... make it some configurable value
[ ] Find way to clear internal to superblock out of code

Testing:

[ ] gist
[ ] api console
[ ] links checker


Small tasks to do before releasing:

[ ] Remove all linkedin urls from frontend/conf/application.conf


Things that need to be improved:

[ ] Opensource the play rest.li plugin and use it in D2IdlFetcher and UrlIdlFetcher
