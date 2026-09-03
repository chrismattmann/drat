const { spawn } = require('child_process');
const fs = require('fs');
const path = require('path');

const root = __dirname;
const dist = path.join(root, 'dist');
const outputRoot = path.resolve(root, '..');
const successFile = path.join(dist, 'index.html');
let finished = false;
let childExited = false;

function remove(target) {
  if (fs.existsSync(target)) {
    fs.rmSync(target, { recursive: true, force: true });
  }
}

function copyDirectoryContents(from, to) {
  for (const entry of fs.readdirSync(from)) {
    const source = path.join(from, entry);
    const target = path.join(to, entry);
    const stat = fs.statSync(source);

    if (stat.isDirectory()) {
      remove(target);
      fs.mkdirSync(target, { recursive: true });
      copyDirectoryContents(source, target);
    } else {
      fs.mkdirSync(path.dirname(target), { recursive: true });
      fs.copyFileSync(source, target);
    }
  }
}

function complete() {
  if (finished) {
    return;
  }

  finished = true;
  copyDirectoryContents(dist, outputRoot);
  remove(dist);
  process.exit(0);
}

remove(dist);

/*
 * Webpack 4 hashes with MD4, which OpenSSL 3 does not provide, so the build
 * dies on any Node from 17 onwards with ERR_OSSL_EVP_UNSUPPORTED before it
 * compiles a line. The flag puts the old provider back. Added here rather
 * than in the npm script because the flag does not exist on older Node and
 * passing it there would break the build for anyone still on one; this way
 * each version gets what it needs.
 */
const nodeMajor = Number(process.versions.node.split('.')[0]);
const buildEnv = Object.assign({}, process.env);
if (nodeMajor >= 17 && !/openssl-legacy-provider/.test(buildEnv.NODE_OPTIONS || '')) {
  buildEnv.NODE_OPTIONS =
    (buildEnv.NODE_OPTIONS ? buildEnv.NODE_OPTIONS + ' ' : '') +
    '--openssl-legacy-provider';
}

const child = spawn(
  path.join(root, 'node_modules', '.bin', 'vue-cli-service'),
  ['build'],
  { cwd: root, env: buildEnv, stdio: 'inherit' }
);

const completionPoll = setInterval(() => {
  if (!childExited && fs.existsSync(successFile)) {
    child.kill('SIGTERM');
  }
}, 1000);

child.on('exit', (code, signal) => {
  childExited = true;
  clearInterval(completionPoll);

  if ((code === 0 || signal === 'SIGTERM') && fs.existsSync(successFile)) {
    complete();
  }

  process.exit(code || 1);
});
