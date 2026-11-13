create table public.video_creations (
  id uuid primary key default gen_random_uuid(),
  user_id uuid references auth.users (id) on delete set null,
  prompt text not null check (char_length(btrim(prompt)) between 1 and 2000),
  video_url text not null check (
    char_length(video_url) between 9 and 4096 and video_url like 'https://%'
  ),
  aspect_ratio text not null check (aspect_ratio in ('16:9', '9:16', '1:1')),
  is_public boolean not null default false,
  created_at timestamptz not null default now()
);

create index video_creations_public_feed
  on public.video_creations (created_at desc, id desc) where is_public;

alter table public.video_creations enable row level security;

-- Explicit grants keep the author UUID out of the public API and reserve
-- publication for a trusted server, including publication on behalf of guests.
revoke all on public.video_creations from public, anon, authenticated;
grant select (id, prompt, video_url, aspect_ratio, is_public, created_at)
  on public.video_creations to anon, authenticated;
grant select, insert, update, delete on public.video_creations to service_role;

create policy "Read public video creations"
  on public.video_creations for select to anon, authenticated using (is_public);
