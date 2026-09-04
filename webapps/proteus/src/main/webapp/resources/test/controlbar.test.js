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
 * What the control bar offers, and what it does with it.
 *
 * The bar used to present the phases of an audit as alternatives to it, so
 * these say the two things it offers now and that each one asks the back end
 * for what it says it will.
 */

import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createVuetify } from 'vuetify'
import * as components from 'vuetify/components'
import * as directives from 'vuetify/directives'
import axios from 'axios'

import store from '../src/store/store'
import logger from '../src/logger'
import controllbar from '../src/components/controll_bar.vue'

let vuetify
let posted

beforeEach(() => {
  posted = []
  vuetify = createVuetify({ components, directives })
  vi.spyOn(axios, 'post').mockImplementation((url, body) => {
    posted.push({ url: String(url), body })
    return Promise.resolve({ data: 'ok' })
  })
  // /drat/repo/valid answers straight away; everything else is incidental.
  vi.spyOn(axios, 'get').mockResolvedValue({ data: 'ok' })
  global.ResizeObserver = class { observe() {} unobserve() {} disconnect() {} }
  // jsdom has neither, and Vuetify's overlay reads both when a dialog opens.
  global.visualViewport = null
  store.commit('setOrigin', 'http://localhost:8180')
  store.commit('setCurrentActionRequest', '')
  store.commit('setprogress', false)
})

afterEach(() => vi.restoreAllMocks())

function bar() {
  return mount(controllbar, {
    global: {
      plugins: [store, vuetify],
      config: { globalProperties: { $log: logger } },
      mocks: { $log: logger }
    },
    attachTo: document.body
  })
}

describe('the control bar offers Go and Reset, and nothing else', () => {
  it('offers no per-phase actions', () => {
    const wrapper = bar()
    /*
     * Crawl, Index, Map and Reduce are phases of an audit, not alternatives
     * to one: picking Map on a repository that had never been crawled
     * started a run with nothing to map. They are still in bin/drat for
     * anyone driving them deliberately.
     */
    const text = wrapper.text()
    for (const phase of ['Crawl', 'Index', 'Map', 'Reduce']) {
      expect(text, `the bar still offers ${phase}`).not.toContain(phase)
    }
    expect(wrapper.vm.dratoptions).toBeUndefined()
    expect(wrapper.vm.selectedAction).toBeUndefined()
    wrapper.unmount()
  })

  it('Go posts the repository to the go endpoint', async () => {
    const wrapper = bar()
    wrapper.vm.url = '/Users/somebody/git/tika'
    wrapper.vm.reponame = 'tika'
    wrapper.vm.repodesc = 'tika from Proteus'
    wrapper.vm.go()
    await wrapper.vm.$nextTick()

    await new Promise(r => setTimeout(r, 0))
    await wrapper.vm.$nextTick()

    expect(posted.length).toBe(1)
    expect(posted[0].url).toContain('/proteus-services/drat/go')
    expect(posted[0].body.repo).toBe('/Users/somebody/git/tika')
    // Only once the back end has accepted it.
    expect(store.state.currentRepo).toBe('/Users/somebody/git/tika')
    expect(store.state.progress).toBe(true)
    wrapper.unmount()
  })

  it('Go with no repository asks for one instead of starting a run', async () => {
    const wrapper = bar()
    wrapper.vm.url = ''
    wrapper.vm.go()
    await wrapper.vm.$nextTick()

    expect(posted).toEqual([])
    expect(wrapper.vm.problem).toContain('path')
    expect(store.state.progress, 'it opened the watch view anyway').toBe(false)
    wrapper.unmount()
  })

  /*
   * The refusal has to be shown rather than walked past.
   *
   * The commits used to run before the request and the failure was rethrown
   * out of the catch, where nothing was listening -- so a repository the back
   * end refused took the reader to a spinning ring for a run that had never
   * started, saying nothing about why.
   */
  it('a repository the back end refuses does not open the watch view', async () => {
    axios.get.mockImplementation(() => Promise.reject({
      response: { status: 400, data: "There is no directory at '/repo,'." }
    }))

    const wrapper = bar()
    wrapper.vm.url = '/repo,'
    wrapper.vm.go()
    await new Promise(r => setTimeout(r, 0))
    await wrapper.vm.$nextTick()

    expect(store.state.progress, 'it opened the watch view anyway').toBe(false)
    expect(wrapper.vm.problem).toContain('no directory')
    expect(wrapper.vm.starting).toBe(false)
    wrapper.unmount()
  })

  it('says something even when the failure carries no message', async () => {
    axios.get.mockImplementation(() => Promise.reject(new Error('Network Error')))

    const wrapper = bar()
    wrapper.vm.url = '/repo'
    wrapper.vm.go()
    await new Promise(r => setTimeout(r, 0))
    await wrapper.vm.$nextTick()

    expect(store.state.progress).toBe(false)
    expect(wrapper.vm.problem.length).toBeGreaterThan(0)
    wrapper.unmount()
  })

  it('Reset posts to the reset endpoint and needs no repository', async () => {
    const wrapper = bar()
    wrapper.vm.url = ''
    wrapper.vm.reset()
    await wrapper.vm.$nextTick()

    expect(posted.length).toBe(1)
    expect(posted[0].url).toContain('/proteus-services/drat/reset')
    // Not a run: this must not put the page into the watch view.
    expect(store.state.progress).toBe(false)
    wrapper.unmount()
  })

  it('Reset is asked about before it happens', async () => {
    const wrapper = bar()
    // The button sets the question; only answering it posts anything.
    expect(wrapper.vm.confirmReset).toBe(false)
    wrapper.vm.confirmReset = true
    await wrapper.vm.$nextTick()
    expect(posted).toEqual([])

    wrapper.vm.reset()
    await wrapper.vm.$nextTick()
    expect(posted.length).toBe(1)
    expect(wrapper.vm.confirmReset).toBe(false)
    wrapper.unmount()
  })

  /*
   * /go answers only when the audit is over, minutes later. Waiting for it
   * left the dialog open with a spinning button for the whole run.
   */
  it('does not wait for the run to finish before showing it', async () => {
    let settle
    axios.post.mockImplementation((url, b) => {
      posted.push({ url: String(url), body: b })
      // As the real one behaves: it does not answer until the run ends.
      return new Promise(resolve => { settle = resolve })
    })

    const wrapper = bar()
    wrapper.vm.url = '/repos/mnemosyne'
    wrapper.vm.go()
    await new Promise(r => setTimeout(r, 0))
    await wrapper.vm.$nextTick()

    expect(posted.length, 'the run was never started').toBe(1)
    expect(store.state.progress, 'the watch view never opened').toBe(true)
    expect(wrapper.vm.dialog, 'the dialog stayed open').toBe(false)
    expect(wrapper.vm.starting, 'the button is still spinning').toBe(false)

    settle({ data: 'done' })
    wrapper.unmount()
  })
})
