import http from "node:http";
import { randomUUID, createHmac, timingSafeEqual } from "node:crypto";

const PORT = Number(process.env.PORT || 8080);
const RUNWAY_API_KEY = process.env.RUNWAY_API_KEY;
const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_PUBLISHABLE_KEY = process.env.SUPABASE_PUBLISHABLE_KEY;
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;
const RUNWAY_BASE = "https://api.dev.runwayml.com";
const RUNWAY_VERSION = "2024-11-06";
const PAYTR_MERCHANT_ID = process.env.PAYTR_MERCHANT_ID;
const PAYTR_MERCHANT_KEY = process.env.PAYTR_MERCHANT_KEY;
const PAYTR_MERCHANT_SALT = process.env.PAYTR_MERCHANT_SALT;
const PAYTR_OK_URL = process.env.PAYTR_OK_URL;
const PAYTR_FAIL_URL = process.env.PAYTR_FAIL_URL;
const PAYTR_TEST_MODE = process.env.PAYTR_TEST_MODE === "1" ? "1" : "0";

function send(res, status, data) {
  res.writeHead(status, {
    "content-type": "application/json; charset=utf-8",
    "cache-control": "no-store",
    "access-control-allow-origin": process.env.ALLOWED_ORIGIN || "*",
    "access-control-allow-methods": "GET, POST, DELETE, OPTIONS",
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

async function readForm(req) {
  let raw = "";
  for await (const chunk of req) { raw += chunk; if (raw.length > 100_000) throw new Error("Ödeme bildirimi çok büyük."); }
  return new URLSearchParams(raw);
}

function sendText(res, status, value) {
  res.writeHead(status, { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" });
  res.end(value);
}

function paytrConfigured() {
  return Boolean(PAYTR_MERCHANT_ID && PAYTR_MERCHANT_KEY && PAYTR_MERCHANT_SALT && PAYTR_OK_URL && PAYTR_FAIL_URL);
}

async function adminRest(path, options = {}) {
  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) throw new Error("Ödeme veritabanı yapılandırılmamış.");
  const response = await fetch(SUPABASE_URL + "/rest/v1/" + path, { ...options, headers: { apikey: SUPABASE_SERVICE_ROLE_KEY, Authorization: "Bearer " + SUPABASE_SERVICE_ROLE_KEY, "Content-Type": "application/json", ...(options.headers || {}) } });
  const raw = await response.text(); let data = null;
  if (raw) { try { data = JSON.parse(raw); } catch { data = raw; } }
  if (!response.ok) throw new Error((data && data.message) || "Ödeme veritabanı işlemi başarısız.");
  return data;
}

function safeHashEqual(a, b) {
  const left = Buffer.from(String(a || "")); const right = Buffer.from(String(b || ""));
  return left.length === right.length && timingSafeEqual(left, right);
}

function paytrCallbackHash(merchantOid, status, totalAmount) {
  return createHmac("sha256", PAYTR_MERCHANT_KEY).update(merchantOid + PAYTR_MERCHANT_SALT + status + totalAmount).digest("base64");
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
  const reservationId = randomUUID();
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
  const url = SUPABASE_URL + "/rest/v1/credit_reservations?select=id&provider_task_id=eq." + encodeURIComponent(taskId);
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
  if (req.method === "GET" && new URL(req.url, "http://localhost").pathname === "/health") {
    const checks = {
      supabaseUrl: Boolean(SUPABASE_URL),
      supabasePublishableKey: Boolean(SUPABASE_PUBLISHABLE_KEY),
      walletServiceKey: Boolean(SUPABASE_SERVICE_ROLE_KEY),
      runwayApiKey: Boolean(RUNWAY_API_KEY),
      paytrCredentials: paytrConfigured()
    };
    return send(res, 200, {
      ok: true,
      service: "foto-foto-api",
      status: checks.supabaseUrl && checks.supabasePublishableKey && checks.walletServiceKey ? "ready_for_account_tests" : "configuration_incomplete",
      checks
    });
  }

  try {
    if (req.method === "DELETE" && new URL(req.url, "http://localhost").pathname === "/api/account") {
      const user = await requireUser(req);
      if (!user?.id) return send(res, 401, { message: "Oturum doğrulanamadı. Tekrar giriş yap." });
      if (!SUPABASE_SERVICE_ROLE_KEY) return send(res, 503, { message: "Hesap silme servisi henüz yapılandırılmamış." });
      const deletion = await fetch(SUPABASE_URL + "/auth/v1/admin/users/" + encodeURIComponent(user.id), {
        method: "DELETE",
        headers: {
          apikey: SUPABASE_SERVICE_ROLE_KEY,
          Authorization: "Bearer " + SUPABASE_SERVICE_ROLE_KEY
        }
      });
      if (!deletion.ok) {
        const detail = await deletion.json().catch(() => ({}));
        return send(res, 502, { message: detail?.message || "Hesap silinemedi. Daha sonra tekrar dene." });
      }
      return send(res, 200, { ok: true, message: "FOTO FOTO hesabın ve hesaba bağlı veriler silindi." });
    }

    const pathname = new URL(req.url, "http://localhost").pathname;

    if (req.method === "POST" && pathname === "/api/webhooks/paytr") {
      if (!PAYTR_MERCHANT_KEY || !PAYTR_MERCHANT_SALT || !SUPABASE_SERVICE_ROLE_KEY) return sendText(res, 503, "PAYTR callback is not configured");
      const form = await readForm(req);
      const merchantOid = form.get("merchant_oid") || ""; const status = form.get("status") || ""; const totalAmount = form.get("total_amount") || ""; const receivedHash = form.get("hash") || "";
      if (!merchantOid || !["success", "failed"].includes(status) || !/^\d+$/.test(totalAmount)) return sendText(res, 400, "Invalid callback");
      if (!safeHashEqual(paytrCallbackHash(merchantOid, status, totalAmount), receivedHash)) return sendText(res, 400, "PAYTR notification failed: bad hash");
      await adminRpc("settle_paytr_order", { p_merchant_oid: merchantOid, p_status: status, p_total_amount: Number(totalAmount), p_payment_type: form.get("payment_type") || "", p_failure_code: form.get("failed_reason_code") || "", p_failure_message: form.get("failed_reason_msg") || "" });
      return sendText(res, 200, "OK");
    }

    if (req.method === "GET" && pathname === "/api/catalog") {
      const user = await requireUser(req);
      if (!user || !user.id) return send(res, 401, { message: "Paketleri görmek için giriş yapmalısın." });
      const products = await adminRest("billing_products?select=product_id,product_type,title,description,price_minor,currency,credits&channel=eq.paytr_web&region=eq.TR&active=eq.true&order=price_minor.asc");
      return send(res, 200, { products });
    }

    if (req.method === "POST" && pathname === "/api/payments/paytr/create") {
      const user = await requireUser(req);
      if (!user || !user.id) return send(res, 401, { message: "Ödeme başlatmak için FOTO FOTO hesabına giriş yapmalısın." });
      if (!paytrConfigured()) return send(res, 503, { message: "PayTR mağaza hesabı açılmadı veya sunucu bilgileri eklenmedi. Şimdilik ödeme alınamaz." });
      const input = await readJson(req); const productId = typeof input.productId === "string" ? input.productId.trim() : "";
      if (!/^[a-zA-Z0-9_-]{2,60}$/.test(productId)) return send(res, 400, { message: "Geçersiz paket seçimi." });
      const products = await adminRest("billing_products?select=product_id,title,price_minor,currency,credits&product_id=eq." + encodeURIComponent(productId) + "&channel=eq.paytr_web&region=eq.TR&active=eq.true&limit=1");
      const product = Array.isArray(products) ? products[0] : null;
      if (!product) return send(res, 404, { message: "Bu kredi paketi henüz satışa açık değil." });
      const amountMinor = Number(product.price_minor); const credits = Number(product.credits);
      if (product.currency !== "TRY" || !Number.isSafeInteger(amountMinor) || amountMinor < 1 || !Number.isSafeInteger(credits) || credits < 1) return send(res, 400, { message: "Paket fiyatı veya kredi miktarı geçersiz." });
      const email = String(user.email || "").trim();
      if (!email || email.length > 100 || /[^\x20-\x7E]/.test(email)) return send(res, 400, { message: "PayTR için hesabında geçerli bir e-posta adresi bulunmalı." });
      const merchantOid = "FF" + randomUUID().replaceAll("-", "");
      const forwarded = String(req.headers["x-forwarded-for"] || "").split(",")[0].trim(); const userIp = forwarded || req.socket.remoteAddress || "127.0.0.1";
      const basket = Buffer.from(JSON.stringify([[product.title, (amountMinor / 100).toFixed(2), 1]])).toString("base64");
      const noInstallment = "1"; const maxInstallment = "0"; const currency = "TL";
      const hashText = PAYTR_MERCHANT_ID + userIp + merchantOid + email + amountMinor + basket + noInstallment + maxInstallment + currency + PAYTR_TEST_MODE;
      const paytrToken = createHmac("sha256", PAYTR_MERCHANT_KEY).update(hashText + PAYTR_MERCHANT_SALT).digest("base64");
      await adminRest("payment_orders", { method: "POST", headers: { Prefer: "return=minimal" }, body: JSON.stringify({ merchant_oid: merchantOid, user_id: user.id, product_id: product.product_id, amount_minor: amountMinor, currency: "TRY", credits_to_grant: credits, status: "pending" }) });
      const params = new URLSearchParams({ merchant_id: PAYTR_MERCHANT_ID, user_ip: userIp, merchant_oid: merchantOid, email: email, payment_amount: String(amountMinor), paytr_token: paytrToken, user_basket: basket, no_installment: noInstallment, max_installment: maxInstallment, currency: currency, test_mode: PAYTR_TEST_MODE, debug_on: PAYTR_TEST_MODE === "1" ? "1" : "0", timeout_limit: "30", lang: "tr", merchant_ok_url: PAYTR_OK_URL, merchant_fail_url: PAYTR_FAIL_URL, user_name: String((user.user_metadata && user.user_metadata.display_name) || "FOTO FOTO kullanıcısı").slice(0, 60), user_address: "Türkiye", user_phone: "" });
      let tokenResponse;
      try {
        const response = await fetch("https://www.paytr.com/odeme/api/get-token", { method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" }, body: params });
        tokenResponse = await response.json().catch(() => ({}));
        if (!response.ok || tokenResponse.status !== "success" || !tokenResponse.token) throw new Error(tokenResponse.reason || "PayTR ödeme formu oluşturulamadı.");
      } catch (error) {
        await adminRest("payment_orders?merchant_oid=eq." + merchantOid, { method: "PATCH", body: JSON.stringify({ status: "failed", failure_message: String(error.message || "PayTR token error").slice(0, 500) }) }).catch(() => {});
        throw error;
      }
      return send(res, 200, { merchantOid: merchantOid, iframeUrl: "https://www.paytr.com/odeme/guvenli/" + encodeURIComponent(tokenResponse.token) });
    }

    if (!RUNWAY_API_KEY) return send(res, 503, { message: "Video üretim servisi henüz yapılandırılmamış." });

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
      const cost = duration === 5 ? 59 : 119;
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
      return send(res, 503, { message: "Video düzenleme için güvenli süre ölçümü ve kredi tarifesi tamamlanıyor. Şimdilik bu işlem başlatılamıyor." });
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
      return send(res, 503, { message: "Karakter animasyonu için güvenli süre ölçümü ve kredi tarifesi tamamlanıyor. Şimdilik bu işlem başlatılamıyor." });
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
      return send(res, 503, { message: "Video kalite yükseltme için kare sayısına dayalı kredi hesabı tamamlanıyor. Şimdilik bu işlem başlatılamıyor." });
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
