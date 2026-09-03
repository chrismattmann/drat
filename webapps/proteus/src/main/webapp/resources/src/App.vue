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
    <v-app >
     
    <controllbar />
    <v-navigation-drawer
      :mini-variant.sync="mini"
      v-model="drawer"
      hide-overlay
      absolute
      stateless
    >
      
      <v-toolbar flat class="transparent">

        <v-list class="pa-0">
          <v-list-tile avatar>
            <v-list-tile-avatar>
              <img width="32px" height="32px" src="drat-mark.svg" alt="DRAT">
            </v-list-tile-avatar>
  
            <v-list-tile-content>
              <v-list-tile-title>Proteus</v-list-tile-title>
            </v-list-tile-content>
  
            <v-list-tile-action>
              <v-btn
                icon
                @click.stop="mini = !mini"
              >
                <v-icon>chevron_left</v-icon>
              </v-btn>
            </v-list-tile-action>
          </v-list-tile>
        </v-list>
      </v-toolbar>
      <v-spacer/>
      <v-list class="pt-0" dense>
        <v-divider></v-divider>
  
        <v-list-tile
          v-for="item in items"
          :key="item.title"
          @click="selectmenu(item)"
        >
          <v-list-tile-action>
            <v-icon>{{ item.icon }}</v-icon>
          </v-list-tile-action>
  
          <v-list-tile-content>
            <v-list-tile-title>{{ item.title }}</v-list-tile-title>
          </v-list-tile-content>
        </v-list-tile>
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
        <v-btn small color="primary" v-if="!progress" @click="watchRun">
          Watch this run
        </v-btn>
        <v-btn small v-else @click="backToSummary">
          Back to summary
        </v-btn>
      </v-card>

      <v-layout row wrap v-if="progress">
        <v-flex xs3>
          <filelistcomp/>
        </v-flex>
        
        <v-flex xs3>
          <progresscomp/>
          <statisticscomp/>
        </v-flex>
        <v-flex xs6>
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
        </v-flex>
       
      </v-layout>
      <section v-else-if="stateView=='summary'">
      <projectstable  />
       <v-flex xs12>
          <section>
            <bublechartcomp/>
          </section>
        </v-flex>
      <v-spacer />
      
      <v-layout row>
       
          <licensepiecomp/>
          <v-spacer/>
          <topmimepiecomp/>
        
      </v-layout>

      </section>
      <auditsummarycomp v-else/>
      <v-snackbar
        v-model="snackbar"
        top
        right
        :timeout="6000"
        
      >
        <v-badge left center>
        <span slot="badge">{{snackbarmessageindex}}</span>
        
      </v-badge>
      <v-icon
      @click="snackbarmessageindex--"
          color="grey lighten-1"
        >
          navigate_before
        </v-icon>
        {{snackbarmessages[snackbarmessageindex]}}

        <v-icon
          color="grey lighten-1"
          @click="snackbarmessageindex++;
          if(snackbarmessageindex>=snackbarmessages.length)snackbarmessageindex=snackbarmessages.length-1"
        >
          navigate_next
        </v-icon>
        <v-icon
          color="red lighten-1"
          @click="removeelement(snackbarmessageindex);"
        >
          clear
        </v-icon>
        <v-btn
          color="pink"
          flat
          @click="snackbar = false"
        >
          Close
        </v-btn>
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
        { title: 'Summary', icon: 'dashboard' },
        { title: 'Audit', icon: 'question_answer' },
        
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
  beforeDestroy(){
    clearInterval(this.runWatchTimer);
  }
}
</script>

<style>
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
