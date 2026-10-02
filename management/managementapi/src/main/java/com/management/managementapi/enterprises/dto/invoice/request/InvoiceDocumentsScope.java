package com.management.managementapi.enterprises.dto.invoice.request;

/**
 * Que faturas entram no zip dos documentos.
 *
 * São <b>dois eixos</b>, não uma lista plana — e foi achatá-los numa lista que
 * confundiu o utilizador a 2026-10-02:
 * <ol>
 *   <li><b>está associada a uma rubrica?</b> — {@link #ASSOCIATED} ou
 *       {@link #UNCLASSIFIED}, e {@link #ALL} para os dois juntos;</li>
 *   <li><b>de que lote?</b> — só se aplica às associadas, porque o lote de uma
 *       fatura vem da rubrica onde é classificada
 *       ({@code expense → budget_item → budget}) e não de um campo dela. Por isso
 *       o lote é um parâmetro <i>opcional</i> do {@code ASSOCIATED} e não um
 *       âmbito à parte: sem ele valem todos os lotes.</li>
 * </ol>
 *
 * O {@link #SELECTED} está fora desses eixos de propósito: é a escotilha de
 * fuga. A lista de faturas já filtra por fornecedor, datas e estado de
 * pagamento, por isso deixar escolher "estas" evita inventar aqui um âmbito por
 * cada corte que alguém venha a querer.
 */
public enum InvoiceDocumentsScope {

    /** Todas as faturas da obra — o que o zip da pasta da obra sempre levou. */
    ALL,

    /**
     * As classificadas em rubricas. Com {@code budgetId}, só as desse lote; sem
     * ele, as de qualquer lote. Uma fatura repartida por rubricas de dois lotes
     * entra nos dois — o documento é o mesmo, e quem o pede por lote quer vê-lo lá.
     */
    ASSOCIATED,

    /**
     * As que ainda não estão associadas a rubrica nenhuma. Não pertencem a lote
     * nenhum, por isso sem este âmbito havia documentos que não saíam em zip
     * nenhum — e não é caso de canto: a 2026-10-02 a Vila Aleu tinha as 44
     * faturas por classificar.
     */
    UNCLASSIFIED,

    /** As faturas escolhidas à mão na lista, por {@code invoiceIds}. */
    SELECTED
}
