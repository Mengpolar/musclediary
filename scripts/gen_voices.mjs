// 批量合成训练语音文件 → app/src/main/assets/voices/default/
// 用法: node scripts/gen_voices.mjs
import fs from 'node:fs';
import path from 'node:path';

const API = 'https://api.senseaudio.cn/v1/t2a_v2';
const KEY = 'sk-g0otwxlVp8h60TCzQYhh0TdmPt2JFAdaCb2985DfE6Cb4aC5BbE50cB343B5A9E5';
const MODEL = 'sensenova-tts-2.0';
const VOICE = 'firefly7';
const OUT = 'app/src/main/assets/voices/default';

// —— 固定短语 ——
const phrases = {
  start: '训练开始，状态拉满',
  finish: '训练完成，今天辛苦了',
  pause: '训练暂停',
  resume: '继续训练',
  next: '下一个动作',
  rest: '做得好，休息一下',
  rest_over: '休息结束，准备继续',
  di: '第',
  set_word: '组',
  hai_you: '还有',
  rep_word: '个',
  sec_word: '秒',
  jiayou: '加油！',
  hold_on: '再坚持一下！',
  great_1: '做得很棒！',
  great_2: '就是这个节奏！',
  great_3: '漂亮的完成！',
  ding: '叮',
};

// —— 数字 ——
const digits = {
  0: '零', 1: '一', 2: '二', 3: '三', 4: '四', 5: '五', 6: '六', 7: '七',
  8: '八', 9: '九', 10: '十', 11: '十一', 12: '十二', 13: '十三', 14: '十四',
  15: '十五', 16: '十六', 17: '十七', 18: '十八', 19: '十九', 20: '二十',
  30: '三十', 40: '四十', 50: '五十', 60: '六十', 70: '七十', 80: '八十',
  90: '九十', 100: '一百',
};

// —— 动作名（与内置模板一一对应） ——
const exercises = {
  ex_pushup: '俯卧撑',
  ex_situp: '仰卧起坐',
  ex_crunch: '卷腹',
  ex_squat: '深蹲',
  ex_lunge: '弓步蹲',
  ex_pullup: '引体向上',
  ex_dip: '双杠臂屈伸',
  ex_db_bench_press: '哑铃卧推',
  ex_db_fly: '哑铃飞鸟',
  ex_db_shoulder_press: '哑铃推肩',
  ex_db_lateral_raise: '哑铃侧平举',
  ex_db_curl: '哑铃弯举',
  ex_db_row: '哑铃划船',
  ex_db_overhead_ext: '哑铃颈后臂屈伸',
  ex_glute_bridge: '臀桥',
  ex_back_ext: '山羊挺身',
  ex_burpee: '波比跳',
  ex_walk: '步行',
  ex_run: '跑步',
  ex_cycle: '骑行',
  ex_plank: '平板支撑',
  ex_jump_rope: '跳绳',
  ex_swim: '游泳',
  // 新增 35 个
  ex_sicilian_crunch: '西西里卷腹',
  ex_russian_twist: '俄罗斯转体',
  ex_leg_raise: '仰卧抬腿',
  ex_side_plank: '侧平板支撑',
  ex_dead_bug: '死虫式',
  ex_mountain_climber: '登山跑',
  ex_superman: '超人式',
  ex_wide_pushup: '宽距俯卧撑',
  ex_close_pushup: '窄距俯卧撑',
  ex_incline_pushup: '上斜俯卧撑',
  ex_decline_pushup: '下斜俯卧撑',
  ex_diamond_pushup: '钻石俯卧撑',
  ex_pike_pushup: '派克俯卧撑',
  ex_handstand_pushup: '倒立撑',
  ex_chinup: '反手引体向上',
  ex_wide_pullup: '宽距引体向上',
  ex_aussie_row: '澳式划船',
  ex_narrow_dip: '窄距双杠臂屈伸',
  ex_squat_jump: '深蹲跳',
  ex_bulgarian_split_squat: '保加利亚分腿蹲',
  ex_wall_sit: '靠墙静蹲',
  ex_calf_raise: '站姿提踵',
  ex_jumping_jack: '开合跳',
  ex_high_knees: '高抬腿',
  ex_db_squat: '哑铃深蹲',
  ex_db_lunge: '哑铃箭步蹲',
  ex_db_deadlift: '哑铃硬拉',
  ex_db_shrug: '哑铃耸肩',
  ex_db_rear_fly: '哑铃俯身飞鸟',
  ex_db_incline_press: '哑铃上斜卧推',
  ex_db_hammer_curl: '哑铃锤式弯举',
  ex_single_glute_bridge: '单腿臀桥',
  ex_hip_thrust: '臀冲',
  ex_reverse_crunch: '反向卷腹',
  ex_flutter_kick: '仰卧交替踢腿',
};

const all = {
  ...Object.fromEntries(Object.entries(phrases).map(([k, v]) => [k, v])),
  ...Object.fromEntries(Object.entries(digits).map(([k, v]) => [`n_${k}`, v])),
  ...exercises,
};

async function synth(fileKey, text, retries = 3) {
  const outPath = path.join(OUT, `${fileKey}.mp3`);
  if (fs.existsSync(outPath) && fs.statSync(outPath).size > 500) {
    return `skip ${fileKey}`;
  }
  for (let i = 0; i < retries; i++) {
    try {
      const res = await fetch(API, {
        method: 'POST',
        headers: { Authorization: KEY, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          model: MODEL,
          voice_setting: { voice_id: VOICE },
          text,
        }),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      if (json.base_resp?.status_code !== 0) {
        throw new Error(`API ${json.base_resp?.status_code}: ${json.base_resp?.status_msg}`);
      }
      const buf = Buffer.from(json.data.audio, 'hex');
      if (buf.length < 500) throw new Error(`audio too small: ${buf.length}`);
      fs.writeFileSync(outPath, buf);
      return `ok   ${fileKey} (${(buf.length / 1024).toFixed(1)}KB) "${text}"`;
    } catch (e) {
      if (i === retries - 1) return `FAIL ${fileKey}: ${e.message}`;
      await new Promise(r => setTimeout(r, 2000 * (i + 1)));
    }
  }
}

// 串行 + 小并发（每 4 个一批），避免打爆接口
const entries = Object.entries(all);
const results = [];
const CONCURRENCY = 4;
for (let i = 0; i < entries.length; i += CONCURRENCY) {
  const batch = entries.slice(i, i + CONCURRENCY);
  const done = await Promise.all(batch.map(([k, v]) => synth(k, v)));
  results.push(...done);
  process.stdout.write(`\r${Math.min(i + CONCURRENCY, entries.length)}/${entries.length}`);
}
console.log('');

fs.mkdirSync(OUT, { recursive: true });

// —— manifest ——
const files = {};
for (const k of Object.keys(all)) files[k] = `${k}.mp3`;
fs.writeFileSync(path.join(OUT, 'manifest.json'), JSON.stringify({
  voiceId: 'default',
  name: '标准音色',
  lang: 'zh-CN',
  format: 'mp3',
  files,
}, null, 2));

const fails = results.filter(r => r.startsWith('FAIL'));
console.log(results.filter(r => !r.startsWith('skip')).slice(0, 8).join('\n'));
console.log(`...（完整日志共 ${results.length} 条）`);
console.log(`\n完成: ${results.length - fails.length - results.filter(r => r.startsWith('skip')).length} 新合成, ${results.filter(r => r.startsWith('skip')).length} 跳过, ${fails.length} 失败`);
if (fails.length) {
  console.log('\n失败清单:');
  console.log(fails.join('\n'));
  process.exit(1);
}
