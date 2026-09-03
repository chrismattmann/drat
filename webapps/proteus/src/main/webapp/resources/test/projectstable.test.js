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
 * The licence chips filter the file table, and the project view is a view.
 *
 * Both were carried across from Vuetify 1 in a form that still rendered: a
 * v-model on a v-chip means visibility rather than selection in Vuetify 3,
 * and :value is not a prop at all -- so the filters did nothing and the
 * fields were both empty and, where readonly had been left off, editable.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import axios from 'axios'

import store from '../src/store/store'
import logger from '../src/logger'
import projectstable from '../src/components/projectstable.vue'

let vuetify

const FILES = [
  { id: 'a.java', mimetype: 'text/x-java', license: 'Apache', header: 'ASF header' },
  { id: 'b.txt', mimetype: 'text/plain', license: 'Unknown', header: '' },
  { id: 'c.png', mimetype: 'image/png', license: 'Binaries', header: '' },
  { id: 'd.md', mimetype: 'text/markdown', license: 'Standards', header: '' },
  { id: 'e.zip', mimetype: 'application/zip', license: 'Archives', header: '' }
]

beforeEach(() => {
  vuetify = createVuetify({ components, directives })
  vi.spyOn(axios, 'get').mockResolvedValue({
    data: { response: { numFound: 0, start: 0, docs: [] } }
  })
  global.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} }
  global.visualViewport = null
  store.commit('setOrigin', 'http://localhost:8180')
})

afterEach(() => vi.restoreAllMocks())

function table() {
  const wrapper = mount(projectstable, {
    global: {
      plugins: [store, vuetify],
      config: { globalProperties: { $log: logger } },
      mocks: { $log: logger }
    },
    attachTo: document.body
  })
  wrapper.vm.license.files = FILES
  return wrapper
}

describe('the licence chips filter the file table', () => {
  it('turning one off removes only that licence', async () => {
    const wrapper = table()
    expect(wrapper.vm.sortedfiles.length).toBe(5)

    wrapper.vm.toggleLicence('apache')
    await wrapper.vm.$nextTick()

    const licences = wrapper.vm.sortedfiles.map(f => f.license)
    expect(licences).not.toContain('Apache')
    expect(licences).toContain('Unknown')
    expect(wrapper.vm.sortedfiles.length).toBe(4)
    wrapper.unmount()
  })

  /*
   * Standards used to stand in for Apache and Unknown as well, so it worked
   * as a master switch: with it on, turning either of those off changed
   * nothing at all.
   */
  it('Standards filters Standards and nothing else', async () => {
    const wrapper = table()
    wrapper.vm.toggleLicence('apache')
    wrapper.vm.toggleLicence('unknown')
    await wrapper.vm.$nextTick()

    const licences = wrapper.vm.sortedfiles.map(f => f.license)
    expect(licences, 'Standards is still showing Apache').not.toContain('Apache')
    expect(licences, 'Standards is still showing Unknown').not.toContain('Unknown')
    expect(licences).toContain('Standards')
    wrapper.unmount()
  })

  it('turning everything off shows nothing', async () => {
    const wrapper = table()
    for (const kind of wrapper.vm.licenceKinds) {
      wrapper.vm.license[kind.flag] = false
    }
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.sortedfiles).toEqual([])
    wrapper.unmount()
  })

  it('Reset puts them all back', async () => {
    const wrapper = table()
    wrapper.vm.toggleLicence('apache')
    wrapper.vm.toggleLicence('archives')
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.sortedfiles.length).toBe(3)

    wrapper.vm.resetLicenceFilters()
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.sortedfiles.length).toBe(5)
    wrapper.unmount()
  })

  it('a licence with no chip of its own still follows Standards', async () => {
    const wrapper = table()
    wrapper.vm.license.files = [{ id: 'x', license: 'SomethingElse' }]
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.sortedfiles.length).toBe(1)

    wrapper.vm.toggleLicence('standard')
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.sortedfiles).toEqual([])
    wrapper.unmount()
  })

  it('the search narrows what the chips left', async () => {
    const wrapper = table()
    wrapper.vm.search = 'java'
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.filteredFiles.map(f => f.id)).toEqual(['a.java'])
    wrapper.unmount()
  })
})

describe('the project view cannot be typed into', () => {
  it('renders the details as text, not as inputs', async () => {
    const wrapper = table()
    wrapper.vm.selectedItem = {
      name: 'tika', description: 'Apache Tika',
      repo: '/repos/tika', loc_url: 'https://example.invalid/tika'
    }
    wrapper.vm.dialog = true
    await wrapper.vm.$nextTick()

    const details = document.querySelector('.project-details')
    expect(details, 'the project details are not rendered').toBeTruthy()
    expect(details.textContent).toContain('tika')
    expect(details.textContent).toContain('/repos/tika')
    // The whole point: nothing in here accepts typing.
    expect(details.querySelectorAll('input, textarea').length).toBe(0)
    wrapper.unmount()
  })
})
