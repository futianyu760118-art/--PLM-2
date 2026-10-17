// 本地电脑与文件访问适配器（只读）：列目录 / 读文件 / 搜文件 / 系统信息。
// 安全约定：
//   - 仅允许访问 LOCAL_FILES_ROOTS 白名单根目录内的路径（防目录穿越）；
//   - 只读：不提供写/删/命令执行（与执行面 readOnly 契约一致）；
//   - 限额：目录列表、搜索结果、读取内容均设上限，避免巨量输出。
import fs from 'node:fs';
import fsp from 'node:fs/promises';
import path from 'node:path';
import os from 'node:os';

const MAX_LIST_ENTRIES = 200;
const MAX_READ_BYTES = 64 * 1024;
const MAX_SEARCH_RESULTS = 100;
const MAX_SEARCH_DIRS = 2000;
const BINARY_EXT = /\.(exe|dll|zip|rar|7z|gz|tar|png|jpe?g|gif|bmp|webp|ico|pdf|docx?|xlsx?|pptx?|mp3|mp4|avi|mov|jar|class|so|dylib|bin|dat|db|sqlite3?)$/i;

function err(code, message) {
  const e = new Error(message);
  e.code = code;
  return e;
}

export function createLocalFilesAdapter({ roots }) {
  const rootList = (roots || '').split(',').map((s) => s.trim()).filter(Boolean);
  if (!rootList.length) rootList.push('/local/d');

  // 把用户输入 path 解析到某个白名单根内；越界直接拒绝
  function safeResolve(inputPath) {
    if (!inputPath) return rootList[0];
    const cleaned = String(inputPath).replace(/\\/g, '/').replace(/^\/+/, '');
    // 容器内路径（/local/d/...）或相对根路径（d/... / PLM-2/...）都归一到根内
    let rel = cleaned;
    for (const root of rootList) {
      const prefix = root.replace(/\/+$/, '') + '/';
      if (cleaned.startsWith(prefix)) { rel = cleaned.slice(prefix.length); break; }
    }
    const base = rootList[0];
    const abs = path.resolve(base, rel);
    const normBase = path.resolve(base);
    if (abs !== normBase && !abs.startsWith(normBase + path.sep)) {
      throw err('FORBIDDEN', `路径越出白名单根目录: ${inputPath}`);
    }
    return abs;
  }

  async function statSafe(p) {
    try { return await fsp.stat(p); } catch { return null; }
  }

  async function list(p) {
    const entries = await fsp.readdir(p, { withFileTypes: true });
    const out = [];
    for (const e of entries.slice(0, MAX_LIST_ENTRIES)) {
      const full = path.join(p, e.name);
      const st = await statSafe(full);
      out.push({
        name: e.name,
        type: e.isDirectory() ? 'dir' : e.isFile() ? 'file' : e.isSymbolicLink() ? 'link' : 'other',
        size: st ? st.size : null,
        modified: st ? st.mtime.toISOString() : null,
      });
    }
    return { path: p, count: entries.length, truncated: entries.length > MAX_LIST_ENTRIES, entries: out };
  }

  async function read(p, maxBytes) {
    const st = await statSafe(p);
    if (!st) throw err('NOT_FOUND', `文件不存在: ${p}`);
    if (!st.isFile()) throw err('INVALID_ARGUMENT', '目标不是文件');
    const limit = Math.min(maxBytes || MAX_READ_BYTES, MAX_READ_BYTES);
    const handle = await fsp.open(p, 'r');
    try {
      const buf = Buffer.alloc(limit + 1);
      const { bytesRead } = await handle.read(buf, 0, buf.length, 0);
      const content = buf.subarray(0, bytesRead);
      if (content.includes(0)) {
        return { path: p, size: st.size, binary: true, base64: content.subarray(0, limit).toString('base64').slice(0, MAX_READ_BYTES), truncated: bytesRead > limit };
      }
      return { path: p, size: st.size, binary: BINARY_EXT.test(p), content: content.subarray(0, limit).toString('utf8'), truncated: bytesRead > limit };
    } finally {
      await handle.close();
    }
  }

  async function search(root, pattern) {
    if (!pattern) throw err('INVALID_ARGUMENT', '搜索需要 pattern');
    const rx = new RegExp(pattern, 'i');
    const results = [];
    let visited = 0;
    async function walk(dir) {
      if (results.length >= MAX_SEARCH_RESULTS || visited >= MAX_SEARCH_DIRS) return;
      visited += 1;
      let entries;
      try { entries = await fsp.readdir(dir, { withFileTypes: true }); } catch { return; }
      for (const e of entries) {
        if (results.length >= MAX_SEARCH_RESULTS) return;
        if (rx.test(e.name)) {
          const full = path.join(dir, e.name);
          const st = await statSafe(full);
          results.push({ path: full, type: e.isDirectory() ? 'dir' : 'file', size: st ? st.size : null, modified: st ? st.mtime.toISOString() : null });
        }
        if (e.isDirectory() && !e.name.startsWith('.') && e.name !== 'node_modules' && e.name !== '$RECYCLE.BIN' && e.name !== 'System Volume Information') {
          await walk(path.join(dir, e.name));
        }
      }
    }
    await walk(root);
    return { root, pattern, count: results.length, truncated: visited >= MAX_SEARCH_DIRS, results };
  }

  async function info() {
    const disks = [];
    for (const root of rootList) {
      try {
        const st = fs.statfsSync(root);
        const total = Number(st.blocks) * Number(st.bsize);
        const free = Number(st.bavail) * Number(st.bsize);
        disks.push({ root, totalGB: +(total / 1024 ** 3).toFixed(1), freeGB: +(free / 1024 ** 3).toFixed(1) });
      } catch { /* ignore */ }
    }
    return {
      hostname: os.hostname(), platform: `${os.type()} ${os.release()} ${os.arch()}`,
      cpus: os.cpus().length, cpuModel: os.cpus()[0]?.model || '',
      memTotalGB: +(os.totalmem() / 1024 ** 3).toFixed(1), memFreeGB: +(os.freemem() / 1024 ** 3).toFixed(1),
      uptimeHours: +(os.uptime() / 3600).toFixed(1), disks,
      allowedRoots: rootList,
    };
  }

  return async function localFiles(request, abortSignal) {
    const input = request && typeof request === 'object' ? request : {};
    const action = input.action || 'list';
    switch (action) {
      case 'list': return list(safeResolve(input.path));
      case 'read': return read(safeResolve(input.path), input.maxBytes);
      case 'search': return search(safeResolve(input.path), input.pattern);
      case 'info': return info();
      default: throw err('INVALID_ARGUMENT', `未知 action: ${action}`);
    }
  };
}
