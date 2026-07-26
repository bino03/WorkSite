---
name: frontend-error-handling
description: Centralized frontend error handling — flat errorCode → PT message map mirroring the backend's ErrorCode.java 1:1, optional per-domain enums for type-safe references, ErrorHandler.handle() (auto-detects fieldErrors for validation display), notification patterns. Use when a React component makes an API call and needs error handling or validation error display.
---

Before writing any code, also read `${CLAUDE_PROJECT_DIR}/docs/skills/references/code-best-practices.md` — apply it to the error-handling code this skill produces.

Then read `${CLAUDE_PROJECT_DIR}/docs/skills/frontend/skill-frontend-error-handling.md` in full and follow it step by step.

If asked to update this checklist, edit the vault file above, not this pointer.
