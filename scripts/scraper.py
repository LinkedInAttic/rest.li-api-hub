#!/usr/bin/env python2.6

import urllib2
import urllib
import json
from os import listdir
from os.path import isfile, join
import re

#
# Scrapes artifactory for ivy coordinates of all rest-clients from the dataset.json given as input, and stores the results in cache/mixin.json.
#

# Should run this against the backup artifactory: http://esv4-cm09.corp.linkedin.com:8081/artifactory/

# Download this from a running instance of rest-search-frontend in EI (glulist -f ei2 -s rest-search-frontend),  
# the file is in /export/content/rest-search-frontend/i001/dataset.json.  Put the file in the same directory as this script.
#. e.g. "scp eat1-app484.stg.linkedin.com:/export/content/rest-search-frontend/i001/dataset.json ."
dataset_filename = 'dataset.json' 

# CHANGE THESE!   I got the artifactory cookie by first going to artifactory in a browser and then finding the cookie it gave me using chrome devtools (network view).
# To get a session number, I had to first run a search on the class search view and then grab the session number and use it here.  Ugh, scraping artifactory sucks.
# This could all eventually be automated if we need to rerun this regularly,  but ideally we will find a better and more reliable long term way of gathering this data 
# that does not involve artifactory.
artifactory_cookie = '8C164C82E95C121F58B1AEC07DFCF0D2' # JSESSIONID=<artifactory_cookie>
artifactory_session_number = '1' # Wicket-Ajax-BaseURL: archivesearch.html?<artifactory_session_number>

#
# This is all hacks to mask issues with the limitations of the scraper.  The two main limitations are:
# 1. It's possible for mutliple resources to have the same resource name, as a result, they both can have a builder of the same name,  the scraper cannot distinguish between the two.
# 2. The scraper does not currently fetch all pages of search results, so if there are multiple pages of results, cases of #1 are not detected AND the incorrect version can sometimes be selected.
#
cache_dir = "cache"
conflict_resolution_overrides = {
  "actions": None, # We won't provide client bindings for this, there is no production service using this resource name
  "activitycomments": "com.linkedin.uscp:client-proxy",
  "comments": "com.linkedin.uscp:client-proxy", # ??? does this exist in groups too?
  "connections": "com.linkedin.cloud-connections-ds:cloud-connections-ds-api", # ???
  "contentServiceRest": "com.linkedin.frameworks:janus-rest-api",
  "groupMemberships": "com.linkedin.anet:anet-rest",
  "groups": "com.linkedin.anet:anet-rest", # ??? does lego have something with the same name?
  "likes": "com.linkedin.uscp:client-proxy", # ??? does this exist in groups too?
  "optInSetting": "com.linkedin.contacts:csb-api", # ??? multiple exist
  "shadowBPS": "com.linkedin.network.entitlements:entitlements-shadow-bps-api",
  "stats": "com.linkedin.pinot-service:pinot-service-api" # ??? cloud has stats too...
}

group_rest_client = {
  "restClient": {
    "conf": "restClient", 
    "name": "anet-rest",
    "org": "com.linkedin.anet",
    "rev": "0.0.14"
  }
}

collab_rest_client = {
  "restClient": {
    "conf": "restClient",
    "name": "rest-client-api",
    "org": "com.linkedin.collab-rest-client-api",
    "rev": "1.0.10"
  }
}

overrides = {
  "groupPosts": group_rest_client,
  "groupContentFlags": group_rest_client,
  "groupContentLikes": group_rest_client,
  "groupFlaggedContent": group_rest_client,
  "groupMemberships": group_rest_client,
  "groupPostFollows": group_rest_client,
  "teams": collab_rest_client,
  "collabCloud": collab_rest_client
}

def main():
  read_dataset(dataset_filename)
  parse_all_in_directory()
  #parse_html("cache/groupMemberships.html") # for debugging
  #print compare_versions("0.9.9", "0.14.9")  # for debugging

def artifactory_class_search(classname, name):
  headers = {
    'Accept': 'text/xml',
    'Cookie': 'JSESSIONID=' + artifactory_cookie,
    'Wicket-Ajax': 'true',
    'Wicket-Ajax-BaseURL': 'archivesearch.html?' + artifactory_session_number,
    'Wicket-FocusedElementId': 'id2319'
  }
  
  values = {
    'query': classname,
    ':submit': '1',
    'id2318_hf_0': ''
  }
  
  data = urllib.urlencode(values)
  url = 'http://esv4-cm09.corp.linkedin.com:8081/artifactory/webapp/archivesearch.html?'+artifactory_session_number+'-160.IBehaviorListener.1-searchTabs-panel-searchBorder-form-submit&random=0.41089121461845934'
  req = urllib2.Request(url, data, headers)
  
  response = urllib2.urlopen(req)
  html = response.read()
  f = open(cache_dir + '/' + name + '.html','w')
  print 'saved ' + name + '.html'
  f.write(html)
  
def read_dataset(filename):
  file_text = open(filename)
  json_data = json.load(file_text)
  for clustername, cluster in json_data['clusters'].iteritems():
    for value in cluster['services']:
      path = value['path']
      if(path and len(path) > 1):
        classname = path.replace('/', '').capitalize() + 'Builders'
        artifactory_class_search(classname, path.replace('/', ''))

def uncapitalize(s):
  return s[:1].lower() + s[1:]
          
def parse_all_in_directory():
  filenames = [ join(cache_dir, f) for f in listdir(cache_dir) if isfile(join(cache_dir, f)) ]
  results = {}
  for filename in filenames:
    result = parse_html(filename)
    results.update(result)
    
  results.update(overrides)
  mixin = {'resourceProvenance': results}
  json_str = json.dumps(mixin, sort_keys=True, indent=2)
  f = open('mixin.json','w')
  f.write(json_str)
  print 'Wrote results to mixin.json with ' + str(len(results)) + " dependencies (of " + str(len(filenames)) + ")"

def parse_html(filename):
  with open(filename, 'r') as f:
    read_data = f.read()
    paging = re.search(r'(\d+) (out of|matches found for)', read_data, re.MULTILINE|re.DOTALL)
    #if paging:
      #if int(paging.group(1)) > 20:
        #print paging.group(1) + " additional matches for " + filename + " skipping..."
        #return {}
    ms = re.findall(r'<span onclick="window.location.href=&#039;(.*?)&#039;;return false;" class="item-link">(.*?)</span>', read_data, re.MULTILINE|re.DOTALL)
    artifacts = [m[0] for m in ms]
    results = {}
    conflicting = {}
    for artifact in artifacts:
      parts = re.match(r'http://esv4-cm09\.corp\.linkedin\.com:8081/artifactory/([^/]*)/(.*)/(.*)', artifact, re.MULTILINE|re.DOTALL)
      repository = parts.group(1)
      path = parts.group(2)
      jarfile = parts.group(3)
      pathparts = path.split('/')
      version = pathparts[-1]
      name = pathparts[-2]
      org = '.'.join(pathparts[0:-2])
      resourceName = filename.replace(cache_dir + '/', '').replace('.html', '')
      
      result = { 'restClient': {
        'org': org,
        'name' : name,
        'rev': version,
        'conf': 'restClient'
      }}
      if (not resourceName in results):
        results[resourceName] = result
      else:
        rest_client = results[resourceName]['restClient']
        if rest_client['org'] == org and rest_client['name'] == name:
          if compare_versions(version, rest_client['rev']) > 0:
            results[resourceName] = result
        elif resourceName in conflict_resolution_overrides: # We allow conflicts, but only if we have a disambiguator to pick the right one
          if conflict_resolution_overrides[resourceName] == org  + ':' + name:
            results[resourceName] = result
          # else we ignore the entry
        else:
          conflicting[resourceName] = True
          print "" + resourceName + ":\n\t" + org + ":" + name + " != " + rest_client['org'] + ":" + rest_client['name']
    for conflicting_resource, value in conflicting.iteritems():
      results.pop(conflicting_resource)
    return results

def compare_versions(left, right):
  if right.endswith("SNAPSHOT"): return 1
  if left.endswith("SNAPSHOT"): return -1
  
  lparts = left.split('.')
  rparts = right.split('.')
  for i in range(0, min(len(lparts), len(rparts))):
    l = lparts[i]
    r = rparts[i]
    if(l.isdigit() and r.isdigit()):
      l = int(l)
      r = int(r)
    if(l > r):
      return 1
    if(l < r):
      return -1
  return 0

if __name__ == "__main__":
  main()
