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
 * The pie the three breakdowns are drawn with.
 *
 * The run view's mime chart used to place its legend by adding up the
 * character counts of the labels before each entry and wrapping at 250
 * pixels, which assumes every character is five wide. Mime types are long
 * and vary wildly in length, so entries ran into one another.
 */

import { describe, it, expect, beforeEach } from 'vitest'
import { drawPie } from '../src/charts/pie'

const MIMES = [
  { key: 'text/plain', y: 900 },
  { key: 'application/vnd.oasis.opendocument.spreadsheet', y: 400 },
  { key: 'text/x-java-source', y: 300 },
  { key: 'image/png', y: 200 },
  { key: 'application/xml', y: 100 }
]

beforeEach(() => {
  document.body.innerHTML = '<svg id="pie"></svg>'
})

function legendRows() {
  return Array.from(document.querySelectorAll('#pie .legend-row'))
}

describe('the pie lays its legend out in rows', () => {
  it('gives every slice a legend row', () => {
    drawPie('#pie', MIMES, {})
    expect(legendRows().length).toBe(MIMES.length)
  })

  /*
   * The overlap, stated as a rule: each row sits below the one before it by
   * a fixed amount, whatever the labels happen to say. The old placement
   * derived the offset from label length, so two long names collided.
   */
  it('stacks the rows, and does not derive their position from the text', () => {
    drawPie('#pie', MIMES, {})

    const ys = legendRows().map(row => {
      const move = /translate\(\s*([-\d.]+)\s*,\s*([-\d.]+)\s*\)/
        .exec(row.getAttribute('transform'))
      return { x: Number(move[1]), y: Number(move[2]) }
    })

    for (let i = 1; i < ys.length; i++) {
      expect(ys[i].y, `row ${i} is not below row ${i - 1}`)
        .toBeGreaterThan(ys[i - 1].y)
      expect(ys[i].x, 'rows are not in one column').toBe(ys[0].x)
    }

    // Evenly spaced: the gap does not depend on how long the names are.
    const gaps = ys.slice(1).map((row, i) => row.y - ys[i].y)
    expect(new Set(gaps).size, `uneven row spacing: ${gaps}`).toBe(1)
  })

  it('names every slice in the legend, however thin', () => {
    drawPie('#pie', MIMES, {})
    const text = legendRows().map(r => r.textContent).join(' ')
    for (const mime of MIMES) {
      expect(text).toContain(mime.key)
    }
  })

  it('says how many and what share', () => {
    drawPie('#pie', MIMES, {})
    const text = legendRows().map(r => r.textContent).join(' ')
    expect(text).toContain('900')
    expect(text).toContain('%')
  })
})

describe('the pie answers the pointer', () => {
  it('every slice can be pointed at and clicked', () => {
    drawPie('#pie', MIMES, {})
    const slices = document.querySelectorAll('#pie .arc')
    expect(slices.length).toBe(MIMES.length)
    for (const slice of slices) {
      expect(slice.style.cursor).toBe('pointer')
    }
  })

  it('tells the caller which slice was held, and when it was let go', () => {
    const held = []
    drawPie('#pie', MIMES, { onSelect: key => held.push(key) })

    const first = document.querySelector('#pie .arc')
    first.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    expect(held[held.length - 1]).toBe('text/plain')

    // Clicking the same slice again lets it go.
    first.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    expect(held[held.length - 1]).toBe(null)
  })

  it('draws a note rather than an empty frame when there is nothing', () => {
    drawPie('#pie', [], { emptyNote: 'Nothing indexed yet' })
    expect(document.querySelector('#pie').textContent)
      .toContain('Nothing indexed yet')
    expect(legendRows().length).toBe(0)
  })
})
