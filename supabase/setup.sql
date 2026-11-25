-- Sets up a Supabase project for Zeitreise's optional cross-device sync.
-- Run it once in the project's SQL editor. It's safe to run again.
--
-- Each user gets one row holding their progress as JSON: passport stamps, home country,
-- passport name, best quiz score and whether they finished the onboarding. The app merges this
-- row with what each device stored, so progress follows the user from device to device.

create table if not exists public.progress (
    user_id uuid primary key default auth.uid() references auth.users (id) on delete cascade,
    data jsonb not null,
    updated_at timestamptz not null default now()
);

-- Row-level security: a signed-in user can only see and change their own row. This is what
-- makes it safe to ship the project's public key inside the app.
alter table public.progress enable row level security;

drop policy if exists "Read own progress" on public.progress;
create policy "Read own progress" on public.progress
    for select to authenticated using ((select auth.uid()) = user_id);

drop policy if exists "Add own progress" on public.progress;
create policy "Add own progress" on public.progress
    for insert to authenticated with check ((select auth.uid()) = user_id);

drop policy if exists "Change own progress" on public.progress;
create policy "Change own progress" on public.progress
    for update to authenticated using ((select auth.uid()) = user_id) with check ((select auth.uid()) = user_id);

-- Keeps updated_at current on every save.
create or replace function public.touch_progress() returns trigger
    language plpgsql set search_path = '' as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists touch_progress on public.progress;
create trigger touch_progress before update on public.progress
    for each row execute function public.touch_progress();
