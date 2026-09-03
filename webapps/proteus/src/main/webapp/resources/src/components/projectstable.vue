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

<v-card id="tablecard">
  
  <v-toolbar color="primary">
     <v-toolbar-title class="text-white">Projects</v-toolbar-title>
  </v-toolbar>
  <section class="projectstable">
    <v-text-field
      id="projectsearch"
      v-model="projectsearch"
      append-inner-icon="mdi-magnify"
      solo
      label="Search"
      
      hide-details>
    </v-text-field>
    <!--
      Filtered here rather than by the table. Vuetify 1 took a row predicate
      and a custom filter to drive it; Vuetify 3 filters column by column and
      has neither, so the same predicate is applied to the list that goes in.
      The rows and the search behave as they did.
    -->
    <v-data-table id="ttx"
      :headers="headers"
      :items="filteredProjects"
      :items-per-page-options="rowsPerPageItemsforProjects"
      class="elevation-1"
    >
      <template #item="{ item, index }">
        <tr>
          <td class="text-left">{{index+1+count.start}}</td>
          <td class="text-left">{{ item.repo }}</td>
          <td class="text-left">{{ item.name }}</td>
          <td class="text-left">{{ item.description }}</td>
          <td>
            <v-btn @click="moreClicked(item)">
              <v-icon>mdi-file-document-outline</v-icon>
            </v-btn>
          </td>
        </tr>
      </template>
      </v-data-table>
  </section>
  <section class="fulldialog">
    <v-row row justify-center>
      <v-dialog v-model="dialog" fullscreen hide-overlay transition="dialog-bottom-transition">
        
        <v-card>
          <v-toolbar color="primary">
            <v-btn icon @click="dialog = false">
              <v-icon>mdi-close</v-icon>
            </v-btn>
            <v-toolbar-title>{{selectedItem.name}}</v-toolbar-title>
            <v-spacer></v-spacer>
            <!-- <v-toolbar-items>
              <v-btn variant="text" @click="dialog = false">Save</v-btn>
            </v-toolbar-items> -->
          </v-toolbar>
          <v-row row justify-space-between>
            <v-col cols="10" offset-xs1>
              <v-card  >
                <v-card id="projectdetails" color="grey lighten-3">
                     <v-row  align-center justify-space-between row>
                      <v-col cols="6" >
                        <p class="text-sm-left">Project Name</p>
                        
                      </v-col>
                      <v-col cols="6">
                        <v-text-field variant="solo"
                          uneditable
                          :value="selectedItem.name"
                          readonly
                        ></v-text-field>
                      </v-col>
                    </v-row>
                    
                    <v-row  justify-space-between row>
                      <v-col cols="6" >
                        <p class="text-sm-left"> Project Description</p>
                       
                      </v-col>
                      <v-col cols="6">
                        <v-text-field
                          name="input-7-1"
                          solo
                          textarea
                          :value="selectedItem.description"
                          flat
                        ></v-text-field>
                        
                      </v-col>
                    </v-row>
                    <v-row  align-center justify-space-between row>
                      <v-col cols="6" >
                        <p class="text-sm-left">Project Repository</p>
                        
                      </v-col>
                      <v-col cols="6">
                        <v-text-field
                          label="Solo"
                         
                          solo  
                          uneditable
                          :value="selectedItem.repo"
                          readonly
                        ></v-text-field>
                      </v-col>
                    </v-row>
                    <v-row  align-center justify-space-between row>
                      <v-col cols="6" >
                        <p class="text-sm-left">Project Location</p>
                      </v-col>
                      <v-col cols="6">
                        <v-text-field
                          label="Solo"
                         
                          solo
                          uneditable
                          :value="selectedItem.loc_url"
                          readonly
                        ></v-text-field>
                      </v-col>
                    </v-row>
                </v-card>
              </v-card>
            </v-col>
            
           
          </v-row>
          <v-row row >
            <v-col cols="10"  offset-xs1>
              <v-card color="grey lighten-3" id="licenselist">
               
                 <br/>
                <v-chip close v-model="license.standard">
                  <v-avatar class="teal">{{license.docs.license_Standards}}</v-avatar>
                  Standards
                </v-chip>
                <v-chip close v-model="license.unknown">
                  <v-avatar class="teal">{{license.docs.license_Unknown}}</v-avatar>
                  Unknown
                </v-chip>
                <v-chip close v-model="license.apache">
                  <v-avatar class="teal">{{license.docs.license_Apache}}</v-avatar>
                  Apache
                </v-chip>
                 <v-chip close v-model="license.binaries">
                  <v-avatar class="teal">{{license.docs.license_Binaries}}</v-avatar>
                  Binaries
                </v-chip>
                 <v-chip close v-model="license.generated">
                  <v-avatar class="teal">{{license.docs.license_Generated}}</v-avatar>
                  Generated
                </v-chip>
                <v-chip close v-model="license.notes">
                  <v-avatar class="teal">{{license.docs.license_Notes}}</v-avatar>
                  Notes
                </v-chip>
                 <v-chip close v-model="license.archives">
                  <v-avatar class="teal">{{license.docs.license_Archives}}</v-avatar>
                  Archives
                </v-chip>
                <br/>
                <v-btn float color="primary"
                    @click="license.unknown =true,license.standard=true,license.apache=true
                    ,license.binaries=true,license.generated=true,license.notes=true,license.archives=true"
                  >Reset</v-btn>
               
              </v-card>
            </v-col>
          </v-row>
          <v-card id="licensefiletable">
            <v-row>
              <v-col cols="10" offset-xs1>
                <v-text-field
                  v-model="search"
                  append-inner-icon="mdi-magnify"
                  label="Search"
                  
                  hide-details>
                </v-text-field>
               
                <v-data-table
                  :headers="license.headers"
                  :items="filteredFiles"
                >
                  <template #item="{ item, index }">
                    <tr>
                      <td class="text-left">{{index+1 }}</td>
                      <td class="text-left">{{ item.id }}</td>
                      <td class="text-left">{{ item.mimetype }}</td>
                      <td class="text-left">{{ item.license }}</td>
                      <td class="text-left" id="headercell">{{ item.header }}</td>
                    </tr>
                  </template>
                  <template #no-data>
                    <v-alert type="error" icon="mdi-alert">
                      Your search for "{{ search }}" found no results.
                    </v-alert>
                  </template>
                </v-data-table>
              </v-col>
            </v-row>
          </v-card> 
        </v-card>
        
      </v-dialog>
    </v-row>
  </section>
</v-card>
</template>

<script lang="js">
import axios from 'axios';
import store from './../store/store';
  export default  {
    name: 'projectstable',
    store,
    props: [],
    mounted() {
        this.loadData();
    },
    beforeUnmount(){
      clearInterval(this.timerClearVar);
    },
    data() {
      return {
        projectsearch:'',
        search:'',
        timerClearVar:'',
        license:{
          files:[],
          unknown:true,
          standard:true,
          apache:true,
          binaries:true,
          generated:true,
          notes:true,
          archives:true,
          docs:[],
          headers:[
            { title: '#',sortable: true, key: 'num' },
            { title: 'Location',sortable: false, key: 'loc' },
            { title: 'Mime Type',sortable: true, key: 'mtype' },
            { title: 'License',sortable: true, key: 'license' },
            { title: 'Header',sortable:false,key:'header',width:'20px'}
          ]
        },
        dialog:false,
        selectedItem:'',
          headers: [
        {
          title: '#',
          align: 'center',
          sortable: false,
          key: 'num'
        },
        { title: 'Repository',sortable: false, key: 'repository' },
        { title: 'Name',sortable: false, key: 'name' },
        { title: 'Description',sortable: false, key: 'description' },
        { title: 'Audit',sortable: false, key: 'audit' },
        ],
        count:{
          numFound :0,
          start:0
        },

        docs:[],
        rowsPerPageItemsforProjects: [
          {title:'50',value:50},{title:'100',value:100},{title:'200',value:200},
          {title:'500',value:500},{title:'1000',value:1000},{title:'3000',value:3000},
          {title:'5000',value:5000},{title:'All',value:-1}
        ]
      }
      
    },
    methods: {
      filterProjects(inputObject,search){
        // Guarded per field: a project with no name or no description is a
        // real thing in the statistics core, and reading .toLowerCase() off
        // the missing one threw for every row the search touched.
        return ['repo','name','description'].some(field => {
          const value = inputObject[field];
          return value != null
              && value.toString().toLowerCase().includes(search);
        });
      },
      moreClicked :function(item){
        this.$log.info("as");
        this.dialog =true;     
        //this.selectedItem = this.docs[index];
        this.selectedItem = item;
        this.search = '';
        this.loadLicenseData();
        this.loadFileDetails();
      },
      customFilterFiles(items, search){
        var normalizedSearch = (search || '').toString().trim().toLowerCase();
        if(!normalizedSearch){
          return items;
        }

        return items.filter(file => {
          return ['id', 'mimetype', 'license', 'header'].some(field => {
            var value = file[field];
            return value != null && value.toString().toLowerCase().includes(normalizedSearch);
          });
        });
      },
      loadData(){
          axios.get(this.origin+"/proteus-services/solr/statistics/select?q=type:project&wt=json")
            .then(response=>{
              this.$log.info(response.data);
              this.docs=response.data.response.docs;
              this.count.numFound = response.data.response.numFound;
              this.count.start = response.data.response.start;
              if(response.data.response.numFound != null && response.data.response.numFound>10){
                axios.get(this.origin+"/proteus-services/solr/statistics/select?q=type:project&rows="+this.count.numFound+"&wt=json")
                  .then(response=>{
                    this.docs= response.data.response.docs;
                    this.count.numFound = response.data.response.numFound;
                    this.count.start = response.data.response.start;
                  })
                  .catch(()=>{

                  })
              }

            })
            .catch(error=>{

              throw error;
            })
      },
      loadLicenseData(){
        axios.get(this.origin+"/proteus-services/solr/statistics/select?q=id:\""+this.selectedItem.repo+"\"&fl=license_*&wt=json")
          .then(response2=>{
            if(response2.data.response.numFound!=null){
                axios.get(this.origin+"/proteus-services/solr/statistics/select?q=id:\""+this.selectedItem.repo+"\"&fl=license_*&rows="+response2.data.response.numFound+"&wt=json")
                .then(response=>{
                    this.$log.info(response.data);
                    this.license.docs=response.data.response.docs[0];
                });
            }
             
            
          })
       },
      loadFileDetails(){
        axios.get(this.origin+"/proteus-services/solr/statistics/select?q=parent:\""+this.selectedItem.repo+"\"&rows=5000&wt=json")
        .then(response2=>{
            axios.get(this.origin+"/proteus-services/solr/statistics/select?q=parent:\""+this.selectedItem.repo+"\"&rows="+response2.data.response.numFound+"&wt=json")
            .then(response=>{
              this.sortedfiles  = response.data.response.docs;
            });
        });
        
      }

    },
    computed: {
      origin(){
        return store.state.origin;
      },
      /*
       * The rows the table is given, already filtered. Vuetify 1 was handed
       * the whole list plus a predicate and did this itself; Vuetify 3 has no
       * equivalent for a predicate that reads the whole row, so the same
       * predicates run here and the table renders what it is given.
       */
      filteredProjects(){
        const search = (this.projectsearch || '').toString().trim().toLowerCase();
        if(!search){
          return this.docs;
        }
        return this.docs.filter(row => this.filterProjects(row, search));
      },
      filteredFiles(){
        return this.customFilterFiles(this.sortedfiles, this.search);
      },
      currentrepo(){
        return store.state.currentRepo;
      },
      sortedfiles:{

        get:function(){
            var listToReturn = [];
        
            if(this.license.files){
              this.license.files.forEach(file => {
                
                switch(file.license){
                  case "Apache":
                    if(this.license.apache || this.license.standard) listToReturn.push(file);
                    break;

                  case "Unknown":
                    if(this.license.unknown || this.license.standard) listToReturn.push(file);
                    break;

                  case "Standards":
                      if(this.license.standard) listToReturn.push(file);
                      break;
                  case "Binaries":
                      if(this.license.binaries) listToReturn.push(file);
                      break;

                  case "Generated":
                      if(this.license.generated) listToReturn.push(file);
                      break; 
                  case "Notes":
                      if(this.license.notes) listToReturn.push(file);
                      break;   
                  case "Archives":
                      if(this.license.archives) listToReturn.push(file);
                      break;
                  default:
                      if(this.license.standard) listToReturn.push(file);
                      break;
                }
              });

            }
            
            return listToReturn;
        },

        set:function(docs){
          this.license.files = docs;
        }
        
      }
    },
    filters:{
      
    }
}
</script>

<style>
  .projectstable {
    padding-top:10px;
    padding-bottom: 10px;
  }

  .row{
    margin:2%
  }

  #projectdetails .row{
    margin:1%;
  }

  tr:nth-child(even){background-color :#f2f2f2}
  tr{background-color :#ddd}
  th{background-color: #2196F3}

  #projectdetails{
    padding: 2%;
    padding-left:10%;
    padding-right:10%;
  }  

  #licenselist{
    padding: 2%;
    margin-top: 2%;
  }

  #headercell{
    max-width: 400px;
    overflow: hidden;
    white-space: nowrap;
  }

  #tablecard{
    margin-top: 20px;
    padding : 10px;
  }

  #licensefiletable{
    margin-bottom: 80px;
    z-index: 950;
  }
  #projectsearch{
    
  }
</style>
