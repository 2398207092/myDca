/* 皮肤包入口纠偏（2026-11-13，三套皮肤）
 * 在应用脚本之前执行：localStorage 里记住的是哪套皮肤，就停留在哪个入口；
 * 从另一个入口误入时，带着合适的路径跳过去。
 *   classic → /index.html   原版
 *   auros   → /auros.html   Auros 卡片版
 *   next    → /next.html    三面版（#/now #/past #/future）
 * 支持 ?skin=classic|auros|next 强制切换（本地调试用，会写回偏好）。
 *
 * 路由不完全互通：原版与 Auros 路径一致，可原样带 hash；三面版路由独立，
 * 跨皮肤进入三面版时落到 #/now，离开三面版时回到 #/。
 *
 * 防循环保护：如果目标入口实际上返回的还是当前入口的页面（典型情况：nginx SPA 兜底把
 * 不存在的 /next.html 回落成 index.html），这里会在 3 秒内检测到“刚跳走又被送回来”，
 * 停止继续跳转，避免无限重定向把页面卡死。
 */
(function () {
  var KEY = 'mydca.ui_skin';
  var GUARD = 'mydca.skin_switch_guard';
  var ENTRIES = { classic: '/index.html', auros: '/auros.html', next: '/next.html' };
  var NEXT_HASH = /^#\/(now|past|future|login)(\?|$)/;
  var entry = document.currentScript ? document.currentScript.getAttribute('data-entry') : 'classic';
  if (!ENTRIES[entry]) entry = 'classic';

  var skin = 'classic';
  try {
    var saved = window.localStorage.getItem(KEY);
    if (ENTRIES[saved]) skin = saved;
  } catch (e) {}

  try {
    var forced = new URLSearchParams(window.location.search).get('skin');
    if (ENTRIES[forced]) {
      skin = forced;
      window.localStorage.setItem(KEY, forced);
    }
  } catch (e) {}

  if (ENTRIES[skin] === ENTRIES[entry]) {
    // 已经停在正确入口：清掉保护标记，下次切换不受影响
    try {
      window.sessionStorage.removeItem(GUARD);
    } catch (e) {}
    return;
  }

  var hash = window.location.hash || '';
  if (skin === 'next') {
    // 进入三面版：只保留三面版自己的路由，否则落到 #/now
    if (!NEXT_HASH.test(hash)) hash = '#/now';
  } else {
    // 离开三面版：三面版路由在原版/Auros 里不存在，回到首页
    if (NEXT_HASH.test(hash)) hash = '#/';
  }

  // 这两条 URL 是不同入口，却是同一个页面（说明目标入口没有真正存在/被兜底了）
  var last = 0;
  try {
    last = parseInt(window.sessionStorage.getItem(GUARD) || '0', 10) || 0;
  } catch (e) {}
  var now = Date.now();
  if (last && now - last < 3000) {
    try {
      window.sessionStorage.removeItem(GUARD);
    } catch (e) {}
    if (window.console && console.warn) {
      console.warn('[skin] 检测到入口重定向循环，已停止跳转（目标入口可能缺失）');
    }
    return;
  }
  try {
    window.sessionStorage.setItem(GUARD, String(now));
  } catch (e) {}
  window.location.replace(ENTRIES[skin] + hash);
})();
