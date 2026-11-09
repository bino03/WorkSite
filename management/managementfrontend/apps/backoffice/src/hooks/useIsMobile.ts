import { useEffect, useState } from "react";

/** Mesmo breakpoint do CSS (`@media (max-width: 767px)`): abaixo de 768px é telemóvel. */
const QUERY = "(max-width: 767px)";

const read = () => (typeof window !== "undefined" ? window.matchMedia(QUERY).matches : false);

/**
 * `true` abaixo de 768px. Só para o que muda a *estrutura* (tabela → cartões, nav → drawer);
 * larguras e paddings resolvem-se com media queries em `index.css`.
 */
export function useIsMobile(): boolean {
  const [isMobile, setIsMobile] = useState(read);

  useEffect(() => {
    const mql = window.matchMedia(QUERY);
    const onChange = () => setIsMobile(mql.matches);
    onChange();
    mql.addEventListener("change", onChange);
    return () => mql.removeEventListener("change", onChange);
  }, []);

  return isMobile;
}
