import http from "node:http";
import { randomUUID, createHmac, timingSafeEqual } from "node:crypto";

const PORT = Number(process.env.PORT || 8080);
const RUNWAY_API_KEY = process.env.RUNWAY_API_KEY;
const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_PUBLISHABLE_KEY = process.env.SUPABASE_PUBLISHABLE_KEY;
const SUPABASE_SERVICE_ROLE_KEY = process.env.SUPABASE_SERVICE_ROLE_KEY;
const RUNWAY_BASE = "https://api.dev.runwayml.com";
const RUNWAY_VERSION = "2024-11-06";
const IYZICO_API_KEY = process.env.IYZICO_API_KEY;
const IYZICO_SECRET_KEY = process.env.IYZICO_SECRET_KEY;
const IYZICO_CALLBACK_URL = process.env.IYZICO_CALLBACK_URL;
const IYZICO_BASE_URL = process.env.IYZICO_BASE_URL === "https://api.iyzipay.com" ? "https://api.iyzipay.com" : "https://sandbox-api.iyzipay.com";

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

function iyzicoConfigured() {
  return Boolean(IYZICO_API_KEY && IYZICO_SECRET_KEY && IYZICO_CALLBACK_URL);
}

function iyzicoAuthorization(uriPath, bodyText) {
  const randomKey = Date.now().toString() + randomUUID().replaceAll("-", "");
  const signature = createHmac("sha256", IYZICO_SECRET_KEY).update(randomKey + uriPath + bodyText).digest("hex");
  const authText = "apiKey:" + IYZICO_API_KEY + "&randomKey:" + randomKey + "&signature:" + signature;
  return { authorization: "IYZWSv2 " + Buffer.from(authText).toString("base64"), randomKey: randomKey };
}

async function iyzicoRequest(uriPath, payload) {
  const bodyText = JSON.stringify(payload);
  const auth = iyzicoAuthorization(uriPath, bodyText);
  const response = await fetch(IYZICO_BASE_URL + uriPath, {
    method: "POST",
    headers: { "Content-Type": "application/json", Authorization: auth.authorization, "x-iyzi-rnd": auth.randomKey },
    body: bodyText
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok || data.status !== "success") {
    const error = new Error(data.errorMessage || "iyzico işlemi başarısız oldu.");
    error.status = 502;
    throw error;
  }
  return data;
}

function normalizeMoney(value) {
  const n = Number(value);
  if (!Number.isFinite(n)) return "";
  return String(n);
}

function verifyIyzicoInitializeSignature(data, conversationId) {
  if (!data.signature || !data.token || data.conversationId !== conversationId) return false;
  const expected = createHmac("sha256", IYZICO_SECRET_KEY).update(conversationId + ":" + data.token).digest("hex");
  return safeHashEqual(expected, data.signature);
}

function verifyIyzicoRetrieveSignature(data) {
  if (!data.signature || !data.paymentStatus || !data.paymentId || !data.currency || !data.basketId || !data.conversationId || !data.token || data.price == null || data.paidPrice == null) return false;
  const fields = [data.paymentStatus, data.paymentId, data.currency, data.basketId, data.conversationId, normalizeMoney(data.paidPrice), normalizeMoney(data.price), data.token];
  const expected = createHmac("sha256", IYZICO_SECRET_KEY).update(fields.join(":")).digest("hex");
  return safeHashEqual(expected, data.signature);
}

function safeHashEqual(a, b) {
  const left = Buffer.from(String(a || "")); const right = Buffer.from(String(b || ""));
  return left.length === right.length && timingSafeEqual(left, right);
}

function splitName(value) {
  const parts = String(value || "").trim().split(/\s+/).filter(Boolean);
  if (parts.length < 2) return { name: parts[0] || "", surname: "Kullanıcı" };
  return { name: parts.slice(0, -1).join(" "), surname: parts[parts.length - 1] };
}
async function adminRest(path, options = {}) {
  if (!SUPABASE_URL || !SUPABASE_SERVICE_ROLE_KEY) throw new Error("Ödeme veritabanı yapılandırılmamış.");
  const response = await fetch(SUPABASE_URL + "/rest/v1/" + path, { ...options, headers: { apikey: SUPABASE_SERVICE_ROLE_KEY, Authorization: "Bearer " + SUPABASE_SERVICE_ROLE_KEY, "Content-Type": "application/json", ...(options.headers || {}) } });
  const raw = await response.text(); let data = null;
  if (raw) { try { data = JSON.parse(raw); } catch { data = raw; } }
  if (!response.ok) throw new Error((data && data.message) || "Ödeme veritabanı işlemi başarısız.");
  return data;
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
      iyzicoCredentials: iyzicoConfigured()
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

    if (req.method === "POST" && pathname === "/api/webhooks/iyzico") {
      if (!iyzicoConfigured() || !SUPABASE_SERVICE_ROLE_KEY) return sendText(res, 503, "iyzico callback is not configured");
      const form = await readForm(req);
      const token = form.get("token") || "";
      if (!token || token.length > 256) return sendText(res, 400, "Invalid iyzico token");
      const rows = await adminRest("payment_orders?select=merchant_oid,iyzico_token&iyzico_token=eq." + encodeURIComponent(token) + "&channel=eq.iyzico_checkout&limit=1");
      const order = Array.isArray(rows) ? rows[0] : null;
      if (!order || order.iyzico_token !== token) return sendText(res, 404, "Order not found");
      const result = await iyzicoRequest("/payment/iyzipos/checkoutform/auth/ecom/detail", { locale: "tr", conversationId: order.merchant_oid, token: token });
      if (!verifyIyzicoRetrieveSignature(result)) return sendText(res, 400, "iyzico response signature validation failed");
      if (result.conversationId !== order.merchant_oid || result.token !== token || result.currency !== "TRY") return sendText(res, 400, "Payment reference mismatch");
      if (result.paymentStatus === "SUCCESS" && Number(result.fraudStatus) === 1 && result.status === "success") {
        const settled = await adminRpc("settle_iyzico_order", { p_conversation_id: result.conversationId, p_token: token, p_payment_id: String(result.paymentId), p_price: Number(result.price), p_paid_price: Number(result.paidPrice), p_currency: result.currency, p_basket_id: result.basketId });
        if (!settled || settled.ok !== true) return sendText(res, 500, "Payment settlement failed");
        return sendText(res, 200, "OK");
      }
      if (result.paymentStatus === "FAILURE" || Number(result.fraudStatus) === -1) {
        await adminRest("payment_orders?merchant_oid=eq." + encodeURIComponent(order.merchant_oid) + "&iyzico_token=eq." + encodeURIComponent(token), { method: "PATCH", body: JSON.stringify({ status: "failed", iyzico_payment_id: result.paymentId ? String(result.paymentId) : null, failure_message: String(result.errorMessage || "iyzico payment failed").slice(0, 500) }) });
        return sendText(res, 200, "OK");
      }
      // Fraud review (fraudStatus=0) remains pending; never grant credits until approved.
      return sendText(res, 200, "OK");
    }

    if (req.method === "GET" && pathname === "/api/catalog") {
      const user = await requireUser(req);
      if (!user || !user.id) return send(res, 401, { message: "Paketleri görmek için giriş yapmalısın." });
      const products = await adminRest("billing_products?select=product_id,product_type,title,description,price_minor,currency,credits&channel=eq.iyzico_checkout&region=eq.TR&active=eq.true&order=price_minor.asc");
      return send(res, 200, { products });
    }

    if (req.method === "POST" && pathname === "/api/payments/iyzico/create") {
      const user = await requireUser(req);
      if (!user || !user.id) return send(res, 401, { message: "Ödeme başlatmak için FOTO FOTO hesabına giriş yapmalısın." });
      if (!iyzicoConfigured()) return send(res, 503, { message: "iyzico API anahtarları henüz sunucuya eklenmedi. Şimdilik ödeme alınamaz." });
      const input = await readJson(req);
      const productId = typeof input.productId === "string" ? input.productId.trim() : "";
      if (!/^[a-zA-Z0-9_-]{2,60}$/.test(productId)) return send(res, 400, { message: "Geçersiz paket seçimi." });
      const products = await adminRest("billing_products?select=product_id,title,price_minor,currency,credits&product_id=eq." + encodeURIComponent(productId) + "&channel=eq.iyzico_checkout&region=eq.TR&active=eq.true&limit=1");
      const product = Array.isArray(products) ? products[0] : null;
      if (!product) return send(res, 404, { message: "Bu kredi paketi henüz satışa açık değil." });
      const amountMinor = Number(product.price_minor); const credits = Number(product.credits);
      if (product.currency !== "TRY" || !Number.isSafeInteger(amountMinor) || amountMinor < 1 || !Number.isSafeInteger(credits) || credits < 1) return send(res, 400, { message: "Paket fiyatı veya kredi miktarı geçersiz." });
      const buyerInput = input.buyer && typeof input.buyer === "object" ? input.buyer : {};
      const metadata = user.user_metadata || {};
      const nameParts = splitName(buyerInput.fullName || metadata.full_name || metadata.display_name || "");
      const buyer = {
        id: user.id,
        name: String(buyerInput.name || nameParts.name).trim(),
        surname: String(buyerInput.surname || nameParts.surname).trim(),
        identityNumber: String(buyerInput.identityNumber || "").replace(/\D/g, ""),
        email: String(user.email || "").trim(),
        gsmNumber: String(buyerInput.gsmNumber || "").trim(),
        registrationAddress: String(buyerInput.address || "").trim(),
        city: String(buyerInput.city || "").trim(),
        country: "Turkey",
        zipCode: String(buyerInput.zipCode || "").trim(),
        ip: String(req.headers["x-forwarded-for"] || req.socket.remoteAddress || "127.0.0.1").split(",")[0].trim()
      };
      if (!buyer.name || !buyer.surname || !/^\d{11}$/.test(buyer.identityNumber) || !buyer.email || !/^\+?[0-9]{10,15}$/.test(buyer.gsmNumber.replace(/[\s()-]/g, "")) || !buyer.registrationAddress || !buyer.city) {
        return send(res, 400, { message: "iyzico ödeme formu için ad, soyad, 11 haneli T.C. kimlik numarası, telefon, adres ve şehir bilgileri gerekli." });
      }
      const conversationId = "FF" + randomUUID().replaceAll("-", "");
      const basketId = conversationId;
      const amount = (amountMinor / 100).toFixed(2);
      await adminRest("payment_orders", { method: "POST", headers: { Prefer: "return=minimal" }, body: JSON.stringify({ merchant_oid: conversationId, user_id: user.id, product_id: product.product_id, channel: "iyzico_checkout", amount_minor: amountMinor, currency: "TRY", credits_to_grant: credits, status: "pending", basket_id: basketId }) });
      let initialized;
      try {
        initialized = await iyzicoRequest("/payment/iyzipos/checkoutform/initialize/auth/ecom", {
          locale: "tr", conversationId: conversationId, price: amount, paidPrice: amount, currency: "TRY", basketId: basketId, paymentGroup: "PRODUCT", callbackUrl: IYZICO_CALLBACK_URL, enabledInstallments: [1],
          buyer: buyer,
          billingAddress: { address: buyer.registrationAddress, contactName: buyer.name + " " + buyer.surname, city: buyer.city, country: buyer.country, zipCode: buyer.zipCode },
          shippingAddress: { address: buyer.registrationAddress, contactName: buyer.name + " " + buyer.surname, city: buyer.city, country: buyer.country, zipCode: buyer.zipCode },
          basketItems: [{ id: product.product_id, price: amount, name: String(product.title).slice(0, 100), category1: "Dijital Hizmet", itemType: "VIRTUAL" }]
        });
        if (!initialized.token || (!initialized.paymentPageUrl && !initialized.checkoutFormContent)) throw new Error("iyzico ödeme formu bağlantısı döndürmedi.");
        if (!verifyIyzicoInitializeSignature(initialized, conversationId)) throw new Error("iyzico ödeme formu imzası doğrulanamadı.");
        await adminRest("payment_orders?merchant_oid=eq." + encodeURIComponent(conversationId), { method: "PATCH", body: JSON.stringify({ iyzico_token: initialized.token }) });
      } catch (error) {
        await adminRest("payment_orders?merchant_oid=eq." + encodeURIComponent(conversationId), { method: "PATCH", body: JSON.stringify({ status: "failed", failure_message: String(error.message || "iyzico initialize error").slice(0, 500) }) }).catch(() => {});
        throw error;
      }
      return send(res, 200, { conversationId: conversationId, token: initialized.token, paymentPageUrl: initialized.paymentPageUrl || null, checkoutFormContent: initialized.checkoutFormContent || null });
    }

    if (req.method === "GET" && pathname === "/api/payments/iyzico/status") {
      const user = await requireUser(req);
      if (!user || !user.id) return send(res, 401, { message: "Oturum doğrulanamadı." });
      const conversationId = new URL(req.url, "http://localhost").searchParams.get("conversationId") || "";
      if (!conversationId) return send(res, 400, { message: "Ödeme referansı gerekli." });
      const rows = await adminRest("payment_orders?select=merchant_oid,status,credits_to_grant,amount_minor,currency,created_at,paid_at,iyzico_token&merchant_oid=eq." + encodeURIComponent(conversationId) + "&user_id=eq." + encodeURIComponent(user.id) + "&channel=eq.iyzico_checkout&limit=1");
      const order = Array.isArray(rows) ? rows[0] : null;
      if (!order) return send(res, 404, { message: "Ödeme kaydı bulunamadı." });
      if (order.status === "pending" && order.iyzico_token) {
        const payment = await iyzicoRequest("/payment/iyzipos/checkoutform/auth/ecom/detail", { locale: "tr", conversationId: order.merchant_oid, token: order.iyzico_token });
        if (verifyIyzicoRetrieveSignature(payment) && payment.conversationId === order.merchant_oid && payment.token === order.iyzico_token && payment.currency === "TRY") {
          if (payment.paymentStatus === "SUCCESS" && Number(payment.fraudStatus) === 1) {
            await adminRpc("settle_iyzico_order", { p_conversation_id: payment.conversationId, p_token: order.iyzico_token, p_payment_id: String(payment.paymentId), p_price: Number(payment.price), p_paid_price: Number(payment.paidPrice), p_currency: payment.currency, p_basket_id: payment.basketId });
          } else if (payment.paymentStatus === "FAILURE" || Number(payment.fraudStatus) === -1) {
            await adminRest("payment_orders?merchant_oid=eq." + encodeURIComponent(order.merchant_oid) + "&iyzico_token=eq." + encodeURIComponent(order.iyzico_token), { method: "PATCH", body: JSON.stringify({ status: "failed", iyzico_payment_id: payment.paymentId ? String(payment.paymentId) : null, failure_message: String(payment.errorMessage || "iyzico payment failed").slice(0, 500) }) });
          }
        }
        const latest = await adminRest("payment_orders?select=status,paid_at&merchant_oid=eq." + encodeURIComponent(order.merchant_oid) + "&user_id=eq." + encodeURIComponent(user.id) + "&limit=1");
        if (Array.isArray(latest) && latest[0]) {
          order.status = latest[0].status;
          order.paid_at = latest[0].paid_at;
        }
      }
      return send(res, 200, { status: order.status, credits: order.status === "paid" ? order.credits_to_grant : 0, amountMinor: order.amount_minor, currency: order.currency, createdAt: order.created_at, paidAt: order.paid_at });
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

    if (req.method === "POST" && pathname === "/api/tasks/reconcile") {
      const user = await requireUser(req);
      if (!user?.id) return send(res, 401, { message: "Oturum doğrulanamadı." });
      const auth = req.headers.authorization || "";
      const pendingUrl = SUPABASE_URL + "/rest/v1/credit_reservations?select=provider_task_id&status=eq.reserved&provider_task_id=not.is.null&user_id=eq." + encodeURIComponent(user.id) + "&limit=20";
      const pendingResponse = await fetch(pendingUrl, {
        headers: { apikey: SUPABASE_PUBLISHABLE_KEY, Authorization: auth }
      });
      if (!pendingResponse.ok) return send(res, 502, { message: "Bekleyen video listesi alınamadı." });
      const pendingRows = await pendingResponse.json();
      let checked = 0;
      let completed = 0;
      let failed = 0;
      for (const row of pendingRows) {
        const taskId = String(row.provider_task_id || "");
        if (!taskId) continue;
        try {
          const task = await runway("/v1/tasks/" + encodeURIComponent(taskId), { method: "GET" });
          const state = String(task.status || "PENDING").toUpperCase();
          const output = Array.isArray(task.output) ? task.output : [];
          const videoUrl = typeof output[0] === "string" && output[0].startsWith("https://") ? output[0] : null;
          if (state === "SUCCEEDED") {
            await settleTask(taskId, true);
            completed++;
          } else if (["FAILED", "CANCELED", "CANCELLED"].includes(state)) {
            await settleTask(taskId, false);
            failed++;
          }
          if (["SUCCEEDED", "FAILED", "CANCELED", "CANCELLED"].includes(state)) {
            const patch = { status: state, updated_at: new Date().toISOString() };
            if (videoUrl) patch.video_url = videoUrl;
            await adminRest("video_generations?provider_task_id=eq." + encodeURIComponent(taskId) + "&user_id=eq." + encodeURIComponent(user.id), {
              method: "PATCH", headers: { Prefer: "return=minimal" }, body: JSON.stringify(patch)
            });
          }
          checked++;
        } catch (error) {
          console.error("Task reconciliation failed:", taskId, error.message);
        }
      }
      return send(res, 200, { ok: true, checked, completed, failed });
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
