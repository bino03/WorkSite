# Skill: Boas Práticas de Código (Transversal)

**When to use**: Sempre — é a referência transversal de qualidade de código para qualquer skill. As regras **do frontend** vivem em [[skill-frontend-design-system]] → "Regras de base" (desde 2026-09-19); aqui ficam os princípios que não dependem da linguagem, as regras do backend e o checklist.

**Time**: Referência contínua, não é um checklist de uma vez

---

## Porque é que este ficheiro existe

Em vez de repetir as mesmas regras de qualidade em cada skill (naming, tratamento de erros, o que testar antes de commitar), estão todas centralizadas aqui. Cada skill específica (`skill-add-backend-feature`, `skill-frontend-design-system`, etc.) foca-se no seu domínio e linka para aqui quando é relevante. Se uma regra geral mudar, muda-se **só neste ficheiro**.

A secção frontend que aqui existia **fundiu-se no `skill-frontend-design-system`** a 2026-09-19: uma tarefa de UI lia quatro camadas (`code-best-practices` → `design-system` → `frontend-visual-consistency` → `design/`), e as regras daqui eram uma versão resumida das que o design-system já tinha. Agora lê-se este ficheiro pelos princípios gerais, o design-system pelas regras de frontend, e `design/` pelos valores visuais — cada facto num sítio só.

---

## Princípios gerais (qualquer linguagem)

- **Nomes descritivos** — sem abreviações obscuras (`asset` não `ast`, `getUserById` não `getUsr`)
- **Funções/métodos pequenos**, uma responsabilidade cada
- **DRY sem abstração prematura** — 3 linhas repetidas é melhor que a abstração errada; só extrair quando o padrão se repete a sério
- **Sem código morto ou comentado** — apagar, não comentar (o git guarda o histórico)
- **Comentários só quando o "porquê" não é óbvio** pelo código — nunca explicar o "o quê" (o nome da função já diz isso)
- **Validar na fronteira, não em todo o lado** — validação de input no controller/formulário, não repetida em cada camada interna
- **Nunca commitar segredos** — `.env`, chaves, tokens (ver `.gitignore` na raiz do vault)

---

## Backend (Java / Spring Boot)

- **Naming**: classes `PascalCase`, métodos/variáveis `camelCase`, constantes `UPPER_SNAKE_CASE`
- **Um `Service` por domínio, um `Repository` por entidade** — não misturar lógica de negócio de várias entidades no mesmo service
- `@Transactional(readOnly = true)` em métodos de leitura; `@Transactional` (escrita) ao nível da classe do service
- **Nunca devolver entidades JPA diretamente** num controller — sempre através de um DTO de resposta (evita expor campos sensíveis e lazy-loading fora de sessão)
- **`@PreAuthorize` em todos os métodos do controller** — nunca confiar só nas regras globais do `SecurityConfig` (ver [[security]])
- Ficheiros nunca vão para a base de dados — só `bucket` + `storageKey` (ver [[skill-add-file-upload]])
- `FetchType.LAZY` em relações — evita N+1 queries

### Tratamento de erros (Backend)

- Todo o erro de negócio usa um `ErrorCode` definido em `dto/error/ErrorCode.java` — nunca strings soltas nem mensagens ad-hoc
- **Antes de adicionar um `ErrorCode` novo, procura no bloco do módulo relevante (`ASSET_xxx`, `USER_xxx`, `FILE_xxx`, etc.) se já existe um código com o significado que precisas.** O ficheiro tem 300+ códigos organizados por módulo — é fácil já existir um genérico (ex. `ACCESS_DENIED`/`USER_025` para negação de acesso, `FILE_TYPE_NOT_ALLOWED`/`FILE_003` para formato inválido) sem ser óbvio pelo nome que se procura. Só criar um novo se nenhum existente descrever bem o caso, e sempre no bloco do módulo certo — nunca inventar um prefixo novo (ex. `AUTH_xxx`) nem reutilizar um número já atribuído a outro significado.
- Lançar exceções tipadas (`ResourceNotFoundException`, `ForbiddenException`, `StorageException`, etc.) — nunca `RuntimeException` genérica
- O `GlobalExceptionHandler` centraliza a conversão exceção → resposta HTTP; não tratar erros manualmente dentro de cada controller
- DTOs de resposta nunca expõem stack traces, passwords ou tokens
- **O frontend nunca inventa códigos de erro** — `src/errors/errorMessages.ts` (Backoffice) espelha 1:1 as strings de `ErrorCode.java` (`'ASSET_001'`, sempre underscore); ver [[skill-frontend-error-handling]].

---


## Checklist rápido antes de qualquer commit

- [ ] Nomes claros, sem abreviações
- [ ] Sem código morto ou comentado
- [ ] Erros tratados de forma centralizada (`ErrorCode` no backend, `ErrorHandler` no frontend)
- [ ] Testado manualmente (curl/Postman no backend, browser no frontend)
- [ ] Sem segredos no diff (`.env`, chaves, tokens)
- [ ] Mensagem de commit segue [[skill-git-commits]]

---

## Skills relacionadas

- [[skill-add-backend-feature]] — Aplica estas regras num CRUD completo
- [[skill-add-database-table]] — Convenções de schema
- [[skill-add-file-upload]] — Regras específicas de upload
- [[skill-permissions-and-auth]] — Autorização e ownership
- [[skill-frontend-design-system]] — Padrões de componentes React **e as regras de base do frontend** (strict mode, services sem try/catch, Zod + RHF, restrição de caracteres, visibilidade por role)
- [[frontend-visual-consistency]] — Router para tokens visuais verificados do Backoffice
- [[skill-frontend-error-handling]] — Detalhe do `ErrorHandler`
- [[skill-git-commits]] — Convenções de mensagens de commit
