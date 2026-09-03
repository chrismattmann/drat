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

            <v-btn v-on:click="dialog=false">Close</v-btn>
            <v-btn v-on:click="go" color="primary">Go</v-btn>
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

      <v-snackbar v-model="invalidInput" :timeout="6000" color="error">
        Please enter a valid path and location, then continue
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
        invalidInput:false,
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
            // Said in place of the vuejs-dialog alert, which is built against
            // Vue 2 and has no Vue 3 release. Nothing was asked of the reader
            // by that dialog beyond dismissing it.
            this.invalidInput = true;
            return;
          }

          store.commit("setCurrentActionRequest","GO");
          store.commit("setprogress",true);
          store.commit("setCurrentRepo",this.url);
          this.dialog = false;

          var body = {
            id:this.repoloc,
            repo:this.url,
            name:this.reponame,
            loc_url:this.repoloc,
            description:this.repodesc
          };
          axios.post(this.origin+"/proteus-services/drat/go",body)
          .then(response=>{
            this.$log.info(response.data);
          })
          .catch(error=>{
            throw error;
          })
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
            this.$log.error(error.toString());
          })
        }

    },
    computed: {
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