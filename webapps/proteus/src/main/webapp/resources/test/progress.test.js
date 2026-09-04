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
 * What the run view says while a run is happening.
 *
 * The ring used to carry a percentage taken from the phase name -- crawl 0,
 * index 25, map 50, reduce 75 -- which is a guess dressed as a measurement:
 * the same 50 for a map that had just begun and one that had finished every
 * partition. What phase it is in is the honest part, so that is what it says.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import axios from 'axios'

import store from '../src/store/store'
import logger from '../src/logger'
import progresscomp from '../src/components/progresscomp.vue'
import statisticscomp from '../src/components/statisticscomp.vue'

let vuetify

beforeEach(() => {
  vuetify = createVuetify({ components, directives })
  vi.spyOn(axios, 'get').mockResolvedValue({ data: {} })
  global.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} }
  global.visualViewport = null
  store.commit('setOrigin', 'http://localhost:8180')
})

afterEach(() => vi.restoreAllMocks())

function mountIt(component) {
  return mount(component, {
    global: {
      plugins: [store, vuetify],
      config: { globalProperties: { $log: logger } },
      mocks: { $log: logger }
    },
    attachTo: document.body
  })
}

describe('progress says what is happening, not a made-up percentage', () => {
  const phases = [
    ['crawl', 'Crawling...'],
    ['index', 'Indexing...'],
    ['map', 'Mapping...'],
    ['reduce', 'Reducing...'],
    ['reset', 'Clearing previous run...'],
    ['audit', 'Auditing...']
  ]

  for (const [phase, said] of phases) {
    it(`${phase} reads as "${said}"`, async () => {
      const wrapper = mountIt(progresscomp)
      wrapper.vm.apply({ running: true, phase })
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.status).toBe(said)
      wrapper.unmount()
    })
  }

  it('carries no percentage while it runs', async () => {
    const wrapper = mountIt(progresscomp)
    wrapper.vm.apply({ running: true, phase: 'map' })
    await wrapper.vm.$nextTick()

    // The ring spins; it does not claim a number.
    expect(wrapper.vm.completed).toBe(false)
    expect(wrapper.vm.value).toBeUndefined()
    expect(wrapper.html()).not.toContain('>50<')
    wrapper.unmount()
  })

  it('a phase it does not know still says something', async () => {
    const wrapper = mountIt(progresscomp)
    wrapper.vm.apply({ running: true, phase: 'something-new' })
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.status).toBe('Running...')
    wrapper.unmount()
  })
})

describe('the statistics panel', () => {
  /*
   * A RAT audit of one mime partition is over in well under the second
   * between polls, so the running count read zero almost every time it was
   * looked at while the finished count climbed past it.
   */
  it('reports finished audits and not running ones', async () => {
    const wrapper = mountIt(statisticscomp)
    wrapper.vm.stat.finishedRatInstances = 64
    await wrapper.vm.$nextTick()

    const text = wrapper.text()
    expect(text).toContain('64')
    expect(text).toContain('Finished')
    expect(text, 'the running count is back').not.toContain('Running')
    wrapper.unmount()
  })
})
