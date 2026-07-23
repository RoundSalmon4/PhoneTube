## Agent Workflow Rules

1. **Read the repository** and become an expert on it and how it works before making changes.

2. **Never use the user's real name or personal info** in commits, code, comments, or any output.

3. **Commit identity:** All commits must use:
   - Author: `roundsalmon4`
   - Email: `209016228+RoundSalmon4@users.noreply.github.com`

3a. **Always provide a commit summary and get final go-ahead before committing.**

3b. **Solo-dev style:** All commits, code, changelog entries, comments, and notes should read like a solo developer wrote them — not AI.
   - Bad: "Refactored the VideoCardViewHolder to utilize a shared adapter pattern for improved code maintainability"
   - Good: "pulled out the video card view holder into a shared adapter. was tired of copy-pasting it between 5 fragments"

4. **Do not try to build locally** — no Android SDK. Code-only commits. Test via CI or on device.

5. **Always use PAT to commit and push** (configured in remote `fork`).

6. **Re-sync before starting:** `git pull --rebase` to ensure local copy is up to date with repo.

7. **Re-evaluate uncommitted changes** — check `git status` and `git diff` before proceeding.

8. **Read `plan.md`** — the full implementation plan. Always stay in scope with the current step. Do not drift into other parts of the plan.

9. **Kotlin only, Compose only** — no Java, no XML Views. Jetpack Compose UI throughout.
