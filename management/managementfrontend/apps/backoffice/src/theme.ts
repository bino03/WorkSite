// src/theme.ts — Industry Design System (steel-blue "blueprint" look)
import type { ThemeConfig } from "antd";

export const antdTheme: ThemeConfig = {
  token: {
    // Brand
    colorPrimary: "#5980a6",         // Accent (steel-blue)

    // Text
    colorText: "#1d1f20",
    colorTextSecondary: "#5d5d60",   // Neutral 700
    colorTextTertiary: "#7a7a7d",    // Neutral 600
    colorTextDisabled: "#b7b7ba",    // Neutral 400

    // Backgrounds
    colorBgLayout: "#f2f2f3",
    colorBgContainer: "#f2f2f3",
    colorBgElevated: "#f2f2f3",
    colorBgSpotlight: "#1d2d3d",     // Accent 900

    // Borders
    colorBorder: "rgba(29,31,32,0.16)",
    colorBorderSecondary: "rgba(29,31,32,0.10)",
    colorSplit: "rgba(29,31,32,0.16)",

    // Fill states
    colorFill: "#e7e7ea",
    colorFillSecondary: "#f5f5f8",
    colorFillTertiary: "#f5f5f8",
    colorFillQuaternary: "#f2f2f3",

    // Link
    colorLink: "#5980a6",
    colorLinkHover: "#597ea3",
    colorLinkActive: "#416180",

    // Error / semantic
    colorError: "#b53333",
    colorSuccess: "#3a7d44",
    colorWarning: "#a0622b",
    colorInfo: "#5980a6",

    // Radius — deliberately square (the "blueprint" look)
    borderRadius: 0,
    borderRadiusLG: 0,
    borderRadiusSM: 0,
    borderRadiusXS: 0,

    // Shadow — soft, ink-tinted, no colored glows
    boxShadow: "0 3px 10px rgba(43,43,45,0.16)",
    boxShadowSecondary: "0 1px 2px rgba(43,43,45,0.14)",

    // Font
    fontFamily: '"Barlow", system-ui, -apple-system, "Segoe UI", Arial, sans-serif',
    fontSize: 15,
    fontSizeLG: 17,
    fontSizeSM: 13,
    lineHeight: 1.55,
    lineHeightLG: 1.55,

    // Control heights
    controlHeight: 36,
    controlHeightLG: 42,
    controlHeightSM: 28,
  },

  components: {
    Button: {
      colorPrimary: "#5980a6",
      colorPrimaryHover: "#597ea3",
      colorPrimaryActive: "#416180",
      defaultBg: "transparent",
      defaultColor: "#1d1f20",
      defaultBorderColor: "rgba(29,31,32,0.16)",
      fontWeight: 600,
      fontFamily: '"Barlow Condensed", system-ui, sans-serif',
      borderRadius: 0,
      controlHeight: 36,
      paddingInline: 16,
    },
    Input: {
      colorBgContainer: "#e9e9ea",
      colorBorder: "rgba(29,31,32,0.16)",
      colorText: "#1d1f20",
      borderRadius: 0,
      activeBorderColor: "#5980a6",
      hoverBorderColor: "#5d5d60",
    },
    Select: {
      colorBgContainer: "#e9e9ea",
      colorBorder: "rgba(29,31,32,0.16)",
      colorText: "#1d1f20",
      borderRadius: 0,
      optionSelectedBg: "#eef6ff",
      optionSelectedColor: "#1d1f20",
    },
    DatePicker: {
      colorBgContainer: "#e9e9ea",
      colorBorder: "rgba(29,31,32,0.16)",
      borderRadius: 0,
      activeBorderColor: "#5980a6",
      hoverBorderColor: "#5d5d60",
    },
    Table: {
      colorBgContainer: "transparent",
      headerBg: "transparent",
      headerColor: "#1d1f20",
      rowHoverBg: "color-mix(in srgb, #1d1f20 4%, transparent)",
      borderColor: "rgba(29,31,32,0.16)",
      borderRadius: 0,
    },
    Card: {
      colorBgContainer: "transparent",
      colorBorderSecondary: "rgba(29,31,32,0.16)",
      borderRadius: 0,
    },
    Modal: {
      contentBg: "#f2f2f3",
      headerBg: "#f2f2f3",
      colorBorder: "rgba(29,31,32,0.16)",
      borderRadius: 0,
    },
    Drawer: {
      colorBgContainer: "#f2f2f3",
      colorBorderSecondary: "rgba(29,31,32,0.16)",
      borderRadius: 0,
    },
    Tag: {
      defaultBg: "#f5f5f8",
      defaultColor: "#424244",
      borderRadius: 0,
    },
    Badge: {
      colorBgContainer: "#f2f2f3",
    },
    Tabs: {
      colorPrimary: "#5980a6",
      inkBarColor: "#5980a6",
      itemActiveColor: "#2c455d",
      itemSelectedColor: "#2c455d",
      itemHoverColor: "#597ea3",
      cardBg: "transparent",
      borderRadius: 0,
    },
    Menu: {
      colorBgContainer: "transparent",
      itemBg: "transparent",
      itemSelectedBg: "#eef6ff",
      itemSelectedColor: "#5980a6",
      itemHoverBg: "#f5f5f8",
      itemHoverColor: "#1d1f20",
    },
    Pagination: {
      colorPrimary: "#5980a6",
      colorPrimaryHover: "#597ea3",
      colorBgContainer: "transparent",
      borderRadius: 0,
    },
    Tooltip: {
      colorBgSpotlight: "#1d2d3d",
      colorTextLightSolid: "#f5f5f8",
    },
    Statistic: {
      contentFontSize: 24,
      titleFontSize: 13,
    },
    Segmented: {
      itemSelectedBg: "#f2f2f3",
      itemSelectedColor: "#1d1f20",
      trackBg: "#e7e7ea",
      borderRadius: 0,
    },
    Steps: {
      colorPrimary: "#5980a6",
    },
    Switch: {
      colorPrimary: "#5980a6",
    },
    Checkbox: {
      colorPrimary: "#5980a6",
      borderRadius: 0,
    },
    Radio: {
      colorPrimary: "#5980a6",
    },
  },
};
