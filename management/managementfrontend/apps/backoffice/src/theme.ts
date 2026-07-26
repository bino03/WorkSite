// src/theme.ts — Anthropic / Claude Design System
import type { ThemeConfig } from "antd";

export const antdTheme: ThemeConfig = {
  token: {
    // Brand
    colorPrimary: "#c96442",         // Terracotta Brand

    // Text
    colorText: "#141413",            // Near Black
    colorTextSecondary: "#5e5d59",   // Olive Gray
    colorTextTertiary: "#87867f",    // Stone Gray
    colorTextDisabled: "#b0aea5",    // Warm Silver

    // Backgrounds
    colorBgLayout: "#f5f4ed",        // Parchment
    colorBgContainer: "#faf9f5",     // Ivory
    colorBgElevated: "#faf9f5",      // Ivory
    colorBgSpotlight: "#30302e",     // Dark Surface

    // Borders
    colorBorder: "#e8e6dc",          // Border Warm
    colorBorderSecondary: "#f0eee6", // Border Cream
    colorSplit: "#f0eee6",

    // Fill states
    colorFill: "#e8e6dc",            // Warm Sand
    colorFillSecondary: "#f0eee6",
    colorFillTertiary: "#f5f4ed",
    colorFillQuaternary: "#faf9f5",

    // Link
    colorLink: "#c96442",
    colorLinkHover: "#d97757",
    colorLinkActive: "#b5573a",

    // Error / semantic
    colorError: "#b53333",
    colorSuccess: "#3a7d44",
    colorWarning: "#a0622b",
    colorInfo: "#3898ec",

    // Radius
    borderRadius: 8,
    borderRadiusLG: 12,
    borderRadiusSM: 6,
    borderRadiusXS: 4,

    // Shadow
    boxShadow: "rgba(0,0,0,0.05) 0px 4px 24px",
    boxShadowSecondary: "0px 0px 0px 1px #d1cfc5",

    // Font
    fontSize: 15,
    fontSizeLG: 17,
    fontSizeSM: 13,
    lineHeight: 1.6,
    lineHeightLG: 1.6,

    // Control heights
    controlHeight: 36,
    controlHeightLG: 42,
    controlHeightSM: 28,
  },

  components: {
    Button: {
      colorPrimary: "#c96442",
      colorPrimaryHover: "#b5573a",
      colorPrimaryActive: "#9e4a2e",
      defaultBg: "#e8e6dc",
      defaultColor: "#4d4c48",
      defaultBorderColor: "#d1cfc5",
      borderRadius: 8,
      controlHeight: 36,
      paddingInline: 16,
    },
    Input: {
      colorBgContainer: "#faf9f5",
      colorBorder: "#e8e6dc",
      colorText: "#141413",
      borderRadius: 8,
      activeBorderColor: "#3898ec",
      hoverBorderColor: "#c2c0b6",
    },
    Select: {
      colorBgContainer: "#faf9f5",
      colorBorder: "#e8e6dc",
      colorText: "#141413",
      borderRadius: 8,
      optionSelectedBg: "#f0eee6",
      optionSelectedColor: "#141413",
    },
    Table: {
      colorBgContainer: "#faf9f5",
      headerBg: "#e8e6dc",
      headerColor: "#4d4c48",
      rowHoverBg: "#f2f1ea",
      borderColor: "#f0eee6",
      borderRadius: 8,
    },
    Card: {
      colorBgContainer: "#faf9f5",
      colorBorderSecondary: "#f0eee6",
      borderRadius: 8,
    },
    Modal: {
      contentBg: "#faf9f5",
      headerBg: "#faf9f5",
      colorBorder: "#e8e6dc",
      borderRadius: 12,
    },
    Drawer: {
      colorBgContainer: "#faf9f5",
      colorBorderSecondary: "#f0eee6",
    },
    Tag: {
      defaultBg: "#f0eee6",
      defaultColor: "#4d4c48",
      borderRadius: 6,
    },
    Badge: {
      colorBgContainer: "#faf9f5",
    },
    Tabs: {
      colorPrimary: "#c96442",
      inkBarColor: "#c96442",
      itemActiveColor: "#c96442",
      itemHoverColor: "#d97757",
      cardBg: "#f0eee6",
      borderRadius: 8,
    },
    Menu: {
      colorBgContainer: "#faf9f5",
      itemBg: "transparent",
      itemSelectedBg: "#f0eee6",
      itemSelectedColor: "#c96442",
      itemHoverBg: "#f0eee6",
      itemHoverColor: "#141413",
    },
    Pagination: {
      colorPrimary: "#c96442",
      colorPrimaryHover: "#d97757",
      colorBgContainer: "#faf9f5",
    },
    Tooltip: {
      colorBgSpotlight: "#30302e",
      colorTextLightSolid: "#faf9f5",
    },
    Statistic: {
      contentFontSize: 24,
      titleFontSize: 13,
    },
    Segmented: {
      itemSelectedBg: "#faf9f5",
      itemSelectedColor: "#141413",
      trackBg: "#e8e6dc",
    },
  },
};
