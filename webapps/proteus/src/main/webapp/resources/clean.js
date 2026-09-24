/*
Licensed to the Apache Software Foundation (ASF) under one or more contributor
license agreements. See the NOTICE.txt file distributed with this work for
additional information regarding copyright ownership. The ASF licenses this
file to you under the Apache License, Version 2.0 (the "License"); you may not
use this file except in compliance with the License. You may obtain a copy of
the License at
     http://www.apache.org/licenses/LICENSE-2.0
Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
License for the specific language governing permissions and limitations under
the License.
*/

import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.dirname(fileURLToPath(import.meta.url))
const outputRoot = path.resolve(root, '..')
const generated = [
  'css',
  'favicon.ico',
  'fonts',
  'index.html',
  'js',
  'drat-logo.svg',
  'drat-mark.svg'
]

let removed = false
for (const entry of generated) {
  const target = path.join(outputRoot, entry)
  if (fs.existsSync(target)) {
    fs.rmSync(target, { recursive: true, force: true })
    removed = true
  }
}

if (!removed) {
  console.log('Already Clean')
}
