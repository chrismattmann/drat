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
 * Builds the bundle and unpacks it into the webapp root, where the servlet
 * container serves it from.
 *
 * This used to work around webpack 4 hashing with md4, which OpenSSL 3 does
 * not provide, by putting the legacy provider back on Node 17 and above; and
 * around the build not exiting, by watching for dist/index.html to appear and
 * killing the child when it did. Neither applies to Vite: it hashes with
 * something OpenSSL still has, and it exits when it is done.
 */

import { spawn } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const root = path.dirname(fileURLToPath(import.meta.url))
const dist = path.join(root, 'dist')
const outputRoot = path.resolve(root, '..')

function remove(target) {
  if (fs.existsSync(target)) {
    fs.rmSync(target, { recursive: true, force: true })
  }
}

function copyDirectoryContents(from, to) {
  for (const entry of fs.readdirSync(from)) {
    const source = path.join(from, entry)
    const target = path.join(to, entry)

    if (fs.statSync(source).isDirectory()) {
      remove(target)
      fs.mkdirSync(target, { recursive: true })
      copyDirectoryContents(source, target)
    } else {
      fs.mkdirSync(path.dirname(target), { recursive: true })
      fs.copyFileSync(source, target)
    }
  }
}

remove(dist)

const vite = path.join(root, 'node_modules', '.bin', 'vite')
const child = spawn(vite, ['build'], { cwd: root, stdio: 'inherit' })

child.on('exit', (code) => {
  if (code !== 0) {
    process.exit(code || 1)
  }

  if (!fs.existsSync(path.join(dist, 'index.html'))) {
    console.error('build reported success but produced no index.html')
    process.exit(1)
  }

  copyDirectoryContents(dist, outputRoot)
  remove(dist)
  process.exit(0)
})
