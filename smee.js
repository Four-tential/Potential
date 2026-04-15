const fs = require('fs');
const path = require('path');
const { execSync, spawn } = require('child_process');

// .env 파일에서 SMEE_URL 읽기
const envPath = path.join(__dirname, '.env');

if (!fs.existsSync(envPath)) {
    console.error('오류: .env 파일을 찾을 수 없습니다');
    process.exit(1);
}

const envContent = fs.readFileSync(envPath, 'utf-8');
let smeeUrl = '';

for (const line of envContent.split('\n')) {
    const trimmed = line.trim();
    if (trimmed.startsWith('#') || !trimmed.includes('=')) continue;
    const [key, ...valueParts] = trimmed.split('=');
    if (key.trim() === 'SMEE_URL') {
        smeeUrl = valueParts.join('=').trim();
        break;
    }
}

if (!smeeUrl) {
    console.error('오류: .env 파일에 SMEE_URL 이 설정되지 않았습니다');
    console.error('예시: SMEE_URL=https://smee.io/xxxxxxxxxxxxxxxx');
    process.exit(1);
}

console.log(`smee 시작: ${smeeUrl} → http://localhost:8080/api/v1/webhooks/portone`);

const smee = spawn('smee', [
    '--url', smeeUrl,
    '--path', '/api/v1/webhooks/portone',
    '--port', '8080'
], { stdio: 'inherit', shell: true });

smee.on('error', (err) => {
    console.error('smee 실행 오류:', err.message);
    console.error('smee-client 가 설치되어 있는지 확인하세요');
    console.error('설치 명령어: npm install --global smee-client');
    process.exit(1);
});

smee.on('close', (code) => {
    process.exit(code);
});