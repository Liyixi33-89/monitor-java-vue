/*!
 * monitor-sdk.js —— 前端错误采集 SDK（轻量，无依赖，<5KB）
 *
 * 用法（挂载到被监控的前端项目 index.html）：
 *   <script src="/monitor-sdk.js"
 *           data-project-id="2"
 *           data-report-url="http://111.231.57.177:4000/api/error/report"
 *           data-sample-rate="1"></script>
 *
 * 采集能力：
 *  1. window.onerror           —— JS 运行时错误
 *  2. unhandledrejection        —— 未处理的 Promise 异常
 *  3. 资源加载失败（img/script/link 的 error 事件，走捕获阶段）
 *  4. fetch / XMLHttpRequest 拦截 —— 接口请求异常（4xx/5xx/网络错误/超时）
 *  5. 上报方式：navigator.sendBeacon 优先，降级 fetch(keepalive)
 *  6. 本地节流：同一错误（同 message+stack 前 200 字符）10 秒内只上报一次，避免刷屏
 */
(function (global) {
  'use strict';

  var scriptEl = document.currentScript ||
    (function () {
      var list = document.getElementsByTagName('script');
      return list[list.length - 1];
    })();

  var cfg = {
    projectId: scriptEl.getAttribute('data-project-id') || 'unknown',
    reportUrl: scriptEl.getAttribute('data-report-url') || '',
    sampleRate: parseFloat(scriptEl.getAttribute('data-sample-rate') || '1'),
    appName: scriptEl.getAttribute('data-app-name') || ''
  };

  if (!cfg.reportUrl) {
    console.warn('[monitor-sdk] data-report-url 未配置，SDK 不会上报任何数据');
    return;
  }

  // ------- 本地节流：避免同一错误短时间内刷屏上报 -------
  var _recent = {}; // key -> timestamp
  var THROTTLE_MS = 10000;
  function shouldThrottle(key) {
    var now = Date.now();
    var last = _recent[key];
    if (last && now - last < THROTTLE_MS) return true;
    _recent[key] = now;
    return false;
  }
  function simpleHash(str) {
    var h = 0;
    str = String(str || '');
    for (var i = 0; i < str.length; i++) {
      h = (h << 5) - h + str.charCodeAt(i);
      h |= 0;
    }
    return h.toString(36);
  }

  // ------- 采样：按 sampleRate 决定是否上报（用于高流量场景降压） -------
  function hitSample() {
    return Math.random() < cfg.sampleRate;
  }

  // ------- 核心上报函数 -------
  function report(type, payload) {
    try {
      if (!hitSample()) return;
      var throttleKey = type + ':' + simpleHash((payload && payload.message) + (payload && payload.stack));
      if (shouldThrottle(throttleKey)) return;

      var body = JSON.stringify(Object.assign({
        projectId: cfg.projectId,
        appName: cfg.appName,
        type: type,
        url: global.location ? global.location.href : '',
        userAgent: navigator.userAgent,
        timestamp: Date.now()
      }, payload || {}));

      if (navigator.sendBeacon) {
        var ok = navigator.sendBeacon(cfg.reportUrl, new Blob([body], { type: 'application/json' }));
        if (ok) return;
      }
      // 降级方案：fetch keepalive（兼容不支持 sendBeacon 的环境或 beacon 失败）
      if (global.fetch) {
        global.fetch(cfg.reportUrl, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: body,
          keepalive: true
        }).catch(function () { /* 静默失败，不影响业务 */ });
      } else {
        var xhr = new XMLHttpRequest();
        xhr.open('POST', cfg.reportUrl, true);
        xhr.setRequestHeader('Content-Type', 'application/json');
        xhr.send(body);
      }
    } catch (e) {
      // SDK 自身异常绝不能影响宿主页面
    }
  }

  // ------- 1&3. window.onerror：JS 错误 + 资源加载错误（捕获阶段，event 对象形式触发） -------
  global.addEventListener('error', function (event) {
    if (event && event.target && event.target !== global && event.target.tagName) {
      // 资源加载失败：img / script / link
      var tag = event.target.tagName.toLowerCase();
      if (tag === 'img' || tag === 'script' || tag === 'link') {
        report('resource_error', {
          message: '资源加载失败: ' + tag,
          url: event.target.src || event.target.href || ''
        });
      }
      return;
    }
    // 普通 JS 运行时错误
    report('js_error', {
      message: event.message || (event.error && event.error.message) || 'Unknown error',
      stack: event.error && event.error.stack,
      sourceUrl: event.filename,
      line: event.lineno,
      col: event.colno
    });
  }, true);

  // ------- 2. 未处理的 Promise 异常 -------
  global.addEventListener('unhandledrejection', function (event) {
    var reason = event.reason;
    report('unhandledrejection', {
      message: (reason && (reason.message || String(reason))) || 'Unhandled rejection',
      stack: reason && reason.stack
    });
  });

  // ------- 4a. fetch 拦截 -------
  if (global.fetch) {
    var _fetch = global.fetch;
    global.fetch = function () {
      var args = arguments;
      var reqUrl = args[0] && (args[0].url || args[0]);
      var start = Date.now();
      return _fetch.apply(this, args).then(function (res) {
        if (!res.ok) {
          report('api_error', {
            message: 'HTTP ' + res.status + ' ' + res.statusText,
            url: String(reqUrl),
            statusCode: res.status,
            duration: Date.now() - start
          });
        }
        return res;
      }).catch(function (err) {
        report('api_error', {
          message: err && err.message || 'fetch network error',
          url: String(reqUrl),
          duration: Date.now() - start
        });
        throw err;
      });
    };
  }

  // ------- 4b. XMLHttpRequest 拦截（兼容未使用 fetch 的旧代码） -------
  var _open = XMLHttpRequest.prototype.open;
  var _send = XMLHttpRequest.prototype.send;
  XMLHttpRequest.prototype.open = function (method, url) {
    this.__monitor_url = url;
    this.__monitor_method = method;
    return _open.apply(this, arguments);
  };
  XMLHttpRequest.prototype.send = function () {
    var xhr = this;
    var start = Date.now();
    xhr.addEventListener('loadend', function () {
      if (xhr.status === 0 || xhr.status >= 400) {
        report('api_error', {
          message: 'XHR ' + xhr.status + ' ' + xhr.__monitor_method,
          url: String(xhr.__monitor_url),
          statusCode: xhr.status,
          duration: Date.now() - start
        });
      }
    });
    return _send.apply(this, arguments);
  };

  global.__MONITOR_SDK__ = { report: report, version: '1.0.0' };
})(window);
