# Skill: Follow Frontend Design System

**When to use**: Building any UI component in the Backoffice (the only frontend app in this project)

**Time**: Part of component creation

> 📐 See also [[code-best-practices]] for general naming/error-handling conventions, and [[frontend-visual-consistency]] for verified design tokens, drawer sizing, and known visual drift to avoid repeating.

---

## Tech Stack

- **React 18** with TypeScript (strict mode)
- **Vite 7** for build
- **Tailwind CSS 4** for styling
- **Ant Design 5** for components
- **React Hook Form + Zod** for forms
- **React Context** for cross-cutting state (`AuthContext`, `ConfirmDialogContext`) — **não há Zustand neste projeto** (nem em `package.json`, nem em `src/`)
- **Axios** for HTTP (single instance in `src/api.ts`)

---

## Component Structure

```
src/components/
├── <domain>/
│   ├── create/
│   │   ├── <Domain>FormSchema.ts    ← Zod validation
│   │   ├── use<Domain>Form.ts       ← Custom hook (if complex)
│   │   ├── ui/                      ← Sub-components only used here
│   │   └── Create<Domain>Drawer.tsx ← Main form component
│   │
│   ├── edit/
│   │   ├── Edit<Domain>Card.tsx     ← Individual edit cards
│   │   └── ...
│   │
│   ├── view/
│   │   ├── View<Domain>Card.tsx     ← Read-only detail cards
│   │   └── ...
│   │
│   ├── <Domain>List.tsx             ← List page component
│   └── <Domain>ViewDrawer.tsx       ← Detail drawer
│
└── common/                          ← Shared across all domains
    ├── CountryDistrictSelect.tsx
    └── ...
```

---

## Naming Conventions

| Thing | Pattern | Example |
|-------|---------|---------|
| Components | PascalCase | `PropertyCard.tsx` |
| Files | PascalCase | `PropertyViewDrawer.tsx` |
| Hooks | `use` prefix | `usePropertyForm.ts` |
| Services | `Service` suffix | `propertyService.ts` |
| Stores | `Store` suffix | `propertyStore.ts` |
| Schemas | `Schema` suffix | `propertyFormSchema.ts` |
| Types | PascalCase | `Property.ts` |
| Route paths | kebab-case | `/properties`, `/my-requests` |

---

## Idioma dos segmentos de rota (URL)

O Backoffice tem hoje uma mistura real no `main.tsx`: os segmentos de topo herdados estão em **português** (`empreendimentos`, `funcionarios`), e os criados já neste projeto estão em inglês (`tasks`, `construction`) — não é a tabela acima aplicada ao pé da letra, é o estado real do código. Ver [[backoffice-app-shell-and-auth]] para a lista completa de rotas.

**Convenção daqui em diante**: qualquer segmento de rota **novo** (uma funcionalidade nova, ou sub-rotas aninhadas dentro de um domínio já existente) usa **termos em inglês**, mesmo que o segmento pai onde a nova rota se aninha esteja em português. Isto acompanha a decisão já tomada para nomes de ficheiros, componentes, tabelas na BD e endpoints do backend — só os segmentos de topo já existentes ficam em português por continuidade com o que já lá está, não é para migrar os antigos.

Exemplo real (feature de etapas/despesas de construção, aninhada dentro do domínio `empreendimentos` já existente):
```tsx
// segmento pai em português (já existia) + segmentos novos em inglês
<Route path="empreendimentos/:enterpriseId/construction" element={<ConstructionStagesPage />} />
<Route path="empreendimentos/:enterpriseId/construction/:stageId" element={<ConstructionSubStagesPage />} />
```
Não `.../construcao/...` — isso reintroduziria a mistura de idioma dentro da mesma funcionalidade que esta convenção existe para evitar.

---

## Visibilidade de campos por role

Antes de construir uma lista (colunas de tabela) ou um formulário (campos), perguntar sempre ao utilizador: **cada campo é visível para todas as roles, ou só para algumas** (ex. só `ADMIN`, não `EMPLOYEE`)? Não assumir nem que tudo é visível para toda a gente, nem o oposto — é a mesma conversa que perguntar quais campos são obrigatórios (ver "Forms" abaixo), não uma pergunta à parte.

- **Duas camadas, não uma.** Se um campo é sensível (dados financeiros, notas internas, contacto pessoal) e só uma role deve vê-lo:
  1. **Backend primeiro**: o DTO de resposta não deve incluir o campo para quem não tem a role certa (ver [[skill-permissions-and-auth]]). Esconder um campo só no frontend quando a API continua a devolvê-lo **não é segurança** — quem inspecionar a resposta de rede vê o campo na mesma.
  2. **Frontend depois**: condicionar a renderização da coluna/campo à role do utilizador autenticado (`useAuth()`/`user_role`), como reforço de UI — nunca como única defesa.
- Se o DTO já existe e já filtra por role no backend, isso já responde à pergunta para esse campo — só perguntar para campos novos ou onde a regra do backend não é clara.

---

## Forms (React Hook Form + Zod)

### Antes de escrever o schema: só perguntar sobre os campos opcionais no backend

O frontend **nunca é mais permissivo que o backend** — só pode ser igual ou mais estrito:

- **Campo obrigatório no backend** (`@NotBlank`/`@NotNull` no DTO) → é **sempre** `.min(1, ...)` no Zod. Não perguntar, não é uma escolha — copiar a obrigatoriedade diretamente do DTO.
- **Campo opcional no backend** (sem `@NotBlank`/`@NotNull`, ou o formulário não tem DTO de referência porque é uma funcionalidade nova) → **aqui sim, perguntar ao utilizador** quais desses campos opcionais devem passar a obrigatórios só no frontend (`.min(1, ...)` em vez de `.optional()`). É a única decisão que não deve ser adivinhada.

Nunca fazer o inverso — relaxar no frontend um campo que é obrigatório no backend (isso só provocaria o pedido a falhar do lado do servidor com um erro pior do que a validação do formulário teria dado).

### Validação de segurança: caracteres proibidos

Todo o campo de texto livre precisa de uma validação que **restrinja o conjunto de caracteres aceites**, não só o comprimento — é a primeira linha de defesa contra XSS armazenado e payloads maliciosos, mesmo sabendo que o React escapa por omissão e que o backend também deve validar (`skill-add-backend-feature` → `@Pattern`/`@Size` no DTO). As duas camadas são complementares, nenhuma substitui a outra.

- **Texto livre (nomes, moradas, descrições curtas)**: usar um regex de lista branca em vez de bloquear caracteres um a um — ex. `/^[\p{L}\p{N}\s.,'-]+$/u` (letras Unicode, números, espaço, e só a pontuação que o campo realmente precisa). Rejeitar sempre `<`, `>`, `` ` ``, `{`, `}`, `$` — não têm razão de existir num nome ou morada e são os caracteres mais usados em tentativas de injeção.
- **Email**: `z.string().email()` (validação embutida do Zod), nunca um regex manual reinventado.
- **Telefone**: regex restrito a dígitos/`+`/espaços/hífens, ex. `/^[\d+\s-]+$/`.
- **Texto longo (descrições, comentários)**: mesmo sendo mais permissivo, manter sempre um `.max(N)` — nunca um campo de texto sem limite de comprimento.
- **Campos numéricos**: `z.number()` com `.positive()`/`.min()`/`.max()` explícitos — nunca aceitar uma string e converter sem limites.

```typescript
// components/property/create/propertyFormSchema.ts
import { z } from "zod";

const SAFE_TEXT = /^[\p{L}\p{N}\s.,'-]+$/u;

export const PropertyFormSchema = z.object({
  name: z.string().min(1, "Name is required").max(120).regex(SAFE_TEXT, "Contains invalid characters"),
  price: z.number().positive("Price must be positive").max(1_000_000_000),
  description: z.string().max(2000).optional(),
});

export type PropertyFormValues = z.infer<typeof PropertyFormSchema>;
```

### In Component

```typescript
const form = useForm<PropertyFormValues>({
  resolver: zodResolver(PropertyFormSchema),
  defaultValues: { name: "", price: 0 },
});

const onSubmit = async (values: PropertyFormValues) => {
  try {
    await createProperty(values);
    notificationService.success("Created successfully");
  } catch (e) {
    ErrorHandler.handle(e);
  }
};

return (
  <form onSubmit={form.handleSubmit(onSubmit)}>
    <input {...form.register("name")} />
    <button type="submit">Create</button>
  </form>
);
```

---

## Service Layer

**Pattern**: One file per domain, CRUD functions, no try/catch

```typescript
// services/propertyService.ts
import api from "../api";

export interface Property { id: string; name: string; /* ... */ }
export interface PropertyFilters { page?: number; size?: number; }

export const getProperties = async (params: PropertyFilters) =>
  (await api.get("/properties", { params })).data;

export const getPropertyById = async (id: string) =>
  (await api.get(`/properties/${id}`)).data;

export const createProperty = async (data: object) =>
  (await api.post("/properties", data)).data;

export const updateProperty = async (id: string, data: object) =>
  (await api.put(`/properties/${id}`, data)).data;

export const deleteProperty = async (id: string) =>
  void (await api.delete(`/properties/${id}`));
```

**Important**: No try/catch in services — errors bubble to caller for centralized handling.

---

## Error Handling

Estrutura completa (error codes, mapa de mensagens PT, `ErrorHandler`) → [[skill-frontend-error-handling]]. Aqui fica só o padrão de uso dentro de um componente:

```typescript
try {
  await createProperty(values);
  notificationService.success("Created");
} catch (e) {
  ErrorHandler.handle(e);  // Mostra a mensagem mapeada em errorMessages.ts
}
```

---

## Drawers Pattern

Instead of separate pages, use Drawers for modals:

```typescript
export function PropertyViewDrawer({ id, onClose }: Props) {
  const [item, setItem] = useState<Property | null>(null);

  useEffect(() => {
    if (!id) return;
    getPropertyById(id).then(setItem).catch(ErrorHandler.handle);
  }, [id]);

  return (
    <Drawer open={!!id} onClose={onClose} width={860}>
      {item ? <PropertyDetails item={item} /> : <Spin />}
    </Drawer>
  );
}
```

---

## Estado partilhado — Context, não Zustand

> ⚠️ O projeto de origem (Property-Management) usava **Zustand** para o estado de listas. O Worksite **não tem Zustand** — não está em `package.json` nem é importado em lado nenhum de `src/`. Não crie stores Zustand: isso acrescentaria uma dependência nova sem necessidade.

O que existe hoje:

- **Estado de lista** (filtros, paginação, `loading`) — **local à página**, com `useState` + `useEffect`, ou via `hooks/useApiCall.ts` / `hooks/useTasks.ts`. Ver `EnterprisesList.tsx`, `EmployeesList.tsx`, `TasksPage.tsx`.
- **Estado transversal** — React Context: `context/AuthContext.tsx` (sessão/utilizador, consumido por `useAuth()`) e `context/ConfirmDialogContext.tsx` (diálogo de confirmação partilhado, via `useConfirm()`).

```typescript
// hooks/useTasks.ts — o padrão a seguir para estado de lista:
// o hook é dono do estado (dados + loading + paginação + filtros) e expõe as ações
const { tasks, loading, pagination, filters, fetchTasks, handleFilterChange } = useTasks();

// hooks/useApiCall.ts — para uma chamada avulsa, não uma lista
const { execute, loading, error } = useApiCall();
```

Se um dia o estado de lista precisar mesmo de ser partilhado entre páginas, discute primeiro se um Context chega — introduzir uma biblioteca de estado é uma decisão de arquitetura, não um detalhe de implementação.

---

## Styling (Tailwind + Ant Design)

Use **Tailwind CSS** for custom styling, **Ant Design** for components:

```typescript
export function PropertyCard({ property }: Props) {
  return (
    <div className="p-4 border border-gray-200 rounded-lg hover:shadow-md">
      <h2 className="text-lg font-semibold mb-2">{property.name}</h2>
      <p className="text-gray-600">{property.description}</p>
      <Button type="primary" className="mt-4">
        View Details
      </Button>
    </div>
  );
}
```

---

## Final Checklist

- [ ] Folder structure follows pattern
- [ ] Components are PascalCase
- [ ] Files are PascalCase
- [ ] Services are one file per domain
- [ ] Perguntei quais campos (lista ou formulário) são visíveis só para certas roles, e confirmei que o backend já filtra esses campos no DTO (não só o frontend a escondê-los)
- [ ] Campos obrigatórios no backend estão `.min(1, ...)` no Zod sem exceção; só perguntei ao utilizador sobre os campos que são opcionais no backend
- [ ] Zod schemas validate all inputs — incluindo restrição de caracteres (regex) em todo o campo de texto livre, não só comprimento
- [ ] Error codes defined for this domain
- [ ] ErrorHandler used for user errors
- [ ] No try/catch in services
- [ ] Drawers used instead of separate pages
- [ ] Estado de lista num hook local (`useTasks`-style) ou `useState` na página — **nunca** um store Zustand (não existe neste projeto)
- [ ] Tailwind for custom styles, Ant Design for components
- [ ] Tested in browser before committing
- [ ] Introduced a new visual/structural pattern (not just reused an existing one)? → update the matching `docs/skills/references/design/backoffice-<area>.md` sub-file via [[frontend-visual-consistency]] (the git pre-commit hook flags `theme.ts`/`index.css`/`colors.css`/services/layout/form-component changes, but the sub-file text itself needs updating by hand — see [[vault-sync-hooks]])

---

## Related Skills

- [[code-best-practices]] — General code quality rules
- [[frontend-visual-consistency]] — Router to verified design tokens and known drift in the Backoffice
- [[skill-frontend-integration-guide]] — How backend integrates with this
- [[skill-frontend-error-handling]] — Error handling details
