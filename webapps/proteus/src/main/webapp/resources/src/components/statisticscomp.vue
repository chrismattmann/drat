<!--
Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements.  See the NOTICE.txt file distributed with this work for
additional information regarding copyright ownership.  The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License.  You may obtain a copy of
the License at
     http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
License for the specific language governing permissions and limitations under
the License.
-->
<template lang="html">

  <section class="statisticscomp">
    <v-card id="statisticscard">
    
    <!--
      Not shown while the previous run is being cleared. There is nothing to
      report about a crawl that has not begun, and a row reading "0 of 3503
      files Crawled" is a claim about this run rather than a description of
      the catalog being emptied. Zeroing the numbers was not enough: the rows
      themselves have to go.
    -->
    <v-card id="crawlingprogress" v-if="!clearingPreviousRun">
       <v-progress-linear height="10" v-model="crawlingprogress"></v-progress-linear>
      <strong>{{stat.crawledfiles}}</strong> of <strong>{{stat.numOfFiles}}</strong> files Crawled
     
    </v-card>
    <v-card id="indexingprogress" v-if="!clearingPreviousRun">
       <v-progress-linear height="10" v-model="indexingprogress"></v-progress-linear>
      <strong>{{stat.indexedfiles}}</strong> of <strong>{{stat.numOfFiles}}</strong> files Indexed
     
    </v-card>
  
    <v-toolbar height="50" color="primary" dark>
      <v-toolbar-title>Statistics</v-toolbar-title>
      <v-spacer></v-spacer>
    </v-toolbar>
    <hr>
    <v-expansion-panel
        v-model="panel"
        expand
      >
        <v-expansion-panel-content id="header"
        >
          <div  slot="header"><b>Repository</b></div>
          <v-card>
            <v-card-text>
            <p style="text-align:left;">In-Memory Size  : <span style="float:right;">    <strong> {{stat.size}}</strong> <br></span></p>
            <p style="text-align:left;">Number of files :<span style="float:right;">  <strong>{{stat.numOfFiles}} </strong><br></span></p>
            </v-card-text>
          </v-card>

        </v-expansion-panel-content>
        <v-expansion-panel-content id="header"
        >
          <div slot="header"><b>Drat</b></div>
          <v-card>
            <v-card-text>
                <strong>{{stat.runningRatInstances}}</strong> Rat Instances <strong>Running</strong><br>
                <strong>{{stat.finishedRatInstances}}</strong> Rat Instances <strong> Finished</strong>
            </v-card-text>
          </v-card>
          
        </v-expansion-panel-content>
      </v-expansion-panel>
    </v-card>
  </section>

</template>

<script lang="js">
 import store from './../store/store'
  import axios from 'axios'
  export default  {
    name: 'statisticscomp',
    store,
    props: [],
    mounted() {
      this.loadSizeData();
        this.timerClearVar = setInterval(function () {
          /*
           * An audit is one workflow holding the crawl, the index and the
           * RAT steps, so while it runs every one of these figures is live.
           * Each used to be asked for only during the single old phase that
           * fed it -- CRAWL for the crawled count, INDEX for the indexed one
           * -- and a run that reports "audit" matched none of them: the
           * counts were fetched once as the panel opened, which is while the
           * reset has the catalog empty, and then left at zero for the rest
           * of the run.
           */
          const auditing = this.currentState=="AUDIT";
          /*
           * Nothing about the crawl while the previous run is being cleared.
           * The catalog is emptying, so what these read is the last run's
           * count going down -- 3,247 crawled, then 2,449, then zero -- which
           * describes a deletion rather than any progress.
           */
          const clearing = this.clearingPreviousRun;
          if(clearing){
            this.stat.crawledfiles = 0;
            this.stat.indexedfiles = 0;
            // Otherwise this stays true from the run before, and the mime
            // breakdown it gates comes straight back while the catalog it
            // describes is being deleted.
            store.commit("setIndexDone", false);
          }
          if(this.currentState!="IDLE") this.loadSizeData();
          // Always: the licence breakdown is shown or hidden on the back of
          // this, so it has to describe now and not the last phase that
          // happened to ask.
          this.loadInstanceCount();
          if(!clearing && (auditing || this.currentState=="CRAWL")) this.loadCrawledFiles();
          if(!clearing && (auditing || this.currentState=="INDEX" || this.currentState=="MAP"))this.loadIndexedFiles();
        }.bind(this), 1000);
    },
    beforeDestroy(){
        clearInterval(this.timerClearVar);
    },
    data() {
      return {
          timerClearVar:'',
          categories:{},
          categoriesAsked:false,
          excludes:'',
          stat:{
            size:0,
            numOfFiles:0,
            runningRatInstances:0,
            finishedRatInstances:0,
            crawledfiles:0,
            indexedfiles:0,
          },
          panel:[false,true,true]

      }
    },
    methods: {
      loadSizeData(){
        /*
         * Asked about the same files the run is working on, and asked that
         * way for as long as this panel is open. The names come from the run
         * itself and are kept once seen: the marker goes when the run ends,
         * and without them the totals would jump from the audited files back
         * to every file in the repository the moment it finished.
         */
        const run = store.state.run;
        if(run && run.excludes && run.excludes.length){
          this.excludes = run.excludes.join(",");
        }
        const skip = this.excludes
            ? "&exclude=" + encodeURIComponent(this.excludes) : "";
        axios.get(this.origin+"/proteus-services/service/repo/size?dir="
            + this.currentRepo + skip)
        .then(response=>{
          if (!(isNaN(parseFloat(response.data.memorySize)) || !isFinite(response.data.memorySize))){
          var units = ['bytes', 'kB', 'MB', 'GB', 'TB', 'PB'],
          number = Math.floor(Math.log(response.data.memorySize) / Math.log(1024)) | 0;
            this.stat.size = (response.data.memorySize / Math.pow(1024, Math.floor(number))).toFixed(1) +  ' ' + units[number];
            this.stat.numOfFiles = response.data.numberOfFiles;
          }
        })
        .catch(error=>{
          throw error;
        });

      },
      /*
       * How many RAT audits are running and how many have finished, counted
       * by the back end from the instances that are actually RAT audits. This
       * used to read every instance the workflow manager held, which during
       * the early part of a run is the pipeline's own scaffolding -- phases,
       * redirectors, the conditions gating them -- and reported five RAT
       * audits running before RAT had been asked to do anything.
       */
      loadInstanceCount(){
        axios.get(this.origin+"/proteus-services/service/rat/progress")
        .then(response=> {
          if(response.data){
            this.stat.runningRatInstances = response.data.running || 0;
            this.stat.finishedRatInstances = response.data.finished || 0;
            store.commit("setRatFinished", this.stat.finishedRatInstances);
          }
        })
        .catch(()=>{
          // Leave the last known figures rather than showing zeroes.
        });
      },
      loadCrawledFiles(){
        axios.get(this.origin+"/proteus-services/filemanager/progress")
        .then(response=> {
            this.stat.crawledfiles = response.data.crawledFiles;
        });
      },
      loadIndexedFiles(){
        axios.get(this.origin+"/proteus-services/solr/drat/select?q=producttype:GenericFile&fl=numFound&wt=json&indent=true")
        .then(response=>{
          this.stat.indexedfiles = response.data.response.numFound;
          // Whether the index has caught up with everything that will be
          // crawled. The mime breakdown on the run view reads the index.
          const total = this.stat.numOfFiles;
          store.commit("setIndexDone",
              total > 0 && this.stat.indexedfiles >= total);
        });
      }

    },
    computed: {
      currentRepo (){
          return store.state.currentRepo;
      },
      origin(){
        return store.state.origin;
      },
      currentState(){
        return store.state.currentActionStep;
      },
      /*
       * Taken from the run itself rather than from the step another component
       * publishes. That step is set by the progress panel, which polls every
       * five seconds while this polls every one -- so for the first few
       * seconds of a reset this still believed the previous phase and went on
       * showing the previous run's counts as they drained away. The run's own
       * phase is kept current by the poll that watches for a run at all.
       */
      clearingPreviousRun(){
        const run = store.state.run;
        if(!run || !run.running){
          return false;
        }
        const phase = String(run.phase || '').toLowerCase();
        // An unknown phase counts as clearing rather than as crawling. This
        // panel opens before the first poll has said what is happening, and
        // guessing "crawling" there puts the previous run's counts on screen
        // for as long as that takes.
        return phase === 'reset' || phase === '';
      },
      crawlingprogress(){
        return this.stat.crawledfiles/this.stat.numOfFiles *100;
      },
      indexingprogress(){
        return this.stat.indexedfiles/this.stat.numOfFiles * 100;
      }
    }
}
</script>

<style scoped>
  #statisticscard{
    margin-top:10%;
    margin-left:10%;
  }
  #header{
    background-color:lightgray
  }
  #indexingprogress{
    margin-bottom: 5%;
  }
</style>
