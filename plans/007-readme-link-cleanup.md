# 007 — Clean up post-rebrand README links that 404 until listings exist

**Audit base:** commit `a2fda9c` — verify with `git rev-parse HEAD` first.

## Why this matters

The rebrand repointed every URL at `sachit1751-art/MoneyPal` and
`com.sachit.moneypal`, but several destinations do not exist yet, so the
README renders broken images/links:

1. **Crowdin** section (`README.md` ~lines 93–97): badge URL and project link
   point at `moneypal-budget-tracker-app`, a Crowdin project that has not
   been created. The badge image 404s.
2. **Download buttons** (badges table, ~lines 24–38): IzzyOnDroid
   (`https://apt.izzysoft.de/packages/com.sachit.moneypal`) and Google Play
   (`https://play.google.com/store/apps/details?id=com.sachit.moneypal`)
   listings don't exist yet — both links dead-end on storefront 404/search
   pages. The GitHub Releases button is live and is the only working install
   path today.
3. **Live third-party badges** (badge row, ~lines 43–48): the
   `m3-markdown-badges.vercel.app/stars/...` and `/issues/...` images for
   `sachit1751-art/MoneyPal` render an error placeholder unless the repo is
   **public** (check with `gh repo view --json visibility` or the API).
4. **"Appearing on" YouTube section** (~lines 60–66): features of the original
   Minus app. Decision for the owner; the default here is **keep** (the app is
   a continuation) — only adjust if the owner objects.

Goal: the README must not show broken badges/links to the app's first
visitors, while making it trivial to re-enable the listings when they go live.

## Steps

1. Verify current reachability of each URL with
   `curl -sL -o /dev/null -w "%{http_code}" <url>` for: the Crowdin badge,
   `crowdin.com/project/moneypal-budget-tracker-app`, the IzzyOnDroid package
   page, the Play details page, and one `m3-markdown-badges.vercel.app` URL.
   Record the codes.
2. Check repo visibility:
   `gh repo view sachit1751-art/MoneyPal --json visibility` (if `gh` is
   unavailable, ask the owner; treat "can't verify" as private for the badge
   decision).
3. **Crowdin section:** if the project is unreachable (404 or badge image
   fails), replace the badge image + invite sentence with plain text noting
   translations are welcome once a Crowdin project exists — or, if the owner
   has since created it, point at the real URL and keep the badge. Leave a
   code comment or adjacent text with the intended slug
   (`moneypal-budget-tracker-app`) so re-enabling is one line.
4. **Download buttons:** keep the GitHub Releases button. For IzzyOnDroid and
   Play, if unreachable, replace the image buttons with either (a) text links
   labeled "IzzyOnDroid (soon)" / "Google Play (soon)" pointing at the same
   URLs, or (b) remove them and keep a single line under the table: *"Play
   Store and IzzyOnDroid listings coming soon — APKs are published with each
   release on GitHub."* Prefer (b) for a clean first-visit README; the exact
   URLs are preserved in git history and AGENTS.md conventions, and in the
   `--repo`/metadata config already in the repo.
5. **Star/issues badges:** if the repo is private or the images 404, remove
   those two `<img>` tags (keep the static Android/Kotlin/Compose badges,
   which are URL-constant). If public and reachable, keep.
6. Re-check: no `<img src="https://...">` in README points at a URL that
   returns 404 (local `assets/*` images are fine).
7. No compile/test impact — docs only. Final visual check: open the rendered
   README (GitHub or a markdown preview) and confirm no broken-image icons in
   the sections above.

## Done criteria

- [ ] `curl` sweep in step 1 recorded; every retained external `<img>`/link
      either returns 200 or is a deliberate "(soon)" placeholder with no
      broken image icon.
- [ ] No `m3-markdown-badges.vercel.app` reference remains if the repo is
      private.
- [ ] `README.md` still opens cleanly (balanced tags, no markdown lint
      regressions introduced).

## Test plan

None (documentation). Verification = the curl sweep + visual check.

## Maintenance note

- Re-enable Crowdin/Play/Izzy links the moment those listings exist — this
  plan is explicitly temporary until then. The intended slugs/URLs are: 
  `crowdin.com/project/moneypal-budget-tracker-app`,
  `apt.izzysoft.de/packages/com.sachit.moneypal`,
  `play.google.com/store/apps/details?id=com.sachit.moneypal`.
- If the repo is made public, restore the star/issues badges (they are
  marketing value for an open-source app).

## Escape hatches

- If any checked URL already returns 200 (listing went live between audit and
  execution), keep that link/badge as-is and note it in the report.

## Out of scope / boundaries

- No changes to code, AGENTS.md, CONTRIBUTING.md, or store metadata under
  `fastlane/` (metadata still targets the real future listings).
- Do not delete the "Appearing on" section unless the owner explicitly asks.
