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

const child = spawn(
  path.join(root, 'node_modules', '.bin', 'vue-cli-service'),
  ['build'],
  { cwd: root, env: process.env, stdio: 'inherit' }
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
