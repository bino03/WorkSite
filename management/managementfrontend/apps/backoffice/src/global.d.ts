export {};

declare global {
  interface Window {
    google: any;
    initMap?: () => void;
  }
}
