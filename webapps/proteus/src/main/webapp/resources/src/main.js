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

import { createApp } from 'vue'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import { aliases, mdi } from 'vuetify/iconsets/mdi'

import 'vuetify/styles'
import '@mdi/font/css/materialdesignicons.css'

import App from './App.vue'
import store from './store/store'
import logger from './logger'

const vuetify = createVuetify({
  components,
  directives,
  /*
   * Named explicitly. Vuetify 1 rendered whatever ligature the template
   * asked for through the Material Icons font; Vuetify 3 has an icon set to
   * choose and defaults to mdi, so the names in the templates are mdi names
   * now (mdi-chevron-left, not chevron_left).
   */
  icons: { defaultSet: 'mdi', aliases, sets: { mdi } }
})

const app = createApp(App)

/*
 * this.$log, as vuejs-logger provided. That package is built against Vue 2's
 * plugin API and has no Vue 3 release, and what the components use of it is
 * four passthrough methods -- so it is those four rather than a dependency.
 */
app.config.globalProperties.$log = logger

app.use(store)
app.use(vuetify)
app.mount('#app')
