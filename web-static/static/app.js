(function () {
  'use strict';

  var API = new URL('./api/', window.location.href);
  var state = {
    catalog: null,
    results: []
  };

  function url(path) {
    return new URL(path, API).toString();
  }

  function el(id) {
    return document.getElementById(id);
  }

  function text(tag, className, value) {
    var node = document.createElement(tag);
    if (className) {
      node.className = className;
    }
    if (value !== undefined && value !== null) {
      node.textContent = value;
    }
    return node;
  }

  function request(path, options) {
    return fetch(url(path), options).then(function (response) {
      return response.text().then(function (body) {
        var data;
        try {
          data = body ? JSON.parse(body) : {};
        } catch (e) {
          data = { error: body || ('HTTP ' + response.status) };
        }
        if (!response.ok) {
          data.error = data.error || ('HTTP ' + response.status);
        }
        return data;
      });
    });
  }

  function busy(flag, message) {
    el('inject').disabled = flag;
    el('generate').disabled = flag;
    el('batch').disabled = flag;
    el('status').textContent = message || '';
  }

  function loadRuntime() {
    return request('runtime').then(function (runtime) {
      var box = el('runtime');
      box.textContent = '';
      var items = [
        ['服务', runtime.server],
        ['版本', runtime.serverVersion],
        ['Jakarta', runtime.jakarta ? '是' : '否'],
        ['Java', runtime.javaVersion],
        ['PID', runtime.pid],
        ['Agent', runtime.agentCapable ? '可用' : '不可用']
      ];
      items.forEach(function (item) {
        box.appendChild(text('span', 'chip', item[0] + ': ' + (item[1] === undefined || item[1] === null ? '-' : item[1])));
      });
    });
  }

  function loadCatalog(server) {
    var query = server ? '?server=' + encodeURIComponent(server) : '';
    return request('catalog' + query).then(function (catalog) {
      if (catalog.error) {
        throw new Error(catalog.error);
      }
      state.catalog = catalog;
      renderServers(catalog);
      renderMatrix(catalog);
      renderTools(catalog);
      renderPackers(catalog);
    });
  }

  function renderServers(catalog) {
    var select = el('server');
    select.textContent = '';
    (catalog.servers || []).forEach(function (server) {
      var option = text('option', null, server);
      option.value = server;
      if (server === catalog.server) {
        option.selected = true;
      }
      select.appendChild(option);
    });
  }

  function renderMatrix(catalog) {
    var box = el('matrix');
    box.textContent = '';
    var matrix = catalog.matrix || {};
    var tools = Object.keys(matrix);
    if (!tools.length) {
      box.appendChild(text('p', 'empty', '当前服务没有可用挂载类型'));
      return;
    }
    tools.forEach(function (tool) {
      var row = text('div', 'matrix-row');
      row.appendChild(text('div', 'matrix-tool', tool));
      var mounts = text('div', 'matrix-mounts');
      (matrix[tool] || []).forEach(function (mount) {
        var button = text('button', 'mount' + (mount.indexOf('Agent') === 0 ? ' agent' : ''), mount);
        button.type = 'button';
        button.addEventListener('click', function () {
          el('tool').value = tool;
          renderMounts(tool, mount);
        });
        mounts.appendChild(button);
      });
      row.appendChild(mounts);
      box.appendChild(row);
    });
  }

  function renderTools(catalog) {
    var select = el('tool');
    select.textContent = '';
    Object.keys(catalog.matrix || {}).forEach(function (tool) {
      var option = text('option', null, tool);
      option.value = tool;
      select.appendChild(option);
    });
    var first = select.value;
    if (first) {
      renderMounts(first, null);
      renderParams(first);
    } else {
      renderMounts('', null);
      renderParams('');
    }
  }

  function renderMounts(tool, selected) {
    var select = el('mount');
    select.textContent = '';
    var mounts = (state.catalog && state.catalog.matrix && state.catalog.matrix[tool]) || [];
    mounts.forEach(function (mount) {
      var option = text('option', null, mount);
      option.value = mount;
      if (mount === selected) {
        option.selected = true;
      }
      select.appendChild(option);
    });
    if (!selected && mounts.length) {
      select.value = mounts[0];
    }
    markActive(tool);
  }

  function markActive(tool) {
    var mount = el('mount').value;
    Array.prototype.forEach.call(document.querySelectorAll('.mount'), function (button) {
      var row = button.closest('.matrix-row');
      var rowTool = row ? row.querySelector('.matrix-tool').textContent : '';
      button.classList.toggle('active', rowTool === tool && button.textContent === mount);
    });
  }

  function renderParams(tool) {
    var box = el('params');
    box.textContent = '';
    var specs = (state.catalog && state.catalog.toolParams && state.catalog.toolParams[tool]) || [];
    if (!specs.length) {
      box.appendChild(text('p', 'empty', '该工具没有额外参数'));
      return;
    }
    specs.forEach(function (spec) {
      var wrapper = text('div', 'field');
      wrapper.appendChild(text('label', null, spec.label || spec.name));
      var input;
      if (spec.options && spec.options.length) {
        input = document.createElement('select');
        spec.options.forEach(function (optionValue) {
          var option = text('option', null, optionValue);
          option.value = optionValue;
          if (optionValue === spec.defaultValue) {
            option.selected = true;
          }
          input.appendChild(option);
        });
      } else {
        input = document.createElement('input');
        input.type = 'text';
        input.value = spec.defaultValue || '';
        if (['pass', 'key', 'headerValue', 'paramName'].indexOf(spec.name) >= 0) {
          input.placeholder = '留空自动生成';
        }
      }
      input.dataset.name = spec.name;
      wrapper.appendChild(input);
      box.appendChild(wrapper);
    });
  }

  function renderPackers(catalog) {
    var select = el('packer');
    select.textContent = '';
    var packers = catalog.packers && catalog.packers.length ? catalog.packers : ['DefaultBase64'];
    packers.forEach(function (packer) {
      var option = text('option', null, packer);
      option.value = packer;
      if (packer === 'DefaultBase64') {
        option.selected = true;
      }
      select.appendChild(option);
    });
  }

  function payload() {
    var body = {
      server: el('server').value,
      tool: el('tool').value,
      mount: el('mount').value,
      packer: el('packer').value
    };
    Array.prototype.forEach.call(el('params').querySelectorAll('[data-name]'), function (input) {
      body[input.dataset.name] = input.value;
    });
    return body;
  }

  function copy(value) {
    if (!value) {
      return;
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(value);
      return;
    }
    var area = document.createElement('textarea');
    area.value = value;
    document.body.appendChild(area);
    area.select();
    try {
      document.execCommand('copy');
    } finally {
      document.body.removeChild(area);
    }
  }

  function kv(label, value) {
    var row = text('div');
    row.appendChild(text('span', 'k', label + ': '));
    var node = text('span', 'v', value);
    node.title = '点击复制';
    node.style.cursor = 'pointer';
    node.addEventListener('click', function () {
      copy(value);
    });
    row.appendChild(node);
    return row;
  }

  function renderResult(result) {
    var ok = result.defined || result.attached;
    var card = text('div', 'result ' + (ok ? (result.warning ? 'warn' : 'ok') : (result.error ? 'bad' : 'ok')));
    if (!ok) {
      card.className = 'result';
    }
    var head = text('div', 'result-head');
    head.appendChild(text('span', 'badge ' + (ok ? 'ok' : 'bad'), ok ? '成功' : '失败'));
    head.appendChild(text('strong', null, result.mount + ' / ' + result.tool));
    head.appendChild(text('span', 'badge', result.method));
    head.appendChild(text('span', 'badge', result.packer));
    if (result.defined) {
      head.appendChild(text('span', 'badge ok', 'defined'));
    }
    if (result.instantiated) {
      head.appendChild(text('span', 'badge ok', 'instantiated'));
    }
    if (result.attached) {
      head.appendChild(text('span', 'badge ok', 'attached'));
    }
    head.appendChild(text('span', 'result-id', result.caseId + ' · ' + (result.elapsedMs || 0) + 'ms'));
    card.appendChild(head);

    var connection = result.connection || {};
    var details = text('div', 'kv');
    details.appendChild(kv('URL', connection.url));
    details.appendChild(kv('URL Pattern', connection.urlPattern));
    if (connection.pass) {
      details.appendChild(kv('Pass', connection.pass));
    }
    if (connection.key) {
      details.appendChild(kv('Key', connection.key));
    }
    if (connection.headerName) {
      details.appendChild(kv('Header', connection.headerName + ': ' + connection.headerValue));
    }
    if (connection.paramName) {
      details.appendChild(kv('Param', connection.paramName));
    }
    if (connection.trigger) {
      details.appendChild(kv('Trigger', connection.trigger));
    }
    details.appendChild(kv('Injector', connection.injectorClassName));
    details.appendChild(kv('Shell', connection.shellClassName));
    details.appendChild(kv('Payload', (result.payloadSize || 0) + ' B'));
    card.appendChild(details);

    if (result.error) {
      card.appendChild(text('div', 'message', 'ERROR ' + result.error));
    }
    if (result.warning) {
      card.appendChild(text('div', 'message warn', 'WARN ' + result.warning));
    }
    return card;
  }

  function addResults(results) {
    state.results = state.results.concat(results);
    if (state.results.length > 300) {
      state.results = state.results.slice(state.results.length - 300);
    }
    renderResults();
  }

  function renderResults() {
    var box = el('results');
    box.textContent = '';
    if (!state.results.length) {
      box.appendChild(text('p', 'empty', '还没有执行记录'));
      el('summary').textContent = '';
      return;
    }
    var ok = state.results.filter(function (item) {
      return item.defined || item.attached;
    }).length;
    el('summary').textContent = '成功 ' + ok + ' / 共 ' + state.results.length;
    state.results.slice().reverse().forEach(function (result) {
      box.appendChild(renderResult(result));
    });
  }

  function run(single) {
    var body = payload();
    if (!body.mount || !body.tool) {
      busy(false, '先选择工具和挂载类型');
      return;
    }
    busy(true, single === 'generate' ? '生成中' : '注入中');
    var path = single === 'generate' ? 'generate' : 'inject';
    request(path, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    }).then(function (result) {
      if (result.error) {
        addResults([{ mount: body.mount, tool: body.tool, method: '-', packer: body.packer,
          caseId: '-', error: result.error, connection: {} }]);
      } else if (single === 'generate') {
        addResults([{ mount: result.mount, tool: result.tool, method: result.method, packer: result.packer,
          caseId: result.caseId, payloadSize: result.payloadSize, defined: true, connection: result.connection }]);
      } else {
        addResults([result]);
      }
    }).catch(function (error) {
      addResults([{ mount: body.mount, tool: body.tool, method: '-', packer: body.packer,
        caseId: '-', error: String(error), connection: {} }]);
    }).then(function () {
      busy(false, '');
    });
  }

  function batch() {
    var body = {
      server: el('server').value,
      includeAgent: el('includeAgent').checked
    };
    busy(true, '批量注入中，完成前请勿关闭页面');
    request('inject/batch', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body)
    }).then(function (response) {
      if (response.results) {
        addResults(response.results);
        el('status').textContent = '完成 ' + response.succeeded + ' / ' + response.total;
      } else {
        addResults([{ mount: '-', tool: '-', method: '-', packer: '-', caseId: '-',
          error: response.error || '批量注入失败', connection: {} }]);
      }
    }).catch(function (error) {
      addResults([{ mount: '-', tool: '-', method: '-', packer: '-', caseId: '-',
        error: String(error), connection: {} }]);
    }).then(function () {
      busy(false, el('status').textContent);
    });
  }

  el('server').addEventListener('change', function () {
    loadCatalog(el('server').value).catch(function (error) {
      el('status').textContent = String(error);
    });
  });
  el('tool').addEventListener('change', function () {
    renderMounts(el('tool').value, null);
    renderParams(el('tool').value);
  });
  el('mount').addEventListener('change', function () {
    markActive(el('tool').value);
  });
  el('inject').addEventListener('click', function () {
    run('inject');
  });
  el('generate').addEventListener('click', function () {
    run('generate');
  });
  el('batch').addEventListener('click', batch);
  el('clear').addEventListener('click', function () {
    state.results = [];
    renderResults();
  });

  loadRuntime().catch(function () {});
  loadCatalog(null).catch(function (error) {
    el('runtime').textContent = String(error);
  });
})();
