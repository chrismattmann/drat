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
<template>
  <div id="app">
    <v-app>

    <controllbar />
    <v-navigation-drawer
      v-model="drawer"
      :rail="mini"
      permanent
    >
      <v-list class="pa-0">
        <v-list-item>
          <template #prepend>
            <v-avatar size="32">
              <img width="32px" height="32px" src="drat-mark.svg" alt="DRAT">
            </v-avatar>
          </template>

          <v-list-item-title>Proteus</v-list-item-title>

          <template #append>
            <v-btn icon variant="text" @click.stop="mini = !mini">
              <v-icon>mdi-chevron-left</v-icon>
            </v-btn>
          </template>
        </v-list-item>
      </v-list>

      <v-divider></v-divider>

      <v-list class="pt-0" density="compact">
        <v-list-item
          v-for="item in items"
          :key="item.title"
          @click="selectmenu(item)"
        >
          <template #prepend>
            <v-icon>{{ item.icon }}</v-icon>
          </template>

          <v-list-item-title>{{ item.title }}</v-list-item-title>
        </v-list-item>
      </v-list>
    </v-navigation-drawer>
    <div id="contentpane" >

      <!--
        Somewhere to say what is happening and one control to act on it. The
        watch view could only be entered by starting a run from this browser
        and left by answering a dialog, so a run already under way was
        unreachable and stepping away from one meant confirming you meant to.
        Neither starts or stops anything: the run is the run either way.
      -->
      <v-card id="runbanner" v-if="runningNow || progress">
        <span id="runbannertext">
          <span v-if="runningNow">
            <strong>DRAT is running</strong>
            <span v-if="phaseLabel"> &mdash; {{ phaseLabel }}</span>
            <span v-if="runStartedByCli"> (started from the command line)</span>
          </span>
          <span v-else>No run in progress</span>
        </span>
        <v-btn size="small" color="primary" v-if="!progress" @click="watchRun">
          Watch this run
        </v-btn>
        <v-btn size="small" v-else @click="backToSummary">
          Back to summary
        </v-btn>
      </v-card>

      <v-row v-if="progress">
        <v-col cols="3">
          <filelistcomp/>
        </v-col>

        <v-col cols="3">
          <progresscomp/>
          <statisticscomp/>
        </v-col>
        <v-col cols="6">
          <section >
          <!--
            Drawn only once this run has something of its own to draw. Not
            refreshing them was not enough: a chart already on screen stays on
            screen, so the previous run's licences and mime types sat there
            through the clear and the crawl, on a page describing this run.
            Taking the components out is what removes them.
          -->
          <barchartcomp v-if="licencesToShow" />
          <piechart v-if="mimeTypesToShow" />
          </section>
        </v-col>

      </v-row>
      <section v-else-if="stateView=='summary'">
      <projectstable  />
       <v-col cols="12">
          <section>
            <bublechartcomp/>
          </section>
        </v-col>
      <v-spacer />

      <!--
        Side by side, sharing the width. A v-spacer between them pushed each
        to its own edge with the whole middle of the page empty between.
      -->
      <v-row>
        <v-col cols="12" md="6">
          <licensepiecomp/>
        </v-col>
        <v-col cols="12" md="6">
          <topmimepiecomp/>
        </v-col>
      </v-row>

      </section>
      <auditsummarycomp v-else/>
      <v-snackbar
        v-model="snackbar"
        location="top right"
        :timeout="6000"
      >
        <v-badge :content="snackbarmessageindex" inline></v-badge>
        <v-icon
          color="grey-lighten-1"
          @click="snackbarmessageindex--"
        >
          mdi-chevron-left
        </v-icon>
        {{snackbarmessages[snackbarmessageindex]}}

        <v-icon
          color="grey-lighten-1"
          @click="snackbarmessageindex++;
          if(snackbarmessageindex>=snackbarmessages.length)snackbarmessageindex=snackbarmessages.length-1"
        >
          mdi-chevron-right
        </v-icon>
        <v-icon
          color="red-lighten-1"
          @click="removeelement(snackbarmessageindex);"
        >
          mdi-close
        </v-icon>

        <template #actions>
          <v-btn color="pink" variant="text" @click="snackbar = false">
            Close
          </v-btn>
        </template>
      </v-snackbar>
    </div>

    <v-spacer/>
    <v-card id="footercard">
      <img id="footerlogo" height="64px" src="drat-logo.svg" alt="DRAT">
    </v-card>

    </v-app>
  </div>
</template>
<script>


import controllbar from './components/controll_bar.vue'
import projectstable from './components/projectstable.vue'
import barchartcomp from './components/barchartcomp.vue'
import piechart from './components/piechart.vue'
import filelistcomp from './components/filelistcomp.vue'
import statisticscomp from './components/statisticscomp.vue'
import progresscomp from './components/progresscomp.vue'
import bublechartcomp from './components/bublechartcomp.vue'
import licensepiecomp from './components/licensepiecomp.vue'
import topmimepiecomp from './components/topmimepiecomp.vue'
import auditsummarycomp from './components/auditsummarycomp.vue'
import axios from 'axios'
import store from './store/store'
export default {
  name: 'app',
  store,
  components: {
   
    controllbar,
    projectstable,
    barchartcomp,
    filelistcomp,
    statisticscomp,
    piechart,
    progresscomp,
    bublechartcomp,
    licensepiecomp,
    topmimepiecomp,
    auditsummarycomp
  },
  data(){
    return{
      snackbar:false,
      snackbarmessage:'',
      snackbarmessageindex:0,
      snackbarmessages:[],
      drawer: true,
      items: [
        { title: 'Summary', icon: 'mdi-view-dashboard' },
        { title: 'Audit', icon: 'mdi-comment-question-outline' },
        
      ],
      mini: true,
      right: null,
      runWatchTimer: null
    }
  },
  methods:{
    setHost(){
      this.$log.info(location.origin)
      store.commit("setOrigin",location.origin);
    },
    watchRun(){
      store.commit("setprogress",true);
      store.commit("setView","summary");
    },
    backToSummary(){
      // Leaving the watch does not touch the run, so there is nothing to
      // confirm and nothing to lose by going back.
      store.commit("setprogress",false);
    },
    /*
     * A run is a run whoever started it. This asks the back end what is
     * happening rather than remembering what this browser asked for, so a run
     * started from the command line -- or from another browser, or before this
     * page was opened -- opens the same watch view as one started here.
     */
    watchForARun(){
      axios.get(location.origin+"/proteus-services/drat/run")
      .then(response=>{
        const run = response.data && typeof response.data === 'object'
            ? response.data : null;
        if(run === null || typeof run.running !== 'boolean'){
          // Not an answer. Leaving the last one in place is right: an
          // unreadable response is not news about the run.
          return;
        }
        store.commit("setRun",run);

        /*
         * Noticing a run is not the same as opening it. This used to set the
         * watch view going by itself, which meant a reload landed on the
         * watch rather than where the reader had been -- and leaving it only
         * lasted until the next poll brought them back. The banner says a run
         * is happening; going to look at it is a click.
         *
         * The repository is worth taking either way: the charts are drawn
         * about a repository, and this is the one being audited.
         */
        if(run.running && run.repo && store.state.currentRepo !== run.repo){
          store.commit("setCurrentRepo",run.repo);
        }
      })
      .catch(()=>{
        // The back end not answering is not a reason to tear the view down;
        // the next poll will say either way.
      });
    },
    /*
     * Both of these were called by the template and by selectmenu below but
     * were never defined, so every click on Summary or Audit threw
     * "showsnackbar is not a function" -- after the commits, so the view
     * still changed and the error only showed in the console. Defined here
     * rather than carried across as a crash.
     */
    showsnackbar(){
      // Only worth raising when there is something to say.
      this.snackbar = this.snackbarmessages.length > 0;
    },
    removeelement(index){
      if(index < 0 || index >= this.snackbarmessages.length){
        return;
      }
      this.snackbarmessages.splice(index, 1);
      if(this.snackbarmessageindex >= this.snackbarmessages.length){
        this.snackbarmessageindex = Math.max(0, this.snackbarmessages.length - 1);
      }
      if(this.snackbarmessages.length === 0){
        this.snackbar = false;
      }
    },
    selectmenu(menu){
      // No dialog either way: moving between the summary and the watch view
      // does not start or stop anything, and the banner offers the way back.
      if(menu.title=="Summary"){
        store.commit("setprogress",false);
        store.commit("setView","summary");
      }else if(menu.title=="Audit"){
        store.commit("setprogress",false);
        store.commit("setView","audit");
      }
      this.showsnackbar();
    },
  },
  computed:{
    progress (){
      return store.state.progress;
    },
    stateView(){
      return store.state.view;
    },
    runningNow(){
      return !!(store.state.run && store.state.run.running);
    },
    /* What RAT found, so not before RAT has finished an audit. */
    licencesToShow(){
      return store.state.ratFinished > 0;
    },
    /* Read from the index, so not before the index has caught up. */
    mimeTypesToShow(){
      return store.state.indexDone;
    },
    runPhase(){
      return store.state.run ? store.state.run.phase : '';
    },
    phaseLabel(){
      // Said the way it would be said out loud.
      const labels = {
        reset: 'clearing the previous run',
        crawl: 'crawling',
        index: 'indexing',
        map: 'mapping',
        reduce: 'reducing',
        audit: 'auditing'
      };
      const phase = this.runPhase;
      return phase ? (labels[String(phase).toLowerCase()] || phase) : '';
    },
    runStartedByCli(){
      return !!(store.state.run && store.state.run.startedBy == 'cli');
    },
  },
  mounted(){
   this.setHost();
   this.watchForARun();
   /*
    * Two seconds, not five. Everything that reacts to a phase reads this --
    * whether the crawl counters are shown, what the banner says -- and a
    * five second gap is five seconds of a page describing the phase before
    * the one it is in. It is one small request against this deployment's own
    * Tomcat.
    */
   this.runWatchTimer = setInterval(this.watchForARun, 2000);
  },
  beforeUnmount(){
    clearInterval(this.runWatchTimer);
  }
}
</script>

<style>
/*
 * Chart text. The slice and legend labels inherited whatever the page had,
 * which at the sizes these are drawn at was too faint to read against the
 * lighter slices.
 */
.chart {
  width: 100%;
  height: auto;
  display: block;
}

.chart text,
#bublesvg text {
  font-family: 'Avenir', Helvetica, Arial, sans-serif;
}

.chart .slice-label,
#bublesvg .bubble-label {
  font-size: 12px;
  font-weight: 600;
  paint-order: stroke;
}

.chart .legend-label {
  font-size: 12.5px;
  fill: #2c3e50;
  font-variant-numeric: tabular-nums;
}

/*
 * Both summary charts sit in one row and are read side by side, so they are
 * the same height and their headings line up whatever each one contains.
 */
.chartcard {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.chartbody {
  padding: 12px 16px 16px 16px;
  flex: 1 1 auto;
}

.chart .arc path {
  transition: opacity 120ms ease-in-out;
  cursor: default;
}

.chart .legend-row {
  cursor: default;
}

.chart .legend-row-on .legend-label {
  font-weight: 700;
}

.chart .pie-readout {
  font-size: 13px;
  font-weight: 700;
  fill: #1a1a1a;
  paint-order: stroke;
  stroke: rgba(255, 255, 255, 0.85);
  stroke-width: 3px;
  pointer-events: none;
}

.chart .chart-empty {
  font-size: 13px;
  fill: #6b7785;
}

#app {
  font-family: 'Avenir', Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  text-align: center;
  color: #2c3e50;
  margin-top: 0px;
}

#contentpane{
  padding-left: 10%;
  padding-right: 10%;
  margin-bottom: 80px;
}
#runbanner{
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px;
  margin-bottom: 12px;
  text-align: left;
}

#runbannertext{
  flex: 1 1 auto;
}

#footercard{
  background-color: #2c3e50;
  width: 100%;
  height: auto;
  position: fixed;
  bottom: 0%;
  z-index: 900;
}

#logospace{
  width: 80%;
}

#footerlogo{
  /* Room around it: the wordmark sits on the footer's own dark ground and
     was previously flush against the edges of the bar. */
  margin: 12px 0 10px 0;
}
</style>
