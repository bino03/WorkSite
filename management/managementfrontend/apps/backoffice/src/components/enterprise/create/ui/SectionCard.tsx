import React from "react";

interface SectionCardProps {
  title: string;
  icon: React.ReactNode;
  children: React.ReactNode;
}

export default function SectionCard({ title, icon, children }: SectionCardProps) {
  return (
    <div style={{ 
      background: 'white', 
      borderRadius: '12px', 
      border: '1px solid #e8e8e8',
      overflow: 'hidden'
    }}>
      <div style={{ 
        padding: '16px 20px', 
        borderBottom: '1px solid #f0f0f0',
        background: '#fafafa'
      }}>
        <div className="flex items-center gap-2">
          {icon}
          <span style={{ 
            fontWeight: 600, 
            color: '#262626',
            fontSize: '16px'
          }}>
            {title}
          </span>
        </div>
      </div>
      <div style={{ padding: '20px' }}>
        {children}
      </div>
    </div>
  );
}