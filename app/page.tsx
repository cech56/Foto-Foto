'use client';

import { useState } from 'react';

const engines = [
  { id: 'gen4.5', name: 'RUNWAY', detail: 'Gen-4.5 · Sinematik', active: true },
  { id: 'kling3.0_standard', name: 'KLING', detail: 'Standard · Hareket', active: false },
  { id: 'veo3.1_fast', name: 'VEO', detail: '3.1 Fast · Hızlı üretim', active: false },
];

export default function Home() {
  const [prompt, setPrompt] = useState('');
  const [model, setModel] = useState('gen4.5');
  const [duration, setDuration] = useState(5);
  const [ratio, setRatio] = useState('1280:720');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('Fikrini yaz, motorunu seç ve ilk sahneni oluşturmaya başla.');
  const [taskId, setTaskId] = useState('');

  async function generate() {
    setBusy(true); setNotice('İsteğin video motoruna gönderiliyor…'); setTaskId('');
    try {
      const response = await fetch('/api/generate', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ prompt, model, duration, ratio }),
      });
      const data = await response.json();
      if (!response.ok) throw new Error(data.message || 'Üretim başlatılamadı.');
      setTaskId(data.taskId || '');
      setNotice(data.message || 'Üretim başladı.');
    } catch (error) {
      setNotice(error instanceof Error ? error.message : 'Bir hata oluştu.');
    } finally { setBusy(false); }
  }

  return <main className="app">
    <header className="nav"><a className="brand" href="#"><span className="brand-icon">F</span> FOTO FOTO</a><div className="nav-right"><span className="status-dot"/> AI VIDEO STUDIO</div></header>
    <section className="hero"><div className="eyebrow">YAPAY ZEKÂ İLE YARATICILIĞIN SINIRSIZ</div><h1>Hayal et.<br/><em>Foto Foto oluştur.</em></h1><p>Aklındaki sahneyi kelimelere dök. Yapay zekâ onu sinematik bir videoya dönüştürsün.</p></section>
    <section className="workspace">
      <div className="panel">
        <div className="panel-title"><div><small>YENİ PROJE</small><h2>Ne hayal ediyorsun?</h2></div><span className="step">01 / OLUŞTUR</span></div>
        <textarea value={prompt} onChange={e=>setPrompt(e.target.value)} maxLength={2500} placeholder="Örnek: Yağmurlu bir gecede neon ışıklarla aydınlanan Tokyo sokaklarında ilerleyen retro bir spor araba. Kamera aracı yandan takip ediyor, sinematik ışık, gerçekçi detaylar..." />
        <div className="counter">{prompt.length} / 2500</div>
        <label>ÜRETİM MOTORU</label>
        <div className="engines">{engines.map(e=><button key={e.id} type="button" disabled={!e.active} className={model===e.id?'engine selected':'engine'} onClick={()=>setModel(e.id)}><strong>{e.name}</strong><span>{e.detail}</span>{!e.active&&<i>API erişimi doğrulanınca açılacak</i>}</button>)}</div>
        <div className="options"><div><label htmlFor="duration">SÜRE</label><select id="duration" value={duration} onChange={e=>setDuration(Number(e.target.value))}><option value={5}>5 saniye</option><option value={10}>10 saniye</option></select></div><div><label htmlFor="ratio">FORMAT</label><select id="ratio" value={ratio} onChange={e=>setRatio(e.target.value)}><option value="1280:720">Yatay · 16:9</option><option value="720:1280">Dikey · 9:16</option><option value="960:960">Kare · 1:1</option></select></div></div>
        <button className="create" onClick={generate} disabled={busy}>{busy?'GÖNDERİLİYOR…':'✦  VİDEOMU OLUŞTUR'}</button>
        <div className="notice" role="status">{notice}{taskId&&<code>Üretim kodu: {taskId}</code>}</div>
      </div>
      <aside className="side"><div className="preview"><div className="orb"><span>F</span></div><div className="preview-title">Sen hayal et, biz sahneleyelim.</div><p>Oluşturduğun videolar burada görüntülenecek.</p><div className="preview-tags"><span>TEXT TO VIDEO</span><span>AI CREATIVE</span><span>STUDIO</span></div></div><div className="tip"><b>✧ İPUCU</b><p>Daha iyi sonuç için sahneyi, ışığı, kamera hareketini ve istediğin görsel tarzı ayrıntılı tarif et.</p></div><div className="wallet"><span>FOTO FOTO CÜZDAN</span><strong>Yakında</strong><small>Kredi sistemi: 1 kredi = 1 TL</small></div></aside>
    </section>
    <footer>FOTO FOTO <span>Hayal Et. Oluştur.</span></footer>
  </main>;
}
