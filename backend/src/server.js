import http from "node:http";

const PORT = Number(process.env.PORT || 8080);
const RUNWAY_API_KEY = process.env.RUNWAY_API_KEY;
const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_PUBLISHABLE_KEY = process.env.SUPABASE_PUBLISHABLE_KEY;
const RUNWAY_BASE = "https://api.dev.runwayml.com";
const RUNWAY_VERSION = "2024-11-06";
const generationWindows = new Map();
const MAX_GENERATIONS_PER_HOUR = 5;

function send(res, status, data) {
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "access-control-allow-origin": process.env.ALLOWED_ORIGIN || "*",
    "access-control-allow-methods": "GET, POST, OPTIONS",
    "access-control-allow-headers": "Content-Type, Authorization"
  });
  res.end(JSON.stringify(data));
}

async function readJson(req) {
  let raw = "";
  for await (const chunk of req) {
    raw += chunk;
    if (raw.length > 20_000) throw new Error("İstek çok büyük.");
  }
  return JSON.parse(raw || "{}");
}

async function requireUser(req) {
  const auth = req.headers.authorization || "";
  if (!auth.startsWith("Bearer ") || !SUPABASE_URL || !SUPABASE_PUBLISHABLE_KEY) return null;
  const response = await fetch(SUPABASE_URL + "/auth/v1/user", { headers: { apikey: SUPABASE_PUBLISHABLE_KEY, Authorization: auth } });
  if (!response.ok) return null;
  return response.json();
}

async function ownsTask(req, taskId) {
  const auth = req.headers.authorization || "";
  const url = SUPABASE_URL + "/rest/v1/video_generations?select=id&provider_task_id=eq." + encodeURIComponent(taskId);
  const response = await fetch(url, { headers: { apikey: SUPABASE_PUBLISHABLE_KEY, Authorization: auth } });
  if (!response.ok) return false;
  const rows = await response.json();
  return Array.isArray(rows) && rows.length > 0;
}

async function runway(path, options = {}) {
  const response = await fetch(RUNWAY_BASE + path, {
    ...options,
    headers: {
      Authorization: `Bearer ${RUNWAY_API_KEY}`,
      "X-Runway-Version": RUNWAY_VERSION,
      "Content-Type": "application/json",
      ...(options.headers || {})
    }
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) {
    const error = new Error(body?.error || body?.message || "Video sağlayıcısı isteği reddetti.");
    error.status = response.status;
    throw error;
  }
  return body;
}

const server = http.createServer(async (req, res) => {
  if (req.method === "OPTIONS") return send(res, 204, {});
  if (req.method === "GET" && req.url === "/health") return send(res, 200, { ok: true, service: "foto-foto-api" });

  if (!RUNWAY_API_KEY) return send(res, 503, { message: "Sunucu API anahtarı yapılandırılmamış." });

  try {
    if (req.method === "POST" && req.url === "/api/generate") {
      const user = await requireUser(req);
      if (!user?.id) return send(res, 401, { message: "FOTO FOTO hesabına giriş yapmalısın." });
      const now = Date.now();
      const windowStart = now - 60 * 60 * 1000;
      const recent = (generationWindows.get(user.id) || []).filter((time) => time > windowStart);
      if (recent.length >= MAX_GENERATIONS_PER_HOUR) {
        return send(res, 429, { message: "Saatlik video oluşturma sınırına ulaştın. Bir süre sonra tekrar dene." });
      }
      const input = await readJson(req);
      const prompt = typeof input.prompt === "string" ? input.prompt.trim() : "";
      const duration = Number(input.duration);
      const ratio = input.ratio;
      if (prompt.length < 10 || prompt.length > 1000) {
        return send(res, 400, { message: "Açıklama 10–1000 karakter arasında olmalı." });
      }
      if (![5, 10].includes(duration)) return send(res, 400, { message: "Video süresi 5 veya 10 saniye olmalı." });
      if (!["1280:720", "720:1280"].includes(ratio)) {
        return send(res, 400, { message: "Bu motor 16:9 veya 9:16 formatını destekliyor." });
      }
      const task = await runway("/v1/text_to_video", {
        method: "POST",
        body: JSON.stringify({ model: "gen4.5", promptText: prompt, duration, ratio })
      });
      recent.push(now);
      generationWindows.set(user.id, recent);
      return send(res, 202, { taskId: task.id || task.taskId, status: task.status || "PENDING" });
    }

    const match = req.method === "GET" && req.url?.match(/^\/api\/tasks\/([a-zA-Z0-9_-]+)$/);
    if (match) {
      const user = await requireUser(req);
      if (!user?.id) return send(res, 401, { message: "Oturum doğrulanamadı." });
      if (!(await ownsTask(req, match[1]))) return send(res, 404, { message: "Bu video görevi hesabında bulunamadı." });
      const task = await runway("/v1/tasks/" + encodeURIComponent(match[1]), { method: "GET" });
      return send(res, 200, task);
    }

    return send(res, 404, { message: "Adres bulunamadı." });
  } catch (error) {
    const status = Number.isInteger(error.status) ? 502 : 400;
    return send(res, status, { message: error.message || "Beklenmeyen sunucu hatası." });
  }
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`FOTO FOTO API listening on ${PORT}`);
});
