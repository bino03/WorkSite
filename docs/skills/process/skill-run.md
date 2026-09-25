# Skill: Run

**When to use**: Sempre que for preciso arrancar a app localmente — backend + Backoffice — para
testar uma alteração, verificar no browser, ou só confirmar que tudo sobe sem erros.

**Time**: ~1-2 minutos (o `./mvnw spring-boot:run` demora ~30-40s a arrancar; o Vite é quase
instantâneo)

---

## Step 0: Confirmar os pré-requisitos

Antes de arrancar nada:

1. **`.env` existem?**
   - `management/managementapi/.env`
   - `management/managementfrontend/apps/backoffice/.env`

   Se faltar algum, não adivinhar valores — ver [[../../environment]] para o template e parar
   para o utilizador preencher.

2. **As portas 8080 e 5173 estão livres?**

   ```bash
   netstat -ano | grep -E ":8080|:5173"
   ```

   Se já houver algo a ouvir, não arrancar uma segunda instância por cima — ver se é já a app
   (nesse caso, não há nada a fazer) ou outro processo a ocupar a porta.

   > ⚠️ **A porta do frontend importa.** O `SecurityConfig` só permite CORS de `localhost:5173` e
   > `:5174`. Se a 5173 estiver ocupada, o Vite salta para a 5175 e todas as chamadas à API ficam
   > bloqueadas, com sintomas que parecem de autenticação — ver [[../../commands]]. Libertar a
   > porta em vez de deixar o Vite escolher outra.

---

## Step 1: Arrancar o backend em background

```bash
cd management/managementapi
./mvnw spring-boot:run
```

Correr como processo em **background** (não bloquear a sessão à espera) — porta 8080.

---

## Step 2: Arrancar o frontend em background

```bash
cd management/managementfrontend/apps/backoffice
npm run dev
```

Também em background — porta 5173.

---

## Step 3: Esperar que ambos fiquem prontos

Não assumir que arrancou — ler o log de cada processo até aparecer um marcador de sucesso **ou**
de falha (usar um `Monitor`/loop de polling sobre o ficheiro de output de cada processo, nunca um
`sleep` fixo às cegas):

- **Backend**: `Started ManagementApiApplication` (sucesso) vs. `APPLICATION FAILED TO START` /
  `ERROR` / `BUILD FAILURE` (falha)
- **Frontend**: `VITE ... ready in` / `Local:` (sucesso) vs. `error` / `EADDRINUSE` (falha)

> ⚠️ **Armadilha do devtools + Flyway** (não se aplica a um arranque normal, mas relevante se
> houver uma migração nova por aplicar): com o `spring-boot-devtools` a correr, uma migração
> Flyway nova aplica-se ao primeiro `mvn compile`, não a este arranque — ver [[../../commands]].

---

## Step 4: Confirmar com um health check

Depois de ambos os logs mostrarem "pronto", confirmar por fora do processo, não só pelo log:

```bash
curl -s -o /dev/null -w "backend /actuator/health: %{http_code}\n" http://localhost:8080/actuator/health
curl -s -o /dev/null -w "frontend :5173: %{http_code}\n" http://localhost:5173
```

Ambos devem responder `200`. Só então reportar ao utilizador que a app está a correr, com os dois
URLs:

- Backend: http://localhost:8080
- Frontend: http://localhost:5173

---

## Final Checklist

- [ ] `.env` do backend e do frontend confirmados (não criados a adivinhar)
- [ ] Portas 8080 e 5173 confirmadas livres antes de arrancar
- [ ] Backend arrancado em background (`./mvnw spring-boot:run`)
- [ ] Frontend arrancado em background (`npm run dev`)
- [ ] Log de cada um verificado até um marcador de sucesso ou falha — nunca um `sleep` cego
- [ ] Health check por `curl` a ambos, não só o log
- [ ] URLs finais reportados ao utilizador

---

## Related Skills

- [[skill-verify-in-browser]] — o passo seguinte natural: depois da app estar a correr, provar
  uma feature no browser real via extensão do Chrome
- [[../../commands]] — comandos completos de build/test, e a armadilha da porta 5173
- [[../../environment]] — variáveis de ambiente que os `.env` têm de ter
