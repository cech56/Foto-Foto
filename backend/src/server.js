import http from "node:http";

const PORT = Number(process.env.PORT || 8080);
const RUNWAY_API_KEY = process.env.RUNWAY_API_KEY;
const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_PUBLISHABLE_KEY = process.env.SUPABASE_PUBLISHABLE_KEY;
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;
const RUNWAY_BASE = "https://api.dev.runwayml.com";
const RUNWAY_VERSION = "2024-11-06";

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
    if (raw.length > 6_500_000) throw new Error("Fotoğraf dosyası çok büyük. Daha küçük bir görsel seç.");
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

async function adminRpc(functionName, args) {
  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) throw new Error("Kredi sistemi sunucu tarafında yapılandırılmamış.");
  const response = await fetch(SUPABASE_URL + "/rest/v1/rpc/" + functionName, {
    method: "POST",
    headers: {
      apikey: SUPABASE_SERVICE_ROLE_KEY,
      Authorization: "Bearer " + SUPABASE_SERVICE_ROLE_KEY,
      "Content-Type": "application/json"
    },
    body: JSON.stringify(args)
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data?.message || "Kredi işlemi tamamlanamadı.");
  return data;
}

async function reserveCredits(userId, amount, description) {
  const reservationId = crypto.randomUUID();
  const result = await adminRpc("reserve_foto_credits", {
    p_user_id: userId,
    p_reservation_id: reservationId,
    p_amount: amount,
    p_description: description
  });
  if (!result?.ok) {
    const balance = Number(result?.balance || 0);
    const error = new Error("Yetersiz kredi. Gerekli: " + amount + ", mevcut: " + balance + ".");
    error.status = 402;
    throw error;
  }
  return reservationId;
}

async function releaseCredits(reservationId) {
  return adminRpc("release_foto_reservation", { p_reservation_id: reservationId });
}

async function attachTask(reservationId, taskId) {
  const result = await adminRpc("attach_foto_task", {
    p_reservation_id: reservationId,
    p_provider_task_id: taskId
  });
  if (result !== true) throw new Error("Üretim görevi kredi kaydına bağlanamadı.");
}

async function settleTask(taskId, success) {
  return adminRpc("settle_foto_credits", {
    p_provider_task_id: taskId,
    p_success: success
  });
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
      const input = await readJson(req);
      const prompt = typeof input.prompt === "string" ? input.prompt.trim() : "";
      const duration = Number(input.duration);
      const ratio = input.ratio;
      if (prompt.length < 10 || prompt.length > 1000) {
        return send(res, 400, { message: "Açıklama 10–1000 karakter arasında olmalı." });
      }
      if (![5, 10].includes(duration)) return send(res, 400, { message: "Video süresi 5 veya 10 saniye olmalı." });
      if (!["1280:720", "720:1280", "960:960", "1104:832", "832:1104", "1584:672"].includes(ratio)) {
        return send(res, 400, { message: "Desteklenmeyen video formatı." });
      }
      const imageUri = typeof input.imageUri === "string" ? input.imageUri : "";
      if (imageUri && (!imageUri.startsWith("data:image/") || imageUri.length > 4_800_000)) {
        return send(res, 400, { message: "Fotoğraf biçimi geçersiz veya dosya çok büyük." });
      }
      const endpoint = imageUri ? "/v1/image_to_video" : "/v1/text_to_video";
      const payload = imageUri
        ? { model: "gen4.5", promptText: prompt, uri: imageUri, position: "first", duration, ratio }
        : { model: "gen4.5", promptText: prompt, duration, ratio };
      const cost = duration * 12;
      const reservationId = await reserveCredits(user.id, cost, "Gen-4.5 video üretimi (" + duration + " saniye)");
      try {
        const task = await runway(endpoint, { method: "POST", body: JSON.stringify(payload) });
        const taskId = task.id || task.taskId;
        if (!taskId) throw new Error("Runway görev numarası döndürmedi.");
        await attachTask(reservationId, taskId);
        return send(res, 202, { taskId, status: task.status || "PENDING", reservedCredits: cost });
      } catch (error) {
        await releaseCredits(reservationId).catch(() => {});
        throw error;
      }
    }

    if (req.method === "POST" && req.url === "/api/edit-video") {
      const user = await requireUser(req);
      if (!user?.id) return send(res, 401, { message: "FOTO FOTO hesabına giriş yapmalısın." });
      const input = await readJson(req);
      const prompt = typeof input.prompt === "string" ? input.prompt.trim() : "";
      const videoUri = typeof input.videoUri === "string" ? input.videoUri : "";
      if (prompt.length < 3 || prompt.length > 1000) {
        return send(res, 400, { message: "Düzenleme açıklaması 3–1000 karakter arasında olmalı." });
      }
      if (!videoUri.startsWith("data:video/") || videoUri.length > 4_800_000) {
        return send(res, 400, { message: "Video biçimi geçersiz veya dosya 3,5 MB sınırını aşıyor." });
      }
      const task = await runway("/v1/video_to_video", {
        method: "POST",
        body: JSON.stringify({ model: "aleph2", videoUri, promptText: prompt })
      });
      return send(res, 202, { taskId: task.id || task.taskId, status: task.status || "PENDING" });
    }

    if (req.method === "POST" && req.url === "/api/animate-character") {
      const user = await requireUser(req);
      if (!user?.id) return send(res, 401, { message: "FOTO FOTO hesabına giriş yapmalısın." });
      const input = await readJson(req);
      const characterUri = typeof input.characterUri === "string" ? input.characterUri : "";
      const referenceUri = typeof input.referenceUri === "string" ? input.referenceUri : "";
      const ratio = input.ratio || "1280:720";
      const intensity = Number.isInteger(input.expressionIntensity) ? input.expressionIntensity : 3;
      if (!characterUri.startsWith("data:image/") || characterUri.length > 4_800_000) {
        return send(res, 400, { message: "Karakter fotoğrafı geçersiz veya dosya çok büyük." });
      }
      if (!referenceUri.startsWith("data:video/") || referenceUri.length > 4_800_000) {
        return send(res, 400, { message: "Hareket videosu geçersiz veya dosya çok büyük." });
      }
      if (!["1280:720", "720:1280", "960:960", "1104:832", "832:1104", "1584:672"].includes(ratio)) {
        return send(res, 400, { message: "Desteklenmeyen video formatı." });
      }
      if (intensity < 1 || intensity > 5) {
        return send(res, 400, { message: "İfade yoğunluğu 1–5 arasında olmalı." });
      }
      const task = await runway("/v1/character_performance", {
        method: "POST",
        body: JSON.stringify({
          model: "act_two",
          character: { type: "image", uri: characterUri },
          reference: { type: "video", uri: referenceUri },
          ratio,
          bodyControl: true,
          expressionIntensity: intensity
        })
      });
      return send(res, 202, { taskId: task.id || task.taskId, status: task.status || "PENDING" });
    }

    if (req.method === "POST" && req.url === "/api/upscale-video") {
      const user = await requireUser(req);
      if (!user?.id) return send(res, 401, { message: "FOTO FOTO hesabına giriş yapmalısın." });
      const input = await readJson(req);
      const videoUri = typeof input.videoUri === "string" ? input.videoUri : "";
      const resolution = typeof input.resolution === "string" ? input.resolution : "2k";
      const creativity = Number.isInteger(input.creativity) ? input.creativity : 30;
      const sharpen = Number.isInteger(input.sharpen) ? input.sharpen : 40;
      if (!videoUri.startsWith("data:video/") || videoUri.length > 4_800_000) {
        return send(res, 400, { message: "Video biçimi geçersiz veya dosya 3,5 MB sınırını aşıyor." });
      }
      if (!["720p", "1k", "2k", "4k"].includes(resolution)) {
        return send(res, 400, { message: "Desteklenmeyen çözünürlük." });
      }
      if (creativity < 0 || creativity > 100 || sharpen < 0 || sharpen > 100) {
        return send(res, 400, { message: "Görüntü ayarları 0–100 arasında olmalı." });
      }
      const task = await runway("/v1/video_upscale", {
        method: "POST",
        body: JSON.stringify({
          model: "magnific_video_upscaler_creative",
          videoUri,
          resolution,
          creativity,
          sharpen,
          flavor: "natural"
        })
      });
      return send(res, 202, { taskId: task.id || task.taskId, status: task.status || "PENDING" });
    }

    const match = req.method === "GET" && req.url?.match(/^\/api\/tasks\/([a-zA-Z0-9_-]+)$/);
    if (match) {
      const user = await requireUser(req);
      if (!user?.id) return send(res, 401, { message: "Oturum doğrulanamadı." });
      if (!(await ownsTask(req, match[1]))) return send(res, 404, { message: "Bu video görevi hesabında bulunamadı." });
      const task = await runway("/v1/tasks/" + encodeURIComponent(match[1]), { method: "GET" });
      const state = String(task.status || "").toUpperCase();
      if (state === "SUCCEEDED") await settleTask(match[1], true);
      if (state === "FAILED" || state === "CANCELED" || state === "CANCELLED") await settleTask(match[1], false);
      return send(res, 200, task);
    }

    return send(res, 404, { message: "Adres bulunamadı." });
  } catch (error) {
    const status = error.status === 402 ? 402 : Number.isInteger(error.status) ? 502 : 400;
    return send(res, status, { message: error.message || "Beklenmeyen sunucu hatası." });
  }
});

server.listen(PORT, "0.0.0.0", () => {
  console.log(`FOTO FOTO API listening on ${PORT}`);
});
