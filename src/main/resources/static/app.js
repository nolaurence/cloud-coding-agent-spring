'use strict';

(function () {
  const STORAGE_API_KEY = 'cca_api_key';
  const STORAGE_WORKSPACE = 'cca_workspace_id';
  const MAX_JSON_PREVIEW = 12000;

  const state = {
    apiKey: localStorage.getItem(STORAGE_API_KEY) || '',
    config: null,
    models: [],
    workspaces: [],
    selectedWorkspaceId: null,
    workspaceRequestId: 0,
    threads: [],
    selectedThreadId: null,
    timeline: emptyTimeline(),
    ws: null,
    wsConnected: false,
    wsRetry: 0,
    runningThreads: new Set(),
    fileCache: new Map(),
    expandedDirs: new Set(),
    selectedFilePath: null,
    renderScheduled: false,
    timelineRequestId: 0,
    usage: null
  };

  const els = {};

  document.addEventListener('DOMContentLoaded', init);

  async function init() {
    cacheDom();
    bindEvents();
    autoResize();
    await loadConfig();
    renderAll();
    if (state.config && state.config.securityEnabled && !state.apiKey) {
      openApiKeyDialog();
      return;
    }
    await bootstrapData();
  }

  function cacheDom() {
    [
      'workspaceButton', 'workspaceName', 'workspaceMenu', 'newThreadBtn', 'threadList',
      'newWorkspaceBtn', 'apiKeyBtn', 'threadTitle', 'threadStatus', 'modelSelect',
      'permissionSelect', 'pinBtn', 'archiveBtn', 'messageList', 'permissionBar',
      'promptInput', 'composerHint', 'stopBtn', 'sendBtn', 'refreshFilesBtn', 'fileTree',
      'fileViewer', 'fileViewerPath', 'closeFileBtn', 'fileViewerContent', 'toastContainer',
      'workspaceDialog', 'workspaceForm', 'threadDialog', 'threadForm', 'apiKeyDialog',
      'apiKeyForm'
    ].forEach(function (id) {
      els[id] = document.getElementById(id);
    });
  }

  function bindEvents() {
    els.workspaceButton.addEventListener('click', function (event) {
      event.stopPropagation();
      toggleWorkspaceMenu();
    });
    document.addEventListener('click', function (event) {
      if (!event.target.closest('.workspace-switch')) hideWorkspaceMenu();
    });
    els.workspaceMenu.addEventListener('click', onWorkspaceMenuClick);
    els.newWorkspaceBtn.addEventListener('click', openWorkspaceDialog);
    els.apiKeyBtn.addEventListener('click', openApiKeyDialog);
    els.newThreadBtn.addEventListener('click', openThreadDialog);
    els.threadList.addEventListener('click', function (event) {
      const item = event.target.closest('[data-thread-id]');
      if (item) selectThread(item.getAttribute('data-thread-id'));
    });
    els.modelSelect.addEventListener('change', function () {
      patchThread({ model: els.modelSelect.value });
    });
    els.permissionSelect.addEventListener('change', function () {
      patchThread({ permissionMode: els.permissionSelect.value });
    });
    els.pinBtn.addEventListener('click', function () {
      const thread = currentThread();
      if (thread) patchThread({ pinned: !thread.pinnedAt });
    });
    els.archiveBtn.addEventListener('click', archiveCurrentThread);
    els.sendBtn.addEventListener('click', sendMessage);
    els.stopBtn.addEventListener('click', stopCurrent);
    els.promptInput.addEventListener('keydown', function (event) {
      if (event.key === 'Enter' && !event.shiftKey) {
        event.preventDefault();
        sendMessage();
      }
    });
    els.promptInput.addEventListener('input', autoResize);
    els.refreshFilesBtn.addEventListener('click', refreshFiles);
    els.fileTree.addEventListener('click', onFileTreeClick);
    els.closeFileBtn.addEventListener('click', hideFileViewer);
    els.permissionBar.addEventListener('click', async function (event) {
      const button = event.target.closest('button[data-permission-id]');
      if (!button) return;
      await resolvePermission(
        button.getAttribute('data-permission-id'),
        button.getAttribute('data-decision') === 'approve'
      );
    });

    els.workspaceForm.addEventListener('submit', function (event) {
      event.preventDefault();
      if (event.submitter && event.submitter.value === 'cancel') {
        els.workspaceDialog.close();
        return;
      }
      createWorkspace(els.workspaceForm);
    });
    els.threadForm.addEventListener('submit', function (event) {
      event.preventDefault();
      if (event.submitter && event.submitter.value === 'cancel') {
        els.threadDialog.close();
        return;
      }
      createThread(els.threadForm);
    });
    els.apiKeyForm.addEventListener('submit', function (event) {
      event.preventDefault();
      if (event.submitter && event.submitter.value === 'cancel') {
        els.apiKeyDialog.close();
        return;
      }
      saveApiKey(els.apiKeyForm);
    });
    [els.workspaceDialog, els.threadDialog, els.apiKeyDialog].forEach(function (dialog) {
      dialog.addEventListener('close', function () {
        const form = dialog.querySelector('form');
        if (form) form.reset();
      });
    });
  }

  async function loadConfig() {
    try {
      state.config = await api('/api/config');
    } catch (error) {
      state.config = { securityEnabled: false, copilotEnabled: true, copilotStatus: 'unknown' };
      toast(error.message || '无法读取服务配置', 'error');
    }
  }

  async function bootstrapData() {
    try {
      state.models = await api('/api/models');
    } catch (error) {
      state.models = [];
    }
    try {
      state.workspaces = await api('/api/workspaces');
    } catch (error) {
      state.workspaces = [];
      toast(error.message || '无法读取工作区', 'error');
      renderAll();
      return;
    }
    sortWorkspaces();
    const saved = localStorage.getItem(STORAGE_WORKSPACE);
    const preferred = state.workspaces.find(function (workspace) {
      return workspace.id === saved;
    }) || state.workspaces[0];
    if (preferred) {
      await selectWorkspace(preferred.id);
    } else {
      state.selectedWorkspaceId = null;
      renderAll();
    }
    connectWebSocket();
  }

  async function api(path, options) {
    options = options || {};
    const headers = Object.assign({}, options.headers || {});
    if (state.apiKey) headers['X-API-Key'] = state.apiKey;
    let body = options.body;
    if (body != null && typeof body !== 'string') {
      headers['Content-Type'] = 'application/json';
      body = JSON.stringify(body);
    }
    const response = await fetch(path, {
      method: options.method || 'GET',
      headers: headers,
      body: body
    });
    if (response.status === 401) {
      openApiKeyDialog();
      const error = new Error('API Key 无效或缺失');
      error.status = 401;
      throw error;
    }
    if (!response.ok) {
      let message = '请求失败 (' + response.status + ')';
      try {
        const data = await response.json();
        if (data && data.message) message = data.message;
      } catch (ignored) {
        // noop
      }
      const error = new Error(message);
      error.status = response.status;
      throw error;
    }
    if (response.status === 204) return null;
    const text = await response.text();
    if (!text) return null;
    try {
      return JSON.parse(text);
    } catch (ignored) {
      return text;
    }
  }

  async function selectWorkspace(workspaceId) {
    hideWorkspaceMenu();
    const requestId = ++state.workspaceRequestId;
    state.selectedWorkspaceId = workspaceId;
    if (state.ws && state.ws.readyState === WebSocket.OPEN && state.selectedThreadId) {
      state.ws.send(JSON.stringify({ type: "unsubscribe", threadId: state.selectedThreadId }));
    }
    state.selectedThreadId = null;
    state.timeline = emptyTimeline();
    state.threads = [];
    state.runningThreads = new Set();
    state.fileCache = new Map();
    state.expandedDirs = new Set();
    state.selectedFilePath = null;
    hideFileViewer();
    localStorage.setItem(STORAGE_WORKSPACE, workspaceId || '');
    renderAll();
    if (!workspaceId) return;
    try {
      const threads = await api('/api/workspaces/' + workspaceId + '/threads?includeArchived=false');
      if (requestId !== state.workspaceRequestId || state.selectedWorkspaceId !== workspaceId) return;
      state.threads = threads || [];
      state.runningThreads = new Set(state.threads.filter(function (thread) {
        return thread.status === 'RUNNING';
      }).map(function (thread) { return thread.id; }));
      const sorted = sortThreads(state.threads.filter(function (thread) { return !thread.archived; }));
      if (sorted.length) await selectThread(sorted[0].id);
      else renderAll();
      await loadFiles('');
    } catch (error) {
      toast(error.message, 'error');
    }
  }

  async function selectThread(threadId) {
    if (!threadId) return;
    if (state.selectedThreadId === threadId && state.timeline.thread &&
        state.timeline.thread.id === threadId) {
      return;
    }
    if (state.ws && state.ws.readyState === WebSocket.OPEN && state.selectedThreadId) {
      state.ws.send(JSON.stringify({ type: 'unsubscribe', threadId: state.selectedThreadId }));
    }
    state.selectedThreadId = threadId;
    const thread = state.threads.find(function (item) { return item.id === threadId; });
    state.timeline = emptyTimeline();
    state.timeline.thread = thread || null;
    renderAll();
    subscribeCurrent();
    await loadTimeline(threadId);
  }

  async function loadTimeline(threadId) {
    const requestId = ++state.timelineRequestId;
    try {
      const data = await api('/api/threads/' + threadId + '/timeline');
      if (requestId !== state.timelineRequestId || state.selectedThreadId !== threadId) return;
      state.timeline = normalizeTimeline(data);
      updateThreadFromDetail(state.timeline.thread);
      renderAll();
      scrollToBottom(true);
    } catch (error) {
      toast(error.message, 'error');
    }
  }

  function connectWebSocket() {
    if (state.ws) {
      state.ws.onclose = null;
      try {
        state.ws.close();
      } catch (ignored) {
        // noop
      }
    }
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const query = state.apiKey ? '?token=' + encodeURIComponent(state.apiKey) : '';
    const socket = new WebSocket(protocol + '//' + window.location.host + '/ws' + query);
    state.ws = socket;
    socket.onopen = function () {
      if (state.ws !== socket) return;
      state.wsConnected = true;
      state.wsRetry = 0;
      updateComposer();
      subscribeCurrent();
    };
    socket.onmessage = function (event) {
      try {
        handleWsEvent(JSON.parse(event.data));
      } catch (error) {
        console.error('无效的 WebSocket 消息', error);
      }
    };
    socket.onclose = function () {
      if (state.ws !== socket) return;
      state.wsConnected = false;
      updateComposer();
      const delay = Math.min(30000, 1000 * Math.pow(2, state.wsRetry));
      state.wsRetry += 1;
      window.setTimeout(function () {
        if (state.ws === socket) connectWebSocket();
      }, delay);
    };
    socket.onerror = function () {
      // onclose handles reconnection.
    };
  }

  function subscribeCurrent() {
    if (!state.ws || state.ws.readyState !== WebSocket.OPEN || !state.selectedThreadId) return;
    state.ws.send(JSON.stringify({ type: 'subscribe', threadId: state.selectedThreadId }));
  }

  function handleWsEvent(event) {
    if (!event || !event.type) return;
    switch (event.type) {
      case 'connection':
        state.wsConnected = true;
        updateComposer();
        return;
      case 'pong':
        return;
      case 'error':
        toast(String(event.data || 'WebSocket 错误'), 'error');
        return;
      case 'thread.snapshot':
        if (event.threadId === state.selectedThreadId) {
          state.timeline = normalizeTimeline(event.data);
          updateThreadFromDetail(state.timeline.thread);
          renderAll();
        }
        return;
      case 'thread.created':
        if (event.data) upsertThread(event.data);
        scheduleRender();
        return;
      case 'thread.updated':
        if (event.data) {
          upsertThread(event.data);
          if (event.threadId === state.selectedThreadId && state.timeline.thread) {
            state.timeline.thread = Object.assign({}, state.timeline.thread, event.data);
          }
          if (event.data.status === 'RUNNING') {
            state.runningThreads.add(event.threadId);
          } else if (event.data.status === 'IDLE' || event.data.status === 'ERROR') {
            state.runningThreads.delete(event.threadId);
          }
        }
        scheduleRender();
        return;
      case 'thread.deleted':
        handleThreadDeleted(event.threadId);
        return;
      case 'thread.title':
        if (event.data && event.data.title) {
          const thread = state.threads.find(function (item) { return item.id === event.threadId; });
          if (thread) thread.title = event.data.title;
          if (state.selectedThreadId === event.threadId && state.timeline.thread) {
            state.timeline.thread.title = event.data.title;
          }
          scheduleRender();
        }
        return;
      default:
        break;
    }
    if (event.threadId === state.selectedThreadId) {
      applyTimelineEvent(event);
    }
    scheduleRender();
  }

  function applyTimelineEvent(event) {
    const timeline = ensureTimeline();
    const data = event.data || {};
    switch (event.type) {
      case 'turn.started':
        state.runningThreads.add(event.threadId);
        if (timeline.thread) timeline.thread.status = 'RUNNING';
        break;
      case 'user.message':
        upsertMessage(data);
        break;
      case 'message.started':
        upsertMessage(data);
        break;
      case 'message.delta': {
        let message = data.messageId ? findMessageById(data.messageId) : findStreamingAssistant(data.turnId);
        if (!message) {
          message = {
            id: data.messageId || ((data.turnId || 'current') + ':assistant'),
            role: 'assistant',
            turnId: data.turnId || null,
            content: '',
            reasoning: '',
            status: 'streaming',
            createdAt: new Date().toISOString()
          };
          timeline.messages.push(message);
        }
        message.content = (message.content || '') + (data.delta || '');
        break;
      }
      case 'message.reasoning_delta': {
        let message = findMessageById(data.messageId) || findStreamingAssistant(data.turnId);
        if (!message) {
          message = {
            id: (data.turnId || 'current') + ':assistant',
            role: 'assistant',
            turnId: data.turnId || null,
            content: '',
            reasoning: '',
            status: 'streaming',
            createdAt: new Date().toISOString()
          };
          timeline.messages.push(message);
        }
        message.reasoning = (message.reasoning || '') + (data.delta || '');
        break;
      }
      case 'message.reasoning': {
        const message = findStreamingAssistant(data.turnId);
        if (message) message.reasoning = data.content || '';
        break;
      }
      case 'message.completed': {
        const existing = findMessageById(data.id);
        const merged = Object.assign({}, existing || {}, data);
        if (existing) {
          if (!data.content) merged.content = existing.content;
          if (!data.reasoning) merged.reasoning = existing.reasoning;
        }
        upsertMessage(merged);
        break;
      }
      case 'tool.started':
        upsertActivity(data);
        break;
      case 'tool.progress': {
        const activity = timeline.activities.find(function (item) { return item.id === data.toolCallId; });
        if (activity) {
          activity.progress = activity.progress || [];
          activity.progress.push(data.message || '');
        }
        break;
      }
      case 'tool.completed': {
        const existing = timeline.activities.find(function (item) { return item.id === data.id; });
        const merged = Object.assign({}, existing || {}, data);
        if (existing) {
          if (!data.toolName) merged.toolName = existing.toolName;
          if (!data.args) merged.args = existing.args;
          if (!data.startedAt) merged.startedAt = existing.startedAt;
          merged.progress = existing.progress;
        }
        upsertActivity(merged);
        break;
      }
      case 'permission.requested':
        if (data && data.id && !timeline.pendingPermissions.some(function (item) { return item.id === data.id; })) {
          timeline.pendingPermissions.push(data);
        }
        break;
      case 'permission.resolved':
        timeline.pendingPermissions = timeline.pendingPermissions.filter(function (item) {
          return item.id !== data.requestId;
        });
        break;
      case 'thread.error':
        state.runningThreads.delete(event.threadId);
        if (timeline.thread) {
          timeline.thread.status = 'ERROR';
          timeline.thread.lastError = data.message || '未知错误';
        }
        toast(data.message || 'Copilot 执行失败', 'error');
        break;
      case 'turn.completed':
        state.runningThreads.delete(event.threadId);
        if (timeline.thread) timeline.thread.status = 'IDLE';
        window.setTimeout(function () {
          if (state.selectedThreadId === event.threadId) loadTimeline(event.threadId);
        }, 200);
        break;
      case 'thread.warning':
        toast(data.message || 'Copilot 警告', 'error');
        break;
      case 'usage.updated':
        state.usage = data;
        break;
      default:
        break;
    }
  }

  function ensureTimeline() {
    if (!state.timeline) state.timeline = emptyTimeline();
    if (!state.timeline.thread && state.selectedThreadId) {
      const thread = state.threads.find(function (item) { return item.id === state.selectedThreadId; });
      if (thread) state.timeline.thread = thread;
    }
    return state.timeline;
  }

  function findMessageById(messageId) {
    if (!messageId) return null;
    return state.timeline.messages.find(function (message) { return message.id === messageId; }) || null;
  }

  function findStreamingAssistant(turnId) {
    const messages = state.timeline.messages;
    for (let index = messages.length - 1; index >= 0; index -= 1) {
      const message = messages[index];
      if (message.role === 'assistant' && (!turnId || message.turnId === turnId)) return message;
    }
    return null;
  }

  function upsertMessage(message) {
    if (!message || !message.id) return;
    const index = state.timeline.messages.findIndex(function (item) { return item.id === message.id; });
    if (index >= 0) {
      state.timeline.messages[index] = Object.assign({}, state.timeline.messages[index], message);
    } else {
      state.timeline.messages.push(message);
    }
  }

  function upsertActivity(activity) {
    if (!activity || !activity.id) return;
    const index = state.timeline.activities.findIndex(function (item) { return item.id === activity.id; });
    if (index >= 0) {
      state.timeline.activities[index] = Object.assign({}, state.timeline.activities[index], activity);
    } else {
      state.timeline.activities.push(activity);
    }
  }

  function upsertThread(thread) {
    if (!thread || !thread.id) return;
    if (thread.workspaceId && state.selectedWorkspaceId &&
        thread.workspaceId !== state.selectedWorkspaceId) {
      return;
    }
    const index = state.threads.findIndex(function (item) { return item.id === thread.id; });
    if (index >= 0) {
      state.threads[index] = Object.assign({}, state.threads[index], thread);
    } else {
      state.threads.push(thread);
    }
  }

  function updateThreadFromDetail(thread) {
    if (!thread || !thread.id) return;
    upsertThread(thread);
  }

  function handleThreadDeleted(threadId) {
    state.threads = state.threads.filter(function (thread) { return thread.id !== threadId; });
    state.runningThreads.delete(threadId);
    if (state.selectedThreadId === threadId) {
      state.selectedThreadId = null;
      state.timeline = emptyTimeline();
      if (state.ws && state.ws.readyState === WebSocket.OPEN) {
        state.ws.send(JSON.stringify({ type: 'unsubscribe', threadId: threadId }));
      }
      toast('会话已删除');
    }
    renderAll();
  }

  function scheduleRender() {
    if (state.renderScheduled) return;
    state.renderScheduled = true;
    window.requestAnimationFrame(function () {
      state.renderScheduled = false;
      renderAll();
    });
  }

  function renderAll() {
    renderWorkspaceMenu();
    renderWorkspaceName();
    renderModelOptions();
    renderThreadList();
    renderHeader();
    renderTimeline();
    renderPermissionBar();
    updateComposer();
    renderFileTree();
  }

  function renderWorkspaceMenu() {
    if (!state.workspaces.length) {
      els.workspaceMenu.innerHTML = '<div class="thread-empty">暂无工作区</div>' +
        '<div class="menu-separator"></div>' +
        '<button class="menu-item" type="button" data-new-workspace>＋ 新建工作区</button>';
      return;
    }
    let html = state.workspaces.map(function (workspace) {
      return '<button class="menu-item ' + (workspace.id === state.selectedWorkspaceId ? 'active' : '') +
        '" type="button" data-workspace-id="' + escapeHtml(workspace.id) + '">' +
        '<span class="menu-workspace-name">' + escapeHtml(workspace.name) + '</span>' +
        '<span class="menu-delete" data-delete-workspace="' + escapeHtml(workspace.id) +
        '" title="删除工作区">×</span></button>';
    }).join('');
    html += '<div class="menu-separator"></div>' +
      '<button class="menu-item" type="button" data-new-workspace>＋ 新建工作区</button>';
    els.workspaceMenu.innerHTML = html;
  }

  function renderWorkspaceName() {
    const workspace = state.workspaces.find(function (item) {
      return item.id === state.selectedWorkspaceId;
    });
    els.workspaceName.textContent = workspace ? workspace.name :
      (state.workspaces.length ? '选择工作区' : '暂无工作区');
    els.workspaceName.title = workspace ? (workspace.path || workspace.name) : '';
  }

  function renderModelOptions() {
    const models = state.models && state.models.length ? state.models : [];
    const preferred = (currentThread() && currentThread().model) ||
      (state.config && state.config.defaultModel) || '';
    let html = '';
    models.forEach(function (model) {
      html += '<option value="' + escapeHtml(model.id) + '">' +
        escapeHtml(model.name || model.id) + '</option>';
    });
    if (!models.length && preferred) {
      html += '<option value="' + escapeHtml(preferred) + '">' + escapeHtml(preferred) + '</option>';
    }
    els.modelSelect.innerHTML = html;
    if (preferred) ensureSelectValue(els.modelSelect, preferred);
    els.modelSelect.value = preferred;
  }

  function ensureSelectValue(select, value) {
    if (!value) return;
    let found = false;
    for (let index = 0; index < select.options.length; index += 1) {
      if (select.options[index].value === value) {
        found = true;
        break;
      }
    }
    if (!found) {
      const option = document.createElement('option');
      option.value = value;
      option.textContent = value;
      select.appendChild(option);
    }
  }

  function renderThreadList() {
    if (!state.selectedWorkspaceId) {
      els.threadList.innerHTML = '';
      return;
    }
    const threads = sortThreads(state.threads.filter(function (thread) { return !thread.archived; }));
    if (!threads.length) {
      els.threadList.innerHTML = '<div class="thread-empty">暂无会话，点击上方 + 新建</div>';
      return;
    }
    els.threadList.innerHTML = threads.map(function (thread) {
      const running = thread.status === 'RUNNING' || state.runningThreads.has(thread.id);
      const active = thread.id === state.selectedThreadId;
      return '<div class="thread-item ' + (active ? 'active ' : '') + (running ? 'running' : '') +
        '" data-thread-id="' + escapeHtml(thread.id) + '">' +
        '<span class="thread-dot"></span>' +
        '<span class="thread-label" title="' + escapeHtml(thread.title || '新会话') + '">' +
        escapeHtml(thread.title || '新会话') + '</span>' +
        (thread.pinnedAt ? '<span class="thread-pin">📌</span>' : '') +
        '<span class="thread-time">' +
        escapeHtml(formatRelative(thread.updatedAt || thread.createdAt)) + '</span></div>';
    }).join('');
  }

  function renderHeader() {
    const thread = currentThread();
    els.threadTitle.textContent = thread ? (thread.title || '新会话') : '新会话';
    els.threadTitle.title = els.threadTitle.textContent;
    const status = thread ? (thread.status || 'IDLE') : 'IDLE';
    els.threadStatus.textContent = statusLabel(status);
    els.threadStatus.className = 'status-badge ' +
      (status === 'RUNNING' ? 'running' : status === 'ERROR' ? 'error' : 'idle');
    if (thread && thread.model) {
      ensureSelectValue(els.modelSelect, thread.model);
      els.modelSelect.value = thread.model;
    } else if (state.config && state.config.defaultModel) {
      els.modelSelect.value = state.config.defaultModel;
    }
    if (thread && thread.permissionMode) {
      els.permissionSelect.value = thread.permissionMode;
    }
    els.modelSelect.disabled = !thread;
    els.permissionSelect.disabled = !thread;
    els.pinBtn.disabled = !thread;
    els.archiveBtn.disabled = !thread;
    els.pinBtn.style.opacity = thread ? '1' : '0.45';
    els.archiveBtn.style.opacity = thread ? '1' : '0.45';
  }

  function renderTimeline() {
    const stick = isNearBottom();
    if (!state.selectedThreadId) {
      els.messageList.innerHTML = emptyStateHtml(false);
      return;
    }
    const groups = buildTurnGroups(state.timeline.messages, state.timeline.activities);
    if (!groups.length) {
      els.messageList.innerHTML = emptyStateHtml(true);
      return;
    }
    let html = '<div class="message-list-inner">';
    groups.forEach(function (group) {
      html += renderTurn(group);
    });
    html += '</div>';
    els.messageList.innerHTML = html;
    if (stick) scrollToBottom();
  }

  function emptyStateHtml(withThread) {
    let description;
    if (!state.selectedWorkspaceId) {
      description = '请先在左侧选择或新建一个工作区。';
    } else if (withThread) {
      description = '发送第一条消息，开始与 Copilot 协作。';
    } else {
      description = '描述你想实现的功能、要修复的 Bug，或粘贴一段代码。' +
        'Copilot 会在当前工作区内读取、修改文件并运行命令。';
    }
    return '<div class="empty-state"><div class="empty-glyph">⌘</div>' +
      '<h2>开始一个编码任务</h2><p>' + description + '</p></div>';
  }

  function buildTurnGroups(messages, activities) {
    const groups = [];
    const byKey = new Map();

    function ensure(key, time) {
      let group = byKey.get(key);
      if (!group) {
        group = { key: key, time: null, userMessages: [], assistantMessages: [], activities: [] };
        byKey.set(key, group);
        groups.push(group);
      }
      if (time && (!group.time || new Date(time) < new Date(group.time))) {
        group.time = time;
      }
      return group;
    }

    (messages || []).slice().sort(function (left, right) {
      return new Date(left.createdAt || 0).getTime() - new Date(right.createdAt || 0).getTime();
    }).forEach(function (message) {
      const group = ensure(message.turnId || message.id, message.createdAt);
      if (message.role === 'user') group.userMessages.push(message);
      else group.assistantMessages.push(message);
    });

    (activities || []).forEach(function (activity) {
      const group = ensure(activity.turnId || activity.id, activity.startedAt);
      group.activities.push(activity);
    });

    groups.sort(function (left, right) {
      return new Date(left.time || 0).getTime() - new Date(right.time || 0).getTime();
    });
    return groups;
  }

  function renderTurn(group) {
    let html = '<div class="turn" data-turn-id="' + escapeHtml(group.key) + '">';
    group.userMessages.forEach(function (message) {
      html += renderUserMessage(message);
    });
    group.activities.slice().sort(function (left, right) {
      return new Date(left.startedAt || 0).getTime() - new Date(right.startedAt || 0).getTime();
    }).forEach(function (activity) {
      html += renderActivity(activity);
    });
    group.assistantMessages.forEach(function (message) {
      html += renderAssistantMessage(message);
    });
    return html + '</div>';
  }

  function renderUserMessage(message) {
    return '<div class="message user"><div class="message-avatar">你</div>' +
      '<div class="message-body"><div class="markdown">' +
      renderMarkdown(message.content) + '</div></div></div>';
  }

  function renderAssistantMessage(message) {
    const streaming = message.status === 'streaming';
    const reasoning = message.reasoning && String(message.reasoning).trim();
    const content = message.content && String(message.content).trim();
    const tokens = [];
    if (message.inputTokens) tokens.push('↑' + message.inputTokens);
    if (message.outputTokens) tokens.push('↓' + message.outputTokens);
    let meta = '<div class="message-meta"><strong>Copilot</strong>';
    if (message.model) meta += '<span>' + escapeHtml(message.model) + '</span>';
    if (tokens.length) meta += '<span>' + escapeHtml(tokens.join(' ')) + '</span>';
    meta += '</div>';
    let body;
    if (content) {
      body = '<div class="markdown">' + renderMarkdown(message.content) + '</div>';
    } else if (streaming) {
      body = '<div class="typing"><i></i><i></i><i></i></div>';
    } else {
      body = '<div class="markdown"><p class="text-muted">（无内容）</p></div>';
    }
    let reasoningHtml = '';
    if (reasoning) {
      reasoningHtml = '<details class="reasoning"' + (streaming ? ' open' : '') +
        '><summary>推理过程</summary><div class="markdown">' +
        renderMarkdown(message.reasoning) + '</div></details>';
    }
    return '<div class="message assistant" data-message-id="' +
      escapeHtml(message.id || '') + '"><div class="message-avatar">AI</div>' +
      '<div class="message-body">' + meta + reasoningHtml + body + '</div></div>';
  }

  function renderActivity(activity) {
    const status = activity.status || 'running';
    const statusText = status === 'complete' ? '完成' : status === 'error' ? '失败' : '运行中';
    const parts = [];
    if (activity.args) parts.push('参数\n' + prettyJson(activity.args));
    if (activity.progress && activity.progress.length) {
      parts.push('进度\n' + activity.progress.join('\n'));
    }
    if (activity.result) parts.push('结果\n' + prettyJson(activity.result));
    let details = '';
    if (parts.length) {
      details = '<details class="tool-details"><summary>详情</summary><pre>' +
        escapeHtml(parts.join('\n\n')) + '</pre></details>';
    }
    return '<div class="tool-card ' + escapeHtml(status) + '" data-tool-call-id="' +
      escapeHtml(activity.id || '') + '"><div class="tool-head"><span class="tool-icon">⚙</span>' +
      '<span class="tool-name" title="' + escapeHtml(activity.toolName || '') + '">' +
      escapeHtml(activity.toolName || 'tool') + '</span><span class="tool-status">' +
      statusText + '</span></div>' + details + '</div>';
  }

  function renderPermissionBar() {
    const pending = state.timeline.pendingPermissions || [];
    els.permissionBar.classList.toggle('hidden', pending.length === 0);
    if (!pending.length) {
      els.permissionBar.innerHTML = '';
      return;
    }
    els.permissionBar.innerHTML = pending.map(function (permission) {
      let html = '<div class="permission-card" data-permission-card="' +
        escapeHtml(permission.id) + '">';
      html += '<div class="permission-title"><span class="dot"></span><span>' +
        escapeHtml(permission.title || '需要权限确认') + '</span></div>';
      if (permission.description) {
        html += '<div class="permission-desc">' + escapeHtml(permission.description) + '</div>';
      }
      if (permission.argumentsJson) {
        html += '<pre>' + escapeHtml(prettyJson(permission.argumentsJson)) + '</pre>';
      }
      html += '<div class="permission-actions">' +
        '<button class="deny" type="button" data-permission-id="' + escapeHtml(permission.id) +
        '" data-decision="deny">拒绝</button>' +
        '<button class="approve" type="button" data-permission-id="' + escapeHtml(permission.id) +
        '" data-decision="approve">批准一次</button></div></div>';
      return html;
    }).join('');
  }

  function renderFileTree() {
    if (!state.selectedWorkspaceId) {
      els.fileTree.innerHTML = '<div class="tree-empty">请先选择工作区</div>';
      return;
    }
    if (!state.fileCache.has('')) {
      els.fileTree.innerHTML = '<div class="tree-empty">加载中…</div>';
      return;
    }
    els.fileTree.innerHTML = renderDirectory('', state.fileCache.get('') || []);
  }

  function renderDirectory(path, entries) {
    return entries.map(function (entry) {
      const isDirectory = entry.kind === 'directory';
      const expanded = state.expandedDirs.has(entry.path);
      const active = entry.path === state.selectedFilePath;
      let html = '<div class="file-row ' + (isDirectory ? 'directory ' : '') +
        (active ? 'active' : '') + '" data-file-path="' + escapeHtml(entry.path) +
        '" data-kind="' + escapeHtml(entry.kind) + '">' +
        '<span class="file-icon">' + (isDirectory ? (expanded ? '▾' : '▸') : '·') + '</span>' +
        '<span class="file-name" title="' + escapeHtml(entry.path) + '">' +
        escapeHtml(entry.name) + '</span></div>';
      if (isDirectory && expanded) {
        const children = state.fileCache.get(entry.path);
        html += '<div class="file-children">' +
          (children ? renderDirectory(entry.path, children) : '<div class="tree-empty">加载中…</div>') +
          '</div>';
      }
      return html;
    }).join('');
  }

  async function onFileTreeClick(event) {
    const row = event.target.closest('.file-row');
    if (!row) return;
    const path = row.getAttribute('data-file-path');
    if (row.getAttribute('data-kind') === 'directory') {
      await toggleDirectory(path);
    } else {
      await openFile(path);
    }
  }

  async function toggleDirectory(path) {
    if (!path) return;
    if (state.expandedDirs.has(path)) {
      state.expandedDirs.delete(path);
      renderFileTree();
      return;
    }
    state.expandedDirs.add(path);
    if (!state.fileCache.has(path)) {
      await loadFiles(path);
    } else {
      renderFileTree();
    }
  }

  async function loadFiles(path) {
    if (!state.selectedWorkspaceId) return;
    const workspaceId = state.selectedWorkspaceId;
    const query = path ? '?path=' + encodeURIComponent(path) : '';
    try {
      const entries = await api('/api/workspaces/' + workspaceId + '/files' + query);
      if (state.selectedWorkspaceId !== workspaceId) return;
      state.fileCache.set(path, entries || []);
      renderFileTree();
    } catch (error) {
      toast(error.message, 'error');
    }
  }

  async function refreshFiles() {
    if (!state.selectedWorkspaceId) return;
    const workspaceId = state.selectedWorkspaceId;
    const paths = [''].concat(Array.from(state.expandedDirs));
    try {
      for (const path of paths) {
        const query = path ? '?path=' + encodeURIComponent(path) : '';
        const entries = await api('/api/workspaces/' + workspaceId + '/files' + query);
        if (state.selectedWorkspaceId !== workspaceId) return;
        state.fileCache.set(path, entries || []);
      }
      if (state.selectedFilePath) await openFile(state.selectedFilePath);
      renderFileTree();
    } catch (error) {
      toast(error.message, 'error');
    }
  }

  async function openFile(path) {
    if (!state.selectedWorkspaceId) return;
    const workspaceId = state.selectedWorkspaceId;
    try {
      const file = await api('/api/workspaces/' + workspaceId + '/file?path=' + encodeURIComponent(path));
      if (state.selectedWorkspaceId !== workspaceId) return;
      state.selectedFilePath = path;
      els.fileViewerPath.textContent = file.path || path;
      els.fileViewerContent.textContent = file.content == null ? '' : file.content;
      els.fileViewer.classList.remove('hidden');
      renderFileTree();
    } catch (error) {
      toast(error.message, 'error');
    }
  }

  function hideFileViewer() {
    state.selectedFilePath = null;
    els.fileViewer.classList.add('hidden');
    els.fileViewerPath.textContent = '';
    els.fileViewerContent.textContent = '';
  }

  async function createWorkspace(form) {
    const data = new FormData(form);
    const payload = {
      name: String(data.get('name') || '').trim(),
      path: String(data.get('path') || '').trim() || null,
      description: String(data.get('description') || '').trim() || null
    };
    if (!payload.name) {
      toast('请输入工作区名称', 'error');
      return;
    }
    try {
      const workspace = await api('/api/workspaces', { method: 'POST', body: payload });
      state.workspaces.push(workspace);
      sortWorkspaces();
      els.workspaceDialog.close();
      await selectWorkspace(workspace.id);
      toast('工作区已创建', 'success');
    } catch (error) {
      toast(error.message, 'error');
    }
  }

  async function deleteWorkspace(workspaceId) {
    if (!workspaceId) return;
    if (!window.confirm('确定删除这个工作区记录吗？磁盘上的文件不会被删除。')) return;
    try {
      await api('/api/workspaces/' + workspaceId, { method: 'DELETE' });
      state.workspaces = state.workspaces.filter(function (workspace) {
        return workspace.id !== workspaceId;
      });
      if (state.selectedWorkspaceId === workspaceId) {
        if (state.ws && state.ws.readyState === WebSocket.OPEN && state.selectedThreadId) {
          state.ws.send(JSON.stringify({ type: 'unsubscribe', threadId: state.selectedThreadId }));
        }
        state.selectedWorkspaceId = null;
        state.selectedThreadId = null;
        state.timeline = emptyTimeline();
        state.threads = [];
        state.fileCache = new Map();
        state.expandedDirs = new Set();
        hideFileViewer();
        if (state.workspaces.length) await selectWorkspace(state.workspaces[0].id);
      }
      renderAll();
      toast('工作区已删除', 'success');
    } catch (error) {
      toast(error.message, 'error');
    }
  }

  async function createThread(form) {
    if (!state.selectedWorkspaceId) {
      openWorkspaceDialog();
      return;
    }
    const data = new FormData(form);
    const payload = {
      title: String(data.get('title') || '').trim() || null,
      model: String(data.get('model') || '').trim() || null,
      permissionMode: String(data.get('permissionMode') || 'request')
    };
    try {
      const thread = await api('/api/workspaces/' + state.selectedWorkspaceId + '/threads', {
        method: 'POST',
        body: payload
      });
      upsertThread(thread);
      els.threadDialog.close();
      await selectThread(thread.id);
      toast('会话已创建', 'success');
    } catch (error) {
      toast(error.message, 'error');
    }
  }

  async function saveApiKey(form) {
    const data = new FormData(form);
    const key = String(data.get('apiKey') || '').trim();
    if (!key) {
      toast('请输入 API Key', 'error');
      return;
    }
    state.apiKey = key;
    localStorage.setItem(STORAGE_API_KEY, key);
    els.apiKeyDialog.close();
    toast('API Key 已保存', 'success');
    if (!state.config) await loadConfig();
    await bootstrapData();
  }

  async function patchThread(payload) {
    const thread = currentThread();
    if (!thread) return;
    try {
      const updated = await api('/api/threads/' + thread.id, {
        method: 'PATCH',
        body: payload
      });
      upsertThread(updated);
      if (state.selectedThreadId === updated.id) {
        state.timeline.thread = Object.assign({}, state.timeline.thread || {}, updated);
      }
      renderAll();
    } catch (error) {
      toast(error.message, 'error');
    }
  }

  async function archiveCurrentThread() {
    const thread = currentThread();
    if (!thread) return;
    if (!window.confirm('归档该会话？归档后会从列表中隐藏。')) return;
    try {
      await api('/api/threads/' + thread.id, {
        method: 'PATCH',
        body: { archived: true }
      });
      state.threads = state.threads.filter(function (item) { return item.id !== thread.id; });
      if (state.selectedThreadId === thread.id) {
        if (state.ws && state.ws.readyState === WebSocket.OPEN) {
          state.ws.send(JSON.stringify({ type: 'unsubscribe', threadId: thread.id }));
        }
        state.selectedThreadId = null;
        state.timeline = emptyTimeline();
      }
      renderAll();
      toast('会话已归档', 'success');
    } catch (error) {
      toast(error.message, 'error');
    }
  }

  async function sendMessage() {
    const content = els.promptInput.value.trim();
    if (!content) return;
    if (!state.selectedWorkspaceId) {
      openWorkspaceDialog();
      return;
    }
    const thread = currentThread();
    if (thread && thread.status === 'RUNNING') {
      toast('当前会话正在执行，请稍候', 'error');
      return;
    }
    els.sendBtn.disabled = true;
    try {
      let threadId = state.selectedThreadId;
      if (!threadId) {
        const title = content.replace(/\s+/g, ' ').slice(0, 40) || '新会话';
        const created = await api('/api/workspaces/' + state.selectedWorkspaceId + '/threads', {
          method: 'POST',
          body: {
            title: title,
            model: els.modelSelect.value || null,
            permissionMode: els.permissionSelect.value || 'request'
          }
        });
        upsertThread(created);
        await selectThread(created.id);
        threadId = created.id;
      }
      await api('/api/threads/' + threadId + '/messages', {
        method: 'POST',
        body: {
          content: content,
          model: els.modelSelect.value || null
        }
      });
      els.promptInput.value = '';
      autoResize();
      if (!state.wsConnected) await loadTimeline(threadId);
      updateComposer();
      scrollToBottom(true);
    } catch (error) {
      toast(error.message, 'error');
      updateComposer();
    }
  }

  async function stopCurrent() {
    if (!state.selectedThreadId) return;
    try {
      await api('/api/threads/' + state.selectedThreadId + '/abort', { method: 'POST' });
      state.runningThreads.delete(state.selectedThreadId);
      if (state.timeline.thread) state.timeline.thread.status = 'IDLE';
      renderAll();
      toast('已请求中止当前回合');
    } catch (error) {
      toast(error.message, 'error');
    }
  }

  async function resolvePermission(permissionId, approve) {
    try {
      await api('/api/permissions/' + permissionId + '/resolve', {
        method: 'POST',
        body: {
          approve: approve,
          feedback: approve ? null : '用户拒绝该操作'
        }
      });
      state.timeline.pendingPermissions = (state.timeline.pendingPermissions || []).filter(function (item) {
        return item.id !== permissionId;
      });
      renderAll();
      toast(approve ? '已批准权限请求' : '已拒绝权限请求', approve ? 'success' : 'info');
    } catch (error) {
      toast(error.message, 'error');
    }
  }

  function updateComposer() {
    const thread = currentThread();
    const running = Boolean((thread && thread.status === 'RUNNING') ||
      (state.selectedThreadId && state.runningThreads.has(state.selectedThreadId)));
    els.sendBtn.classList.toggle('hidden', running);
    els.stopBtn.classList.toggle('hidden', !running);
    els.sendBtn.disabled = running || !state.selectedWorkspaceId;
    els.stopBtn.disabled = !running;
    els.promptInput.disabled = !state.selectedWorkspaceId;
    els.composerHint.textContent = hintText();
  }

  function hintText() {
    if (!state.selectedWorkspaceId) return '请选择一个工作区';
    if (!state.wsConnected) return 'Copilot · 连接中…';
    if (state.config && state.config.copilotEnabled === false) return 'Copilot · 已禁用';
    if (state.config && state.config.copilotStatus &&
        state.config.copilotStatus !== 'ready' && state.config.copilotStatus !== 'not_started') {
      return 'Copilot · ' + state.config.copilotStatus;
    }
    return 'Copilot · 已连接';
  }

  function currentThread() {
    if (state.timeline && state.timeline.thread &&
        state.timeline.thread.id === state.selectedThreadId) {
      return state.timeline.thread;
    }
    return state.threads.find(function (thread) {
      return thread.id === state.selectedThreadId;
    }) || null;
  }

  function toggleWorkspaceMenu() {
    els.workspaceMenu.classList.toggle('hidden');
  }

  function hideWorkspaceMenu() {
    els.workspaceMenu.classList.add('hidden');
  }

  async function onWorkspaceMenuClick(event) {
    const deleteButton = event.target.closest('[data-delete-workspace]');
    if (deleteButton) {
      event.stopPropagation();
      await deleteWorkspace(deleteButton.getAttribute('data-delete-workspace'));
      return;
    }
    if (event.target.closest('[data-new-workspace]')) {
      hideWorkspaceMenu();
      openWorkspaceDialog();
      return;
    }
    const item = event.target.closest('[data-workspace-id]');
    if (item) {
      hideWorkspaceMenu();
      await selectWorkspace(item.getAttribute('data-workspace-id'));
    }
  }

  function openWorkspaceDialog() {
    hideWorkspaceMenu();
    closeDialog(els.threadDialog);
    if (!els.workspaceDialog.open) {
      els.workspaceForm.reset();
      els.workspaceDialog.showModal();
    }
  }

  function openThreadDialog() {
    if (!state.selectedWorkspaceId) {
      toast('请先选择工作区', 'error');
      openWorkspaceDialog();
      return;
    }
    closeDialog(els.workspaceDialog);
    if (!els.threadDialog.open) {
      els.threadForm.reset();
      els.threadDialog.showModal();
    }
  }

  function openApiKeyDialog() {
    closeDialog(els.workspaceDialog);
    closeDialog(els.threadDialog);
    if (!els.apiKeyDialog.open) {
      const input = els.apiKeyForm.elements.apiKey;
      if (input) input.value = state.apiKey || '';
      els.apiKeyDialog.showModal();
    }
  }

  function closeDialog(dialog) {
    if (dialog && dialog.open) dialog.close();
  }

  function normalizeTimeline(data) {
    const timeline = data || {};
    return {
      thread: timeline.thread || null,
      messages: Array.isArray(timeline.messages) ? timeline.messages : [],
      activities: Array.isArray(timeline.activities) ? timeline.activities : [],
      pendingPermissions: Array.isArray(timeline.pendingPermissions) ? timeline.pendingPermissions : []
    };
  }

  function emptyTimeline() {
    return { thread: null, messages: [], activities: [], pendingPermissions: [] };
  }

  function sortThreads(threads) {
    return threads.slice().sort(function (left, right) {
      const leftPinned = left.pinnedAt ? 1 : 0;
      const rightPinned = right.pinnedAt ? 1 : 0;
      if (leftPinned !== rightPinned) return rightPinned - leftPinned;
      return new Date(right.updatedAt || right.createdAt || 0).getTime() -
        new Date(left.updatedAt || left.createdAt || 0).getTime();
    });
  }

  function sortWorkspaces() {
    state.workspaces.sort(function (left, right) {
      const leftOrder = left.sortOrder == null ? 0 : left.sortOrder;
      const rightOrder = right.sortOrder == null ? 0 : right.sortOrder;
      if (leftOrder !== rightOrder) return leftOrder - rightOrder;
      return new Date(left.createdAt || 0).getTime() - new Date(right.createdAt || 0).getTime();
    });
  }

  function statusLabel(status) {
    if (status === 'RUNNING') return '运行中';
    if (status === 'ERROR') return '异常';
    if (status === 'IDLE') return '空闲';
    return status || '空闲';
  }

  function isNearBottom() {
    return els.messageList.scrollHeight - els.messageList.scrollTop -
      els.messageList.clientHeight < 140;
  }

  function scrollToBottom(force) {
    if (force || isNearBottom()) {
      els.messageList.scrollTop = els.messageList.scrollHeight;
    }
  }

  function autoResize() {
    const input = els.promptInput;
    if (!input) return;
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 220) + 'px';
  }

  function formatRelative(value) {
    if (!value) return '';
    const time = new Date(value).getTime();
    if (Number.isNaN(time)) return '';
    const diff = Date.now() - time;
    if (diff < 60000) return '刚刚';
    if (diff < 3600000) return Math.floor(diff / 60000) + ' 分钟前';
    if (diff < 86400000) return Math.floor(diff / 3600000) + ' 小时前';
    return new Date(value).toLocaleDateString('zh-CN', { month: '2-digit', day: '2-digit' });
  }

  function renderMarkdown(text) {
    if (text == null || text === '') return '';
    try {
      const html = window.marked.parse(String(text), { gfm: true, breaks: true });
      return window.DOMPurify.sanitize(html, { USE_PROFILES: { html: true } });
    } catch (error) {
      return '<p>' + escapeHtml(text).replace(/\n/g, '<br>') + '</p>';
    }
  }

  function prettyJson(value) {
    if (value == null) return '';
    let text = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
    if (typeof text === 'string' && text.length) {
      try {
        const parsed = JSON.parse(text);
        if (parsed !== null && typeof parsed === 'object') {
          text = JSON.stringify(parsed, null, 2);
        }
      } catch (error) {
        // Keep the original text.
      }
    }
    text = String(text);
    return text.length > MAX_JSON_PREVIEW ? text.slice(0, MAX_JSON_PREVIEW) + '\n…' : text;
  }

  function escapeHtml(value) {
    return String(value == null ? '' : value).replace(/[&<>"']/g, function (character) {
      switch (character) {
        case '&': return '&amp;';
        case '<': return '&lt;';
        case '>': return '&gt;';
        case '"': return '&quot;';
        case "'": return '&#39;';
        default: return character;
      }
    });
  }

  function toast(message, type, timeout) {
    const container = els.toastContainer;
    if (!container) return;
    const item = document.createElement('div');
    item.className = 'toast ' + (type || 'info');
    item.textContent = message;
    container.appendChild(item);
    window.setTimeout(function () {
      item.remove();
    }, timeout || 4000);
  }
})();
