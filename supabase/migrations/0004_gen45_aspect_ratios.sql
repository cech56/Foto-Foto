-- FOTO FOTO migration 0004: align history constraints with supported Gen-4.5 ratios.
-- Run after 0003_video_history.sql in the FOTO FOTO Supabase project.

alter table public.video_generations
  drop constraint if exists video_generations_aspect_ratio_check;

alter table public.video_generations
  add constraint video_generations_aspect_ratio_check
  check (aspect_ratio in (
    '1280:720',
    '720:1280',
    '960:960',
    '1104:832',
    '832:1104',
    '1584:672'
  ));
