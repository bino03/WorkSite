---
name: frontend-error-handling
description: Tratamento de erros centralizado no frontend — mapa plano errorCode → mensagem PT, espelho 1:1 do ErrorCode.java do backend, enums opcionais por domínio para referências type-safe, ErrorHandler.handle() (deteta fieldErrors para mostrar erros de validação), padrões de notificação. Usar quando um componente React chama a API e precisa de tratar erros ou mostrar erros de validação.
---

Before writing any code, also read `${CLAUDE_PROJECT_DIR}/docs/skills/references/code-best-practices.md` (the language-agnostic principles) and the "Regras de base" section of `${CLAUDE_PROJECT_DIR}/docs/skills/frontend/skill-frontend-design-system.md` (the frontend rules: no try/catch in services, `ErrorHandler` in the component) — apply both to the error-handling code this skill produces.

Then read `${CLAUDE_PROJECT_DIR}/docs/skills/frontend/skill-frontend-error-handling.md` in full and follow it step by step.

If asked to update this checklist, edit the vault file above, not this pointer.
