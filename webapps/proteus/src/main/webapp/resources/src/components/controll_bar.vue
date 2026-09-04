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
  <section class="controll-bar">
    <v-card id="controllbarcard">
      <v-row>
        <v-text-field variant="solo"
          name="url"
          label="Repository to add to DRAT"
          v-model="url"
        />
        <!--
          Two things to do, both of them whole. The bar used to offer Go,
          Crawl, Index, Map, Reduce and Reset from one dropdown, which
          presented the phases of an audit as if they were alternatives to
          it: choosing Map on a repository that had never been crawled
          started a run with nothing to map. The phases are still there in
          bin/drat for anyone driving them deliberately; what this offers is
          the audit, and the way to clear one.
        -->
        <v-btn v-on:click="dialog=true" color="primary"> Go </v-btn>
        <v-btn v-on:click="confirmReset=true" id="resetbtn"> Reset </v-btn>

        <v-dialog v-model="dialog" persistent max-width="500px">
          <v-card id="repodetailscard">
            <v-text-field variant="solo"
                name="url"
                label="Repository to add to DRAT"
                v-model="url"
              />
              <hr>
              <v-text-field variant="solo"
                name="name"
                label="Name of the repository"
                v-model="reponame"
              />

              <v-spacer/>
              <hr/>
              <v-text-field variant="solo"
                name="description"
                label="Description about the repository"
                v-model="repodesc"
              />
              <hr/>

            <v-btn v-on:click="dialog=false" :disabled="starting">Close</v-btn>
            <v-btn v-on:click="go" color="primary" :loading="starting">Go</v-btn>
          </v-card>
        </v-dialog>

        <!--
          Asked first. Reset stops the services, empties the catalog and
          clears every workflow instance, and there is nothing to undo it
          with -- which is a poor fit for a button sitting next to the one
          that starts a run.
        -->
        <v-dialog v-model="confirmReset" persistent max-width="500px">
          <v-card id="resetconfirmcard">
            <v-card-title>Clear the previous run?</v-card-title>
            <v-card-text>
              This empties the catalog and clears every workflow instance
              from the last audit. The statistics behind the summary charts
              are kept.
            </v-card-text>
            <v-card-actions>
              <v-spacer/>
              <v-btn v-on:click="confirmReset=false">Cancel</v-btn>
              <v-btn v-on:click="reset" color="primary">Reset</v-btn>
            </v-card-actions>
          </v-card>
        </v-dialog>
      </v-row>

      <v-snackbar v-model="hasProblem" :timeout="8000" color="error">
        {{ problem }}
      </v-snackbar>
    </v-card>
  </section>

</template>

<script lang="js">
  import store from './../store/store'
  import axios from 'axios'
  
  export default  {
    name: 'controllbar',
    store,
    props: [],
    mounted() {

    },
    watch:{
      progress:function(newVal){
        if(newVal==false){
          this.clearRepoDetails();
        }
      }
    },
    data() {
      return {
        dialog:false,
        confirmReset:false,
        starting:false,
        problem:'',
        msg: 'null for now',
        url: '',
        repo:'',
        repodesc:'',
        repoloc:'',
        reponame:''
      }
    },
    methods: {
        clearRepoDetails:function(){
          this.url="";
          this.repo="";
          this.repodesc="";
          this.repoloc="";
          this.reponame="";
        },
        run: function(){
           
            store.commit("invert");
            store.commit("setCurrentRepo",this.url);
        },
        search:function(){
          store.commit("setprogress",false);
        },
        /*
         * Runs the audit. There is one action now, so there is nothing to
         * translate a menu selection into: this used to switch over six
         * labels to pick the endpoint, and five of those were the phases of
         * the sixth.
         */
        go: function(){
          if(this.url.length==0){
            this.problem = "Enter the path to the directory you want audited.";
            return;
          }

          var body = {
            id:this.repoloc,
            repo:this.url,
            name:this.reponame,
            loc_url:this.repoloc,
            description:this.repodesc
          };

          /*
           * Asked first, and answered straight away.
           *
           * /go does not return until the whole audit has finished, so it
           * cannot be waited on to find out whether the request was any good:
           * doing that left the dialog open with a spinning button for the
           * length of the run. Committing first instead took the reader to a
           * spinning progress ring for a run the back end had refused, and
           * said nothing about why. So the question -- is this a repository
           * I can audit -- is asked on its own, and only once it is answered
           * does the run start and the view change.
           */
          this.starting = true;
          axios.get(this.origin + "/proteus-services/drat/repo/valid?dir="
              + encodeURIComponent(this.url))
          .then(()=>{
            this.starting = false;
            store.commit("setCurrentActionRequest","GO");
            store.commit("setprogress",true);
            store.commit("setCurrentRepo",this.url);
            this.dialog = false;

            /*
             * Started and not waited for. The response comes when the audit
             * is over; what happens in between is reported by the run itself,
             * which the watch view is already following.
             */
            axios.post(this.origin+"/proteus-services/drat/go",body)
            .then(response=>{
              this.$log.info(response.data);
            })
            .catch(error=>{
              // A run that fails after it started is the watch view's story
              // to tell, not this dialog's -- it is no longer on screen.
              this.$log.error(this.reasonFrom(error));
            })
          })
          .catch(error=>{
            this.starting = false;
            this.problem = this.reasonFrom(error);
          })
        },

        /* What the back end said, or the best available account of it. */
        reasonFrom: function(error){
          var said = error && error.response && error.response.data;
          if (typeof said === "string" && said.trim().length > 0) {
            return said.trim();
          }
          if (error && error.message) {
            return "DRAT could not start the run: " + error.message;
          }
          return "DRAT could not start the run.";
        },

        /*
         * Clears the previous run. Takes no repository: it is about what is
         * in the catalog, not about what would be audited next.
         */
        reset: function(){
          this.confirmReset = false;
          store.commit("setCurrentActionRequest","RESET");
          axios.post(this.origin+"/proteus-services/drat/reset","")
          .then(response=>{
            this.$log.info(response.data);
          })
          .catch(error=>{
            this.problem = this.reasonFrom(error);
          })
        }

    },
    computed: {
        /*
         * The snackbar shows while there is something to say, and clearing it
         * clears the message rather than leaving it to reappear.
         */
        hasProblem: {
          get(){
            return this.problem.length > 0;
          },
          set(showing){
            if(!showing){
              this.problem = '';
            }
          }
        },
        currentRepo (){
          return store.state.currentRepo;
        },
        origin(){
          return store.state.origin;
        },
        progress(){
          return store.state.progress;
        }

    }
}
</script>

<style scoped>
  

  #controllbarcard{
    padding: 1%;
    padding-left: 10%;
    padding-right: 10%;
  }

  #repodetailscard{
    padding: 2%;
  }
</style>
run