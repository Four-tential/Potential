const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');

const envPath = path.join(__dirname, '.env');
const defaultTarget = 'http://127.0.0.1:8080/api/v1/webhooks/portone';

if (!fs.existsSync(envPath)) {
    console.error('ERROR: .env file was not found.');
    process.exit(1);
}

const env = readEnv(envPath);
const smeeUrl = env.SMEE_URL;
const targetUrl = env.SMEE_TARGET_URL || defaultTarget;

if (!smeeUrl) {
    console.error('ERROR: SMEE_URL is missing in .env.');
    console.error('Example: SMEE_URL=https://smee.io/xxxxxxxxxxxxxxxx');
    process.exit(1);
}

const smeeCommand = resolveSmeeCommand();
const smeeArgs = ['--url', smeeUrl, '--target', targetUrl];

console.log(`[smee] source: ${smeeUrl}`);
console.log(`[smee] target: ${targetUrl}`);
console.log(`[smee] command: ${smeeCommand}`);

const smee = runSmee(smeeCommand, smeeArgs);

smee.on('error', (err) => {
    console.error('[smee] failed to start:', err.message);
    console.error('[smee] install command: npm install --global smee-client');
    process.exit(1);
});

smee.on('close', (code) => {
    console.log(`[smee] stopped with code ${code}`);
    process.exit(code ?? 0);
});

function readEnv(filePath) {
    const content = fs.readFileSync(filePath, 'utf-8');
    const values = {};

    for (const line of content.split(/\r?\n/)) {
        const trimmed = line.trim();
        if (!trimmed || trimmed.startsWith('#') || !trimmed.includes('=')) {
            continue;
        }

        const [key, ...valueParts] = trimmed.split('=');
        values[key.trim()] = valueParts.join('=').trim();
    }

    return values;
}

function resolveSmeeCommand() {
    return process.platform === 'win32' ? 'smee.cmd' : 'smee';
}

function runSmee(command, args) {
    const childEnv = withoutProxyEnv();

    if (process.platform !== 'win32') {
        return spawn(command, args, { stdio: 'inherit', env: childEnv });
    }

    return spawn(command, args, { stdio: 'inherit', shell: true, env: childEnv });
}

function withoutProxyEnv() {
    const childEnv = { ...process.env };

    for (const key of [
        'HTTP_PROXY',
        'HTTPS_PROXY',
        'ALL_PROXY',
        'http_proxy',
        'https_proxy',
        'all_proxy',
        'GIT_HTTP_PROXY',
        'GIT_HTTPS_PROXY'
    ]) {
        delete childEnv[key];
    }

    childEnv.NO_PROXY = 'localhost,127.0.0.1,::1,smee.io';
    childEnv.no_proxy = childEnv.NO_PROXY;
    return childEnv;
}
