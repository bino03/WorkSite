# Backoffice — Cards

> Parte de [[../frontend-visual-consistency]]. Só Backoffice.

## BlueprintCard / `.ind-card` — o padrão

O card do sistema Industry é `src/components/common/BlueprintCard.tsx` (classe `.ind-card` em
`index.css`): caixa transparente com borda hairline, cantos **quadrados** (raio forçado globalmente),
quatro marcas "+" de registo nos cantos (`.ind-corner`), elevação por `.ind-elev-sm/md/lg`. Props:
`kicker` (rótulo pequeno em caixa alta, `.ind-card-kicker`), `elevation`, `corners`, `style`.

O header de secção com ícone é `src/components/enterprise/create/ui/SectionCard.tsx` — um
`BlueprintCard` com ícone em `var(--ind-color-accent)` + `.ind-card-title`. **Não tem gradiente,
nem ícone em caixa translúcida, nem círculo decorativo** — isso era o padrão do Property-Management,
substituído a 2026-08-05 (ver [[backoffice-tokens-and-colors]]).

Usado, entre outros, em `enterprise/EnterpriseViewDrawer.tsx`, `enterprise/create/*Section.tsx`,
`invoices/InvoiceDocumentGallery.tsx`, `ConstructionExpensesPage` (cartão de total) e nas páginas de
Construção. Antes de escrever um `<Card>` do AntD com `bodyStyle` à mão, verifica se `BlueprintCard`
ou `className="ind-card"` já resolve.

Acentos de estado num sub-card: usar as classes `.ind-tag-*` ou os tokens `--ind-accent-*` /
`--ind-neutral-*` — não gradientes claros por cor de estado (ver drift abaixo).

## Drift — o que ainda está no look antigo (não copiar)

> ✅ **`enterprise/edit/Edit*Card.tsx` migrados a 2026-09-16.** Os cinco já usam `BlueprintCard`/
> tokens `--ind-*`, sem gradiente cinza-pedra nem `<Card>` do AntD com `bodyStyle` à mão — o mesmo
> drawer deixou de mudar de sistema visual a meio ao carregar em "Editar". Overview, Datas & Áreas e
> Financeiro passaram também para RHF+Zod, reutilizando as secções da criação (ver
> [[backoffice-forms-and-validation]] §1). Localização e Galeria ficaram com `BlueprintCard` mas
> estado local próprio (razão em [[backoffice-forms-and-validation]] §1).

- Os mesmos gradientes claros ad-hoc (`#fff7e6 → #fef3e2`, `#f6ffed → #f0fff3`, `#f0f9ff → #e0f2fe`,
  `#fafaf9 → #f5f5f4`) que os `Edit*Card` tinham continuam em `InvitesDrawer.tsx`,
  `MapLocationPickerDrawer.tsx` e `ProfileView.tsx` — limpar quando esses ficheiros forem tocados.

## Skills relacionadas
- [[../../frontend/skill-frontend-design-system]]
- [[backoffice-tokens-and-colors]]
- [[backoffice-drawers-and-modals]]
- [[backoffice-forms-and-validation]] — os `Edit*Card` também divergem na biblioteca de formulários
