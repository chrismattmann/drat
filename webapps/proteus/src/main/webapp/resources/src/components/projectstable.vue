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
              <v-card variant="flat">
                <!--
                  A view, so it reads as one. These were four v-text-fields
                  bound with :value, which is not a Vuetify 3 prop: the value
                  never reached the field, and the boxes were editable
                  wherever "readonly" had been left off -- the description was
                  one, so a project's description could be typed over with no
                  way to save it and nothing saying so. A definition list
                  cannot be typed into at all.
                -->
                <v-card id="projectdetails" variant="tonal">
                  <dl class="project-details">
                    <dt>Project Name</dt>
                    <dd>{{ selectedItem.name || '\u2014' }}</dd>

                    <dt>Project Description</dt>
                    <dd>{{ selectedItem.description || '\u2014' }}</dd>

                    <dt>Project Repository</dt>
                    <dd class="path">{{ selectedItem.repo || '\u2014' }}</dd>

                    <dt>Project Location</dt>
                    <dd class="path">{{ selectedItem.loc_url || '\u2014' }}</dd>
                  </dl>
                </v-card>
              </v-card>
            </v-col>
            
           
          </v-row>
          <v-row row >
            <v-col cols="10"  offset-xs1>
              <v-card id="licenselist" variant="tonal">
                <!--
                  These filter the table below. They were bound with v-model,
                  which on a Vuetify 3 v-chip controls whether the chip is
                  shown rather than whether it is selected -- so clicking one
                  did nothing to the files, and Reset had nothing to undo.
                  A chip now says whether it is on, and toggles on click.
                -->
                <div class="licence-filters">
                  <v-chip
                    v-for="kind in licenceKinds"
                    :key="kind.flag"
                    :color="license[kind.flag] ? 'primary' : undefined"
                    :variant="license[kind.flag] ? 'flat' : 'outlined'"
                    filter
                    :model-value="license[kind.flag]"
                    class="licence-chip"
                    @click="toggleLicence(kind.flag)"
                  >
                    {{ kind.label }}
                    <span class="licence-count">
                      {{ license.docs[kind.field] || 0 }}
                    </span>
                  </v-chip>
                </div>

                <div class="licence-actions">
                  <span class="licence-showing">
                    Showing {{ filteredFiles.length }} of
                    {{ (license.files || []).length }} files
                  </span>
                  <v-btn size="small" color="primary" @click="resetLicenceFilters">
                    Reset
                  </v-btn>
                </div>
              </v-card>
            </v-col>
          </v-row>
          <v-card id="licensefiletable">
            <v-row>
              <v-col cols="10" offset-xs1>
                <!--
                  Filters as it is typed, so the icon is a label for the box
                  rather than a button. It sat on the right, where it read as
                  something to press, and pressing it did nothing.
                -->
                <v-text-field
                  v-model="search"
                  prepend-inner-icon="mdi-magnify"
                  label="Search"
                  clearable
                  hide-details>
                </v-text-field>
               
                <!--
                  Paged, and told how tall it may be. Given every audited file
                  at once it ran the length of the page and on under the fixed
                  footer, where the last rows could not be reached.
                -->
                <v-data-table
                  :headers="license.headers"
                  :items="filteredFiles"
                  :items-per-page="25"
                  :items-per-page-options="filesPerPage"
                  class="licence-table"
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
        /*
         * One row per licence RAT reports, so the chips are generated rather
         * than seven near-identical copies that could drift apart.
         */
        filesPerPage:[
          {title:'25',value:25},{title:'50',value:50},
          {title:'100',value:100},{title:'All',value:-1}
        ],
        licenceKinds:[
          { flag:'standard',  label:'Standards', field:'license_Standards' },
          { flag:'unknown',   label:'Unknown',   field:'license_Unknown' },
          { flag:'apache',    label:'Apache',    field:'license_Apache' },
          { flag:'binaries',  label:'Binaries',  field:'license_Binaries' },
          { flag:'generated', label:'Generated', field:'license_Generated' },
          { flag:'notes',     label:'Notes',     field:'license_Notes' },
          { flag:'archives',  label:'Archives',  field:'license_Archives' }
        ],
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
      toggleLicence(flag){
        this.license[flag] = !this.license[flag];
      },
      resetLicenceFilters(){
        for(const kind of this.licenceKinds){
          this.license[kind.flag] = true;
        }
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

        /*
         * One chip, one licence. Apache files used to be shown when either
         * Apache or Standards was on, and Unknown ones likewise, so Standards
         * acted as a master switch that put everything back on screen and no
         * single chip could be used to narrow anything down.
         *
         * Anything RAT reports under a name with no chip of its own still
         * follows Standards, which is where it was already counted.
         */
        get:function(){
          const named = {
            Apache: 'apache',
            Unknown: 'unknown',
            Standards: 'standard',
            Binaries: 'binaries',
            Generated: 'generated',
            Notes: 'notes',
            Archives: 'archives'
          };

          if(!this.license.files){
            return [];
          }

          return this.license.files.filter(file => {
            const flag = named[file.license] || 'standard';
            return !!this.license[flag];
          });
        },

        set:function(docs){
          this.license.files = docs;
        }

      }
    },
}
</script>

<style>
  .projectstable {
    padding-top:10px;
    padding-bottom: 10px;
  }

  /*
   * Readable rows. Every cell sat on the same flat grey as the card behind
   * it, and the values were grey too, so a project's name and its
   * description ran together as one band of grey with no edge between them.
   */
  #ttx table > tbody > tr > td,
  .licence-table table > tbody > tr > td {
    border-bottom: 1px solid rgba(0, 0, 0, 0.08);
    color: #22303c;
  }

  #ttx table > tbody > tr:nth-child(even),
  .licence-table table > tbody > tr:nth-child(even) {
    background-color: rgba(0, 0, 0, 0.025);
  }

  #ttx table > tbody > tr:hover,
  .licence-table table > tbody > tr:hover {
    background-color: rgba(25, 118, 210, 0.07);
  }

  #ttx table > thead > tr > th,
  .licence-table table > thead > tr > th {
    background-color: #eceff1;
    color: #22303c;
    font-weight: 600;
    border-bottom: 2px solid rgba(0, 0, 0, 0.12);
  }

  /*
   * The header column holds a whole licence header, which is a paragraph.
   * Unwrapped it pushed the table wider than the page and ran off the right
   * margin, taking the columns before it with it.
   */
  #headercell {
    max-width: 380px;
    white-space: normal;
    overflow-wrap: anywhere;
    font-size: 12px;
    line-height: 1.35;
    padding-top: 6px;
    padding-bottom: 6px;
  }

  .licence-table {
    max-width: 100%;
  }

  /* Room for the fixed footer, so the last rows are reachable. */
  #licensefiletable {
    margin-bottom: 96px;
    overflow-x: auto;
  }

  /* A view of a project, not a form. */
  .project-details {
    display: grid;
    grid-template-columns: minmax(140px, 220px) 1fr;
    gap: 10px 20px;
    margin: 0;
    padding: 16px 20px;
    text-align: left;
  }

  .project-details dt {
    font-weight: 600;
    color: #37474f;
  }

  .project-details dd {
    margin: 0;
    color: #22303c;
    overflow-wrap: anywhere;
  }

  .project-details dd.path {
    font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
    font-size: 12.5px;
  }

  .licence-filters {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    padding: 14px 16px 6px 16px;
  }

  .licence-chip {
    font-weight: 500;
  }

  /*
   * The count, set apart from the name. Both used to sit in the chip as bare
   * neighbouring text, so "Standards" and its number ran into each other and
   * into the next chip along.
   */
  .licence-count {
    margin-left: 8px;
    padding: 1px 7px;
    border-radius: 9px;
    background: rgba(0, 0, 0, 0.16);
    font-size: 11.5px;
    font-variant-numeric: tabular-nums;
  }

  .licence-actions {
    display: flex;
    align-items: center;
    gap: 14px;
    padding: 6px 16px 14px 16px;
  }

  .licence-showing {
    font-size: 12.5px;
    opacity: 0.75;
  }

  #projectdetails {
    margin-bottom: 12px;
  }

  #licenselist {
    margin-bottom: 12px;
  }
</style>
