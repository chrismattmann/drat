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

  <section class="barchartcomp">
    <v-card id = "barchart">
    <v-toolbar height="50" color="primary">
      <v-toolbar-title class="text-center">License Breakdown</v-toolbar-title>
    </v-toolbar>

    <!--
      Said out loud when there is nothing to draw. This was set in four places
      and rendered in none, so a card with no data showed an empty svg: a
      heading, a blank rectangle, and no indication whether the audit had
      found nothing, had not got there yet, or had failed.
    -->
    <div id="barempty" v-if="emptynote">{{ emptynote }}</div>
    <svg id="barsvg" width="400" height="270" v-show="!emptynote"></svg>
    
    </v-card>
  </section>

</template>

<script lang="js">
import * as d3 from 'd3';
import axios from 'axios';
import store from './../store/store'

  export default  {
    name: 'barchartcomp',
    props: [],
    store,
    mounted() {
        this.timerClearvar = setInterval(function () {
          // "audit" is the whole pipeline, the RAT steps included; see statisticscomp.
          if(this.currentState=="AUDIT" || this.currentState=="MAP" || this.currentState=="REDUCE" || this.currentState=="DONE")this.loadData();
        }.bind(this), 1000);
        
    },
    beforeUnmount(){
      clearInterval(this.timerClearvar);
    },
    data() {
      return {
        licenseTypes : [], 
        emptynote : '',
        timerClearvar:''
       
      }
    },
    watch:{
     
    },
    methods: {
      /*
       * Nothing to show until RAT has finished at least one audit. The
       * licence breakdown is what RAT reported, and a run that has only
       * crawled so far has nothing of its own to report -- so what would be
       * drawn is the previous run's, on a page that is describing this one.
       * The statistics core survives a reset by design, which is what made
       * the stale figures look current.
       */
      nothingAuditedYet(){
        // Not "while a run is happening": a run that stopped after clearing
        // the catalog has audited nothing either, and the breakdown that came
        // back the moment it ended was the previous run's.
        return !(store.state.ratFinished > 0);
      },
        loadData(){
        if(this.nothingAuditedYet()){
          this.emptynote = "No audit has finished yet";
          return;
        }
          if(this.currentRepo=='')return;
          var query = 'parent:"' + this.currentRepo + '" AND type:file';
          axios.get(this.origin + '/proteus-services/solr/statistics/select?q=' + encodeURIComponent(query) + '&rows=0&facet=true&facet.field=license&wt=json')
            .then(response=>{
              if(response.data.response.numFound>0){
                this.licenseTypes=this.buildLicenseFacetBreakdown(response.data);
                this.init();
                return;
              }
              this.loadAggregateData();
            })
            .catch(error=>{
              this.emptynote = error.toString();
              throw error;
            })
            
        },
        loadAggregateData(){
          var query = 'id:"' + this.currentRepo + '"';
          axios.get(this.origin + '/proteus-services/solr/statistics/select?q=' + encodeURIComponent(query) + '&rows=1&fl=license_*&wt=json')
            .then(response=>{
              var docs = response.data.response.docs;
              if(docs.length==0){
                this.licenseTypes=[];
                this.init();
                return;
              }
              this.licenseTypes=this.buildLicenseBreakdown(docs[0]);
              this.init();
            })
            .catch(error=>{
              this.emptynote = error.toString();
              throw error;
            })
        },
        buildLicenseFacetBreakdown(data){
          var facetFields = data.facet_counts && data.facet_counts.facet_fields;
          if(!facetFields || !facetFields.license)return [];
          return this.buildBreakdownFromPairs(facetFields.license);
        },
        buildBreakdownFromPairs(values){
          var out = [];
          var total = 0;
          for(var i=0;i<values.length;i+=2){
            total += values[i+1];
          }
          if(total==0)return out;
          for(i=0;i<values.length;i+=2){
            if(values[i+1]>0){
              out.push({
                type:values[i],
                numberOfObjects:values[i+1],
                weight:values[i+1] / total
              });
            }
          }
          return out;
        },
        buildLicenseBreakdown(doc){
          var out = [];
          var total = 0;
          for(var key in doc){
            if(key.indexOf("license_")==0){
              total += doc[key];
            }
          }
          if(total==0)return out;
          for(key in doc){
            if(key.indexOf("license_")==0 && doc[key]>0){
              out.push({
                type:key.split("license_")[1],
                numberOfObjects:doc[key],
                weight:doc[key] / total
              });
            }
          }
          return out;
        },
        init(){
          var  svg = d3.select("#barsvg"),
              margin = {top: 32, right: 20, bottom: 50, left: 40},
              width = +svg.attr("width") - margin.left - margin.right,
              height = +svg.attr("height") - margin.top - margin.bottom;

              svg.selectAll("*").remove();

          var x = d3.scaleBand().rangeRound([0, width]).padding(0.1),
              y = d3.scaleLinear().rangeRound([height, 0]);

          var g = svg.append("g")
              .attr("transform", "translate(" + margin.left + "," + margin.top + ")");

          
          
          var dataval = this.dts;
          if(dataval.length!=0){
            this.emptynote='';
            x.domain(dataval.map(function(d) { return d.letter; }));
            y.domain([0, d3.max(dataval, function(d) { return d.frequency; })]);

            g.append("g")
                .attr("class", "axis axis--x")
                .attr("transform", "translate(0," + height + ")")
                
                .call(d3.axisBottom(x))
              .selectAll("text")
              .attr("transform", "rotate(15)")
                .style("text-anchor", "start");


            

            g.append("g")
                .attr("class", "axis axis--y")
                .call(d3.axisLeft(y).ticks(10, "%"))
              .append("text")
                .attr("transform", "rotate(-90)")
                .attr("y", 6)
                .attr("dy", "0.71em")
                .attr("text-anchor", "end")
                
                .text("Frequency");
            

            /*
             * A readout above the bars rather than a browser tooltip, which
             * arrives a second late and goes as soon as the pointer moves.
             * The bar carried no figure at all: its height was a proportion
             * of an axis in percent, so reading one meant measuring it by
             * eye against the ticks.
             */
            var readout = g.append("text")
                .attr("class", "bar-readout")
                .attr("x", width / 2)
                .attr("y", -6)
                .attr("text-anchor", "middle")
                .style("opacity", 0);

            var counts = this.licenseTypes;

            g.selectAll(".bar")
                .data(dataval)
              .enter()
                .append("rect")
                .attr("class", "bar")
                .attr("x", function(d) { return x(d.letter); })
                .attr("y", function(d) { return y(d.frequency); })
                .attr("width", x.bandwidth())
                .attr("height", function(d) { return height - y(d.frequency); })
                .on("mouseenter", function(event, d) {
                  var found = counts.filter(function(c){ return c.type === d.letter; })[0];
                  var howMany = found ? found.numberOfObjects : null;
                  readout
                    .text(d.letter + " \u00b7 "
                        + (howMany === null ? "" : howMany + " files, ")
                        + (d.frequency * 100).toFixed(1) + "%")
                    .style("opacity", 1);
                })
                .on("mouseleave", function() {
                  readout.style("opacity", 0);
                })
              .append("title")
                .text(function(d) {
                  return d.letter + ": " + (d.frequency * 100).toFixed(1) + "%";
                });

          }else{
            // Which of the two it is, rather than "Retrieving Data..." for
            // both: a run that has audited nothing yet is not the same as a
            // finished run whose licences could not be read.
            this.emptynote = this.nothingAuditedYet()
                ? "No audit has finished yet"
                : "No licence data for this repository yet";
          }
          
        },
        
        
        
    },
    computed: {
      dts:function (){
        var out = [];
        for(var item in this.licenseTypes){
          out.push({letter:this.licenseTypes[item].type,frequency:this.licenseTypes[item].weight});
        }
        return out;
      },
      origin(){
        return store.state.origin;
      },
      currentState(){
        return store.state.currentActionStep;
      },
      currentRepo(){
        return store.state.currentRepo;
      }
    }
}
</script>

<style>
.bar-readout {
  font-size: 12.5px;
  font-weight: 600;
  fill: #22303c;
}

#barempty {
  padding: 34px 16px;
  font-size: 13px;
  opacity: 0.7;
}

.bar {
  fill: steelblue;
}

.bar {
  cursor: default;
  transition: fill 120ms ease-in-out;
}

.bar:hover {
  fill: #b1541f;
}



 #barchart {
    margin-top: 5%;
    margin-bottom :5%;
    margin-left:5%
  }
</style>
