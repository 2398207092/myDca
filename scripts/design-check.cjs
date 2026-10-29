#!/usr/bin/env node
/**
 * design-check · 设计系统门禁
 *
 * 目的：让「绕开设计系统」这件事会报错，而不是靠自觉。
 * 真相源：myPhonePro/stitch_fund_dividend_tracker/tailwind.config.js
 * 基准：design/00-基准.md（意图与禁令）
 *
 * 用法：
 *   node scripts/design-check.cjs            门禁模式：超出基线则退出码 1
 *   node scripts/design-check.cjs --report   只报告，不判失败
 *   node scripts/design-check.cjs --update   把当前值写为新基线（只许调低，不许调高）
 *
 * 分级：
 *   error  字号 / 颜色 ——— 直接决定视觉一致性，硬拦
 *   warn   尺寸 / 间距 ——— 存在 w-[3px] 这类无 token 可表达的微调，只统计不阻断
 */

const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const SRC = path.join(ROOT, 'myPhonePro', 'stitch_fund_dividend_tracker', 'src');
const BASELINE_FILE = path.join(ROOT, 'design', 'baseline.json');

const RULES = [
  {
    id: 'FONT-ARBITRARY',
    level: 'error',
    label: '任意值字号',
    re: /text-\[[\d.]+px\]/g,
    hint: '改用 9 档 token：text-xs(12) sm(13) base(14) md(15) lg(17) xl(21) 2xl(25) 3xl(33) 4xl(38)',
  },
  {
    id: 'COLOR-ARBITRARY',
    level: 'error',
    label: '任意值颜色',
    re: /(?:bg|text|border|ring|from|to|via|fill|stroke|shadow)-\[#[0-9a-fA-F]{3,8}\]/g,
    hint: '改用语义色：brand / pos / neg / gold / flat / text-primary / page-bg / card-bg',
  },
  {
    id: 'COLOR-RAW-HEX',
    level: 'error',
    label: 'style 块硬编码 hex',
    re: /#[0-9a-fA-F]{6}\b/g,
    inStyleOnly: true,
    hint: '提到 tailwind.config.js 里定义，或改用 theme() / CSS 变量',
  },
  {
    id: 'SPACE-ARBITRARY',
    level: 'warn',
    label: '任意值尺寸/间距',
    re: /(?:^|[\s"'`(])(?:p|px|py|pt|pb|pl|pr|m|mx|my|mt|mb|ml|mr|gap|gap-x|gap-y|w|h|min-w|min-h|max-w|max-h|top|bottom|left|right|inset|translate-x|translate-y)-\[[\d.]+px\]/g,
    hint: '优先用 8pt 间距：xs4 sm8 md12 lg16 xl20 2xl24 3xl32 section32',
  },
];

function walk(dir, out = []) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    const st = fs.statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if (name.endsWith('.vue')) out.push(full);
  }
  return out;
}

// 标记每一行是否落在 <style> 块内（用于 COLOR-RAW-HEX）
function styleLineMask(lines) {
  const mask = new Array(lines.length).fill(false);
  let inside = false;
  for (let i = 0; i < lines.length; i++) {
    if (/<style[\s>]/.test(lines[i])) inside = true;
    mask[i] = inside;
    if (/<\/style>/.test(lines[i])) inside = false;
  }
  return mask;
}

function scan() {
  const files = walk(SRC);
  const hits = [];
  for (const file of files) {
    const text = fs.readFileSync(file, 'utf8');
    const lines = text.split(/\r?\n/);
    const mask = styleLineMask(lines);
    for (const rule of RULES) {
      lines.forEach((line, idx) => {
        if (rule.inStyleOnly && !mask[idx]) return;
        const matches = line.match(rule.re);
        if (!matches) return;
        for (const m of matches) {
          hits.push({
            rule: rule.id,
            level: rule.level,
            label: rule.label,
            file: path.relative(ROOT, file).replace(/\\/g, '/'),
            line: idx + 1,
            text: m.trim(),
          });
        }
      });
    }
  }
  return { hits, fileCount: files.length };
}

function summarize(hits) {
  const byRule = {};
  for (const h of hits) {
    byRule[h.rule] = byRule[h.rule] || { label: h.label, level: h.level, count: 0, samples: [] };
    byRule[h.rule].count++;
    if (byRule[h.rule].samples.length < 6) byRule[h.rule].samples.push(h);
  }
  const error = hits.filter((h) => h.level === 'error').length;
  const warn = hits.filter((h) => h.level === 'warn').length;
  return { byRule, error, warn };
}

// 文档漂移检测：00-基准.md 里出现的 hex，必须在 tailwind.config.js 里真实存在。
// 防止基准文档写下代码里不存在的色值，变成第二个真相源（上一份设计文档就是这么死的）。
function docDriftHits() {
  const doc = path.join(ROOT, 'design', '00-基准.md');
  const cfg = path.join(ROOT, 'myPhonePro', 'stitch_fund_dividend_tracker', 'tailwind.config.js');
  if (!fs.existsSync(doc) || !fs.existsSync(cfg)) return [];
  const docText = fs.readFileSync(doc, 'utf8');
  const cfgText = fs.readFileSync(cfg, 'utf8').toLowerCase();
  const seen = new Set();
  const hits = [];
  const re = /#[0-9a-fA-F]{6}\b/g;
  let m;
  while ((m = re.exec(docText)) !== null) {
    const hex = m[0].toLowerCase();
    if (seen.has(hex)) continue;
    seen.add(hex);
    if (!cfgText.includes(hex)) {
      hits.push({
        rule: 'DOC-HEX-DRIFT',
        level: 'error',
        label: '文档色值代码里不存在',
        file: 'design/00-基准.md',
        line: docText.slice(0, m.index).split(/\r?\n/).length,
        text: m[0],
      });
    }
  }
  return hits;
}

function main() {
  const args = process.argv.slice(2);
  const reportOnly = args.includes('--report');
  const update = args.includes('--update');

  if (!fs.existsSync(SRC)) {
    console.error(`找不到前端源码目录：${SRC}`);
    process.exit(2);
  }

  const { hits, fileCount } = scan();
  const drift = docDriftHits();
  if (drift.length) hits.push(...drift);
  const { byRule, error, warn } = summarize(hits);

  console.log('');
  console.log('########## 设计门禁 design-check ##########');
  console.log(`扫描 ${fileCount} 个 .vue 文件`);
  console.log('');

  for (const [id, r] of Object.entries(byRule)) {
    const mark = r.level === 'error' ? '✗' : '·';
    console.log(`${mark} ${r.label}（${id}）：${r.count} 处`);
    for (const s of r.samples) {
      console.log(`    ${s.file}:${s.line}  ${s.text}`);
    }
    if (r.count > r.samples.length) {
      console.log(`    ……另有 ${r.count - r.samples.length} 处`);
    }
    const rule = RULES.find((x) => x.id === id);
    if (rule) console.log(`    建议：${rule.hint}`);
    console.log('');
  }

  console.log(`合计：error ${error} 处 ｜ warn ${warn} 处`);

  let baseline = null;
  if (fs.existsSync(BASELINE_FILE)) {
    try {
      baseline = JSON.parse(fs.readFileSync(BASELINE_FILE, 'utf8'));
    } catch (e) {
      console.error(`基线文件损坏：${BASELINE_FILE}`);
    }
  }

  if (update) {
    if (baseline && error > baseline.error) {
      console.error('');
      console.error(`拒绝更新：当前 error ${error} 处，高于基线 ${baseline.error} 处。`);
      console.error('棘轮只允许往下调。要放宽基线，必须先在 design/01-决策.md 记一条决策并说明理由。');
      process.exit(1);
    }
    fs.mkdirSync(path.dirname(BASELINE_FILE), { recursive: true });
    fs.writeFileSync(
      BASELINE_FILE,
      JSON.stringify(
        { updatedAt: new Date().toISOString().slice(0, 10), error, warn, byRule: Object.fromEntries(Object.entries(byRule).map(([k, v]) => [k, v.count])) },
        null,
        2
      ) + '\n',
      'utf8'
    );
    console.log(`基线已更新：error ${error} ｜ warn ${warn}`);
    console.log('');
    return;
  }

  if (!baseline) {
    console.log('');
    console.log('尚未建立基线。执行 node scripts/design-check.cjs --update 生成初始基线。');
    console.log('');
    process.exit(reportOnly ? 0 : 1);
  }

  const delta = error - baseline.error;
  console.log(`基线（${baseline.updatedAt}）：error ${baseline.error} 处 ｜ warn ${baseline.warn} 处`);

  if (delta > 0) {
    console.log('');
    console.log(`✗ 门禁失败：新增 ${delta} 处违规。`);
    console.log('  要么改回 token，要么在 design/01-决策.md 记一条决策说明为什么必须放宽。');
    console.log('');
    process.exit(1);
  }

  if (delta < 0) {
    console.log('');
    console.log(`↓ 比基线少 ${-delta} 处。执行 node scripts/design-check.cjs --update 把新低点固化为基线。`);
  }

  console.log('');
  console.log(reportOnly ? '（报告模式，不判失败）' : '✓ 门禁通过');
  console.log('');
  process.exit(0);
}

main();
