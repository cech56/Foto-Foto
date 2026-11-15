-- FOTO FOTO generation history. Run after 0001_profiles.sql and 0002_wallet.sql.
create table if not exists public.video_generations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  provider_task_id text not null unique,
  prompt text not null,
  duration_seconds integer not null check (duration_seconds in (5, 10)),
  aspect_ratio text not null check (aspect_ratio in ('1280:720', '720:1280')),
  status text not null default 'PENDING',
  video_url text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists video_generations_user_created_idx
  on public.video_generations(user_id, created_at desc);

alter table public.video_generations enable row level security;

drop policy if exists "Users can read their own video history" on public.video_generations;
create policy "Users can read their own video history"
  on public.video_generations for select using (auth.uid() = user_id);

drop policy if exists "Users can add their own video history" on public.video_generations;
create policy "Users can add their own video history"
  on public.video_generations for insert with check (auth.uid() = user_id);

drop policy if exists "Users can update their own video history" on public.video_generations;
create policy "Users can update their own video history"
  on public.video_generations for update using (auth.uid() = user_id) with check (auth.uid() = user_id);
