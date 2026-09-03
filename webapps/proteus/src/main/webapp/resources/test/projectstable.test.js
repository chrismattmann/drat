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
  it('a chip exists for every licence on the files, and for no others', () => {
    const wrapper = table()
    const named = wrapper.vm.licenceKinds.map(k => k.value).sort()
    expect(named).toEqual(['Apache', 'Archives', 'Binaries', 'Standards', 'Unknown'])
    wrapper.unmount()
  })

  /*
   * The bug this replaces. The chips were built from the project document's
   * license_* fields, which count a different thing from the files: for Tika
   * that reads Standards 2645 while no file carries the value "Standards" at
   * all, and MIT and !GPL2 -- which files do carry -- had no chip, so they
   * were swept in under whichever chip the fallback happened to name.
   */
  it('every chip count is the number of rows that chip selects', async () => {
    const wrapper = table()
    for (const kind of wrapper.vm.licenceKinds) {
      wrapper.vm.licencesOff = wrapper.vm.licenceKinds
        .map(k => k.value).filter(v => v !== kind.value)
      await wrapper.vm.$nextTick()
      expect(wrapper.vm.sortedfiles.length,
        `${kind.value} says ${kind.count} but shows ${wrapper.vm.sortedfiles.length}`)
        .toBe(kind.count)
    }
    wrapper.unmount()
  })

  it('the chip counts add up to the whole file list', () => {
    const wrapper = table()
    const total = wrapper.vm.licenceKinds.reduce((sum, k) => sum + k.count, 0)
    expect(total).toBe(FILES.length)
    wrapper.unmount()
  })

  it('a licence RAT reports that has no fixed category still gets a chip', async () => {
    const wrapper = table()
    wrapper.vm.license.files = [
      { id: 'n.txt', license: 'MIT' },
      { id: 'g.c', license: '!GPL2' }
    ]
    await wrapper.vm.$nextTick()

    const named = wrapper.vm.licenceKinds.map(k => k.value).sort()
    expect(named).toEqual(['!GPL2', 'MIT'])

    wrapper.vm.toggleLicence('MIT')
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.sortedfiles.map(f => f.id)).toEqual(['g.c'])
    wrapper.unmount()
  })

  it('turning one off removes only that licence', async () => {
    const wrapper = table()
    expect(wrapper.vm.sortedfiles.length).toBe(FILES.length)

    wrapper.vm.toggleLicence('Apache')
    await wrapper.vm.$nextTick()

    const licences = wrapper.vm.sortedfiles.map(f => f.license)
    expect(licences).not.toContain('Apache')
    expect(licences).toContain('Unknown')
    wrapper.unmount()
  })

  it('turning everything off shows nothing', async () => {
    const wrapper = table()
    wrapper.vm.licencesOff = wrapper.vm.licenceKinds.map(k => k.value)
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.sortedfiles).toEqual([])
    wrapper.unmount()
  })

  it('Reset puts them all back', async () => {
    const wrapper = table()
    wrapper.vm.toggleLicence('Apache')
    wrapper.vm.toggleLicence('Archives')
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.sortedfiles.length).toBe(FILES.length - 2)

    wrapper.vm.resetLicenceFilters()
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.sortedfiles.length).toBe(FILES.length)
    wrapper.unmount()
  })

  it('opening another project clears the filters', async () => {
    const wrapper = table()
    wrapper.vm.toggleLicence('Apache')
    expect(wrapper.vm.licencesOff.length).toBe(1)

    wrapper.vm.moreClicked({ repo: '/repos/other', name: 'other' })
    await wrapper.vm.$nextTick()
    expect(wrapper.vm.licencesOff).toEqual([])
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
