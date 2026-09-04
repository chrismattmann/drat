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

import * as d3 from 'd3'
import tinycolor from 'tinycolor2'

/*
 * One pie, drawn so that everything it draws stays inside it.
 *
 * The licence and mime charts each carried their own copy of this, and both
 * copies had the same three faults: the radius was taken from the full height
 * of a tall svg, so a slice label at radius-40 ran past the right edge and
 * "Standards" was cut off by it; every slice was labelled however thin it
 * was, so neighbouring slivers wrote their names on top of each other; and
 * the legend was appended as a second <svg> at the origin, which put it over
 * the pie rather than under it.
 *
 * Here the pie is sized from the space it actually has, a slice is labelled
 * only when its own arc can hold the text, and the legend is laid out below
 * the pie in the same coordinate system.
 */

// Below this a slice is thinner than its own label is tall, so the label
// goes in the legend instead of on top of its neighbour.
const SMALLEST_LABELLED_SLICE = 0.30 // radians, ~17 degrees

const LEGEND_ROW = 22
const LEGEND_SWATCH = 13

export function drawPie(selector, data, options) {
  const settings = options || {}
  const svg = d3.select(selector)
  if (svg.empty()) {
    return
  }

  svg.selectAll('*').remove()

  const total = data.reduce((sum, d) => sum + (d.y || 0), 0)
  if (!data.length || total <= 0) {
    svg.append('text')
      .attr('x', 12).attr('y', 24)
      .attr('class', 'chart-empty')
      .text(settings.emptyNote || 'Nothing to show yet')
    return
  }

  const width = settings.width || 420
  const legendHeight = data.length * LEGEND_ROW + 12
  const pieHeight = settings.pieHeight || 260
  const height = pieHeight + legendHeight

  /*
   * Sized and scaled by the viewBox rather than by fixed pixels, so the chart
   * fits the column it is placed in instead of overflowing the card on a
   * narrow window.
   */
  svg.attr('viewBox', `0 0 ${width} ${height}`)
    .attr('width', '100%')
    .attr('height', null)
    .attr('preserveAspectRatio', 'xMidYMid meet')

  const radius = Math.min(width, pieHeight) / 2 - 12
  const colour = d3.scaleOrdinal(settings.scheme || d3.schemeTableau10)

  const g = svg.append('g')
    .attr('transform', `translate(${width / 2},${pieHeight / 2})`)

  const pie = d3.pie().sort(null).value(d => d.y)
  const slices = pie(data)

  const path = d3.arc().outerRadius(radius).innerRadius(0)
  const labelAt = d3.arc().outerRadius(radius * 0.62).innerRadius(radius * 0.62)

  const arc = g.selectAll('.arc').data(slices).enter()
    .append('g').attr('class', 'arc')

  arc.append('path')
    .attr('d', path)
    .attr('style', d => `fill:${colour(d.data.key)}`)

  /*
   * Hover. A slice and its legend row light up together and the reading
   * appears in the middle of the pie, so the answer is where the pointer
   * already is rather than in a browser tooltip a second later.
   */
  const readout = g.append('text')
    .attr('class', 'pie-readout')
    .attr('text-anchor', 'middle')
    .attr('dy', '0.35em')
    .style('opacity', 0)

  function highlight(key) {
    arc.selectAll('path')
      .style('opacity', d => (key === null || d.data.key === key) ? 1 : 0.35)
    svg.selectAll('.legend-row')
      .classed('legend-row-on', d => key !== null && d.data.key === key)
  }

  arc.on('mouseenter', function (event, d) {
    highlight(d.data.key)
    readout.text(`${d.data.key} · ${d.data.y} (${percent(d.data.y, total)})`)
      .style('opacity', 1)
  }).on('mouseleave', function () {
    highlight(null)
    readout.style('opacity', 0)
  })

  arc.append('title')
    .text(d => `${d.data.key}: ${d.data.y} (${percent(d.data.y, total)})`)

  arc.append('text')
    .attr('transform', d => `translate(${labelAt.centroid(d)})`)
    .attr('dy', '0.35em')
    .attr('text-anchor', 'middle')
    .attr('class', 'slice-label')
    .attr('style', d => `fill:${readableOn(colour(d.data.key))}`)
    // Only where it fits. A label wider than the arc it sits in is the one
    // that ends up on top of the next slice's.
    .text(d => (d.endAngle - d.startAngle) >= SMALLEST_LABELLED_SLICE
      ? d.data.key : '')

  const legend = svg.append('g')
    .attr('transform', `translate(14,${pieHeight + 6})`)
    .selectAll('g').data(slices).enter()
    .append('g')
    .attr('class', 'legend-row')
    .attr('transform', (d, i) => `translate(0,${i * LEGEND_ROW})`)
    .on('mouseenter', (event, d) => {
      highlight(d.data.key)
      readout.text(`${d.data.key} · ${d.data.y} (${percent(d.data.y, total)})`)
        .style('opacity', 1)
    })
    .on('mouseleave', () => {
      highlight(null)
      readout.style('opacity', 0)
    })

  legend.append('rect')
    .attr('width', LEGEND_SWATCH)
    .attr('height', LEGEND_SWATCH)
    .attr('rx', 2)
    .style('fill', d => colour(d.data.key))

  legend.append('text')
    .attr('x', LEGEND_SWATCH + 8)
    .attr('y', LEGEND_SWATCH - 2)
    .attr('class', 'legend-label')
    // Every slice is named here, which is what lets the thin ones go
    // unlabelled on the pie itself without becoming unidentifiable.
    .text(d => `${d.data.key} — ${d.data.y} (${percent(d.data.y, total)})`)
}

function percent(value, total) {
  return `${((value / total) * 100).toFixed(1)}%`
}

/* Black or white, whichever can be read on the slice it is written on. */
function readableOn(background) {
  return tinycolor(background).isLight() ? '#1a1a1a' : '#ffffff'
}
