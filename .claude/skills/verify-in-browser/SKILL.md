---
name: verify-in-browser
description: Verify a Backoffice feature in the real app through the Claude-in-Chrome extension — start backend + Vite, connect the extension (restart Chrome, select_browser), log in, prove each item by DOM + fetch to the API (never by screenshot), write only inside an is_test enterprise created by fetch and deleted at the end (invoices first, credit notes before their origins), then update notes/verificacao-browser-pendente.md. Use when implement-todo reaches step 5.5 for a UI task, when the user asks to "verify in the browser", "test the UI", "check it in the app", or to run the pending browser checks in one batch. Runs inline, never in a subagent.
---

Read `${CLAUDE_PROJECT_DIR}/docs/skills/process/skill-verify-in-browser.md` in full and follow it step by step, starting from Step 0 (have the list of what to verify in hand before opening the browser).

Run it **inline in this session** — never delegate it to a subagent: the Chrome extension belongs to the main session. Load the `mcp__claude-in-chrome__*` tools you need in a single `ToolSearch` call before starting.

If asked to update this checklist, edit the vault file above, not this pointer.
