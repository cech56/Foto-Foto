import { NextRequest, NextResponse } from 'next/server';

export const runtime = 'nodejs';

const allowedModels = new Set(['gen4.5']);

export async function POST(request: NextRequest) {
  const secret = process.env.RUNWAYML_API_SECRET;
  if (!secret) return NextResponse.json({ message: 'Video motoru henüz sunucuya bağlanmadı. RUNWAYML_API_SECRET ayarlanmalı.' }, { status: 503 });

  try {
    const body = await request.json() as { prompt?: string; model?: string; duration?: number; ratio?: string };
    const prompt = body.prompt?.trim() ?? '';
    const model = body.model ?? 'gen4.5';
    const duration = Number(body.duration ?? 5);
    const ratio = body.ratio ?? '1280:720';

    if (prompt.length < 10 || prompt.length > 2500) return NextResponse.json({ message: 'Açıklama 10–2500 karakter arasında olmalı.' }, { status: 400 });
    if (!allowedModels.has(model)) return NextResponse.json({ message: 'Bu video motoru henüz etkin değil.' }, { status: 400 });
    if (![5, 10].includes(duration)) return NextResponse.json({ message: 'Şimdilik 5 veya 10 saniye seçebilirsin.' }, { status: 400 });
    if (!['1280:720', '720:1280', '960:960'].includes(ratio)) return NextResponse.json({ message: 'Geçersiz görüntü oranı.' }, { status: 400 });

    const response = await fetch('https://api.dev.runwayml.com/v1/text_to_video', {
      method: 'POST',
      headers: { Authorization: `Bearer ${secret}`, 'Content-Type': 'application/json', 'X-Runway-Version': '2024-11-06' },
      body: JSON.stringify({ model, promptText: prompt, duration, ratio }),
    });
    const data = await response.json();
    if (!response.ok) return NextResponse.json({ message: data?.message ?? data?.error ?? 'Video servisi isteği reddetti.' }, { status: response.status });
    return NextResponse.json({ taskId: data.id, status: 'processing', message: 'Üretim isteği gönderildi.' });
  } catch {
    return NextResponse.json({ message: 'İstek işlenemedi. Lütfen tekrar dene.' }, { status: 500 });
  }
}
