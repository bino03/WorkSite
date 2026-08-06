import type { CSSProperties, ReactNode } from "react";

interface BlueprintCardProps {
  children: ReactNode;
  kicker?: string;
  elevation?: "sm" | "md" | "lg" | "none";
  corners?: "all" | "tl-bl" | "none";
  style?: CSSProperties;
  className?: string;
  onClick?: () => void;
}

/**
 * The "Industry" design system's card primitive: a transparent, hairline-
 * bordered box with four small "+" registration marks at the corners.
 * Square radius is enforced globally via .ind-card in index.css.
 */
export default function BlueprintCard({
  children,
  kicker,
  elevation = "sm",
  corners = "all",
  style,
  className,
  onClick,
}: BlueprintCardProps) {
  const elevClass = elevation === "none" ? "" : `ind-elev-${elevation}`;

  return (
    <div
      className={`ind-blueprint ind-card ${elevClass} ${className ?? ""}`.trim()}
      style={onClick ? { cursor: "pointer", ...style } : style}
      onClick={onClick}
    >
      {corners === "all" && (
        <>
          <i className="ind-corner tl" />
          <i className="ind-corner tr" />
          <i className="ind-corner bl" />
          <i className="ind-corner br" />
        </>
      )}
      {corners === "tl-bl" && (
        <>
          <i className="ind-corner tl" />
          <i className="ind-corner bl" />
        </>
      )}
      {kicker && <span className="ind-card-kicker">{kicker}</span>}
      {children}
    </div>
  );
}
