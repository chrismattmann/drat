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

  <section class="topmimepiecomp">
    <v-card id="topmimecard">
      <v-toolbar color="primary">
         <v-toolbar-title class="text-white">Top MIME Types</v-toolbar-title>
      </v-toolbar>
      <!--
        How many types to show. This was a text field bound with :value, which
        is not a Vuetify 3 prop -- so the field held nothing and its floating
        label sat across the middle of the box, on top of where the number
        should have been. It is a reading rather than something to type into,
        so it is said as one.
      -->
      <div class="count-control">
        <v-btn size="small" variant="tonal" aria-label="Show fewer types"
          :disabled="count <= 1" @click="fewer">&minus;</v-btn>
        <span class="count-value">
          <strong>{{ count }}</strong>
          <span class="count-caption">types</span>
        </span>
        <v-btn size="small" variant="tonal" aria-label="Show more types"
          :disabled="count >= 25" @click="more">+</v-btn>
      </div>

      <svg id="pietopmimesvg" class="chart"></svg>
    </v-card>
  </section>

</template>

<script lang="js">
  import * as d3 from 'd3';
  import axios from 'axios';
  import store from './../store/store';
  import { drawPie } from './../charts/pie';

  export default  {
    name: 'topmimepiecomp',
    store,
    props: [],
    mounted() {
      this.init(this.count);
    },
    watch:{
      count:function(val) {
        this.init(val);
      }
    },
    data() {
      return {
        count:10,
      }
    },
    methods: {
        /*
         * Bounded, where the old handlers were not: "+" wrapped a count of 51
         * back to 25, and "-" ran down to zero and drew an empty chart.
         */
        fewer(){
          if(this.count > 1) this.count--;
        },
        more(){
          if(this.count < 25) this.count++;
        },
        init(rows){
          axios.get(this.origin + '/proteus-services/solr/statistics/select?q=type:software&fl=mime_*&wt=json')
          .then(response2=>{
            if(response2.data.response.numFound!=null){
                axios.get(this.origin + '/proteus-services/solr/statistics/select?q=type:software&rows='+response2.data.response.numFound+'&fl=mime_*&wt=json')
                .then(function(response) {

                console.log(response.data);
                  var docs = response.data.response.docs;
                  var resultingData = [];
                  var result = [];
                  var mime = {};

                  for(var i = 0; i < docs.length; i++) {
                    var doc = docs[i];
                    for(var x in doc) {
                      var key = x.split("mime_")[1];
                      var value = doc[x];
                      if(typeof mime[key] === 'undefined') {
                        mime[key] = value;
                      }
                      else {
                        mime[key] += value;
                      }
                    }
                  }

                  for(x in mime) {
                    var jsonObject = {};
                    jsonObject["key"] = x;
                    jsonObject["y"] = mime[x];
                    resultingData.push(jsonObject);
                  }

                  resultingData.sort(function(a, b) {
                      return b.y - a.y;
                  });
                  if(rows > resultingData.length)rows=resultingData.length;
                  for( i = 1; i <= rows; i++) {
                    result[i-1] = resultingData[i-1];
                  }

                  console.log(result);

                drawPie("#pietopmimesvg", result, {
                  scheme: d3.schemeTableau10,
                  emptyNote: "No mime data yet"
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

<style scoped>
  #topmimecard {
    padding: 16px;
  }

  .count-control {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 14px;
    padding: 6px 0 10px 0;
  }

  .count-value {
    display: inline-flex;
    align-items: baseline;
    gap: 5px;
    min-width: 74px;
    justify-content: center;
  }

  .count-value strong {
    font-size: 20px;
    font-variant-numeric: tabular-nums;
  }

  .count-caption {
    font-size: 12px;
    opacity: 0.7;
  }
</style>
