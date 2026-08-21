import { z } from "zod";

/**
 * Espelha o `EmailProviderUpsertDTO` do backend.
 *
 * A password é o único campo cuja obrigatoriedade muda: ao criar é indispensável
 * (sem ela não há autenticação SMTP), ao editar vir vazia significa "mantém a que
 * está" — como nunca é devolvida pelo servidor, exigi-la aqui obrigaria quem só
 * quer corrigir o nome do remetente a sabê-la de cor.
 */
const baseSchema = z.object({
  providerName: z
    .string()
    .trim()
    .min(1, "O nome do provedor é obrigatório")
    .max(100, "Nome do provedor demasiado longo"),
  host: z
    .string()
    .trim()
    .min(1, "O host é obrigatório")
    .max(255, "Host demasiado longo"),
  port: z
    .number({ message: "A porta é obrigatória" })
    .int("A porta tem de ser um número inteiro")
    .min(1, "Porta inválida")
    .max(65535, "Porta inválida"),
  username: z
    .string()
    .trim()
    .min(1, "O utilizador é obrigatório")
    .max(255, "Utilizador demasiado longo"),
  password: z.string().max(255, "Password demasiado longa").optional(),
  fromEmail: z
    .string()
    .trim()
    .min(1, "O email de remetente é obrigatório")
    .email("Email de remetente inválido")
    .max(255, "Email de remetente demasiado longo"),
  fromName: z.string().trim().max(255, "Nome de remetente demasiado longo").optional(),
  // Os três valores que o backend sabe traduzir em propriedades do JavaMailSender.
  encryption: z.enum(["tls", "ssl", "none"]),
  isDefault: z.boolean(),
  isActive: z.boolean(),
});

/**
 * A obrigatoriedade da password entra por `superRefine` e não por um schema
 * diferente: assim o tipo dos valores do formulário é o mesmo nos dois modos e o
 * `useForm` não muda de forma entre criar e editar.
 */
export const emailProviderFormSchema = (requirePassword: boolean) =>
  baseSchema.superRefine((values, ctx) => {
    if (requirePassword && !values.password?.trim()) {
      ctx.addIssue({
        code: "custom",
        path: ["password"],
        message: "A password é obrigatória",
      });
    }
  });

export type EmailProviderFormValues = z.infer<typeof baseSchema>;

export const emailProviderDefaults: EmailProviderFormValues = {
  providerName: "",
  host: "",
  // 587 (STARTTLS) é a porta submission — a que serve para quase todo o SMTP
  // autenticado de hoje, e a que combina com o `tls` por omissão.
  port: 587,
  username: "",
  password: "",
  fromEmail: "",
  fromName: "",
  encryption: "tls",
  isDefault: false,
  isActive: true,
};
