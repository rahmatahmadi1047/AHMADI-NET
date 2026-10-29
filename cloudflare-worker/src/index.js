const encoder = new TextEncoder();
const decoder = new TextDecoder();

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET, POST, OPTIONS",
      "access-control-allow-headers": "content-type, authorization",
    },
  });
}

function encodeLength(n) {
  if (n < 0x80) return Uint8Array.of(n);
  if (n < 0x4000) return Uint8Array.of((n >> 8) | 0x80, n & 0xff);
  if (n < 0x200000) return Uint8Array.of((n >> 16) | 0xc0, (n >> 8) & 0xff, n & 0xff);
  if (n < 0x10000000) return Uint8Array.of((n >> 24) | 0xe0, (n >> 16) & 0xff, (n >> 8) & 0xff, n & 0xff);
  return Uint8Array.of(0xf0, (n >> 24) & 0xff, (n >> 16) & 0xff, (n >> 8) & 0xff, n & 0xff);
}

function joinBytes(chunks) {
  const size = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
  const result = new Uint8Array(size);
  let offset = 0;
  for (const chunk of chunks) {
    result.set(chunk, offset);
    offset += chunk.length;
  }
  return result;
}

class ApiStream {
  constructor(socket) {
    this.socket = socket;
    this.reader = socket.readable.getReader();
    this.writer = socket.writable.getWriter();
    this.chunk = new Uint8Array(0);
    this.offset = 0;
  }

  async readByte() {
    while (this.offset >= this.chunk.length) {
      const next = await this.reader.read();
      if (next.done) throw new Error("ROUTER_SOCKET_CLOSED");
      this.chunk = next.value;
      this.offset = 0;
    }
    return this.chunk[this.offset++];
  }

  async readWord() {
    const first = await this.readByte();
    if (first === 0) return new Uint8Array(0);
    let length;
    if ((first & 0x80) === 0) {
      length = first;
    } else if ((first & 0xc0) === 0x80) {
      length = ((first & 0x3f) << 8) | (await this.readByte());
    } else if ((first & 0xe0) === 0xc0) {
      length = ((first & 0x1f) << 16) | ((await this.readByte()) << 8) | (await this.readByte());
    } else if ((first & 0xf0) === 0xe0) {
      length = ((first & 0x0f) << 24) | ((await this.readByte()) << 16) | ((await this.readByte()) << 8) | (await this.readByte());
    } else if (first === 0xf0) {
      length = ((await this.readByte()) << 24) | ((await this.readByte()) << 16) | ((await this.readByte()) << 8) | (await this.readByte());
    } else {
      throw new Error("ROUTER_BAD_LENGTH");
    }
    if (length > 1024 * 1024) throw new Error("ROUTER_WORD_TOO_LARGE");
    const bytes = new Uint8Array(length);
    for (let i = 0; i < length; i++) bytes[i] = await this.readByte();
    return bytes;
  }

  async readSentence() {
    const words = [];
    while (true) {
      const bytes = await this.readWord();
      if (bytes.length === 0) return words;
      words.push(decoder.decode(bytes));
    }
  }

  async sendSentence(words) {
    const chunks = [];
    for (const word of words) {
      const bytes = encoder.encode(word);
      chunks.push(encodeLength(bytes.length), bytes);
    }
    chunks.push(Uint8Array.of(0));
    await this.writer.write(joinBytes(chunks));
  }

  async command(words) {
    await this.sendSentence(words);
    const records = [];
    while (true) {
      const sentence = await this.readSentence();
      if (!sentence.length) continue;
      const kind = sentence[0];
      if (kind === "!re") {
        records.push(parseRecord(sentence));
      } else if (kind === "!trap" || kind === "!fatal") {
        const trap = parseRecord(sentence);
        throw new Error(trap.message || trap.category || "ROUTER_COMMAND_FAILED");
      } else if (kind === "!done") {
        return records;
      }
    }
  }

  async close() {
    try { await this.writer.close(); } catch {}
    try { this.reader.releaseLock(); } catch {}
    try { this.writer.releaseLock(); } catch {}
    try { this.socket.close(); } catch {}
  }
}

function parseRecord(sentence) {
  const result = {};
  for (const word of sentence.slice(1)) {
    if (!word.startsWith("=")) continue;
    const split = word.indexOf("=", 1);
    if (split > 1) result[word.slice(1, split)] = word.slice(split + 1);
  }
  return result;
}

async function routerClient(env) {
  if (!env.PRIVATE_NETWORK) throw new Error("VPC_BINDING_MISSING");
  if (!env.ROUTER_API_USER || !env.ROUTER_API_PASSWORD) throw new Error("ROUTER_SECRET_MISSING");
  const host = env.ROUTER_HOST || "192.168.60.1";
  const port = Number(env.ROUTER_PORT || "8728");
  const socket = await env.PRIVATE_NETWORK.connect(`${host}:${port}`);
  const client = new ApiStream(socket);
  try {
    await client.command([
      "/login",
      `=name=${env.ROUTER_API_USER}`,
      `=password=${env.ROUTER_API_PASSWORD}`,
    ]);
    return client;
  } catch (error) {
    await client.close();
    throw error;
  }
}

function validateUsername(username) {
  return typeof username === "string" &&
    username.length >= 1 &&
    username.length <= 64 &&
    /^[A-Za-z0-9_.@-]+$/.test(username);
}

async function readCustomer(env, username, suppliedPassword = null) {
  const client = await routerClient(env);
  try {
    const properties = suppliedPassword !== null
      ? ".proplist=name,password,profile,uptime,bytes-in,bytes-out,limit-bytes-total,disabled"
      : ".proplist=name,profile,uptime,bytes-in,bytes-out,limit-bytes-total,disabled";
    const users = await client.command([
      "/ip/hotspot/user/print",
      `?name=${username}`,
      properties,
    ]);
    const user = users.find((item) => item.name === username);
    if (!user) return { invalid: true };

    if (suppliedPassword !== null) {
      // RouterOS only exposes local Hotspot passwords to a service user with the
      // sensitive policy. The password is compared here and is never returned.
      if (typeof user.password !== "string") throw new Error("ROUTER_PASSWORD_FIELD_UNAVAILABLE");
      if (user.password !== suppliedPassword) return { invalid: true };
    }

    if (user.disabled === "true") return { invalid: true };

    const active = await client.command([
      "/ip/hotspot/active/print",
      `?user=${username}`,
      ".proplist=user,uptime,bytes-in,bytes-out,address",
    ]);

    const uploadBytes = Math.max(0, Number(user["bytes-in"] || 0));
    const downloadBytes = Math.max(0, Number(user["bytes-out"] || 0));
    const usedBytes = uploadBytes + downloadBytes;
    const limit = Math.max(0, Number(user["limit-bytes-total"] || 0));
    const connected = active.some((item) => item.user === username);
    const currentSession = active.find((item) => item.user === username);

    return {
      account: {
        username: user.name,
        profile: user.profile || null,
        status: connected ? "connected" : "enabled",
        connected,
        uptime: currentSession?.uptime || user.uptime || "0s",
        downloadBytes,
        uploadBytes,
        usedBytes,
        totalLimitBytes: limit > 0 ? limit : null,
        remainingBytes: limit > 0 ? Math.max(0, limit - usedBytes) : null,
      },
    };
  } finally {
    await client.close();
  }
}

function b64url(bytes) {
  let binary = "";
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/g, "");
}

function fromB64url(value) {
  const base64 = value.replace(/-/g, "+").replace(/_/g, "/");
  const padded = base64 + "=".repeat((4 - (base64.length % 4)) % 4);
  return Uint8Array.from(atob(padded), (c) => c.charCodeAt(0));
}

async function hmacKey(env) {
  if (!env.SESSION_SECRET || env.SESSION_SECRET.length < 32) throw new Error("SESSION_SECRET_MISSING_OR_WEAK");
  return crypto.subtle.importKey(
    "raw",
    encoder.encode(env.SESSION_SECRET),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign", "verify"],
  );
}

async function createSession(env, username) {
  const payload = b64url(encoder.encode(JSON.stringify({
    sub: username,
    exp: Math.floor(Date.now() / 1000) + 3600,
    aud: "ahmadinet-customer",
  })));
  const signature = await crypto.subtle.sign("HMAC", await hmacKey(env), encoder.encode(payload));
  return `${payload}.${b64url(new Uint8Array(signature))}`;
}

async function verifySession(env, request) {
  const auth = request.headers.get("authorization") || "";
  const match = auth.match(/^Bearer ([A-Za-z0-9_.-]+)$/);
  if (!match) return null;
  const parts = match[1].split(".");
  if (parts.length !== 2) return null;
  try {
    const valid = await crypto.subtle.verify(
      "HMAC",
      await hmacKey(env),
      fromB64url(parts[1]),
      encoder.encode(parts[0]),
    );
    if (!valid) return null;
    const payload = JSON.parse(decoder.decode(fromB64url(parts[0])));
    if (payload.aud !== "ahmadinet-customer" || !validateUsername(payload.sub) || payload.exp <= Math.floor(Date.now() / 1000)) return null;
    return payload;
  } catch {
    return null;
  }
}

async function readJson(request) {
  const length = Number(request.headers.get("content-length") || "0");
  if (length > 4096) throw new Error("BODY_TOO_LARGE");
  const text = await request.text();
  if (text.length > 4096) throw new Error("BODY_TOO_LARGE");
  return JSON.parse(text);
}


function adminAuthorized(request, env) {
  const configured = env.ADMIN_TOKEN;
  if (typeof configured !== "string" || configured.length < 32) return false;
  const auth = request.headers.get("authorization") || "";
  return auth === `Bearer ${configured}`;
}

const adminHtml = `<!doctype html>
<html lang="fa" dir="rtl">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>مدیریت یوزرهای احمدی‌نت</title>
<style>
*{box-sizing:border-box}body{margin:0;font-family:Tahoma,Arial,sans-serif;background:#10172a;color:#f5f7ff;padding:20px}
main{max-width:560px;margin:24px auto;background:#18233d;border:1px solid #34415f;border-radius:18px;padding:22px}
h1{font-size:22px;margin-top:0}p{line-height:1.8;color:#c8d2e9}.field{display:block;margin:14px 0}
label{display:block;margin-bottom:7px;font-size:14px}input,select,button{width:100%;padding:13px;border-radius:10px;border:1px solid #52617f;font:inherit}
input,select{background:#0d1425;color:#fff}button{background:#7867f5;color:white;border:0;font-weight:bold;cursor:pointer;margin-top:10px}
button:disabled{opacity:.6}#msg{padding:12px;border-radius:10px;margin-top:14px;white-space:pre-wrap;display:none}
.note{font-size:12px;color:#bac7df}.danger{color:#ffccbf}
</style></head><body><main>
<h1>مدیریت یوزرهای احمدی‌نت</h1>
<p>با این پنل یوزر Hotspot جدید می‌سازی. کاربران موجود تغییر نمی‌کنند. رمز مدیریت فقط برای این درخواست استفاده می‌شود و در این صفحه ذخیره نمی‌شود.</p>
<form id="f">
<div class="field"><label for="token">رمز امن پنل (ADMIN_TOKEN)</label><input id="token" type="password" autocomplete="off" required minlength="32"></div>
<div class="field"><label for="username">نام کاربری جدید</label><input id="username" required maxlength="64" pattern="[A-Za-z0-9_.@-]+" autocomplete="off"></div>
<div class="field"><label for="password">رمز عبور مشتری</label><input id="password" type="text" required maxlength="128" autocomplete="off"></div>
<div class="field"><label for="profile">پروفایل Hotspot موجود</label><select id="profile" required><option value="">ابتدا رمز پنل را وارد و پروفایل‌ها را بارگیری کنید</option></select></div>
<button id="load" type="button">بارگیری پروفایل‌ها از روتر</button>
<button id="create" type="submit">ساخت یوزر جدید</button>
</form>
<div id="msg" role="status"></div>
<p class="note danger">این پنل فقط یوزر را ایجاد می‌کند. اعتبار تقویمی ۳۰ روز از اولین ورود باید جداگانه تنظیم و آزمایش شود؛ انتخاب یک پروفایل به‌تنهایی آن را تضمین نمی‌کند.</p>
</main>
<script>
const $=id=>document.getElementById(id);
function message(t,ok=false){const m=$("msg");m.style.display="block";m.textContent=t;m.style.background=ok?"#153d34":"#512d35";}
function headers(){return {"content-type":"application/json","authorization":"Bearer "+$("token").value};}
$("load").addEventListener("click",async()=>{if($("token").value.length<32){message("رمز مدیریت باید حداقل ۳۲ نویسه باشد.");return;} $("load").disabled=true;try{const r=await fetch("/api/admin/profiles",{headers:headers()});const d=await r.json();if(!r.ok||!d.ok)throw new Error(d.error||"بارگیری پروفایل‌ها ناموفق بود.");const s=$("profile");s.replaceChildren();for(const p of d.profiles){const o=document.createElement("option");o.value=p.name;o.textContent=p.name;s.appendChild(o);}if(!d.profiles.length)message("هیچ پروفایلی از روتر دریافت نشد.");else message("پروفایل‌ها از روتر بارگیری شد.",true);}catch(e){message(e.message||"ارتباط با Worker یا روتر برقرار نشد.");}finally{$("load").disabled=false;}});
$("f").addEventListener("submit",async e=>{e.preventDefault();$("create").disabled=true;try{const r=await fetch("/api/admin/users",{method:"POST",headers:headers(),body:JSON.stringify({username:$("username").value.trim(),password:$("password").value,profile:$("profile").value})});const d=await r.json();if(!r.ok||!d.ok)throw new Error(d.error||"ساخت یوزر انجام نشد.");message("یوزر جدید ساخته شد: "+d.username+"\nپروفایل: "+d.profile,true);$("username").value="";$("password").value="";}catch(e){message(e.message||"ارتباط با Worker یا روتر برقرار نشد.");}finally{$("create").disabled=false;}});
</script></body></html>`;

export default {
  async fetch(request, env) {
    if (request.method === "OPTIONS") {
      return new Response(null, {
        status: 204,
        headers: {
          "access-control-allow-origin": "*",
          "access-control-allow-methods": "GET, POST, OPTIONS",
          "access-control-allow-headers": "content-type, authorization",
          "access-control-max-age": "86400",
        },
      });
    }

    const url = new URL(request.url);

    // Public status endpoint: the Android app expects every successful JSON
    // response to include ok: true.
    if ((url.pathname === "/" || url.pathname === "/health") && request.method === "GET") {
      return json({
        ok: true,
        status: "online",
        service: "AHMADI-NET API",
        message: "Cloudflare Worker is running.",
        mode: "worker-ready",
      });
    }


    if (url.pathname === "/admin" && request.method === "GET") {
      return new Response(adminHtml, {
        headers: {
          "content-type": "text/html; charset=utf-8",
          "cache-control": "no-store",
          "x-content-type-options": "nosniff",
          "content-security-policy": "default-src 'none'; style-src 'unsafe-inline'; script-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'; form-action 'none'",
        },
      });
    }

    if (url.pathname === "/api/admin/profiles" && request.method === "GET") {
      if (!adminAuthorized(request, env)) return json({ ok: false, error: "رمز مدیریت نامعتبر است یا تنظیم نشده." }, 401);
      try {
        const client = await routerClient(env);
        try {
          const profiles = await client.command([
            "/ip/hotspot/user/profile/print",
            ".proplist=name",
          ]);
          return json({ ok: true, profiles: profiles.filter((p) => typeof p.name === "string").map((p) => ({ name: p.name })) });
        } finally {
          await client.close();
        }
      } catch {
        return json({ ok: false, error: "اتصال امن به روتر برقرار نیست." }, 502);
      }
    }

    if (url.pathname === "/api/admin/users" && request.method === "POST") {
      if (!adminAuthorized(request, env)) return json({ ok: false, error: "رمز مدیریت نامعتبر است یا تنظیم نشده." }, 401);
      let body;
      try {
        body = await readJson(request);
      } catch {
        return json({ ok: false, error: "درخواست نامعتبر است." }, 400);
      }
      const username = typeof body.username === "string" ? body.username.trim() : "";
      const password = typeof body.password === "string" ? body.password : "";
      const profile = typeof body.profile === "string" ? body.profile : "";
      if (!validateUsername(username) || password.length < 1 || password.length > 128 || !validateUsername(profile)) {
        return json({ ok: false, error: "نام کاربری، رمز یا پروفایل معتبر نیست." }, 400);
      }
      try {
        const client = await routerClient(env);
        try {
          const existing = await client.command([
            "/ip/hotspot/user/print",
            `?name=${username}`,
            ".proplist=name",
          ]);
          if (existing.some((item) => item.name === username)) {
            return json({ ok: false, error: "این نام کاربری از قبل وجود دارد؛ کاربر قبلی تغییر داده نشد." }, 409);
          }
          const profiles = await client.command([
            "/ip/hotspot/user/profile/print",
            `?name=${profile}`,
            ".proplist=name",
          ]);
          if (!profiles.some((item) => item.name === profile)) {
            return json({ ok: false, error: "پروفایل انتخاب‌شده در روتر وجود ندارد." }, 400);
          }
          await client.command([
            "/ip/hotspot/user/add",
            `=name=${username}`,
            `=password=${password}`,
            `=profile=${profile}`,
          ]);
          return json({ ok: true, username, profile, message: "یوزر جدید ایجاد شد." }, 201);
        } finally {
          await client.close();
        }
      } catch {
        return json({ ok: false, error: "ساخت یوزر انجام نشد؛ اتصال و دسترسی API روتر را بررسی کنید." }, 502);
      }
    }

    if (url.pathname === "/api/login" && request.method === "POST") {
      try {
        const body = await readJson(request);
        const username = typeof body.username === "string" ? body.username.trim() : "";
        const password = typeof body.password === "string" ? body.password : "";
        if (!validateUsername(username) || password.length < 1 || password.length > 128) {
          return json({ ok: false, error: "نام کاربری یا رمز نادرست است." }, 401);
        }
        const result = await readCustomer(env, username, password);
        if (result.invalid || !result.account) {
          return json({ ok: false, error: "نام کاربری یا رمز نادرست است." }, 401);
        }
        const token = await createSession(env, username);
        return json({ ok: true, token, expiresIn: 3600, account: result.account });
      } catch (error) {
        const message = String(error?.message || error);
        if (message === "BODY_TOO_LARGE") return json({ ok: false, error: "درخواست بیش از حد بزرگ است." }, 413);
        if (message === "ROUTER_PASSWORD_FIELD_UNAVAILABLE" || message === "ROUTER_SECRET_MISSING" || message === "VPC_BINDING_MISSING" || message === "SESSION_SECRET_MISSING_OR_WEAK") {
          return json({ ok: false, error: "تنظیم اتصال سرور کامل نیست. مدیر باید تنظیمات را بررسی کند." }, 503);
        }
        return json({ ok: false, error: "ارتباط امن با روتر برقرار نیست." }, 502);
      }
    }

    if (url.pathname === "/api/account" && request.method === "GET") {
      const session = await verifySession(env, request);
      if (!session) return json({ ok: false, error: "نشست معتبر نیست؛ دوباره وارد شوید." }, 401);
      try {
        const result = await readCustomer(env, session.sub);
        if (result.invalid || !result.account) return json({ ok: false, error: "حساب قابل دسترسی نیست." }, 404);
        return json({ ok: true, account: result.account });
      } catch {
        return json({ ok: false, error: "ارتباط امن با روتر برقرار نیست." }, 502);
      }
    }

    return json({ ok: false, error: "مسیر درخواست پیدا نشد." }, 404);
  },
};
