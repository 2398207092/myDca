/* 皮肤包入口纠偏（2026-11-12）
 * 在应用脚本之前执行：localStorage 里记住的是哪套皮肤，就停留在哪个入口；
 * 从另一个入口误入时，带着当前 hash 路径跳过去（两套前端路由路径一致）。
 * 支持 ?skin=classic|auros 强制切换（本地调试用，会写回偏好）。
 */
(function () {
  var KEY = 'mydca.ui_skin';
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
  if (target !== want) {
    window.location.replace(target + window.location.hash);
  }
})();
