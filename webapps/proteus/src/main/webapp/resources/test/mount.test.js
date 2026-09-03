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

/*
 * That every component still mounts and renders.
 *
 * A build only proves the templates compile. Vuetify 1 components that no
 * longer exist, slots that moved and props that were renamed all compile
 * happily and then fail in the browser, which is exactly the class of
 * breakage a port introduces -- so each component is mounted here and its
 * poll is left to run once.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import axios from 'axios'

import store from '../src/store/store'
import logger from '../src/logger'

import App from '../src/App.vue'
import auditsummarycomp from '../src/components/auditsummarycomp.vue'
import barchartcomp from '../src/components/barchartcomp.vue'
import bublechartcomp from '../src/components/bublechartcomp.vue'
import controllbar from '../src/components/controll_bar.vue'
import filelistcomp from '../src/components/filelistcomp.vue'
import licensepiecomp from '../src/components/licensepiecomp.vue'
import piechart from '../src/components/piechart.vue'
import progresscomp from '../src/components/progresscomp.vue'
import projectstable from '../src/components/projectstable.vue'
import statisticscomp from '../src/components/statisticscomp.vue'
import topmimepiecomp from '../src/components/topmimepiecomp.vue'

/*
 * Answers shaped like the back end's, so the components take the path they
 * take against a real deployment rather than bailing out at the first
 * undefined and mounting an empty shell that proves nothing.
 */
function answerFor(url) {
  if (url.includes('/drat/run')) {
    return { running: true, phase: 'audit', startedBy: 'cli', repo: '/tmp/repo', excludes: [] }
  }
  if (url.includes('/service/waiting')) {
    return { reasons: ['condition:urn:drat:MapsDone'] }
  }
  if (url.includes('/rat/progress')) {
    return { running: 2, finished: 76 }
  }
  if (url.includes('/repo/size')) {
    return { memorySize: 1048576, numberOfFiles: 3503 }
  }
  if (url.includes('/filemanager/progress')) {
    return { crawledFiles: 3503 }
  }
  if (url.includes('/service/products')) {
    return [{ title: 'a.java', link: '/a.java' }]
  }
  if (url.includes('facet.field=license')) {
    return { response: { numFound: 3 }, facet_counts: { facet_fields: { license: ['Apache', 2, 'Unknown', 1] } } }
  }
  if (url.includes('facet.field=mimetype')) {
    return { response: { numFound: 3 }, facet_counts: { facet_fields: { mimetype: ['text/plain', 2, 'text/html', 1] } } }
  }
  if (url.includes('q=type:project')) {
    return { response: { numFound: 1, start: 0, docs: [{ id: '/tmp/repo', repo: '/tmp/repo', name: 'repo', description: 'a repo' }] } }
  }
  if (url.includes('producttype:GenericFile')) {
    return { response: { numFound: 3503, docs: [] } }
  }
  return { response: { numFound: 0, start: 0, docs: [] } }
}

let vuetify

beforeEach(() => {
  vi.useFakeTimers()
  vuetify = createVuetify({ components, directives })
  vi.spyOn(axios, 'get').mockImplementation((url) =>
    Promise.resolve({ data: answerFor(String(url)) }))
  vi.spyOn(axios, 'post').mockImplementation(() => Promise.resolve({ data: 'ok' }))
  global.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} }
  global.visualViewport = null
})

afterEach(() => {
  vi.restoreAllMocks()
  vi.useRealTimers()
})

function mountIt(component) {
  return mount(component, {
    global: {
      plugins: [store, vuetify],
      config: { globalProperties: { $log: logger } },
      mocks: { $log: logger },
      stubs: { transition: false }
    },
    attachTo: document.body
  })
}

const everyComponent = {
  App,
  auditsummarycomp,
  barchartcomp,
  bublechartcomp,
  controllbar,
  filelistcomp,
  licensepiecomp,
  piechart,
  progresscomp,
  projectstable,
  statisticscomp,
  topmimepiecomp
}

describe('every component mounts under Vue 3 and Vuetify 3', () => {
  for (const [name, component] of Object.entries(everyComponent)) {
    it(`${name} mounts and renders`, async () => {
      /*
       * Warnings as well as errors. Vue reports a component that does not
       * exist -- a v-list-tile left behind by the port, say -- as a warning
       * and renders nothing in its place, so watching console.error alone
       * passes happily on exactly the breakage this is here to catch.
       */
      const complaints = []
      const collect = (...args) => complaints.push(args.join(' '))
      const errorSpy = vi.spyOn(console, 'error').mockImplementation(collect)
      const warnSpy = vi.spyOn(console, 'warn').mockImplementation(collect)

      const wrapper = mountIt(component)
      expect(wrapper.html().length).toBeGreaterThan(0)

      // One turn of whatever the component polls on, so a mistake in the
      // handler shows up here rather than a second after the page loads.
      await vi.advanceTimersByTimeAsync(1200)
      await wrapper.vm.$nextTick()

      /*
       * Kept narrow on purpose: these are the ways a Vue 2 template that was
       * not fully ported fails, and each is fatal to what it renders.
       */
      const real = complaints.filter(c =>
        /Failed to resolve component/i.test(c)
        || /Failed to resolve directive/i.test(c)
        || /Invalid prop/i.test(c)
        || /Unhandled error/i.test(c)
        || /is not a function/i.test(c)
        || /Cannot read (properties|property)/i.test(c))
      expect(real, `${name}: ${real.join(' | ')}`).toEqual([])

      wrapper.unmount()
      errorSpy.mockRestore()
      warnSpy.mockRestore()
    })
  }
})

/*
 * The nav was calling two methods that did not exist, which threw on every
 * click. Mounting alone never reached them -- it takes using the thing.
 */
describe('the navigation works', () => {
  it('switching between Summary and Audit does not throw', async () => {
    const complaints = []
    vi.spyOn(console, 'error').mockImplementation((...a) => complaints.push(a.join(' ')))
    vi.spyOn(console, 'warn').mockImplementation((...a) => complaints.push(a.join(' ')))

    const wrapper = mountIt(App)
    wrapper.vm.selectmenu({ title: 'Audit' })
    await wrapper.vm.$nextTick()
    expect(store.state.view).toBe('audit')

    wrapper.vm.selectmenu({ title: 'Summary' })
    await wrapper.vm.$nextTick()
    expect(store.state.view).toBe('summary')

    const real = complaints.filter(c => /is not a function|Cannot read/i.test(c))
    expect(real, real.join(' | ')).toEqual([])
    wrapper.unmount()
  })

  it('dismissing a snackbar message removes it', async () => {
    const wrapper = mountIt(App)
    wrapper.vm.snackbarmessages = ['first', 'second']
    wrapper.vm.snackbarmessageindex = 1
    wrapper.vm.removeelement(1)
    expect(wrapper.vm.snackbarmessages).toEqual(['first'])
    expect(wrapper.vm.snackbarmessageindex).toBe(0)
    wrapper.unmount()
  })
})
