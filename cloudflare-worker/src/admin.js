function json(data, status = 200) {
  return new Response(JSON.stringify(data), { status, headers: {
    "content-type": "application/json; charset=utf-8", "cache-control": "no-store",
    "x-content-type-options": "nosniff", "access-control-allow-origin": "*",
    "access-control-allow-methods": "GET, POST, DELETE, OPTIONS",
    "access-control-allow-headers": "content-type, authorization"
  }});
}
function validName(v) { return typeof v === "string" && v.length > 0 && v.length <= 64 && /^[A-Za-z0-9_.@-]+$/.test(v); }
function validType(v) { return v === "hotspot" || v === "usermanager" ? v : null; }
function authorized(req, env) {
  const expected = env.ADMIN_TOKEN || "";
  const auth = req.headers.get("authorization") || "";
  const supplied = auth.startsWith("Bearer ") ? auth.slice(7) : "";
  if (expected.length < 32 || supplied.length !== expected.length) return false;
  let diff = 0;
  for (let i = 0; i < expected.length; i++) diff |= expected.charCodeAt(i) ^ supplied.charCodeAt(i);
  return diff === 0;
}
async function readBody(req) {
  if (Number(req.headers.get("content-length") || "0") > 4096) throw new Error("BODY_TOO_LARGE");
  const raw = await req.text();
  if (raw.length > 4096) throw new Error("BODY_TOO_LARGE");
  return JSON.parse(raw);
}
async function withRouter(env, routerClient, action) {
  const client = await routerClient(env);
  try { return await action(client); } finally { await client.close(); }
}
async function listProfiles(client, type) {
  const path = type === "hotspot" ? "/ip/hotspot/user/profile/print" : "/user-manager/profile/print";
  const rows = await client.command([path, ".proplist=name"]);
  return rows.filter(x => typeof x.name === "string").map(x => ({ name: x.name }));
}
async function listUsers(client, type) {
  if (type === "hotspot") {
    const rows = await client.command(["/ip/hotspot/user/print", ".proplist=.id,name,profile,disabled"]);
    return rows.filter(x => typeof x.name === "string").map(x => ({
      username: x.name, profile: x.profile || "", disabled: x.disabled === "true"
    }));
  }
  const users = await client.command(["/user-manager/user/print", ".proplist=.id,name,disabled"]);
  const links = await client.command(["/user-manager/user-profile/print", ".proplist=.id,user,profile,state,end-time"]);
  const map = new Map();
  for (const link of links) {
    if (!link.user) continue;
    const items = map.get(link.user) || [];
    items.push({ profile: link.profile || "", state: link.state || "", endTime: link["end-time"] || "" });
    map.set(link.user, items);
  }
  return users.filter(x => typeof x.name === "string").map(x => ({
    username: x.name, profiles: map.get(x.name) || [], disabled: x.disabled === "true"
  }));
}
async function createUser(client, type, body) {
  const username = typeof body.username === "string" ? body.username.trim() : "";
  const password = typeof body.password === "string" ? body.password : "";
  const profile = typeof body.profile === "string" ? body.profile : "";
  if (!validName(username) || !validName(profile) || password.length < 1 || password.length > 128)
    return { error: "نام کاربری، رمز یا پروفایل معتبر نیست.", status: 400 };
  const userPath = type === "hotspot" ? "/ip/hotspot/user/print" : "/user-manager/user/print";
  const existing = await client.command([userPath, "?name=" + username, ".proplist=name"]);
  if (existing.some(x => x.name === username))
    return { error: "این نام کاربری از قبل وجود دارد؛ کاربر قبلی تغییر داده نشد.", status: 409 };
  const profiles = await listProfiles(client, type);
  if (!profiles.some(x => x.name === profile))
    return { error: "پروفایل انتخاب‌شده در روتر وجود ندارد.", status: 400 };
  if (type === "hotspot") {
    await client.command(["/ip/hotspot/user/add", "=name=" + username, "=password=" + password, "=profile=" + profile]);
    return { ok: true, username, profile, type };
  }
  await client.command(["/user-manager/user/add", "=name=" + username, "=password=" + password]);
  try {
    const created = await client.command(["/user-manager/user/print", "?name=" + username, ".proplist=.id,name"]);
    const user = created.find(x => x.name === username && x[".id"]);
    if (!user) throw new Error("USER_MANAGER_USER_ID_NOT_FOUND");
    await client.command(["/user-manager/user-profile/add", "=user=" + username, "=profile=" + profile]);
  } catch (error) {
    try {
      const created = await client.command(["/user-manager/user/print", "?name=" + username, ".proplist=.id,name"]);
      const user = created.find(x => x.name === username && x[".id"]);
      if (user) await client.command(["/user-manager/user/remove", "=.id=" + user[".id"]]);
    } catch {}
    throw error;
  }
  return { ok: true, username, profile, type };
}
async function deleteUser(client, type, username) {
  if (!validName(username)) return { error: "نام کاربری معتبر نیست.", status: 400 };
  if (type === "hotspot") {
    const rows = await client.command(["/ip/hotspot/user/print", "?name=" + username, ".proplist=.id,name"]);
    const user = rows.find(x => x.name === username && x[".id"]);
    if (!user) return { error: "کاربر پیدا نشد.", status: 404 };
    const active = await client.command(["/ip/hotspot/active/print", "?user=" + username, ".proplist=.id"]);
    for (const session of active) if (session[".id"])
      await client.command(["/ip/hotspot/active/remove", "=.id=" + session[".id"]]);
    await client.command(["/ip/hotspot/user/remove", "=.id=" + user[".id"]]);
    return { ok: true, username, type };
  }
  const rows = await client.command(["/user-manager/user/print", "?name=" + username, ".proplist=.id,name"]);
  const user = rows.find(x => x.name === username && x[".id"]);
  if (!user) return { error: "کاربر پیدا نشد.", status: 404 };
  const assignments = await client.command(["/user-manager/user-profile/print", "?user=" + username, ".proplist=.id"]);
  for (const item of assignments) if (item[".id"])
    await client.command(["/user-manager/user-profile/remove", "=.id=" + item[".id"]]);
  await client.command(["/user-manager/user/remove", "=.id=" + user[".id"]]);
  return { ok: true, username, type };
}
const adminHtml = [
'<!doctype html>','<html lang="fa" dir="rtl"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><meta name="color-scheme" content="dark"><title>AHMADI-NET MANAGER</title>',
'<style>*{box-sizing:border-box}body{margin:0;background:#0d1425;color:#f4f6ff;font-family:Tahoma,Arial,sans-serif;padding:16px}main{max-width:920px;margin:20px auto}header,.panel{background:#17233b;border:1px solid #34425f;border-radius:16px;padding:18px;margin-bottom:14px}h1{font-size:23px;margin:0 0 8px}.muted{color:#b9c5dc;font-size:13px;line-height:1.8}.grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:12px}.field{margin:12px 0}label{display:block;font-size:13px;margin-bottom:7px}input,select,button{font:inherit;width:100%;padding:12px;border-radius:10px;border:1px solid #52617d}input,select{background:#0d172b;color:#fff}button{border:0;background:#7165e8;color:#fff;font-weight:bold;cursor:pointer}button:disabled{opacity:.5}.secondary{background:#304362}.danger{background:#a83e4b}.tabs{display:flex;gap:8px;flex-wrap:wrap}.tabs button{width:auto;flex:1;min-width:130px}.tabs button.active{background:#16856b}.message{padding:12px;border-radius:10px;margin-top:12px;display:none;white-space:pre-wrap;background:#53303a}.message.ok{background:#174638}.user{display:flex;gap:10px;align-items:center;justify-content:space-between;padding:12px 0;border-bottom:1px solid #34425f}.user small{display:block;color:#b9c5dc;margin-top:5px}.user button{width:auto;min-width:86px;padding:9px}.hidden{display:none}@media(max-width:520px){body{padding:9px}.user{align-items:flex-start}.user button{min-width:72px}}</style></head><body><main>',
'<header><h1>AHMADI-NET MANAGER</h1><div class="muted">مدیریت محدود کاربران روتر RB951؛ فقط ساخت، مشاهده و حذف کاربران. تنظیمات شبکه و پروفایل‌ها قابل تغییر نیستند.</div></header>',
'<section class="panel"><div class="field"><label for="token">رمز دسترسی فروشنده</label><input id="token" type="password" autocomplete="off" placeholder="ADMIN_TOKEN" minlength="32"></div><button id="connect">اتصال و بارگیری اطلاعات</button><div id="message" class="message" role="status"></div></section>',
'<section id="workspace" class="hidden"><section class="panel"><div class="tabs"><button id="hotspotTab" class="active" type="button">Hotspot Users</button><button id="umTab" class="secondary" type="button">User Manager</button></div></section>',
'<section class="panel"><h2>ساخت کاربر</h2><div class="grid"><div class="field"><label for="username">نام کاربری</label><input id="username" maxlength="64" autocomplete="off" placeholder="مثلاً 930001"></div><div class="field"><label for="password">رمز کاربر</label><input id="password" maxlength="128" autocomplete="off" placeholder="رمز مشتری"></div></div><div class="field"><label for="profile">پروفایل موجود</label><select id="profile"><option value="">ابتدا اتصال را انجام دهید</option></select></div><button id="create">ساخت کاربر</button></section>',
'<section class="panel"><div style="display:flex;justify-content:space-between;align-items:center;gap:12px"><h2 style="margin:0">فهرست کاربران</h2><button id="refresh" class="secondary" style="width:auto">تازه‌سازی</button></div><div id="users" class="muted" style="margin-top:12px">برای بارگیری، ابتدا اتصال را انجام دهید.</div></section></section></main>',
'<script>const $=id=>document.getElementById(id);let token="";let type="hotspot";',
'function msg(t,ok=false){const e=$("message");e.textContent=t;e.className="message"+(ok?" ok":"");e.style.display="block"}',
'async function api(path,options={}){const h=Object.assign({"authorization":"Bearer "+token},options.headers||{});if(options.body)h["content-type"]="application/json";const r=await fetch(path,Object.assign({},options,{headers:h}));let d={};try{d=await r.json()}catch{}if(!r.ok||!d.ok)throw new Error(d.error||"درخواست انجام نشد.");return d}',
'async function profiles(){const d=await api("/api/admin/profiles?type="+type);const s=$("profile");s.replaceChildren();for(const p of d.profiles){const o=document.createElement("option");o.value=p.name;o.textContent=p.name;s.appendChild(o)}if(!d.profiles.length){const o=document.createElement("option");o.value="";o.textContent="پروفایلی پیدا نشد";s.appendChild(o)}}',
'async function users(){const box=$("users");box.textContent="در حال دریافت کاربران...";const d=await api("/api/admin/users?type="+type);box.replaceChildren();if(!d.users.length){box.textContent="کاربری ثبت نشده است.";return}for(const u of d.users){const row=document.createElement("div");row.className="user";const info=document.createElement("div");const name=document.createElement("strong");name.textContent=u.username;info.appendChild(name);const detail=document.createElement("small");detail.textContent=type==="hotspot"?"پروفایل: "+(u.profile||"—"):(u.profiles||[]).map(p=>p.profile+(p.state?" ("+p.state+")":"")).join("، ")||"بدون پروفایل";info.appendChild(detail);const del=document.createElement("button");del.className="danger";del.type="button";del.textContent="حذف";del.addEventListener("click",()=>removeUser(u.username));row.append(info,del);box.appendChild(row)}}',
'async function removeUser(name){if(!confirm("کاربر "+name+" حذف شود؟ این عملیات برگشت‌پذیر نیست."))return;try{await api("/api/admin/users?type="+type+"&username="+encodeURIComponent(name),{method:"DELETE"});msg("کاربر "+name+" حذف شد.",true);await users()}catch(e){msg(e.message)}}',
'async function switchType(t){type=t;$("hotspotTab").className=t==="hotspot"?"active":"secondary";$("umTab").className=t==="usermanager"?"active":"secondary";await profiles();await users()}',
'$("connect").addEventListener("click",async()=>{token=$("token").value.trim();if(token.length<32){msg("رمز دسترسی باید حداقل ۳۲ نویسه باشد.");return}try{await profiles();$("workspace").classList.remove("hidden");await users();msg("اتصال به RB951 موفق بود.",true)}catch(e){msg(e.message)}});',
'$("hotspotTab").addEventListener("click",()=>switchType("hotspot").catch(e=>msg(e.message)));$("umTab").addEventListener("click",()=>switchType("usermanager").catch(e=>msg(e.message)));$("refresh").addEventListener("click",()=>Promise.all([profiles(),users()]).then(()=>msg("فهرست تازه شد.",true)).catch(e=>msg(e.message)));',
'$("create").addEventListener("click",async()=>{const username=$("username").value.trim(),password=$("password").value,profile=$("profile").value;if(!username||!password||!profile){msg("نام کاربری، رمز و پروفایل را کامل کنید.");return}const b=$("create");b.disabled=true;try{const d=await api("/api/admin/users",{method:"POST",body:JSON.stringify({type,username,password,profile})});msg("کاربر ساخته شد: "+d.username+" | "+d.profile,true);$("username").value="";$("password").value="";await users()}catch(e){msg(e.message)}finally{b.disabled=false}});',
'</script></body></html>'
].join("\n");

export async function handleAdminRequest(request, env, routerClient) {
  const url = new URL(request.url);
  if (url.pathname === "/admin" && request.method === "GET") return new Response(adminHtml, { headers: {
    "content-type": "text/html; charset=utf-8", "cache-control": "no-store",
    "x-content-type-options": "nosniff",
    "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'"
  }});
  if (!url.pathname.startsWith("/api/admin/")) return null;
  if (!authorized(request, env)) return json({ ok: false, error: "رمز دسترسی فروشنده نامعتبر است یا تنظیم نشده." }, 401);
  const type = validType(url.searchParams.get("type"));
  if (url.pathname === "/api/admin/profiles" && request.method === "GET") {
    if (!type) return json({ ok: false, error: "نوع حساب نامعتبر است." }, 400);
    try { return json({ ok: true, profiles: await withRouter(env, routerClient, c => listProfiles(c, type)) }); }
    catch { return json({ ok: false, error: "اتصال امن به RB951 برقرار نشد یا User Manager در دسترس نیست." }, 502); }
  }
  if (url.pathname === "/api/admin/users" && request.method === "GET") {
    if (!type) return json({ ok: false, error: "نوع حساب نامعتبر است." }, 400);
    try { return json({ ok: true, users: await withRouter(env, routerClient, c => listUsers(c, type)) }); }
    catch { return json({ ok: false, error: "دریافت کاربران از RB951 انجام نشد." }, 502); }
  }
  if (url.pathname === "/api/admin/users" && request.method === "POST") {
    let body; try { body = await readBody(request); } catch { return json({ ok: false, error: "درخواست نامعتبر یا بیش از حد بزرگ است." }, 400); }
    const bodyType = validType(body.type);
    if (!bodyType) return json({ ok: false, error: "نوع حساب نامعتبر است." }, 400);
    try {
      const result = await withRouter(env, routerClient, c => createUser(c, bodyType, body));
      if (result.error) return json({ ok: false, error: result.error }, result.status);
      return json(result, 201);
    } catch { return json({ ok: false, error: "ساخت کاربر انجام نشد؛ دسترسی API و وضعیت User Manager را بررسی کنید." }, 502); }
  }
  if (url.pathname === "/api/admin/users" && request.method === "DELETE") {
    if (!type) return json({ ok: false, error: "نوع حساب نامعتبر است." }, 400);
    try {
      const result = await withRouter(env, routerClient, c => deleteUser(c, type, url.searchParams.get("username") || ""));
      if (result.error) return json({ ok: false, error: result.error }, result.status);
      return json(result);
    } catch { return json({ ok: false, error: "حذف کاربر انجام نشد؛ کاربر یا نشست‌های آن را در روتر بررسی کنید." }, 502); }
  }
  return json({ ok: false, error: "روش درخواست پشتیبانی نمی‌شود." }, 405);
}
