import React from "react";
import BlueprintCard from "@/components/common/BlueprintCard";

interface SectionCardProps {
  title: string;
  icon: React.ReactNode;
  children: React.ReactNode;
}

export default function SectionCard({ title, icon, children }: SectionCardProps) {
  return (
    <BlueprintCard style={{ padding: "13.6px", gap: "10.2px" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <span style={{ color: "var(--ind-color-accent)" }}>{icon}</span>
        <span className="ind-card-title">{title}</span>
      </div>
      {children}
    </BlueprintCard>
  );
}
