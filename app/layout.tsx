import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'FOTO FOTO — Hayal Et, Oluştur',
  description: 'Yapay zekâyla fikirlerini sinematik videolara dönüştür.',
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="tr"><body>{children}</body></html>;
}
