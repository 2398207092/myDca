/* 皮肤包入口纠偏（2026-11-12）
 * 在应用脚本之前执行：localStorage 里记住的是哪套皮肤，就停留在哪个入口；
 * 从另一个入口误入时，带着当前 hash 路径跳过去（两套前端路由路径一致）。
 * 支持 ?skin=classic|auros 强制切换（本地调试用，会写回偏好）。
 *
 * 防循环保护：如果目标入口实际上返回的还是当前入口的页面（典型情况：nginx SPA 兜底把
 * 不存在的 /auros.html 回落成 index.html），这里会在 3 秒内检测到“刚跳走又被送回来”，
 * 停止继续跳转，避免无限重定向把页面卡死。
 */
(function () {
  var KEY = 'mydca.ui_skin';
  var GUARD = 'mydca.skin_switch_guard';
  var entry = document.currentScript ? document.currentScript.getAttribute('data-entry') : 'classic';
  var skin = 'classic';
  try {
    skin = window.localStorage.getItem(KEY) === 'auros' ? 'auros' : 'classic';
  } catch (e) {}

  try {
    var forced = new URLSearchParams(window.location.search).get('skin');
    if (forced === 'classic' || forced === 'auros') {
      skin = forced;
      window.localStorage.setItem(KEY, forced);
    }
  } catch (e) {}

  var target = skin === 'auros' ? '/auros.html' : '/index.html';
  var want = entry === 'auros' ? '/auros.html' : '/index.html';

  if (target === want) {
    // 已经停在正确入口：清掉保护标记，下次切换不受影响
    try {
      window.sessionStorage.removeItem(GUARD);
    } catch (e) {}
    return;
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
  window.location.replace(target + window.location.hash);
})();
