/*
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
*/

import Vuex from 'vuex';
import Vue from 'vue';
Vue.use(Vuex);

const store = new Vuex.Store({
    state:{
        progress:false,
        view:"summary",
        currentRepo:'',
        origin:'',
        currentActionRequest:'',
        currentActionStep:'IDLE',
        // What DRAT is actually doing, as the back end reports it. A run
        // started from the command line is a real run and shows here too, so
        // the watch view is driven by this rather than by whether somebody
        // pressed a button in this browser.
        run:{running:false,phase:'',startedBy:'',repo:''},
        // How many RAT audits have finished. The licence breakdown is what
        // RAT found, so until one has finished there is nothing of this run's
        // to show -- and what is on screen would be the previous run's.
        ratFinished:0,
        // Whether every crawled file has been indexed. The mime breakdown on
        // the run view is drawn from the index, so before this there is
        // nothing of this run's in it to draw.
        indexDone:false
    },
    mutations:{
        invert(state){
            state.progress = !state.progress;
        },
        setprogress(state,val){
            state.progress = val;
        },
        setCurrentRepo(state,newVal){
            state.currentRepo = newVal;
        },
        setOrigin(state,neworigin){
            state.origin = neworigin;
        },
        setView(state,newVal){
            state.view = newVal;
        },
        setCurrentActionRequest(state,newVal){
            state.currentActionRequest = newVal;
        },
        setCurrentActionStep(state,newVal){
            state.currentActionStep = newVal;
        },
        setRun(state,newVal){
            state.run = newVal;
        },
        setRatFinished(state,newVal){
            state.ratFinished = newVal;
        },
        setIndexDone(state,newVal){
            state.indexDone = newVal;
        }
        
    },
    getters:{
        getprog(state){
            return state.progress;
        },
        getcurrentrepo(state){
            return state.currentRepo;
        },
        getCurrentOrigin(state){
            return state.origin;
        }
    }
});
export default store;