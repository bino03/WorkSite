# Skill: Resumir fatura (descrição por alto)

**When to use**: O utilizador dá o caminho de um PDF (ou foto) de uma fatura de fornecedor e pede a "descrição
por alto", "a soma das quantidades", "quanto betão/ferro vem nesta". Também serve para escrever o campo
`description` de uma fatura antes de a lançar na app ou no Excel.
**Time**: ~1-2 min por fatura

> Não escreve nada: não lança no Excel, não cria a fatura na app e não mexe no ficheiro. Lançar faturas no Excel é
> a skill `importar-faturas` do vault Vilatro (`C:\Users\jlalv\Desktop\VILATRO\Vilatro\.claude\skills\importar-faturas\`).
> Esta skill só lê e devolve o texto.

---

## Step 1: Ler todas as páginas

Ler o ficheiro com a ferramenta Read. Num PDF com mais de 10 páginas é obrigatório passar `pages` (até 20 de cada
vez), por isso pede-se em blocos até chegar à última página.

As faturas de betão (Brivel) têm **uma guia por carga** e espalham-se por muitas páginas, com "A Transportar" e
"Transportado" no fundo e no topo de cada uma. Uma linha de artigo pode começar numa página e acabar na seguinte.
Só a última página tem os totais.

## Step 2: Separar o material das linhas acessórias

Somar **só as linhas do material**, e por unidade (m³ com m³, kg com kg). As linhas acessórias não entram na soma:

| Fornecedor (exemplo) | Material (somar) | Acessórias (não somar) |
|---|---|---|
| Brivel — betão | `Betão C30/37 …` em **M3** | `Serviço de Bombagem` (UN, mesma qtd. do betão), `Deslocação da Bomba` |
| Fepronor — ferro | `Varão A500 Ø…` em **KG**, `Arame recozido` em KG | — |

- Se houver **mais do que um tipo** do mesmo material (classes de betão diferentes, betão com e sem hidrófugo),
  dá-se uma soma por tipo e só depois o total.
- No ferro, o varão (todos os diâmetros juntos) e o arame vão em somas separadas. Se a fatura imprimir um
  "Total: N Kg", confere-se com essa soma.

Para contar, agrupa-se por quantidade (ex. 9 cargas × 12 + 8 × 11,5 + …): é mais difícil falhar uma linha do que
somando uma a uma, e dá logo o número de guias para confirmar. O número de guias tem de bater com a numeração
GRB (ex. 4165–4191 = 27 guias).

## Step 3: Confirmar a soma pelo valor

Uma soma não se entrega sem esta prova:

```
qtd. total × preço unitário  (+ acessórias)  ≈  "Mercadoria/Serviços" / "Total Ilíquido"
```

- Na Brivel: `m³ × preço do betão + m³ × 6,75 (bombagem) + 70 (deslocação)`.
- Diferenças de **alguns cêntimos** vêm do arredondamento linha a linha (ex. 7,50 × 6,75 = 50,625 → 50,63) e
  aceitam-se, dizendo de onde vêm.
- Uma diferença maior quer dizer que falta ou sobra uma linha: volta-se ao Step 2.

## Step 4: Escrever a descrição

Segue o formato do campo `description` da fatura ([[database]] → `description`): **artigo e quantidade total,
sem `Fornecedor - ` no início**. O fornecedor já é um campo da fatura; no Excel ele costuma vir no início do
texto, mas na app não se repete (ver [[excel-parity]] §3.1).

```
Betão C30/37 S3 D=22, 294 m³ (com bombagem)
Betão C30/37 S3 D=22 com hidrófugo, 38 m³ (com bombagem)
Varão A500 Ø8–Ø20, 27 560 kg + arame recozido, 520 kg
```

Os números escrevem-se em pt-PT (espaço nos milhares, vírgula nas décimas).

## Step 5: Responder

Fica curto, com a descrição em destaque e por baixo:

- a soma por tipo e quantas guias ou cargas a compõem;
- a conta de confirmação pelo valor (Step 3);
- o nº da fatura, o fornecedor, a data e o **total com IVA**;
- se for o caso, o que não bate ou não se lê ("A fatura não tem betão, só ferro").

Se o utilizador pedir "a soma de betão" e a fatura não tiver betão, diz-se isso logo e resume-se o que lá está.

---

## Final Checklist

- [ ] Li todas as páginas, até à que tem os totais
- [ ] Somei só o material, sem a bombagem nem a deslocação
- [ ] O número de guias bate com a numeração GRB (quando há guias)
- [ ] A soma × preço bate com o total ilíquido (ao cêntimo, ou com a diferença explicada)
- [ ] A descrição não começa pelo fornecedor
- [ ] Indiquei o nº da fatura, a data e o total com IVA
- [ ] Não escrevi em lado nenhum

## Related Skills

- [[excel-parity]] — formato do campo descrição partilhado com o Excel
- `importar-faturas` (vault Vilatro) — para lançar a fatura no Excel depois de a resumir
- [[skill-create-new-skill]] — como esta skill foi criada
