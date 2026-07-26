# Backoffice — Cards

> Parte de [[../frontend-visual-consistency]]. Só Backoffice.

## SectionCard — o padrão de header partilhado

`src/components/properties/create/ui/SectionCard.tsx` é o padrão real:

- **Gradiente real**: `linear-gradient(135deg, #78716cff 0%, #44403cff 100%)` — cinza-pedra (stone), **não** azul-roxo. (A documentação antiga no `CLAUDE.md` do Backoffice tinha o gradiente errado — `#86b3dd → #7738cf` — já corrigida para refletir o código real.)
- `Card` com `rounded-2xl`, `shadow-sm`, `border-0`, `bodyStyle={{padding:0}}`
- Ícone em container 56×56px, `borderRadius:16px`, `backgroundColor: rgba(255,255,255,0.2)`, `backdropFilter: blur(10px)`
- Círculo decorativo `rgba(255,255,255,0.1)` no canto superior direito
- Corpo com `p-8`

**Reutilizado corretamente** (mesmo gradiente `#78716c → #44403c`) em: `enterprise/EnterpriseViewDrawer.tsx`, vários `building/edit/Edit*Card.tsx`, `properties/edit/EditSettingsCard.tsx`, `properties/view/ViewSettings.tsx`, `properties/create/SettingsSection.tsx`.

## Drift encontrado — não repetir

- Alguns headers (ex. um bloco em `EnterpriseViewDrawer.tsx`) usam `borderRadius: 12px` + um padrão SVG de fundo em vez do círculo decorativo — inconsistente com o `SectionCard` original.
- Vários "sub-cards" usam gradientes claros ad-hoc por cor de estado, sem ligação a `theme.ts`:
  - Âmbar (aviso): `#fff7e6 → #fef3e2`
  - Verde (sucesso): `#f6ffed → #f0fff3`
  - Azul (informação): `#f0f9ff → #e0f2fe`
  - Neutro: `#fafaf9 → #f5f5f4`

  Usar **exatamente estas quatro combinações** se precisares de um acento de cor de estado num sub-card — não inventar mais.

## Skills relacionadas
- [[../../frontend/skill-frontend-design-system]]
- [[backoffice-tokens-and-colors]]
- [[backoffice-drawers-and-modals]]
