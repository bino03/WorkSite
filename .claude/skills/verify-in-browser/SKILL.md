---
name: verify-in-browser
description: Verificar uma feature do Backoffice na app real através da extensão Claude in Chrome — arrancar backend + Vite, ligar a extensão (reiniciar o Chrome, select_browser), fazer login, provar cada item por DOM + fetch à API (nunca por screenshot), escrever só dentro de uma obra is_test criada por fetch e apagada no fim (faturas primeiro, notas de crédito antes das origens), e depois atualizar notes/verificacao-browser-pendente.md. Usar quando o implement-todo chega ao passo 5.5 numa tarefa com UI, quando o utilizador pede para "verificar no browser", "testar a UI", "ver na app", ou para correr as verificações pendentes de uma vez. Corre inline, nunca num subagente.
---

Read `${CLAUDE_PROJECT_DIR}/docs/skills/process/skill-verify-in-browser.md` in full and follow it step by step, starting from Step 0 (have the list of what to verify in hand before opening the browser).

Run it **inline in this session** — never delegate it to a subagent: the Chrome extension belongs to the main session. Load the `mcp__claude-in-chrome__*` tools you need in a single `ToolSearch` call before starting.

If asked to update this checklist, edit the vault file above, not this pointer.
