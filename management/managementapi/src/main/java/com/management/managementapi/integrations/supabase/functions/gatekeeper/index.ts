// functions/before-sign-in/index.ts
// Bloqueia o login se a conta estiver "locked" no teu pm.profile
// Docs: este endpoint recebe um POST do Auth com info do utilizador que tenta login.

import { serve } from "https://deno.land/std@0.177.0/http/mod.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

serve(async (req) => {
  try {
    // O Supabase passa um payload com o utilizador a autenticar
    const event = await req.json();
    // Consoante o provedor, podes receber 'user' ou 'record' com { id, email, ... }
    const userId: string | undefined =
      event?.user?.id ?? event?.record?.id ?? event?.user_id;

    if (!userId) {
      // Se não der para determinar o userId, por segurança bloqueia
      return new Response(JSON.stringify({ error: "Cannot determine user" }), { status: 401 });
    }

    // Client com SERVICE_ROLE (permitido no Edge runtime)
    const supabase = createClient(
      Deno.env.get("SUPABASE_URL")!,
      Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    // Consulta o profile
    const { data: profile, error } = await supabase
      .from("profile")
      .select("account_status")
      .eq("auth_user_id", userId)
      .maybeSingle();

    if (error) {
      // Em caso de erro DB, é mais seguro falhar fechado
      return new Response(JSON.stringify({ error: "DB error" }), { status: 401 });
    }

    if (!profile || profile.account_status !== "unlocked") {
      return new Response(
        JSON.stringify({ error: "ACCOUNT_LOCKED" }),
        { status: 401 }
      );
    }

    // OK: deixa o login continuar
    return new Response(JSON.stringify({ ok: true }), { status: 200 });
  } catch (_e) {
    return new Response(JSON.stringify({ error: "Bad request" }), { status: 400 });
  }
});
