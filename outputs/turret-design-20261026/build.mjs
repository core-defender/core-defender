import fs from 'node:fs/promises';
import {Workbook, SpreadsheetFile} from '@oai/artifact-tool';

const dir = new URL('.', import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1');
const wb = Workbook.create();
const names = ['밸런스 계산', '기본 수치', '모듈과 증폭', '제작법 제안', '배치와 소모', '확정 규칙', '검토 항목'];
/** @type {Object<string, *>} Worksheet proxies are provided by artifact-tool at runtime. */
const sheets = Object.fromEntries(names.map(n => [n, wb.worksheets.add(n)]));
const colors = {ink: '#233247', head: '#304966', input: '#FFF3D6', calc: '#EDF3F8', green: '#E7F1E9', amber: '#FCE4C3'};
const widthsBySheet = {};
const col = n => String.fromCharCode(65 + n);
// Artifact-tool provides this API dynamically; verified by workbook generation.
//noinspection JSUnresolvedFunction
function val(s, a, v) {
    // Artifact-tool supplies worksheet proxies at runtime.
    //noinspection JSUnresolvedFunction
    sheets[s].getRange(a).values = [[v]];
}

// Artifact-tool provides this API dynamically; verified by workbook generation.
//noinspection JSUnresolvedFunction
function formula(s, a, f) {
    // Artifact-tool supplies worksheet proxies at runtime.
    //noinspection JSUnresolvedFunction
    sheets[s].getRange(a).formulas = [[f]];
}

function init(name, title, sub, widths) {
    widthsBySheet[name] = widths;
    const s = sheets[name];
    s.showGridLines = false;
    const end = col(widths.length - 1);
    s.getRange(`A1:${end}150`).format.font = {name: 'Malgun Gothic', size: 11, color: colors.ink};
    s.getRange(`A1:${end}150`).format.verticalAlignment = 'center';
    widths.forEach((w, i) => s.getRange(`${col(i)}1:${col(i)}150`).format.columnWidth = w);
    val(name, 'A2', title);
    s.getRange('A2').format.font = {name: 'Malgun Gothic', size: 16, bold: true, color: colors.ink};
    val(name, 'A3', sub);
    s.getRange('A3').format.font = {size: 10, color: '#52677A'};
    s.getRange(`A4:${end}4`).format.borders = {bottom: {style: 'thin', color: '#8FA4B8'}};
    s.tabColor = colors.head;
    return s;
}

function table(name, row, headers, data, widthRows = 40) {
    const s = sheets[name], last = col(headers.length - 1), end = row + data.length;
    s.getRange(`A${row}:${last}${end}`).values = [headers, ...data.map(r => headers.map((_, i) => r[i] ?? ''))];
    s.getRange(`A${row}:${last}${row}`).format = {
        fill: colors.head,
        font: {bold: true, color: '#FFFFFF'},
        wrapText: true,
        rowHeight: 32,
        horizontalAlignment: 'center'
    };
    if (data.length) {
        s.getRange(`A${row + 1}:${last}${end}`).format.wrapText = true;
        for (let r = row + 1; r <= end; r++) {
            const content = data[r - row - 1];
            const lines = Math.max(1, ...content.map((v, i) => typeof v === 'string' ? Math.ceil([...v].reduce((n, c) => n + (c.charCodeAt(0) > 255 ? 1.8 : 1), 0) / (widthsBySheet[name][i] * .88)) : 1));
            s.getRange(`A${r}:${last}${r}`).format.rowHeight = Math.max(widthRows, lines * 15 + 10);
            if ((r - row) % 2 === 0) s.getRange(`A${r}:${last}${r}`).format.fill = '#F3F6F9';
        }
    }
    s.freezePanes.freezeRows(5);
    return end;
}

// Artifact-tool provides this API dynamically; verified by workbook generation.
//noinspection JSUnresolvedFunction
function input(name, range, fmt = '0.00') {
    // Artifact-tool supplies worksheet proxies at runtime.
    //noinspection JSUnresolvedFunction
    const r = sheets[name].getRange(range);
    r.format.fill = colors.input;
    r.setNumberFormat(fmt);
    r.format.horizontalAlignment = 'right';
}

// Artifact-tool provides this API dynamically; verified by workbook generation.
//noinspection JSUnresolvedFunction
function calc(name, range, fmt = '0.00') {
    // Artifact-tool supplies worksheet proxies at runtime.
    //noinspection JSUnresolvedFunction
    const r = sheets[name].getRange(range);
    r.format.fill = colors.calc;
    r.setNumberFormat(fmt);
    r.format.horizontalAlignment = 'right';
}

init('기본 수치', '포탑 기본 수치 제안', '출처: 설계 대화 2026-10-26. 모든 전투 수치는 제안이며 게임 실측값이 아님. 황색 셀은 입력값.', [27, 15, 17, 17, 17, 17, 60]);
const base = [
    ['직접 피해', 'HP', 4, 14, 18, 2, '화염은 판정 1회, 투석은 폭발 중심 피해. 방어력 적용 전.'],
    ['직격 추가 피해', 'HP', 0, 4, 4, 0, '투석 직격 대상은 폭발과 직격 피해를 함께 받음.'],
    ['기본 주기', '초', .8, 3, 3, 1.5, '화염 기본 주기는 분사 시간 + 휴지 시간으로 계산.'],
    ['분사 시간', '초', 0, 0, 0, .5, '화염 공속 강화로 바뀌지 않음.'],
    ['휴지 시간', '초', 0, 0, 0, 1, '화염 공속 강화는 휴지 시간을 나누어 단축.'],
    ['분사당 피해 판정', '회', 1, 1, 1, 4, '분사 도중 대상이 계속 범위에 있을 때.'],
    ['사거리', '블록', 16, 24, 21, 4, '일반 돌보다 심층암은 사거리가 짧음.'],
    ['공격 범위', '블록', 0, 3, 3.5, 0, '투석 폭발 반경. 화염은 사거리와 방사각으로 판정.'],
    ['방사각', '도', 0, 0, 0, 90, '화염 최대 360도.'],
    ['오발 확률', '비율', .15, 0, 0, 0, '화살만 별도 확률. 투석은 느린 탄속에 따른 실제 빗나감.'],
    ['탄약 소비', '개/공격', 1, 1, 1, 0, '다중발사 시 화살은 실제 발사 수만큼 소비.'],
    ['연료당 발사', '발/개', 0, 8, 8, 0, '석탄·목탄 동등. 석탄 블록은 9개분.'],
    ['종자유 소비', 'mB/분사틱', 0, 0, 0, 2, '정상 20TPS. 대상 수와 무관하게 실제 분사 시간에 비례.'],
    ['잔류 화상', 'HP/초', 0, 0, 0, 1, '다른 포탑끼리 중첩. 같은 포탑은 지속시간 갱신.'],
    ['화상 지속시간', '초', 0, 0, 0, 4, '마지막 적중 시점부터 계산.'],
    ['수평 조준속도', '도/초', 180, 90, 90, 0, '투석 90은 추가 제안. 화염은 즉시 조준.'],
    ['수직 조준속도', '도/초', 120, 60, 60, 0, '투석 60은 추가 제안.'],
    ['발사 허용 오차', '도', 5, 5, 5, 0, '회전 중 사선이 확보되어도 오차 조건 충족 필요.'],
    ['초기 탄속', '블록/틱', 2.4, 1.25, 1.25, 0, '투석 탄속은 새 제안. 중력·항력과 함께 탄도 검증 필요.'],
    ['오발 최소 오차', '도', 4, 0, 0, 0, '오발 시 추가 각도 오차의 하한.'],
    ['오발 최대 오차', '도', 10, 0, 0, 0, '오발 시 추가 각도 오차의 상한.'],
    ['수직 범위 반경', '블록', 0, 0, 0, 2, '화염 분사구 위아래 범위.'],
    ['최소 공격 주기', '초', .2, .75, .6, '추가 제안. 화염은 최소 휴지 0.1초와 분사 0.5초.'],
    ['기본 탄약 슬롯', '칸', 9, 8, 8, 0, '확정 저장 규격. 투석 연료 슬롯은 별도 1칸.'],
    ['종자유 탱크', 'mB', 0, 0, 0, 9000, '확정 규격. 증폭 유체 탱크는 별도 1000mB.']
];
// Artifact-tool provides this API dynamically; verified by workbook generation.
//noinspection JSUnresolvedFunction
table('기본 수치', 5, ['항목', '단위', '화살', '투석 일반', '투석 심층암', '화염', '정의'], base, 36);
input('기본 수치', 'C6:F30');
// Artifact-tool supplies worksheet proxies at runtime.
//noinspection JSUnresolvedFunction
sheets['기본 수치'].getRange('C15:F15').setNumberFormat('0%');
formula('기본 수치', 'F8', '=F9+F10');
calc('기본 수치', 'F8');
val('기본 수치', 'A33', '생산·시간 환산');
table('기본 수치', 34, ['항목', '단위', '값', '상태', '근거'], [
    ['서버 틱', '틱/초', 20, '계산 기준', '정상 TPS 기준.'], ['수동 배치 씨앗', '개', 10, '기존 구현', 'Vinery 종자유 압착'], ['수동 배치 생산', 'mB', 250, '기존 구현', 'Vinery 종자유 압착'], ['Create 씨앗 생산', 'mB/개', 100, '설치본 확인', 'Create Crafts & Additions 1.6.0 기본 압착'], ['종자유 병', 'mB', 250, '기존 구현', '병 4개는 양동이 1개분.'], ['양동이', 'mB', 1000, '기존 구현', '표준 유체 용기.'], ['석탄 블록 환산', '석탄 개', 9, '제안', '압축 전후 동등 효율.'], ['범위 표시 유지', '초', 15, '제안', '각 포탑별 만료. 재클릭하면 시간 갱신.']
], 32);
input('기본 수치', 'C35:C42', '0');

init('모듈과 증폭', '모듈과 증폭 제안', '수치 출처: 설계 대화와 이번 제안. 모듈 2칸, 칸당 1개, 중복 허용. I 극초반 / II Create / III 이후 공학.', [26, 18, 16, 13, 13, 13, 61]);
const mods = [['공격력', '공통', '비율', .15, .30, .45, '직접 피해·화상·집속 파편 포함.'], ['공격속도', '공통', '비율', .10, .20, .30, '화염은 휴지 시간만 단축.'], ['관통', '화살', '추가 적 수', 1, 2, 3, '다음 관통 피해는 직전의 80%. 특수 효과는 유지.'], ['다중발사', '화살', '추가 표적', 1, 2, 3, '서로 다른 적에게 한 발씩. 탄약 수만큼 발사 제한.'], ['도탄', '화살', '추가 횟수', 1, 2, 3, '관통의 마지막 예정 적중 성공 시 시작. 빗나가면 발생 안 함.'], ['조준속도', '화살', '비율', .30, .60, .90, '회전·표적 추적 속도. 오발 확률은 변경하지 않음.'], ['사거리', '투석', '비율', .15, .30, .45, '실제 탄도로 도달할 수 있어야 발사.'], ['폭발 범위', '투석', '비율', .15, .30, .45, '반경 증가. 피해량 보너스와는 별개.'], ['집속탄', '투석', '파편 수', 2, 4, 6, '본체 폭발 후 파편 산개. 파편 재분열 없음.'], ['사거리', '화염', '블록', .5, 1, 1.5, '아래 방사각 보너스와 한 모듈의 결합 효과.'], ['방사각 부가효과', '화염', '도', 45, 90, 135, '별도 모듈 아님. 화염 사거리 모듈에 포함.'], ['지속 피해', '화염', '비율', .25, .50, .75, '화상만 증가. 공통 공격력과 같은 기준값에 합산.']];
// Artifact-tool provides this API dynamically; verified by workbook generation.
//noinspection JSUnresolvedFunction
table('모듈과 증폭', 5, ['모듈', '대상', '단위', 'I', 'II', 'III', '규칙'], mods, 42);
input('모듈과 증폭', 'D6:F17');
for (const r of [6, 7, 11, 12, 13, 17]) {
    // Artifact-tool supplies worksheet proxies at runtime.
    //noinspection JSUnresolvedFunction
    sheets['모듈과 증폭'].getRange(`D${r}:F${r}`).setNumberFormat('0%');
}
table('모듈과 증폭', 20, ['항목', '대상', '단위', '값', '구분', '상태', '설명'], [
    ['관통 피해 유지율', '화살', '비율', .8, '감쇠', '제안', '관통마다 곱함.'], ['도탄 피해 유지율', '화살', '비율', .75, '감쇠', '제안', '도탄마다 곱함. 동일 적 재도탄 금지 제안.'], ['도탄 탐색 반경', '화살', '블록', 6, '거리', '제안', '일반 블록에 가려진 표적 제외.'], ['집속 파편 피해', '투석', 'HP', 4, '피해', '제안', '공통 공격력 보너스 적용.'], ['집속 파편 반경', '투석', '블록', 1, '반경', '제안', '폭발 범위 모듈 적용 여부는 검토 항목.'], ['화염 최대 각도', '화염', '도', 360, '상한', '확정', '사거리 모듈의 각도 합산 후 제한.']
// Artifact-tool provides this API dynamically; verified by workbook generation.
//noinspection JSUnresolvedFunction
], 36);
input('모듈과 증폭', 'D21:D26');
// Artifact-tool supplies worksheet proxies at runtime.
//noinspection JSUnresolvedFunction
sheets['모듈과 증폭'].getRange('D21:D22').setNumberFormat('0%');
table('모듈과 증폭', 29, ['증폭제', '대상', '피해 보너스', '공속 보너스', '사거리 보너스', '탄속 보너스', '소비 규칙'], [
    ['블레이즈 가루', '투석', 0, .25, 0, 0, '1개로 아래 지정 발수 증폭.'], ['화약', '투석', .25, 0, .20, .15, '1개로 아래 지정 발수 증폭.'], ['용암', '화염', .30, 0, 0, 0, '직접 피해·화상 증가. 분사 중 별도 탱크 소비.']
], 36);
input('모듈과 증폭', 'C30:F32', '0%');
table('모듈과 증폭', 35, ['항목', '대상', '단위', '값', '상태'], [['아이템 증폭 지속', '투석', '발/개', 16, '제안'], ['용암 소비', '화염', 'mB/분사틱', 1, '제안'], ['증폭 유체 탱크', '공통', 'mB', 1000, '확정'], ['증폭 아이템 슬롯', '공통', '칸', 1, '확정'], ['동시 활성 증폭', '공통', '종류', 1, '확정']], 30);
input('모듈과 증폭', 'D36:D40', '0');

init('밸런스 계산', '포탑 밸런스 비교', '전투 수치는 제안. 방어력·사선·실제 명중·광역 대상 수를 제외한 이론 비교. 황색 입력 / 청회색 계산.', [23, 16, 16, 16, 17, 17, 19, 19]);
table('밸런스 계산', 5, ['포탑', '직접 피해', '직격 추가', '주기(초)', '직접 DPS', '직격 DPS', '사거리(블록)', '폭발 반경'], [['화살'], ['투석 일반'], ['투석 심층암'], ['화염']], 35);
table('밸런스 계산', 12, ['포탑', '발사/분사 횟수/분', '탄약/분', '석탄 환산/분', '종자유 mB/분', '증폭제/분', '화상 DPS', '분사당 기름 mB'], [['화살'], ['투석 일반'], ['투석 심층암'], ['화염']], 40);
table('밸런스 계산', 19, ['포탑', '공격력 합산', '공속 합산', '사거리 보너스', '화상 전용 보너스', '각도 추가(도)', '증폭 종류', '방사각 결과'], [['화살', 0, 0, 0, 0, 0, '없음'], ['투석 일반', 0, 0, 0, 0, 0, '없음'], ['투석 심층암', 0, 0, 0, 0, 0, '없음'], ['화염', 0, 0, 0, 0, 0, '없음']], 36);
input('밸런스 계산', 'B20:E23', '0%');
input('밸런스 계산', 'D23', '0.0');
input('밸런스 계산', 'F20:F23', '0');
input('밸런스 계산', 'G20:G23', '@');
// Artifact-tool provides this API dynamically; verified by workbook generation.
//noinspection JSUnresolvedFunction
sheets['밸런스 계산'].getRange('G20').dataValidation = {rule: {type: 'list', values: ['없음']}};
// Artifact-tool provides this API dynamically; verified by workbook generation.
//noinspection JSUnresolvedFunction
sheets['밸런스 계산'].getRange('G21:G22').dataValidation = {rule: {type: 'list', values: ['없음', '블레이즈 가루', '화약']}};
// Artifact-tool provides this API dynamically; verified by workbook generation.
//noinspection JSUnresolvedFunction
sheets['밸런스 계산'].getRange('G23').dataValidation = {rule: {type: 'list', values: ['없음', '용암']}};
val('밸런스 계산', 'A26', '입력 규칙');
val('밸런스 계산', 'A27', '모듈 2칸의 효과를 합산해 입력. 화염 사거리만 블록 단위, 나머지 사거리 보너스는 비율.');
val('밸런스 계산', 'A28', '화살 다중발사·관통·도탄, 투석 집속·범위 모듈은 이 표의 단일 대상 DPS에 포함하지 않음.');
val('밸런스 계산', 'A29', '공격력·증폭·화상 전용 보너스는 기준 화상 피해에 합산. 화상은 동일 포탑 재적중 시 갱신.');
val('밸런스 계산', 'A30', '화염 공속: 분사시간 + 휴지시간/(1+공속). 공격 주기 전체를 나누는 방식과 다름.');
val('밸런스 계산', 'A31', '증폭제/분: 투석은 개/분, 화염은 mB/분. 실제 소비는 잔여 발수와 분사틱으로 처리.');
for (let i = 0; i < 4; i++) {
    const r = 6 + i, c = col(2 + i), t = 20 + i, q = 13 + i;
    const flame = i === 3, stone = i === 1 || i === 2;
    const damageAmp = stone ? `IF(G${t}="화약",'모듈과 증폭'!C31,0)` : flame ? `IF(G${t}="용암",'모듈과 증폭'!C32,0)` : '0';
    const speedAmp = stone ? `IF(G${t}="블레이즈 가루",'모듈과 증폭'!D30,0)` : '0';
    formula('밸런스 계산', `B${r}`, `='기본 수치'!${c}6*(1+B${t}+${damageAmp})`);
    formula('밸런스 계산', `C${r}`, `='기본 수치'!${c}7*(1+B${t}+${damageAmp})`);
    formula('밸런스 계산', `D${r}`, flame ? `=MAX('기본 수치'!F28,'기본 수치'!F9+'기본 수치'!F10/(1+C${t}))` : `=MAX('기본 수치'!${c}28,'기본 수치'!${c}8/(1+C${t}+${speedAmp}))`);
    formula('밸런스 계산', `E${r}`, `=B${r}*'기본 수치'!${c}11/D${r}`);
    formula('밸런스 계산', `F${r}`, `=(B${r}*'기본 수치'!${c}11+C${r})/D${r}`);
    formula('밸런스 계산', `G${r}`, flame ? `='기본 수치'!F12+D${t}` : `='기본 수치'!${c}12*(1+D${t}${stone ? `+IF(G${t}="화약",'모듈과 증폭'!E31,0)` : ''})`);
    formula('밸런스 계산', `H${r}`, `='기본 수치'!${c}13`);
    formula('밸런스 계산', `B${q}`, `=60/D${r}`);
    formula('밸런스 계산', `C${q}`, `=B${q}*'기본 수치'!${c}16`);
    formula('밸런스 계산', `D${q}`, stone ? `=C${q}/'기본 수치'!${c}17` : '=0');
    formula('밸런스 계산', `H${q}`, flame ? `='기본 수치'!F9*'기본 수치'!C35*'기본 수치'!F18` : '=0');
    formula('밸런스 계산', `E${q}`, `=B${q}*H${q}`);
    formula('밸런스 계산', `F${q}`, stone ? `=IF(G${t}="없음",0,B${q}/'모듈과 증폭'!D36)` : flame ? `=IF(G${t}="용암",B${q}*'기본 수치'!F9*'기본 수치'!C35*'모듈과 증폭'!D37,0)` : '=0');
    formula('밸런스 계산', `G${q}`, flame ? `='기본 수치'!F19*(1+B${t}+E${t}+${damageAmp})` : '=0');
    formula('밸런스 계산', `H${t}`, flame ? `=MIN('모듈과 증폭'!D26,'기본 수치'!F14+F${t})` : '=0');
}
calc('밸런스 계산', 'B6:H9');
calc('밸런스 계산', 'B13:H16');
calc('밸런스 계산', 'H20:H23', '0');

init('배치와 소모', '배치와 자원 소모', '예시 배치는 6대. 공격 가능 시간의 비율을 가동률로 입력. 계산은 투사체 빗나감과 대상 사망을 시뮬레이션하지 않음.', [23, 14, 15, 16, 16, 18, 18, 18]);
table('배치와 소모', 5, ['포탑', '대수', '가동률', '전투 시간(초)', '공격 횟수', '탄약 수', '연료 개수 환산', '종자유 mB'], [['화살', 3, .5, 60], ['투석 일반', 2, .5, 60], ['투석 심층암', 0, .5, 60], ['화염', 1, .5, 60]], 36);
input('배치와 소모', 'B6:B9', '0');
input('배치와 소모', 'C6:C9', '0%');
input('배치와 소모', 'D6:D9', '0');
for (let i = 0; i < 4; i++) {
    const r = 6 + i, q = 13 + i;
    formula('배치와 소모', `E${r}`, `=B${r}*C${r}*D${r}/'밸런스 계산'!D${r}`);
    formula('배치와 소모', `F${r}`, `=B${r}*C${r}*D${r}/60*'밸런스 계산'!C${q}`);
    formula('배치와 소모', `G${r}`, `=B${r}*C${r}*D${r}/60*'밸런스 계산'!D${q}`);
    formula('배치와 소모', `H${r}`, `=B${r}*C${r}*D${r}/60*'밸런스 계산'!E${q}`);
}
calc('배치와 소모', 'E6:H9');
val('배치와 소모', 'A11', '합계');
for (const c of ['B', 'E', 'F', 'G', 'H']) formula('배치와 소모', `${c}11`, `=SUM(${c}6:${c}9)`);
calc('배치와 소모', 'B11:H11');
table('배치와 소모', 14, ['종자유 보급 지표', '수량', '단위', '정의'], [
    ['필요 종자유', null, 'mB', '전투 가동률과 화염 포탑 수를 반영.'], ['수동 압착 배치', null, '회', '필요량을 올림하여 병 단위로 생산.'], ['수동 씨앗', null, '개', '배치당 씨앗 소모.'], ['Create 씨앗', null, '개', '기본 압착 레시피 기준 올림.'], ['수동 생산 후 잔여', null, 'mB', '필요량을 넘긴 병 단위 잔량.'], ['화염 탱크 1개 지속', null, '초', '한 포탑이 100% 가동할 때.'], ['250mB병 완전 분사', null, '회', '일부 분사분은 제외.'], ['250mB병 잔여 기름', null, 'mB', '완전 분사 횟수 계산 후 잔량.']
], 36);
formula('배치와 소모', 'B15', '=H11');
formula('배치와 소모', 'B16', "=ROUNDUP(B15/'기본 수치'!C37,0)");
formula('배치와 소모', 'B17', "=B16*'기본 수치'!C36");
formula('배치와 소모', 'B18', "=ROUNDUP(B15/'기본 수치'!C38,0)");
formula('배치와 소모', 'B19', "=B16*'기본 수치'!C37-B15");
formula('배치와 소모', 'B20', "='기본 수치'!F30/'밸런스 계산'!E16*60");
formula('배치와 소모', 'B21', "=INT('기본 수치'!C39/'밸런스 계산'!H16)");
formula('배치와 소모', 'B22', "='기본 수치'!C39-B21*'밸런스 계산'!H16");
calc('배치와 소모', 'B15:B22');
val('배치와 소모', 'A25', '공격 횟수는 장기 평균치. 전투 시작 시 선장전·틱 반올림으로 실제 횟수는 달라질 수 있음.');
val('배치와 소모', 'A26', '증폭 사용 시 밸런스 계산 탭에서 선택. 다중발사 탄약 증가는 이 배치 표에 포함하지 않음.');

init('제작법 제안', '극초반 제작법 제안', '전부 신규 제안. 게임 제작법 미변경. .은 빈 칸. 재료는 표기된 결과 수를 얻는 1회 제작 기준.', [24, 12, 16, 14, 14, 14, 63, 47]);
const recipes = [
    ['화살 포탑', 1, '작업대', 'SBS', 'IRI', 'CCC', 'S 막대기×2 / B 활×1 / I 철 주괴×2 / R 레드스톤×1 / C 조약돌×3', '단일 표적 주력. 기존 기초 포탑을 대체.'],
    ['투석 포탑', 1, '작업대', 'SLS', 'IPI', 'CCC', 'S 실×2 / L 원목×1 / I 철 주괴×2 / P 피스톤×1 / C 조약돌×3', '피스톤 제작비 포함 실질 철 3개.'],
    ['화염 포탑', 1, '작업대', 'ICI', 'BFB', 'CRC', 'I 철 주괴×2 / C 구리 주괴×3 / B 양동이×2 / F 화로×1 / R 레드스톤×1', '양동이 포함 실질 철 8개. 근접 광역의 진입 비용.'],
    ['방어 분석기', 1, '작업대', '.C.', '.R.', '.I.', 'C 구리 주괴×1 / R 레드스톤×1 / I 철 주괴×1', '소모되지 않음. 각 포탑 우클릭 시 제안 15초 표시.'],
    ['모듈 기판', 4, '작업대', 'CIC', '.R.', 'CIC', 'C 구리 주괴×4 / I 철 주괴×2 / R 레드스톤×1', '공통 부품을 4개 생산. 신설 아이템 제안.'],
    ['공격력 I', 1, '무형 조합', '', '', '', '모듈 기판×1 / 철 주괴×2 / 부싯돌×2', '공격 부품 강화.'],
    ['공격속도 I', 1, '무형 조합', '', '', '', '모듈 기판×1 / 구리 주괴×2 / 레드스톤×2', '극초반에는 기름을 제작 재료로 요구하지 않음.'],
    ['관통 I', 1, '무형 조합', '', '', '', '모듈 기판×1 / 철 주괴×1 / 부싯돌×4', '화살 전용.'],
    ['다중발사 I', 1, '무형 조합', '', '', '', '모듈 기판×1 / 실×2 / 일반 화살×4', '화살 전용. 소모 탄약 증가가 실제 비용.'],
    ['도탄 I', 1, '무형 조합', '', '', '', '모듈 기판×1 / 슬라임볼×1 / 철 주괴×2', '슬라임 확보가 탐험 제약. 대체 재료 여부 검토.'],
    ['조준속도 I', 1, '무형 조합', '', '', '', '모듈 기판×1 / 레드스톤×2 / 실×2', '네더 재료를 요구하지 않음.'],
    ['투석 사거리 I', 1, '무형 조합', '', '', '', '모듈 기판×1 / 실×2 / 레드스톤×1', '장력·발사 제어 강화.'],
    ['폭발 범위 I', 1, '무형 조합', '', '', '', '모듈 기판×1 / 화약×2 / 석탄 또는 목탄×2', '네더 이전 몹 드롭으로 제작 가능.'],
    ['집속탄 I', 1, '무형 조합', '', '', '', '모듈 기판×1 / 화약×2 / 부싯돌×2', '본체 폭발 후 파편 폭발.'],
    ['화염 사거리 I', 1, '무형 조합', '', '', '', '모듈 기판×1 / 구리 주괴×2 / 유리×2', '사거리와 방사각을 함께 증가.'],
    ['지속 피해 I', 1, '무형 조합', '', '', '', '모듈 기판×1 / 석탄 또는 목탄×2 / 화약×1', '화상 피해 강화.'],
    ['모듈 II 공통안', 1, 'Create 단계', '', '', '', '동종 모듈 I×1 + Create 가공 부품', '등급·단계만 확정. 정확한 재료와 제작 장치는 추후 결정.'],
    ['모듈 III 공통안', 1, '이후 공학', '', '', '', '동종 모듈 II×1 + 이후 공학 부품', '현재 구현 범위에서 정확한 레시피를 확정하지 않음.']
];
table('제작법 제안', 5, ['결과', '결과 수', '방식·단계', '상단', '중단', '하단', '재료와 수량', '의도·검토'], recipes, 60);
val('제작법 제안', 'A26', '재료 선택 규칙');
table('제작법 제안', 27, ['구분', '표현', '범위', '상태'], [
    ['나무', '원목', '수종은 태그로 허용. 가공 판자와 구분.', '제안'], ['석재 탄약', '일반 돌 태그', '돌·조약돌·화강암·섬록암·안산암·응회암·방해석·흑암·현무암·네더랙·엔드 돌. 가공품 제외.', '확정 범위'], ['석재 탄약', '심층암 태그', '심층암·심층암 조약돌. 벽돌·반블록·계단 제외.', '확정'], ['연료', '석탄·목탄', '동일 효율. 석탄 블록은 9개분. 다른 압축 연료는 지원 태그 추가 시 환산 지정.', '확정 / 환산 제안'], ['기름', 'Create 종자유', '포탑·모듈 제작에는 미사용. 화염 연료로만 사용.', '확정']
], 65);

init('확정 규칙', '포탑 시스템 확정 규칙', '출처: 사용자 77개 답변 및 후속 13개 답변. 수치 제안과 달리 아래 동작 규칙은 확정 사항.', [18, 27, 91]);
const rules = [
    ['구조', '포탑 종류', '화살·투석·화염 3종. 모두 Create 이전 제작 가능. 다음 티어 업그레이드는 추후, 자체 레벨 없음.'],
    ['구조', '모듈', '공통 슬롯 2칸. 칸당 1개, 같은 모듈 중복 가능. 상위 등급 존재. 보너스 합산.'],
    ['구조', '회수', '포탑 아이템에 탄약·모듈·유체·설정 보존. 재설치로 공격 대기시간 우회 금지.'],
    ['구조', '설치', '디펜스 차원 내부만. 내부가 성벽 안쪽을 뜻하는지는 검토 항목에 별도 표시.'],
    ['전투', '대상', '웨이브 적·관리자 소환 적·더미. 일반 적대 몹은 기본 대상에 포함하지 않음.'],
    ['전투', '우선순위', 'UI에서 선택. 기본 성벽에 가장 가까운 적. 거리·체력 우선순위 지원.'],
    ['전투', '표적 유지', '죽거나 유효 조건을 벗어날 때까지 유지 / 공격마다 재선정 중 UI 선택.'],
    ['전투', '독립성', '포탑별 독립 공격. 다른 포탑의 예상 피해로 표적을 예약하지 않음.'],
    ['전투', '피해 판정', '방어력·보호 효과 적용. 적끼리 피해 없음. 플레이어·아군 피해 없음 및 투사체 통과.'],
    ['전투', '환경', '지형·건물·포탑 파괴와 불 번짐 없음. 실제 공격 경로로 타격 가능 여부 확인.'],
    ['전투', '레드스톤', '항상 / 신호 있을 때 / 신호 없을 때 작동을 UI에서 선택.'],
    ['전투', '준비', '화살·투석은 표적이 없어도 미리 장전. 화염은 공격할 때만 기름 소비.'],
    ['모듈', '교체', '전투 중 교체 가능. 교체 시 진행 중 장전 초기화. 발사체는 발사 시점 능력치 유지.'],
    ['모듈', '단계', 'I 극초반, II Create, III 이후 공학. 모듈 조합 제한 없음.'],
    ['화살', '탄약', '일반·물약·분광 화살. 모든 바닐라 효과와 회복 화살의 언데드 특성 유지.'],
    ['화살', '탄약 순서', '슬롯 순서 소비. 한 번의 다중발사에 서로 다른 화살을 섞어 사용 가능.'],
    ['화살', '다중발사', '동시에 서로 다른 적에게 한 발씩. 적 1체면 1발. 실제 발사 수만큼 탄약 소비.'],
    ['화살', '관통', '몬스터 추가 관통. 블록·방어력 관통 아님. 관통 시 직접 피해 감소, 특수 효과 유지.'],
    ['화살', '도탄 결합', '관통의 마지막 예정 적중에서만 도탄 시작. 그 적중이 빗나가면 도탄 없음. 도탄은 관통 횟수 재획득 안 함.'],
    ['화살', '오발', '개별 화살의 추가 오차. 다른 적에 맞으면 정상 피해·특수 효과. 발사 화살 회수 불가.'],
    ['화살', '추적', '이동 예측, 유도 없음. 조준속도 모듈은 오발 확률을 바꾸지 않음.'],
    ['투석', '재료', '돌 8칸, 연료 1칸. 일반 돌은 태그로 지정. 가공 석재·계단·반블록 제외.'],
    ['투석', '심층암', '일반 돌보다 피해·폭발 반경 증가, 사거리 감소.'],
    ['투석', '탄도', '느린 포물선. 빠른 적도 발사 대상이 될 수 있으나 피할 수 있음. 최소 사거리 없음.'],
    ['투석', '폭발', '거리별 피해 감소, 직격 추가 피해, 일반 블록 차폐.'],
    ['투석', '집속탄', '착탄·본체 폭발 후 파편이 튀어 작은 폭발 발생.'],
    ['증폭', '활성 종류', '동시에 한 종류만 사용. 아이템 슬롯 최대 1칸, 유체 탱크 최대 1000mB 1개.'],
    ['증폭', '투석 소비', '발수 기준. 블레이즈 가루 공속 / 화약 피해·탄속·사거리 증가.'],
    ['증폭', '고갈', '기본 공격으로 전환. 증폭제 없으면 정지 옵션 제공. 사용하던 증폭 고갈은 외부 표시.'],
    ['화염', '형태', '적 방향으로 즉시 향하는 부채꼴. 짧게 분사하고 휴지. 방사각 상한 360도.'],
    ['화염', '기름', '종자유 9000mB. 실제 분사 시간에 비례해 소비. 용기 투입·회수 슬롯 제공.'],
    ['화염', '화상', '직접 분사는 각각 적용. 서로 다른 포탑 화상 중첩, 같은 포탑은 지속시간 갱신.'],
    ['화염', '보너스', '공통 공격력은 직접 피해·화상·집속 파편에 적용. 지속 피해 모듈은 화상만 강화.'],
    ['화염', '공속', '분사 시간 유지, 휴지 시간 단축. 초당 연료 소비 증가.'],
    ['화염', '면역', '불 면역·화염 저항 대상은 직접 분사와 화상 모두 피해 없음.'],
    ['화염', '용암', '별도 탱크, 분사 중 함께 소비. 없으면 일반 공격 또는 설정에 따라 정지.'],
    ['UI', '조작', '우클릭 UI, 웅크려 우클릭 빠른 투입. 모든 플레이어 조작 가능.'],
    ['UI', '내용', '운용·모듈·설정 탭. 실효 능력치와 계산 내역, 현재 표적, 장전·분사·정지 이유, 이론 및 최근 실제 DPS.'],
    ['UI', '외부 표시', '필수 자원 부족, 사용하던 증폭제 고갈을 UI 없이 식별. 색상과 원인 아이콘 병행.'],
    ['UI', '범위 도구', '도구로 우클릭한 포탑만 일정 시간 표시. 여러 포탑을 클릭하면 각각 독립적으로 표시.'],
    ['자동화', '기본 상태', '모든 면 차단. UI에서 면마다 입력·출력·양방향·차단 지정.'],
    ['자동화', '출력 제한', '빈 병·빈 양동이·부산물만 자동 출력. 원료·탄약·연료·모듈 자동 추출 금지.'],
    ['자동화', '유체', '종자유·용암 파이프 입력만 허용. 직접 유체 추출 금지. UI 수동 회수 지원.'],
    ['자동화', '목표 재고', '별도 아이템 필터 없음. 품목 역할별 입력 목표량까지 받는 기능 제공.'],
    ['자동화', '용기', '유리병·양동이 자동 비우기. 출력 칸이 가득 차면 비우기만 정지, 기존 연료로 공격 계속.'],
    ['자동화', '부분 주입', '용기는 전량 수용 가능할 때만 비움. 파이프는 남은 공간만큼 입력.'],
    ['이전', '기존 포탑', '기존 기초 포탑을 새 화살 포탑으로 전환. 기존 블록 ID·탄약 유지.']
];
table('확정 규칙', 5, ['분류', '항목', '확정 내용'], rules, 55);

init('검토 항목', '추가 제안과 검증 항목', '확정되지 않은 수치와 구현 시 확인할 경계를 분리. 게임 코드 및 제작법 변경 작업은 이 파일에 포함되지 않음.', [24, 42, 64, 22]);
table('검토 항목', 5, ['항목', '제안 또는 확인 내용', '이유·검증 방법', '상태'], [
    ['기본 수치 승인', '전투 수치·모듈 등급값·증폭 효율·제작법', '사용자는 설계 규칙을 확정했으나 모든 숫자에 별도 승인한 것은 아님.', '검토 요청'],
    ['범위 표시 시간', '포탑별 15초, 재클릭 시 연장', '도구를 놓아도 타이머 동안 유지. 여러 선택은 각각 만료.', '추가 제안'],
    ['투석 탄도', '초기 탄속 1.25블록/틱', '중력·항력을 포함해 24블록 도달, 화약 증폭, 근접 발사, 천장 차폐 검증.', '실측 필요'],
    ['최소 공격 주기', '화살 0.2초 / 투석 0.75초 / 화염 0.6초', '과도한 공속에서 틱 한계와 피해 판정 횟수 보호.', '추가 제안'],
    ['폭발 감쇠', '중심 최대, 반경 끝 0인 선형 감소', '거리별 피해 수치는 아직 확정되지 않음.', '추가 제안'],
    ['집속 파편 산개', '반경 2~4블록 착탄, 파편 재분열 없음', '자기 중심 재중첩으로 과도한 단일 대상 피해가 나지 않는지 측정.', '추가 제안'],
    ['집속 범위 모듈', '폭발 범위 모듈은 본체·파편 반경 모두 적용 제안', '공통 공격력 적용은 확정. 반경 적용 대상은 별도 확정 필요.', '검토 요청'],
    ['디펜스 차원 내부', '성벽 안쪽만 설치·운용으로 해석 제안', '다른 차원 금지는 확정. 성벽 바깥도 금지인지 문구 확인 필요.', '검토 요청'],
    ['최근 실제 DPS', '최근 10초간 실제 HP 피해 합계 / 10초', '오버킬 제외, 화상·파편은 소유 포탑으로 귀속.', '추가 제안'],
    ['정지 표기', '자원 부족·신호 정지·사선 차단을 원인별 표시', '표적 없음은 정상 대기. 증폭을 사용하지 않은 포탑에는 고갈 경고 없음.', '표현 제안'],
    ['도탄 마지막 적중', '관통 횟수를 모두 소모할 예정인 적중에서만 발생', '앞 적중 후 비행 종료·블록 충돌·대상 부족은 마지막 적중으로 소급하지 않음.', '확정 해석'],
    ['화염 기름 부담', '기본 20mB/분사, 병당 완전 분사 12회', '6대 중 화염 1대가 60초의 50% 가동 시 400mB 소모. 수동 씨앗 공급 부담 검증.', '밸런스 검토'],
    ['제작 재료 접근성', '슬라임·화약은 탐험/몹 드롭 필요', '도탄 I 획득이 과도하게 지연되면 슬라임볼 대체 제작 검토.', '밸런스 검토'],
    ['동시 피격', '다중 화살·여러 포탑 직접 화염 피해를 각각 반영', '바닐라 피격 무적시간 때문에 의도한 피해가 누락되지 않는지 테스트.', '구현 검증'],
    ['경제 보존', '용기 반환·모듈 회수·연료 잔여 발수 저장', '회수/재설치·청크 재로드·동시 UI·호퍼 처리 중 복제 및 소실 검증.', '구현 검증'],
    ['엑셀 계산 범위', '기본 및 수치 보너스 비교, 자원 장기 평균', '실제 명중률·범위 내 적 수·비행시간·피격 무적·틱 반올림을 포함한 시뮬레이터는 아님.', '계산 정의']
], 68);

// Place the long recipe scope descriptions in the already-wide columns.
// Artifact-tool provides this API dynamically; verified by workbook generation.
//noinspection JSUnresolvedFunction
const recipeScope = sheets['제작법 제안'].getRange('A27:D32').values;
// Artifact-tool provides this API dynamically; verified by workbook generation.
//noinspection JSUnresolvedFunction
sheets['제작법 제안'].getRange('A27:H32').clear({applyTo: 'all'});
table('제작법 제안', 27, ['구분', '표현', '', '', '', '', '범위', '상태'], recipeScope.slice(1).map(r => [r[0], r[1], '', '', '', '', r[2], r[3]]), 60);
// Artifact-tool provides this API dynamically; verified by workbook generation.
//noinspection JSUnresolvedFunction
sheets['제작법 제안'].getRange('C27:F32').clear({applyTo: 'all'});
// Verify meaningful recalculation, restore inputs, then inspect and export.
wb.recalculate();
// Artifact-tool provides this API dynamically; verified by workbook generation.
//noinspection JSUnresolvedFunction
const get = (n, a) => sheets[n].getRange(a).values[0][0];

function near(n, a, v) {
    const actual = get(n, a);
    if (typeof actual !== 'number' || Math.abs(actual - v) > 1e-6) throw new Error(`${n}!${a}: ${actual} != ${v}`);
}

near('밸런스 계산', 'E6', 5);
near('밸런스 계산', 'E9', 8 / 1.5);
near('배치와 소모', 'H11', 400);
near('배치와 소모', 'B17', 20);
near('배치와 소모', 'B18', 4);
val('밸런스 계산', 'C23', .6);
wb.recalculate();
near('밸런스 계산', 'D9', 1.125);
near('밸런스 계산', 'E16', 20 * 60 / 1.125);
val('밸런스 계산', 'C23', 0);
val('밸런스 계산', 'G21', '화약');
wb.recalculate();
near('밸런스 계산', 'B7', 17.5);
near('밸런스 계산', 'G7', 28.8);
val('밸런스 계산', 'G21', '없음');
val('배치와 소모', 'B9', 0);
wb.recalculate();
near('배치와 소모', 'H11', 0);
val('배치와 소모', 'B9', 1);
wb.recalculate();
const errors = await wb.inspect({
    kind: 'match',
    searchTerm: '#REF!|#DIV/0!|#VALUE!|#NAME\\?|#N/A|#NUM!|#NULL!',
    options: {useRegex: true, maxResults: 30},
    maxChars: 1500
});
console.log(errors.ndjson);
// Artifact-tool provides this API dynamically; verified by workbook generation.
//noinspection JSUnresolvedVariable
console.log((await wb.inspect({
    kind: 'region',
    sheetId: '밸런스 계산',
    range: 'A5:H9',
    maxChars: 2200,
    tableMaxRows: 6,
    tableMaxCols: 8
})).ndjson);
await fs.mkdir(dir + '/previews', {recursive: true});
for (const n of names) {
    const end = n === '밸런스 계산' ? 'H23' : n === '기본 수치' ? 'G18' : n === '모듈과 증폭' ? 'G17' : n === '제작법 제안' ? 'H12' : n === '배치와 소모' ? 'H22' : n === '확정 규칙' ? 'C16' : 'D13';
    const p = await wb.render({sheetName: n, range: `A1:${end}`, scale: 1, format: 'png'});
    await fs.writeFile(dir + `/previews/${n}.png`, new Uint8Array(await p.arrayBuffer()));
}
const out = await SpreadsheetFile.exportXlsx(wb);
await out.save(dir + '/포탑_밸런스_제작법_제안.xlsx');
console.log('SAVED ' + dir + '/포탑_밸런스_제작법_제안.xlsx');
for (const [n, range] of [['기본 수치', 'A33:G42'], ['모듈과 증폭', 'A20:G40'], ['제작법 제안', 'A24:H32'], ['확정 규칙', 'A35:C52'], ['검토 항목', 'A14:D21']]) {
    const p = await wb.render({sheetName: n, range, scale: 1, format: 'png'});
    await fs.writeFile(dir + `/previews/${n}_상세.png`, new Uint8Array(await p.arrayBuffer()));
}
