#!/usr/bin/env node
/**
 * EBMS → PLM2 用户/角色数据迁移工具（PAND-117 / ADR-1 / ADR-2 / ADR-3）
 *
 * 设计要点：
 *  - 只读 EBMS JSON 源，直接写 PLM2 PostgreSQL；不进入 PLM2 运行时代码。
 *  - 身份体系红线：不建第二套，全部并入 PLM2 现有 sys_* 表（ADR-1）。
 *  - 密码：scrypt 原串进 legacy_password（登录时透明重哈希）；明文源默认「内存中一次性
 *    BCrypt 哈希后落库」，绝不落盘/回显/写日志；口令材料不进入任何输出（ADR-2）。
 *  - 幂等：按 username / role_code 自然键 upsert；重复执行记录数不变（AC10.1）。
 *  - 回滚：按 run_id + source='EBMS' 精确删除本批数据，不触碰 PLM2 原生数据（AC10.2）。
 *  - 永不覆盖 PLM2 原生账号：用户名冲突时跳过并进冲突清单（AC8.3）。
 *
 * 用法：
 *   node ebms2plm-migrate.mjs --source <EBMS database/database 目录> [--dry-run] [--apply-schema]
 *   node ebms2plm-migrate.mjs --rollback <run_id>
 * 连接串：--db-url 或环境变量 PLM_DB_URL（未给则用 PG* 标准变量）。
 */

import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import { fileURLToPath } from 'node:url';
import pg from 'pg';
import bcrypt from 'bcryptjs';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const BCryptCost = 10; // 与 PLM2 BCryptPasswordEncoder 默认强度一致

/** ADR-3 角色映射默认表（业务可经 --role-map 覆盖） */
const DEFAULT_ROLE_MAP = {
  admin: 'ADMIN',
  sales_manager: 'SALES',
  sales: 'SALES',
  engineer: 'ENGINEER',
  purchase: 'EBMS_PURCHASE',
  finance: 'EBMS_FINANCE',
  project_manager: 'EBMS_PM',
  rd_manager: 'ENGINEER',
  viewer: 'EBMS_VIEWER',
};

/**
 * 映射到 PLM2 未内置角色时需要在 sys_role 内新建的角色（builtin=0，仍属同一 RBAC 体系，不违反红线）。
 * 权限码取自 PLM2 自有权限矩阵（database/02_seed_data.sql），不迁 EBMS 权限码。
 */
const NEW_ROLE_DEFS = {
  EBMS_PURCHASE: {
    name: '采购(EBMS迁移)',
    level: 2,
    dataScope: 2,
    perms: ['material', 'bom', 'material:export', 'bom:export'],
  },
  EBMS_FINANCE: {
    name: '财务(EBMS迁移)',
    level: 2,
    dataScope: 2,
    perms: ['bom', 'bom:export', 'material:export'],
  },
  EBMS_PM: {
    name: '项目经理(EBMS迁移)',
    level: 4,
    dataScope: 1,
    perms: ['material', 'ecn', 'bom', 'mold', 'quality', 'material:add', 'material:export', 'bom:export', 'ecn:add'],
  },
  EBMS_VIEWER: {
    name: '只读(EBMS迁移)',
    level: 1,
    dataScope: 1,
    perms: ['material', 'bom', 'quality', 'material:export', 'bom:export'],
  },
};

/** EBMS initData.js 中角色插入顺序即 id，用于解释 user_roles.role_id */
const EBMS_ROLE_ID_ORDER = [
  'admin', 'sales_manager', 'sales', 'engineer', 'purchase',
  'finance', 'project_manager', 'rd_manager', 'viewer',
];

// ---------------------------------------------------------------- CLI

function parseArgs(argv) {
  const args = { dryRun: false, applySchema: false, mode: 'hash-once' };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    const next = () => argv[++i];
    switch (a) {
      case '--source': args.source = next(); break;
      case '--db-url': args.dbUrl = next(); break;
      case '--rollback': args.rollback = next(); break;
      case '--role-map': args.roleMapPath = next(); break;
      case '--dept-map': args.deptMapPath = next(); break;
      case '--report': args.reportPath = next(); break;
      case '--mode': args.mode = next(); break;
      case '--dry-run': args.dryRun = true; break;
      case '--apply-schema': args.applySchema = true; break;
      case '--help': case '-h': args.help = true; break;
      default: throw new Error(`未知参数: ${a}`);
    }
  }
  return args;
}

const HELP = `EBMS → PLM2 用户迁移工具

  --source <dir>      EBMS 数据目录（含 users.json / user_roles.json / org_personnel.json）
  --db-url <url>      PLM2 PostgreSQL 连接串（默认取环境变量 PLM_DB_URL 或 PG* 变量）
  --dry-run           只出对账报告，事务结尾回滚，不产生副作用
  --apply-schema      执行前先套用 database/33_ebms_migration.sql（幂等）
  --mode <m>          hash-once（默认，明文源内存哈希，保原密码登录）
                      | force-reset（明文源置随机口令 + 强制改密，需管理员下发初始口令）
  --role-map <file>   覆盖默认角色映射（JSON: { "ebms角色码": "PLM2角色码" }）
  --dept-map <file>   部门映射（JSON: { "EBMS部门名": "PLM2 dept_code" }）；未映射不迁部门
  --report <file>     对账报告输出路径（JSON）
  --rollback <run_id> 按批次回滚（删除本批迁入数据）
`;

// ---------------------------------------------------------------- helpers

function readRecords(sourceDir, name) {
  const file = path.join(sourceDir, name);
  if (!fs.existsSync(file)) return null;
  const raw = JSON.parse(fs.readFileSync(file, 'utf8'));
  return Array.isArray(raw) ? raw : (raw.records ?? []);
}

/** 明文口令只在内存中短暂存在：哈希后立即丢弃引用，绝不打印/落盘 */
function hashPlaintext(plaintext) {
  return bcrypt.hashSync(String(plaintext), BCryptCost);
}

/** 随机 BCrypt 占位：保证 NOT NULL 且不可被猜测登录 */
function randomBcryptPlaceholder() {
  return bcrypt.hashSync(crypto.randomBytes(32).toString('hex'), BCryptCost);
}

function classifyPassword(stored) {
  if (stored === null || stored === undefined || String(stored) === '') return 'empty';
  return String(stored).startsWith('$scrypt$') ? 'scrypt' : 'plaintext';
}

function newRunId() {
  const ts = new Date().toISOString().replace(/[-:T.Z]/g, '').slice(0, 14);
  return `EBMS-${ts}-${crypto.randomBytes(3).toString('hex')}`;
}

function normalizeTableName(t) {
  if (typeof t !== 'string') return t;
  return t.replace(/[`"]/g, '').split('.').pop().toLowerCase();
}

// ---------------------------------------------------------------- DB

async function connect(args) {
  const url = args.dbUrl || process.env.PLM_DB_URL;
  // 未显式给出连接串时退回 pg 的 PG* 标准环境变量
  const client = new pg.Client(url ? { connectionString: url } : {});
  await client.connect();
  return client;
}

async function applySchema(client) {
  // 33 = 迁移支撑列/审计表；34 = sys_permission 序列修正（R1 首次应用层 INSERT 的前置条件）
  for (const name of ['33_ebms_migration.sql', '34_permission_seq_fix.sql']) {
    const sqlPath = path.resolve(__dirname, '..', '..', 'database', name);
    if (!fs.existsSync(sqlPath)) throw new Error(`找不到 schema 脚本: ${sqlPath}`);
    await client.query(fs.readFileSync(sqlPath, 'utf8'));
  }
}

async function assertSchemaReady(client) {
  const { rows } = await client.query(
    `SELECT column_name FROM information_schema.columns
      WHERE table_name = 'sys_user'
        AND column_name IN ('legacy_password','must_change_password','source','migration_run_id')`,
  );
  const found = new Set(rows.map((r) => r.column_name));
  const missing = ['legacy_password', 'must_change_password', 'source', 'migration_run_id']
    .filter((c) => !found.has(c));
  if (missing.length) {
    throw new Error(`sys_user 缺少迁移列: ${missing.join(', ')}；请先执行 database/33_ebms_migration.sql（或加 --apply-schema）`);
  }
}

// ---------------------------------------------------------------- 迁移主体

async function resolveRoles(client, sourceDir, roleMap, report) {
  const users = readRecords(sourceDir, 'users.json') ?? [];
  const userRoles = readRecords(sourceDir, 'user_roles.json') ?? [];

  // 角色 id → 码（EBMS initData.js 插入顺序）
  const idToCode = new Map(EBMS_ROLE_ID_ORDER.map((code, i) => [i + 1, code]));

  // 每个 EBMS 用户的目标 PLM2 角色集合（users.role 与 user_roles 双源合并）
  const targetByUser = new Map();
  const addRole = (userId, ebmsCode) => {
    if (!ebmsCode) return;
    const target = roleMap[ebmsCode];
    if (!target) {
      report.unmappedRoles.add(ebmsCode);
      return;
    }
    if (!targetByUser.has(userId)) targetByUser.set(userId, new Set());
    targetByUser.get(userId).add(target);
  };
  for (const u of users) addRole(u.id, u.role);
  for (const ur of userRoles) addRole(ur.user_id, idToCode.get(ur.role_id));

  // 确保 PLM2 角色存在
  const { rows: existing } = await client.query('SELECT id, role_code FROM sys_role');
  const roleIdByCode = new Map(existing.map((r) => [r.role_code, r.id]));

  for (const code of new Set([...targetByUser.values()].flatMap((s) => [...s]))) {
    if (roleIdByCode.has(code)) continue;
    const def = NEW_ROLE_DEFS[code];
    if (!def) {
      report.conflicts.push({ type: 'role_missing', role: code, note: '目标角色未内置且无新增定义' });
      continue;
    }
    const { rows } = await client.query(
      `INSERT INTO sys_role (role_code, role_name, role_level, data_scope, builtin, status, source, remark)
       VALUES ($1,$2,$3,$4,0,1,'EBMS',$5)
       ON CONFLICT (role_code) DO UPDATE SET updated_at = NOW()
       RETURNING id`,
      [code, def.name, def.level, def.dataScope, 'EBMS 迁移新增角色'],
    );
    roleIdByCode.set(code, rows[0].id);
    report.rolesCreated.push(code);

    // 按 PLM2 权限码授权（ADR-3：不迁 EBMS 权限码）
    for (const permCode of def.perms) {
      await client.query(
        `INSERT INTO sys_role_permission (role_id, permission_id)
         SELECT $1, id FROM sys_permission WHERE perm_code = $2
         ON CONFLICT DO NOTHING`,
        [rows[0].id, permCode],
      );
    }
  }

  // 记录映射表备查
  for (const [src, target] of Object.entries(roleMap)) {
    await client.query(
      `INSERT INTO sys_role_mapping (source_system, source_role, target_role, note)
       VALUES ('EBMS',$1,$2,'ADR-3 默认映射') ON CONFLICT (source_system, source_role) DO NOTHING`,
      [src, target],
    );
  }

  return { targetByUser, roleIdByCode };
}

/** sys_user.username 唯一（含逻辑删除行，唯一约束不区分 deleted），源内重名需先归并 */
function dedupeByUsername(users, report) {
  const byName = new Map();
  for (const u of users) {
    const key = u.username;
    if (!key) { byName.set(Symbol('anon'), u); continue; }
    if (byName.has(key)) {
      report.conflicts.push({ type: 'duplicate_username_in_source', username: key, droppedEbmsId: u.id });
      continue;
    }
    byName.set(key, u);
  }
  return [...byName.values()];
}

function buildEnrichmentIndex(sourceDir, report) {
  const personnel = readRecords(sourceDir, 'org_personnel.json')
    ?? readRecords(sourceDir, 'personnel.json') ?? [];
  const byName = new Map();
  for (const p of personnel) {
    if (!p.name) continue;
    const key = String(p.name).trim();
    if (!byName.has(key)) byName.set(key, p);
  }
  const byEmpCode = new Map();
  for (const p of personnel) {
    if (p.emp_code === undefined || p.emp_code === null || p.emp_code === '') continue;
    const key = String(p.emp_code).trim();
    if (!byEmpCode.has(key)) byEmpCode.set(key, p);
  }
  report.personnelTotal = personnel.length;
  return { byName, byEmpCode };
}

async function migrateUsers(client, args, sourceDir, roleMap, report, runId) {
  const users = dedupeByUsername(readRecords(sourceDir, 'users.json') ?? [], report);
  const { byName } = buildEnrichmentIndex(sourceDir, report);
  // resolveRoles 可能新建角色，故角色码→id 必须用它的返回值，不能依赖前置缓存
  const { targetByUser, roleIdByCode } = await resolveRoles(client, sourceDir, roleMap, report);

  const deptMap = args.deptMapPath
    ? JSON.parse(fs.readFileSync(args.deptMapPath, 'utf8'))
    : {};
  const { rows: deptRows } = await client.query('SELECT id, dept_code FROM sys_department');
  const deptIdByCode = new Map(deptRows.map((d) => [d.dept_code, d.id]));

  // 已存在的用户名 → {id, source}（含逻辑删除行，唯一约束不区分 deleted）
  const names = users.map((u) => u.username).filter(Boolean);
  const { rows: existingRows } = names.length
    ? await client.query('SELECT id, username, source, employee_no FROM sys_user WHERE username = ANY($1)', [names])
    : { rows: [] };
  const existingByName = new Map(existingRows.map((r) => [r.username, r]));

  const { rows: allEmp } = await client.query('SELECT id, username, employee_no FROM sys_user WHERE employee_no IS NOT NULL');
  const empOwner = new Map(allEmp.map((r) => [r.employee_no, r.username]));

  report.sourceUsers = users.length;
  let migrated = 0;

  for (const u of users) {
    if (!u.username) {
      report.conflicts.push({ type: 'user_no_username', ebmsId: u.id });
      report.skipped++;
      continue;
    }
    const prior = existingByName.get(u.username);
    if (prior && prior.source !== 'EBMS') {
      // AC8.3：绝不覆盖 PLM2 原生账号
      report.conflicts.push({ type: 'username_taken_by_native', username: u.username });
      report.skipped++;
      continue;
    }

    const pwdKind = classifyPassword(u.password);
    let password;
    let legacyPassword = null;
    let mustChange = 0;
    if (pwdKind === 'scrypt') {
      legacyPassword = String(u.password);
      password = randomBcryptPlaceholder();
      report.passwordBuckets.scrypt++;
    } else if (pwdKind === 'plaintext' && args.mode !== 'force-reset') {
      password = hashPlaintext(u.password); // 一次性哈希，明文即刻丢弃
      report.passwordBuckets.hashOnce++;
    } else {
      // 空口令或 force-reset：无可校验凭据，置随机占位 + 强制改密，由管理员下发初始口令
      password = randomBcryptPlaceholder();
      mustChange = 1;
      report.passwordBuckets[pwdKind === 'empty' ? 'empty' : 'forceReset']++;
      report.needAdminReset.push(u.username);
    }

    const person = byName.get(String(u.name ?? '').trim());

    // employee_no 唯一：被他人占用则放弃该字段并记账，不因此中断本批
    let employeeNo = person?.emp_code ? String(person.emp_code).trim() : null;
    if (employeeNo) {
      const owner = empOwner.get(employeeNo);
      if (owner && owner !== u.username) {
        report.conflicts.push({ type: 'employee_no_taken', username: u.username, employeeNo });
        employeeNo = null;
      }
    }

    let deptId = null;
    if (person?.department_name && deptMap[person.department_name]) {
      deptId = deptIdByCode.get(deptMap[person.department_name]) ?? null;
    }
    if (person && !deptId) report.unmappedDepartments.add(person.department_name ?? '(空)');
    if (!person) report.enrichmentMisses.push(u.username);

    if (prior) {
      await client.query(
        `UPDATE sys_user SET password=$2, legacy_password=$3, must_change_password=$4,
                real_name=$5, employee_no=COALESCE($6, employee_no), email=COALESCE($7, email),
                phone=COALESCE($8, phone), dept_id=COALESCE($9, dept_id),
                source='EBMS', migration_run_id=$10, updated_at=NOW()
          WHERE id=$1`,
        [prior.id, password, legacyPassword, mustChange, u.name ?? u.username,
          employeeNo, person?.email || null, person?.phone || null, deptId, runId],
      );
    } else {
      const { rows } = await client.query(
        `INSERT INTO sys_user (username, password, legacy_password, must_change_password, real_name,
                employee_no, email, phone, dept_id, status, source, migration_run_id, remark)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,1,'EBMS',$10,'EBMS 迁入')
         RETURNING id`,
        [u.username, password, legacyPassword, mustChange, u.name ?? u.username,
          employeeNo, person?.email || null, person?.phone || null, deptId, runId],
      );
      existingByName.set(u.username, { id: rows[0].id, username: u.username, source: 'EBMS' });
      if (employeeNo) empOwner.set(employeeNo, u.username);
    }

    const userId = existingByName.get(u.username).id;
    for (const roleCode of targetByUser.get(u.id) ?? []) {
      const roleId = roleIdByCode.get(roleCode);
      if (!roleId) continue;
      await client.query(
        'INSERT INTO sys_user_role (user_id, role_id) VALUES ($1,$2) ON CONFLICT DO NOTHING',
        [userId, roleId],
      );
      report.userRoleLinks++;
    }
    migrated++;
  }

  report.targetUsers = migrated;
}

// ---------------------------------------------------------------- 回滚

async function rollback(client, runId, report) {
  const { rows: users } = await client.query(
    "SELECT id, username FROM sys_user WHERE source='EBMS' AND migration_run_id=$1", [runId]);
  if (!users.length) {
    report.rollback = { runId, deletedUsers: 0, note: '未找到该批次迁入用户（可能已回滚）' };
    return;
  }
  const ids = users.map((u) => u.id);
  await client.query('DELETE FROM sys_user_role WHERE user_id = ANY($1)', [ids]);
  await client.query('DELETE FROM sys_user WHERE id = ANY($1)', [ids]);
  // 仅回收本批新增且已无人引用的角色，PLM2 内置角色(builtin=1)不动
  const { rows: dropped } = await client.query(
    `DELETE FROM sys_role r
      WHERE r.source='EBMS' AND r.builtin=0
        AND NOT EXISTS (SELECT 1 FROM sys_user_role ur WHERE ur.role_id = r.id)
      RETURNING role_code`);
  await client.query(
    "UPDATE migration_run SET status='ROLLED_BACK', rolled_back_at=NOW() WHERE run_id=$1", [runId]);
  report.rollback = { runId, deletedUsers: ids.length, droppedRoles: dropped.map((r) => r.role_code) };
}

// ---------------------------------------------------------------- main

async function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) { process.stdout.write(HELP); return; }

  const report = {
    runId: null, dryRun: args.dryRun, mode: args.mode,
    sourceUsers: 0, targetUsers: 0, skipped: 0,
    conflictCount: 0, passwordBuckets: { scrypt: 0, hashOnce: 0, forceReset: 0, empty: 0 },
    userRoleLinks: 0, rolesCreated: [], conflicts: [], needAdminReset: [],
    unmappedRoles: new Set(), unmappedDepartments: new Set(), enrichmentMisses: [],
    personnelTotal: 0,
  };

  const client = await connect(args);
  try {
    if (args.applySchema) await applySchema(client);

    if (args.rollback) {
      await assertSchemaReady(client);
      await client.query('BEGIN');
      await rollback(client, args.rollback, report);
      if (args.dryRun) { await client.query('ROLLBACK'); } else { await client.query('COMMIT'); }
      emit(report, args);
      return;
    }

    if (!args.source) throw new Error('缺少 --source（EBMS 数据目录）');
    if (!fs.existsSync(args.source)) throw new Error(`--source 目录不存在: ${args.source}`);
    await assertSchemaReady(client);

    const roleMap = args.roleMapPath
      ? JSON.parse(fs.readFileSync(args.roleMapPath, 'utf8'))
      : DEFAULT_ROLE_MAP;

    const runId = newRunId();
    report.runId = runId;

    await client.query('BEGIN');
    await client.query(
      `INSERT INTO migration_run (run_id, source_system, mode, dry_run, phase, status)
       VALUES ($1,'EBMS',$2,$3,'users','RUNNING')`,
      [runId, args.mode, args.dryRun ? 1 : 0],
    );

    await migrateUsers(client, args, args.source, roleMap, report, runId);

    report.conflictCount = report.conflicts.length;
    await client.query(
      `UPDATE migration_run SET phase='done', source_count=$2, target_count=$3, conflict_count=$4,
              skipped_count=$5, detail=$6, finished_at=NOW(), status=$7 WHERE run_id=$1`,
      [runId, report.sourceUsers, report.targetUsers, report.conflictCount, report.skipped,
        JSON.stringify({
          passwordBuckets: report.passwordBuckets,
          rolesCreated: report.rolesCreated,
          unmappedRoles: [...report.unmappedRoles],
        }),
        args.dryRun ? 'DRY_RUN' : 'SUCCESS'],
    );

    if (args.dryRun) {
      await client.query('ROLLBACK');
    } else {
      await client.query('COMMIT');
    }
    emit(report, args);
  } catch (err) {
    try { await client.query('ROLLBACK'); } catch { /* 连接已失效时忽略 */ }
    throw err;
  } finally {
    await client.end();
  }
}

function emit(report, args) {
  const serializable = {
    ...report,
    unmappedRoles: [...report.unmappedRoles],
    unmappedDepartments: [...report.unmappedDepartments],
  };
  if (args.reportPath) {
    fs.writeFileSync(args.reportPath, JSON.stringify(serializable, null, 2), 'utf8');
  }
  const lines = [
    `批次 run_id        : ${report.runId ?? '-'}${report.dryRun ? '  (DRY-RUN，已回滚)' : ''}`,
    `模式 mode          : ${report.mode}`,
    `源用户数           : ${report.sourceUsers}`,
    `迁入(新增+更新)     : ${report.targetUsers}`,
    `跳过               : ${report.skipped}`,
    `冲突               : ${report.conflictCount}`,
    `用户-角色关联写入   : ${report.userRoleLinks}`,
    `新建角色           : ${report.rolesCreated.join(', ') || '(无)'}`,
    `口令处理           : scrypt=${report.passwordBuckets.scrypt} 明文一次性哈希=${report.passwordBuckets.hashOnce} 强制重置=${report.passwordBuckets.forceReset} 空=${report.passwordBuckets.empty}`,
    `人员富化命中        : ${report.sourceUsers - report.enrichmentMisses.length}/${report.sourceUsers}`,
  ];
  if (report.rollback) {
    lines.push(`回滚               : 删除用户 ${report.rollback.deletedUsers}，回收角色 ${(report.rollback.droppedRoles ?? []).join(', ') || '(无)'}`);
  }
  if (report.needAdminReset.length) {
    lines.push(`需管理员下发初始口令: ${report.needAdminReset.join(', ')}`);
  }
  if (report.unmappedRoles.size) lines.push(`未映射 EBMS 角色    : ${[...report.unmappedRoles].join(', ')}`);
  if (report.conflicts.length) {
    lines.push('冲突明细:');
    for (const c of report.conflicts) lines.push(`  - ${JSON.stringify(c)}`);
  }
  process.stdout.write(lines.join('\n') + '\n');
}

main().catch((err) => {
  process.stderr.write(`迁移失败: ${err.message}\n`);
  process.exitCode = 1;
});
