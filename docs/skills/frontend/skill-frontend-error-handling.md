# Skill: Implement Frontend Error Handling

**When to use**: Any component that makes API calls

**Time**: ~15 minutes per feature

> 📐 See also [[code-best-practices]] for general naming/error-handling conventions used throughout this checklist.
>
> ⚠️ **Antes de adicionares um código novo**: os códigos de erro nascem sempre no backend (`dto/error/ErrorCode.java`), nunca no frontend. Este skill só **espelha** códigos que já existem lá. Se o teu caso de erro ainda não tem código no backend, cria-o primeiro seguindo [[skill-add-backend-feature]] (verifica sempre o bloco do módulo relevante — pode já existir um código adequado antes de inventares um novo).

---

## Structure

```
src/errors/
├── error.types.ts      ← Tipos de resposta de erro + enums de código por domínio (opcionais, para referência type-safe)
├── errorMessages.ts    ← ERROR_MESSAGES: mapa plano código → mensagem PT (fonte de verdade, 1:1 com o backend)
└── errorHandler.ts     ← Classe ErrorHandler, com handle() centralizado
```

O mapa `ERROR_MESSAGES` em `errorMessages.ts` é indexado **diretamente pela string do código do backend** (ex. `'ASSET_001'`, `'ERR_001'` — sempre com underscore, nunca hífen, porque tem de bater certo com o que a API envia em `errorCode`). Os enums por domínio em `error.types.ts` (`AssetErrorCode`, `AgentErrorCode`, `UserErrorCode`, etc.) são uma camada de conveniência opcional para quem precisa referenciar um código específico com type-safety num componente — **não são a fonte de verdade** e nem todos os módulos do backend têm enum equivalente. Se precisares de um enum novo, os valores têm de ser exatamente as strings do backend, nunca inventados.

---

## Step 1: Confirmar/Adicionar o Error Code

Antes de escrever qualquer coisa no frontend, confirma o código no backend:

```java
// dto/error/ErrorCode.java — bloco do módulo relevante, ex. ASSET_xxx
ASSET_NOT_FOUND("ASSET_001", "Asset não encontrado"),
```

Só depois, se ainda não existir, adiciona a entrada correspondente em `error.types.ts` (opcional — só se o código for referenciado por nome algures no código, não só usado como string solta):

```typescript
// errors/error.types.ts
export enum AssetErrorCode {
  ASSET_NOT_FOUND = 'ASSET_001', // tem de bater certo com ErrorCode.java
  // ...
}
```

---

## Step 2: Mapear para Mensagem PT

```typescript
// errors/errorMessages.ts
export const ERROR_MESSAGES: Record<string, string> = {
  // ── Assets / Propriedades ──────────────────────────────────────────
  'ASSET_001': 'Propriedade não encontrada. Pode ter sido removida.',
  'ASSET_002': 'Esta propriedade já existe no sistema.',
  // ...

  'DEFAULT': 'Ocorreu um erro. Por favor, tente novamente.',
};

/** Retorna a mensagem user-friendly para um errorCode */
export function getUserFriendlyMessage(errorCode?: string): string {
  if (!errorCode) return ERROR_MESSAGES['DEFAULT'];
  return ERROR_MESSAGES[errorCode] ?? ERROR_MESSAGES['DEFAULT'];
}
```

Agrupa por módulo com um comentário (`// ── Assets ──`), na mesma ordem dos blocos de `ErrorCode.java`, para ser fácil comparar os dois ficheiros lado a lado.

---

## Step 3: Centralized Handler

```typescript
// errors/errorHandler.ts
import { AxiosError } from 'axios';
import type { ErrorResponse, ErrorConfig } from '@/errors/error.types';
import { getUserFriendlyMessage } from '@/errors/errorMessages';
import { notificationService } from '../services/general/notificationService';

export class ErrorHandler {
  static handle(error: unknown, config: ErrorConfig = {}) {
    const {
      showNotification = true,
      notificationType = 'error',
      customMessage,
      logToConsole = true,
    } = config;

    if (logToConsole && process.env.NODE_ENV === 'development') {
      console.error('Error caught:', error);
    }

    if (this.isAxiosError(error)) {
      const errorResponse = error.response?.data as ErrorResponse;

      // Erros de validação (múltiplos campos) — mostrados por campo, não como toast genérico
      if (errorResponse?.fieldErrors && errorResponse.fieldErrors.length > 0) {
        if (showNotification) {
          notificationService.validationError(errorResponse.fieldErrors);
        }
        return errorResponse;
      }

      const message = customMessage ||
                     getUserFriendlyMessage(errorResponse?.errorCode) ||
                     errorResponse?.message ||
                     'Ocorreu um erro inesperado.';

      if (showNotification) {
        notificationService[notificationType]('Erro', message);
      }

      return errorResponse;
    }

    const message = customMessage || 'Ocorreu um erro inesperado.';
    if (showNotification) {
      notificationService[notificationType]('Erro', message);
    }
    return null;
  }

  private static isAxiosError(error: unknown): error is AxiosError {
    return (error as AxiosError).isAxiosError === true;
  }

  /** Helper para extrair só a mensagem, sem disparar notificação */
  static getMessage(error: unknown): string {
    if (this.isAxiosError(error)) {
      const errorResponse = error.response?.data as ErrorResponse;
      return getUserFriendlyMessage(errorResponse?.errorCode) ||
             errorResponse?.message ||
             'Ocorreu um erro inesperado.';
    }
    if (error instanceof Error) return error.message;
    return 'Ocorreu um erro inesperado.';
  }
}
```

`ErrorConfig` (`error.types.ts`): `showNotification?: boolean`, `notificationType?: 'error' | 'warning' | 'info'`, `customMessage?: string`, `logToConsole?: boolean`. Não existe modo `silent` — para não mostrar notificação, passa `showNotification: false`.

Validação de campos **não é um método separado** — `ErrorHandler.handle()` já deteta `errorResponse.fieldErrors` (formato `{ field, message }[]`, populado pelo backend em erros 400 de `@Valid`) e delega para `notificationService.validationError(fieldErrors)`, que lista os campos na notificação. Não há `ErrorHandler.handleValidation()`; se precisares de aplicar os erros diretamente aos campos do React Hook Form em vez de só notificar, lê `error.response.data.fieldErrors` no `catch` e chama `form.setError(field, { message })` para cada um, antes ou depois de chamar `ErrorHandler.handle()`.

---

## Step 4: Use in Components

### In List Components

```typescript
export function PropertyList() {
  const [data, setData] = useState<Property[]>([]);
  const [loading, setLoading] = useState(false);

  const load = async () => {
    setLoading(true);
    try {
      const res = await getProperties({ page: 0, size: 20 });
      setData(res.content);
    } catch (e) {
      ErrorHandler.handle(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { load(); }, []);

  return <Table dataSource={data} loading={loading} />;
}
```

### In Forms

```typescript
export function CreatePropertyForm() {
  const form = useForm<PropertyValues>();

  const onSubmit = async (values: PropertyValues) => {
    try {
      await createProperty(values);
      notificationService.success('Propriedade criada com sucesso!');
      form.reset();
    } catch (e) {
      ErrorHandler.handle(e);
    }
  };

  return (
    <form onSubmit={form.handleSubmit(onSubmit)}>
      {/* fields */}
      <button type="submit">Criar</button>
    </form>
  );
}
```

### With Custom Message

```typescript
try {
  await createProperty(values);
} catch (e) {
  ErrorHandler.handle(e, {
    customMessage: "Não foi possível criar a propriedade. Tente mais tarde.",
  });
}
```

### Without Notification (silent)

```typescript
try {
  await checkIfEmailExists(email);
} catch (e) {
  ErrorHandler.handle(e, { showNotification: false });
  // Handle silently, show custom UI instead
}
```

---

## Step 5: Errors in list hooks

> Este projeto **não usa Zustand** (ver [[skill-frontend-design-system]]) — o estado de lista vive num hook local. O padrão de erro é o mesmo: apanhar, passar ao `ErrorHandler`, e guardar a mensagem se a UI precisar de a mostrar inline.

```typescript
// hooks/useSomething.ts
const [items, setItems] = useState<Item[]>([]);
const [loading, setLoading] = useState(false);
const [error, setError] = useState<string | null>(null);

const fetchItems = async () => {
  setLoading(true);
  setError(null);
  try {
    const res = await getItems();
    setItems(res.content);
  } catch (e) {
    ErrorHandler.handle(e);
    setError(ErrorHandler.getMessage(e));
  } finally {
    setLoading(false);
  }
};
```

---

## HTTP Status → Categoria (quando não há errorCode)

Usado por `getCategoryFromStatus` em `errorMessages.ts` para classificar o erro (afeta cor/ícone da notificação), não para inventar um `errorCode` que o backend não enviou:

```typescript
export function getCategoryFromStatus(status: number): ErrorCategory {
  if (status === 404) return ErrorCategory.NOT_FOUND;
  if (status === 401 || status === 403) return ErrorCategory.PERMISSION;
  if (status === 409) return ErrorCategory.DATABASE;
  if (status >= 500) return ErrorCategory.SERVER;
  return ErrorCategory.BUSINESS;
}
```

---

## Final Checklist

- [ ] Código já existe em `ErrorCode.java` (verificado, não inventado) — se não existir, foi criado lá primeiro
- [ ] Entrada correspondente adicionada a `ERROR_MESSAGES` em `errorMessages.ts`, com a **mesma string exata** do backend
- [ ] (Só se necessário) Enum de conveniência adicionado/atualizado em `error.types.ts`
- [ ] `ErrorHandler.handle()` usado em todos os `catch` de chamadas à API
- [ ] Try/catch envolve todas as operações assíncronas
- [ ] Notificações mostram mensagens user-friendly em português
- [ ] `showNotification: false` usado para erros não-críticos que têm UI própria
- [ ] Erros de campo (`fieldErrors`) verificados quando o endpoint tem validação `@Valid`
- [ ] Loading state limpo mesmo em erro (`finally`)
- [ ] Testado com cenários de erro reais (não só o caminho feliz)

---

## Related Skills

- [[code-best-practices]] — General code quality rules
- [[skill-frontend-design-system]] — Component patterns
- [[skill-frontend-integration-guide]] — How backend errors map
- [[skill-add-backend-feature]] — Onde os ErrorCodes nascem (Step 1)
