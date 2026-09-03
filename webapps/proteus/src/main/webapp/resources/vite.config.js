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

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vuetify from 'vite-plugin-vuetify'

export default defineConfig({
  plugins: [
    vue({
      template: {
        /*
         * The logos are referenced as bare relative urls (src="drat-mark.svg")
         * and are served from the webapp root beside index.html, not imported
         * as modules. Vite resolves img src through the bundler by default and
         * fails the build on them; webpack left a bare url alone. Turning the
         * transform off for these tags keeps them what they are -- urls the
         * browser resolves against the context path the app is deployed at.
         */
        transformAssetUrls: { img: [], 'v-img': [] }
      }
    }),
    vuetify({ autoImport: true })
  ],

  /*
   * Relative, because the app is served from a context path (/proteus/) that
   * the build has no way of knowing. Absolute asset urls would resolve
   * against the host root and 404 for every deployment that is not at /.
   */
  base: './',

  /*
   * The bundle is unpacked into the webapp root next to index.html, and the
   * clean script removes css/, js/ and fonts/ by name. Vite's default is a
   * single assets/ directory, so the layout is spelled out here to keep both
   * the deployment and that script working as they did.
   */
  build: {
    outDir: 'dist',
    assetsDir: '',
    rollupOptions: {
      output: {
        entryFileNames: 'js/[name].[hash].js',
        chunkFileNames: 'js/[name].[hash].js',
        assetFileNames: (info) => {
          const name = info.names ? info.names[0] : info.name
          if (/\.css$/.test(name)) return 'css/[name].[hash][extname]'
          if (/\.(woff2?|eot|ttf|otf)$/.test(name)) return 'fonts/[name].[hash][extname]'
          return '[name].[hash][extname]'
        }
      }
    }
  },

  test: {
    environment: 'jsdom',
    server: { deps: { inline: ['vuetify'] } }
  },

  server: {
    proxy: {
      '/proteus-services': {
        target: process.env.VUE_APP_ROOT_API,
        changeOrigin: true
      },
      '/proteus': {
        target: process.env.VUE_APP_ROOT_API,
        changeOrigin: true
      },
      '/solr': {
        target: process.env.VUE_APP_ROOT_API,
        changeOrigin: true
      }
    }
  }
})
