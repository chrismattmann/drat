#!/usr/bin/env python
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
# 
# $Id$
#
# Author: mattmann
# Description: Grabs all RAT log files specified on standard in from a root dir
# and then adds up the following stats from them:
#
# Notes, Binaries, Archives, Standards, Apache Licensed, Generated Documents
# Unkown Licenses

import sys
import os

from urllib.request import urlopen, Request
import json
import glob
import hashlib
from collections import Counter


def decode_rat_text(value):
   """Decode text copied from an arbitrary audited file into a RAT report."""
   return value.decode('utf-8', errors='replace')


def parse_license(s):
   li_dict = {'N': 'Notes', 'B': 'Binaries', 'A': 'Archives', 'AL': 'Apache', '!?????': 'Unknown'}
   if s and not s.isspace():
      # RAT writes "<marker> <path>". Splitting at the first slash happened
      # to work for absolute Unix paths, but a Windows path made the marker
      # "!????? C:" and exposed that drive letter as a license name.
      arr = s.strip().split(None, 1)
      li = decode_rat_text(arr[0])
      if li in li_dict:
         li = li_dict[li]

      if len(arr) > 1:
         path = arr[1].replace(b"\\", b"/")
         return [decode_rat_text(path.rsplit(b"/", 1)[-1]), li]
      else:
         #print('split not correct during license parsing '+str(arr))
         return ["/dev/null", li_dict['!?????']]
   else:
      #print('blank line provided to parse license ['+s+']')
      return ["/dev/null", li_dict['!?????']]


def parseFile(filepath):
   f = open(filepath, 'r')
   lines = f.readlines()
   notes = 0
   binaries = 0
   archives = 0
   standards = 0
   apachelicensed = 0
   generated = 0
   unknown = 0

   for line in lines:
      if line.startswith('Notes:'):
         notes = notes + int(line.split(':')[1].strip())
      if line.startswith('Binaries:'):
         binaries = binaries + int(line.split(':')[1].strip())
      if line.startswith('Archives:'):
         archives = archives + int(line.split(':')[1].strip())
      if line.startswith('Standards:'):
         standards = standards + int(line.split(':')[1].strip())
      if line.startswith('Apache Licensed:'):
         apachelicensed = apachelicensed + int(line.split(':')[1].strip())
      if line.startswith('Generated:'):
         generated = generated + int(line.split(':')[1].strip())
      if line.find('Unknown Licenses') != -1:
         unknown = unknown + int(line.split(' ')[0].strip())
         return (notes, binaries,archives,standards,apachelicensed,generated,unknown)

   return (-1,-1,-1,-1,-1,-1,-1)

def count_num_files(path, exclude):
   count = 0
   for root, dirs, files in os.walk(path):
      for filename in files:
         if exclude not in os.path.join(root, filename):
            count += 1
   return count

def is_current_repo_file(fullpath, current_repo):
   try:
      return os.path.commonpath([os.path.realpath(fullpath), current_repo]) == current_repo
   except ValueError:
      return False

def index_solr(json_data):
   #print(json_data)
   request = Request(os.getenv("SOLR_URL") + "/statistics/update/json?commit=true")
   request.add_header('Content-type', 'application/json')
   urlopen(request, json_data.encode('utf-8'))

def main(argv=None):
   usage = 'rat_aggregator.py logfile1 logfile2 ... logfileN'
   #print("starting rat aggregator")

   repo_file_url = os.getenv("DRAT_HOME") + "/data/repo"
   with open(repo_file_url,'rb')as repoFile:
      data = ''
      for line in repoFile:
          data+=line.decode('utf-8')
   rep = json.loads(data)
   current_repo = os.path.realpath(rep["repo"])
   
   index_solr(json.dumps([rep]))

   if len(argv) == 0:
      print(usage)
      sys.exit()

   retVal = True

   if retVal:
      # Copy Data with datetime variables above, extract output from RatAggregate file, extract data from Solr Core
      #print("\nCopying data to Solr and Output Directory...\n")

      # Parse RAT logs
      rat_logs_dir = os.getenv("DRAT_HOME") + "/data/archive/rat/*/*.log"
      rat_license = {}
      rat_header = {}
      for filename in glob.glob(rat_logs_dir):
         l = 0
         h = 0
         cur_file = ''
         cur_header = ''
         cur_section = ''
         parsedHeaders = False
         parsedLicenses = False

         with open(filename, 'rb') as f:
            for line in f:
               if b'*****************************************************' in line:
                  l = 0
                  h = 0
                  if cur_section == 'licenses':
                     parsedLicenses = True
                  if cur_section == 'headers':
                     parsedHeaders = True

                  cur_file = ''
                  cur_header = ''
                  cur_section = ''
               if line.startswith(b'  Files with Apache') and not parsedLicenses:
                  cur_section = 'licenses'
               if line.startswith(b' Printing headers for ') and not parsedHeaders:
                  cur_section = 'headers'
               if cur_section == 'licenses':
                  l += 1
                  if l > 4:
                     line = line.strip()
                     if line:
                        #print("File: %s with License Line: %s" % (filename, line))
                        li = parse_license(line)
                        rat_license[li[0]] = li[1]
                        #print(li)
               if cur_section == 'headers':
                  if b'=====================================================' in line or b'== File:' in line:
                     h += 1
                  if h == 2:
                     cur_file = decode_rat_text(line.split(b"/")[-1].strip())
                  if h == 3:
                     # RAT may include bytes copied verbatim from a file that
                     # is not UTF-8 (for example Windows-1252 punctuation or
                     # binary content misidentified as text). One such byte
                     # must not discard the entire repository aggregation.
                     cur_header += decode_rat_text(line)
                  if h == 4:
                     rat_header[cur_file] = cur_header.split("\n", 1)[1]
                     cur_file = ''
                     cur_header = ''
                     h = 1
         if h == 3:
            rat_header[cur_file] = cur_header.split("\n", 1)[1]
         parsedHeaders = True
         parsedLicenses = True

      #Additionally
      stats = {}
      stats['id'] =rep["repo"]

      # Extract data from Solr
      neg_mimetype = ["image", "application", "text", "video", "audio", "message", "multipart"]
      response = json.loads(urlopen(os.getenv("SOLR_URL") + "/drat/select?q=*%3A*&rows=0&facet=true&facet.field=mimetype&wt=json&indent=true").read().decode('utf-8'))
      mime_count = response["facet_counts"]["facet_fields"]["mimetype"]

      for i in range(0, len(mime_count), 2):
         if mime_count[i].split("/")[0] not in neg_mimetype:
            stats["mime_" + mime_count[i]] = mime_count[i + 1]


      # Use the files actually admitted to this audit. Walking the checkout a
      # second time counted excluded build/vendor directories and made the
      # summary disagree with both crawl progress and its own file records.
      stats["files"] = response["response"]["numFound"]
      # Index RAT logs into Solr
      response = json.loads(urlopen(os.getenv("SOLR_URL") +
                                "/drat/select?q=*%3A*&fl=filename%2Cfilelocation%2Cmimetype&wt=json&rows=0&indent=true").read().decode('utf-8'))
      num_found = response['response']['numFound']
      response = json.loads(urlopen(os.getenv("SOLR_URL") +
                                "/drat/select?q=*%3A*&fl=filename%2Cfilelocation%2Cmimetype&wt=json&rows="
                                + str(num_found) +"&indent=true").read().decode('utf-8'))
      docs = response['response']['docs']
      file_data = []
      unique_file_data = {}
      batch = 100
      dc = 0

      for doc in docs:
         fdata = {}
         fdata['id'] = os.path.join(doc['filelocation'][0], doc['filename'][0])
         if not is_current_repo_file(fdata['id'], current_repo):
            continue
         m = hashlib.md5(fdata['id'].encode('utf-8'))
         hashId = m.hexdigest()
         fileId = hashId+"-"+doc['filename'][0]

         if fileId not in rat_license:
            #print "File: "+str(fdata['id'])+": ID: ["+fileId+"] not present in parsed licenses => Likely file copying issue. Skipping."
            continue #handle issue with DRAT #93

         fdata["type"] = 'file'
         fdata['parent'] = rep["repo"]
         fdata['mimetype'] = doc['mimetype'][0]
         fdata['license'] = rat_license[fileId]
         if fileId in rat_header:
            fdata['header'] = rat_header[fileId]
         unique_file_data[fdata['id']] = fdata

      for fdata in unique_file_data.values():
         file_data.append(fdata)
         dc += 1
         if dc % batch == 0:
            json_data = json.dumps(file_data)
            index_solr(json_data)
            file_data = []
      if dc % batch != 0:
         json_data = json.dumps(file_data)
         index_solr(json_data)

      license_counts = Counter(fdata["license"] for fdata in unique_file_data.values())
      totalNotes = license_counts["Notes"]
      totalBinaries = license_counts["Binaries"]
      totalArchives = license_counts["Archives"]
      totalApache = license_counts["Apache"]
      totalGenerated = license_counts["Generated"]
      totalUnknown = license_counts["Unknown"]
      # These are already represented by their own aggregate fields. Keep
      # Standards mutually exclusive so one file cannot appear in both the
      # Unknown (or Apache) bar and the Standards bar.
      non_standard_licenses = set([
          "Notes", "Binaries", "Archives", "Generated", "Apache", "Unknown"
      ])
      totalStandards = sum(count for license_name, count in license_counts.items()
                           if license_name not in non_standard_licenses)

      stats["license_Notes"] = totalNotes
      stats["license_Binaries"] = totalBinaries
      stats["license_Archives"] = totalArchives
      stats["license_Standards"] = totalStandards
      stats["license_Apache"] = totalApache
      stats["license_Generated"] = totalGenerated
      stats["license_Unknown"] = totalUnknown

      # Write data into Solr
      stats["type"] = 'software'
      stats_data = []
      stats_data.append(stats)
      json_data = json.dumps(stats_data)
      index_solr(json_data)

      # Copying data to Output Directory
      print ("Notes,Binaries,Archives,Standards,Apache,Generated,Unknown")
      print(str(totalNotes)+","+str(totalBinaries)+","+str(totalArchives)+","+str(totalStandards)+","+str(totalApache)+"    ,"+str(totalGenerated)+","+str(totalUnknown))
      
      #print("\nData copied to Solr and Output Directory: OK\n")


if __name__ == "__main__":
   main(sys.argv[1:])
