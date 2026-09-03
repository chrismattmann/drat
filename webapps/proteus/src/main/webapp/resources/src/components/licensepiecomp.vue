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

  <section class="licensepiecomp">
    <v-card id="licensecard" class="chartcard">
      <v-toolbar color="primary">
         <v-toolbar-title class="text-white">License Types</v-toolbar-title>
      </v-toolbar>

      <div class="chartbody">
        <svg id="pielicensesvg" class="chart"></svg>
      </div>
    </v-card>
  </section>

</template>

<script lang="js">
  import * as d3 from 'd3';
  import axios from 'axios';
  import store from './../store/store';
  import { drawPie } from './../charts/pie';

  export default  {
    name: 'licensepiecomp',
    store,
    props: [],
    mounted() {
      this.init();
    },
    data() {
      return {

      }
    },
    methods: {
      init(){
        axios.get(this.origin + '/proteus-services/solr/statistics/select?q=type:software&fl=license_*&wt=json')
        .then(response2=>{
          if(response2.data.response.numFound!=null){
              axios.get(this.origin + '/proteus-services/solr/statistics/select?q=type:software&rows='+response2.data.response.numFound+'&fl=license_*&wt=json')
              .then(function(response) {

                console.log(response.data);
                var docs = response.data.response.docs;
                var resultingData = [];
                var result = [];
                var license = {};

                for(var i = 0; i < docs.length; i++) {
                  var doc = docs[i];
                  for(var x in doc) {
                    var key = x.split("license_")[1];
                    var value = doc[x];
                    if(typeof license[key] === 'undefined') {
                      license[key] = value;
                    }
                    else {
                      license[key] += value;
                    }
                  }
                }

                for(x in license) {
                  var jsonObject = {};
                  jsonObject["key"] = x;
                  jsonObject["y"] = license[x];
                  resultingData.push(jsonObject);
                }

                resultingData.sort(function(a, b) {
                    return b.y - a.y;
                });

                for( i = 0; i < resultingData.length; i++) {
                  if(resultingData[i]["y"] == 0)
                    break;
                  result[i] = resultingData[i];
                }

                drawPie("#pielicensesvg", result, {
                  scheme: d3.schemeSet3,
                  emptyNote: "No licence data yet"
                });

                  console.log(result);
                });
          }

        });

      }
    },
    computed: {
      origin(){
        return store.state.origin;
      }
    }
}
</script>

<style>

    .arc text {
    font: 10px sans-serif;
    text-anchor: middle;
  }

.arc path {
  stroke: transparent

}

/*
   * No padding on the card itself. 5% here sat between the card's edge and
   * its toolbar, so this card's heading started lower than the one beside it
   * -- which has 16px -- and the two cards read as misaligned. The padding
   * belongs around the chart, where it was meant to be.
   */
  #licensecard {
    margin-bottom: 0;
    padding: 0;
  }
</style>
