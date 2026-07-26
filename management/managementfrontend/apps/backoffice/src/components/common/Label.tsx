import React from "react";

interface LabelProps {
  children: React.ReactNode;
  required?: boolean;
  hasError?: boolean;
  className?: string;
}

export default function Label({ children, required, hasError = false, className }: LabelProps) {
  return (
    <label
      className={`block text-sm mb-1 ${hasError ? 'text-red-500' : ''} ${className || ''}`}
      style={{ color: hasError ? "#ff4d4f" : "var(--text-strong)" }}
    >
      {children} {required ? <span className="text-red-500">*</span> : null}
    </label>
  );
}
