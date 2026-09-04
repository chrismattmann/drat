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

  <section class="piechart">
    <v-card id="piecard">
    <v-toolbar height="50" color="primary">
      <v-toolbar-title class="text-center">Mime Type Breakdown</v-toolbar-title>
    </v-toolbar>
    <svg id="piesvg" class="chart"></svg>
    </v-card>
  </section>

</template>

<script lang="js">
import axios from 'axios';
import store from './../store/store';
import { drawPie } from './../charts/pie';
  export default  {
    name: 'piechart',
    store,
    props: [],
    mounted() {
        // Draw what is in the catalog straight away, then keep it current
        // while a run is moving.
        //
        // This used to load only while the run state was INDEX, MAP, REDUCE
        // or DONE, which ties a description of the catalog to what this
        // session happened to do. The catalog outlives the session: it is
        // populated by bin/drat as often as by this UI, and the run state
        // resets to IDLE when Tomcat restarts. Either way the chart went
        // blank while thousands of products sat in Solr.
        this.loadData();
        this.timerClearVar = setInterval(function () {
          // "audit" is the whole pipeline, indexing included; see statisticscomp.
          if(this.currentState=="AUDIT" || this.currentState=="INDEX" || this.currentState=="MAP" || this.currentState=="REDUCE" || this.currentState=="DONE")this.loadData();
        }.bind(this), 1000);
    },
    beforeUnmount(){
      clearInterval(this.timerClearVar);
    },
    data() {
      return {
          data:[]
      }
    },
    methods: {
      /*
       * The mime breakdown on the run view is drawn from the index, and the
       * index is filled during the run. Before it has caught up there is
       * nothing of this run's in it, so what would be shown is a partial
       * picture that reads as a finished one. Only while a run is happening:
       * with no run under way the index is simply what the catalog holds.
       */
      indexNotReadyYet(){
        return !store.state.indexDone;
      },
      /*
       * Drawn by the shared helper, which lays the legend out in rows beneath
       * the pie. This had its own placement: legend entries were positioned
       * by adding up the character counts of the labels before them and
       * wrapping at 250 pixels, which assumes every character is five pixels
       * wide. Mime types are long and vary -- "application/vnd.oasis..." next
       * to "text/plain" -- so entries ran into each other.
       */
      loadData(){
        if(this.indexNotReadyYet()){
          return;
        }
        axios.get(this.origin+"/proteus-services/solr/drat/select?q=producttype:GenericFile&rows=0&facet=true&facet.field=mimetype&wt=json")
            .then(response=>{
              this.data=this.buildMimeBreakdown(response.data, 8);
              drawPie("#piesvg", this.data.map(d => ({key:d.type, y:d.numberOfObjects})), {
                scheme: ["#98abc5", "#8a89a6", "#7b6888", "#6b486b", "#a05d56",
                         "#d0743c", "#ff8c00", "#5b8c5a"],
                emptyNote: "Nothing indexed yet"
              });
            })
            .catch(error=>{
              
              throw error;
            })
      },
      buildMimeBreakdown(data, limit){
        var facetFields = data.facet_counts && data.facet_counts.facet_fields;
        if(!facetFields || !facetFields.mimetype)return [];
        var values = facetFields.mimetype;
        var out = [];
        var total = 0;
        for(var i=0;i<values.length;i+=2){
          if(values[i].indexOf("/")>=0){
            total += values[i+1];
          }
        }
        var requireFullMime = total>0;
        if(total==0){
          for(i=0;i<values.length;i+=2){
            total += values[i+1];
          }
        }
        if(total==0)return out;
        for(i=0;i<values.length && out.length<limit;i+=2){
          if(values[i+1]>0 && (!requireFullMime || values[i].indexOf("/")>=0)){
            out.push({
              type:values[i],
              numberOfObjects:values[i+1],
              weight:values[i+1] / total
            });
          }
        }
        return out;
      }
    },
    computed: {
      origin(){
        return store.state.origin;
      },
      currentState(){
        return store.state.currentActionStep;
      }
    }
}
</script>

<style>
  .piechart {

  }
    .arc text {
    font: 10px sans-serif;
    text-anchor: middle;
  }

.arc path {
  stroke: transparent
  
}

#piecard {
    margin-top: 5%;
    margin-bottom :5%;
    margin-left:5%
  }
</style>
