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

  <section class="progresscomp">
    <v-card id="progresscard">
    <h1>Progress</h1>
    <hr>

     <v-progress-circular
      id="progresscircle"
        :rotate="-90"
        :size="100"
        :width="15"
        :model-value="value"
        :indeterminate="indeterminate"
        color="primary"
      >
        <span v-if="!indeterminate">{{ value }}</span>
      </v-progress-circular><br/>
      {{status}}
      <div id="waitingon" v-if="waitingLabel">
        waiting on {{ waitingLabel }}
      </div>
      <div id="runsource" v-if="startedElsewhere">
        started from the command line
      </div>
      <div id="runrepo" v-if="repo">{{ repo }}</div>
    </v-card>
  </section>

</template>

<script lang="js">
  import axios from 'axios';
  import store from './../store/store';
  export default  {
    name: 'progresscomp',
    props: [],
    mounted() {
        this.loaddata();
        this.timerClearVar = setInterval(function () {
          this.loaddata();
        }.bind(this), 5000);
    },
    beforeUnmount(){
      clearInterval(this.timerClearVar);
    },
    data() {
      return {
          value:0,
          status:"IDLE",
          crawled:false,
          indexed:false,
          mapped:false,
          reduced:false,
          completed:false,
          indeterminate:false,
          sawRunning:false,
          notRunningSeen:0,
          waiting:[],
          timerClearVar:''
      }
    },
    methods: {
      loaddata(){
        this.loadWaitingOn();
        axios.get(this.origin+"/proteus-services/drat/run")
        .then(response=>{
          /*
           * Only an answer that is actually an answer counts. A response that
           * cannot be read is not the same as a run that has ended, and
           * treating it as one closed the watch on a run that was still
           * crawling: this said a run was complete, drew the bar to 100 and
           * put up the dialog, while the crawl carried on behind it.
           */
          const run = response.data && typeof response.data === 'object'
              ? response.data : null;
          if(run === null || typeof run.running !== 'boolean'){
            return;
          }
          this.apply(run);
        })
        .catch(()=>{
          /*
           * The back end being unreachable says nothing about the run. It
           * happens whenever the webapp is restarted underneath an open page,
           * and finishing on it would end the watch every time that happens.
           */
        })
      },

      /*
       * What the run is held up by, if anything. A run can look finished for
       * a minute or more while the aggregate step waits for its conditions to
       * agree that no more logs are coming, and a page that cannot say so is
       * a page showing a spinner for no stated reason.
       */
      loadWaitingOn(){
        axios.get(this.origin+"/proteus-services/service/waiting")
        .then(response=>{
          const reasons = response.data && response.data.reasons;
          this.waiting = Array.isArray(reasons) ? reasons : [];
        })
        .catch(()=>{
          this.waiting = [];
        });
      },

      /*
       * One phase, one reading. The phases below -- crawl, index, map, reduce
       * -- are the steps a run used to be driven as, one command at a time,
       * and are kept because a deployment driving them individually still
       * reports them. A whole audit is now a single workflow holding all of
       * them, so it reports "audit", and there is no honest percentage to put
       * on that from here: the ring says so by spinning rather than by
       * inventing a number.
       */
      apply(run){
        const phase = String(run.phase || '').toLowerCase();

        if(run.running){
          this.sawRunning = true;
          this.notRunningSeen = 0;
        }else{
          /*
           * Asked twice. One reading is a moment, and the marker this comes
           * from is a file that a run rewrites; a reader that catches it
           * between versions gets "nothing is running" for an instant. Ending
           * a watch is not reversible from here, so it takes two.
           */
          this.notRunningSeen = this.notRunningSeen + 1;
          if(this.notRunningSeen >= 2){
            this.finish();
          }
          return;
        }

        this.indeterminate = false;
        if(phase == "crawl"){
          this.status = "Crawling...";
          this.crawled = true;
          store.commit("setCurrentActionStep","CRAWL");
          this.value = 0;
        }else if(phase == "index"){
          this.status = "Indexing...";
          this.indexed = true;
          store.commit("setCurrentActionStep","INDEX");
          this.value = 25;
        }else if(phase == "map"){
          this.status = "Mapping...";
          this.mapped = true;
          store.commit("setCurrentActionStep","MAP");
          this.value = 50;
        }else if(phase == "reduce"){
          this.status = "Reducing...";
          this.reduced = true;
          store.commit("setCurrentActionStep","REDUCE");
          this.value = 75;
        }else if(phase == "reset"){
          /*
           * Before the audit there is a reset: services stopped, the catalog
           * cleared, services started again. It is a minute or two in which
           * DRAT is plainly busy, and saying so beats an empty page.
           */
          this.status = "Clearing previous run...";
          store.commit("setCurrentActionStep","RESET");
          this.indeterminate = true;
        }else if(phase == "audit"){
          this.status = "Auditing...";
          store.commit("setCurrentActionStep","AUDIT");
          this.indeterminate = true;
        }else{
          this.status = "Running...";
          this.indeterminate = true;
        }
      },

      /*
       * Done is the back end saying nothing is running, after it had said
       * something was. This used to ask whether the step this browser had
       * requested was among the ones seen, which no run started anywhere else
       * can satisfy -- so a command line run would watch itself finish and
       * never say it had.
       */
      finish(){
        if(!this.sawRunning || this.completed){
          return;
        }
        /*
         * What actually happened, rather than "Completed" for anything that
         * stopped. A run that aborted during its reset said Completed and
         * drew a full bar, which is the opposite of what had happened.
         */
        const outcome = store.state.run ? store.state.run.lastOutcome : null;
        const finished = outcome !== 'aborted';
        this.status = finished ? "Completed" : "Stopped before finishing";
        this.indeterminate = false;
        this.value = finished ? 100 : 0;
        this.completed = true;
        store.commit("setCurrentActionStep","DONE");
        store.commit("setCurrentActionRequest","");
        /*
         * Said here rather than asked in a dialog. A run finishing is not a
         * question, and the one that used to be put -- click yes to close --
         * had to be answered before anything else could be looked at. The
         * banner above offers the way back whenever the reader wants it.
         */
      }
    },
    computed: {
      origin(){
        return store.state.origin;
      },
      currentActionRequest(){
        return store.state.currentActionRequest;
      },
      repo(){
        return store.state.run ? store.state.run.repo : '';
      },
      startedElsewhere(){
        return store.state.run && store.state.run.startedBy == 'cli';
      },
      /*
       * Named the way the policy names them, minus the prefix the engine uses
       * to say what kind of thing it is: a reader looking at this wants
       * "MapsDone", not "condition:urn:drat:MapsDone".
       */
      waitingLabel(){
        if(this.status == "Completed" || !this.waiting.length){
          return '';
        }
        const names = this.waiting.map(reason=>{
          const withoutKind = String(reason).replace(/^(condition|task):/, '');
          const parts = withoutKind.split(':');
          return parts[parts.length - 1].replace(/-task$/, '');
        });
        return names.filter((n,i)=>names.indexOf(n)===i).join(', ');
      }
    }
}
</script>

<style scoped >
  #progresscard {
    margin-left: 10%;
    margin-top: 10%;
    padding:4%;
    background: lightgray
  }

  #waitingon {
    margin-top: 6px;
    font-size: 12px;
    opacity: 0.85;
  }

  #runsource {
    margin-top: 6px;
    font-size: 12px;
    font-style: italic;
    opacity: 0.75;
  }

  #runrepo {
    margin-top: 2px;
    font-size: 12px;
    word-break: break-all;
    opacity: 0.75;
  }

  #progresscircle{
    margin-top: 4%;
  }
</style>
