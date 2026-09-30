<template>
  <svg
    class="app-icon"
    :class="[{ 'is-spin': spin, 'is-breath': pulse }]"
    :width="px"
    :height="px"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    :stroke-width="strokeWidth"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    v-html="paths"
  ></svg>
</template>

<script>
/**
 * 线型 SVG 图标库（lucide 视觉语言，24×24 stroke）
 * 统一全项目图标语言，避免混用 Element 字体图标。
 */
const ICONS = {
  // 基础
  search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  minus: '<path d="M5 12h14"/>',
  close: '<path d="M18 6 6 18M6 6l12 12"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
  refresh: '<path d="M21 12a9 9 0 1 1-2.64-6.36M21 4v5h-5"/>',
  trash: '<path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><path d="M10 11v6M14 11v6"/>',
  copy: '<rect x="9" y="9" width="12" height="12" rx="2.5"/><path d="M5 15H4.5A2.5 2.5 0 0 1 2 12.5v-8A2.5 2.5 0 0 1 4.5 2h8A2.5 2.5 0 0 1 15 4.5V5"/>',
  download: '<path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><path d="M7 10l5 5 5-5"/><path d="M12 15V3"/>',
  send: '<path d="m21.5 2.5-19 8 8 3 3 8Z"/><path d="M21.5 2.5 10.5 13.5"/>',
  stop: '<rect x="6" y="6" width="12" height="12" rx="2.5" fill="currentColor" stroke="none"/>',
  filter: '<path d="M21 4H3l7.5 9v6.5l3 1.5V13Z"/>',
  settings: '<circle cx="12" cy="12" r="3"/><path d="M12 2v2.4M12 19.6V22M4.6 4.6l1.7 1.7M17.7 17.7l1.7 1.7M2 12h2.4M19.6 12H22M4.6 19.4l1.7-1.7M17.7 6.3l1.7-1.7"/>',
  sliders: '<path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0"/><circle cx="16" cy="6" r="2"/><circle cx="10" cy="12" r="2"/><circle cx="18" cy="18" r="2"/>',
  'more-horizontal': '<circle cx="5" cy="12" r="1.2"/><circle cx="12" cy="12" r="1.2"/><circle cx="19" cy="12" r="1.2"/>',
  'more-vertical': '<circle cx="12" cy="5" r="1.2"/><circle cx="12" cy="12" r="1.2"/><circle cx="12" cy="19" r="1.2"/>',
  menu: '<path d="M4 6h16M4 12h16M4 18h16"/>',
  grid: '<rect x="3" y="3" width="7.5" height="7.5" rx="2"/><rect x="13.5" y="3" width="7.5" height="7.5" rx="2"/><rect x="3" y="13.5" width="7.5" height="7.5" rx="2"/><rect x="13.5" y="13.5" width="7.5" height="7.5" rx="2"/>',
  list: '<path d="M8 6h13M8 12h13M8 18h13"/><circle cx="3.6" cy="6" r="1.1"/><circle cx="3.6" cy="12" r="1.1"/><circle cx="3.6" cy="18" r="1.1"/>',

  // 箭头
  'chevron-down': '<path d="m6 9.5 6 6 6-6"/>',
  'chevron-up': '<path d="m18 14.5-6-6-6 6"/>',
  'chevron-right': '<path d="m9.5 18 6-6-6-6"/>',
  'chevron-left': '<path d="m14.5 18-6-6 6-6"/>',
  'arrow-right': '<path d="M4 12h15M13 6l6 6-6 6"/>',
  'arrow-left': '<path d="M20 12H5M11 6l-6 6 6 6"/>',
  'arrow-up': '<path d="M12 20V5M6 11l6-6 6 6"/>',
  'arrow-down': '<path d="M12 4v15M6 13l6 6 6-6"/>',
  'corner-up': '<path d="m4 14 5-5 5 5"/><path d="M9 9v6a4 4 0 0 0 4 4h7"/>',

  // 业务
  brain: '<path d="M12 5.2a3 3 0 1 0-5.9.2 4 4 0 0 0-2.6 5.7 4 4 0 0 0 .6 6.6A4 4 0 1 0 12 18Z"/><path d="M12 5.2a3 3 0 1 1 5.9.2 4 4 0 0 1 2.6 5.7 4 4 0 0 1-.6 6.6A4 4 0 1 1 12 18Z"/><path d="M12 5.2V18"/>',
  database: '<ellipse cx="12" cy="5.5" rx="8" ry="3"/><path d="M4 5.5v13c0 1.7 3.6 3 8 3s8-1.3 8-3v-13"/><path d="M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/>',
  sigma: '<path d="M18 5H6.5l6 7-6 7H18"/>',
  calculator: '<rect x="4" y="2.5" width="16" height="19" rx="2.5"/><path d="M8 7h8"/><path d="M8.5 11.5h.01M12 11.5h.01M15.5 11.5h.01M8.5 15.5h.01M12 15.5h.01M15.5 15.5h.01M8.5 18.5h7"/>',
  microscope: '<path d="M6 18h12"/><path d="M9 18a5 5 0 0 1 0-10"/><path d="M13 4.5 16 7.5"/><path d="m11 6.5 3-3 3.5 3.5-3 3z"/><path d="M14 12a5 5 0 0 1-1.5 6"/>',
  'file-text': '<path d="M14.5 2.5H7A2 2 0 0 0 5 4.5v15a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7Z"/><path d="M14 2.5V7h5"/><path d="M9 13h6M9 17h4"/>',
  'clipboard-check': '<rect x="8.5" y="2.5" width="7" height="4" rx="1.5"/><path d="M15.5 4.5H17a2 2 0 0 1 2 2v13a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-13a2 2 0 0 1 2-2h1.5"/><path d="m9.5 13.5 2 2 3.5-4"/>',
  sparkles: '<path d="m12 3.5 1.7 5a2 2 0 0 0 1.3 1.3l5 1.7-5 1.7a2 2 0 0 0-1.3 1.3l-1.7 5-1.7-5a2 2 0 0 0-1.3-1.3l-5-1.7 5-1.7a2 2 0 0 0 1.3-1.3Z"/><path d="M19 3v3M17.5 4.5h3"/>',
  bot: '<rect x="4" y="8" width="16" height="12" rx="3.5"/><path d="M12 8V5"/><circle cx="12" cy="3.6" r="1.2"/><path d="M9 13v1.4M15 13v1.4"/><path d="M9.5 17h5"/>',
  'bar-chart': '<path d="M3 3v18h18"/><path d="M8 17V10M13 17V6M18 17v-4"/>',
  'trending-up': '<path d="m21 7-8 8-4-4-6 6"/><path d="M15 7h6v6"/>',
  'pie-chart': '<path d="M21.2 15.9A10 10 0 1 1 8 2.9"/><path d="M22 12A10 10 0 0 0 12 2v10Z"/>',
  activity: '<path d="M22 12h-4l-3 8-6-16-3 8H2"/>',
  lightbulb: '<path d="M9.5 18h5"/><path d="M10 21.5h4"/><path d="M15.1 14.2c.2-1 .7-1.8 1.4-2.5A4.7 4.7 0 0 0 18 8.3 6 6 0 0 0 6 8.3c0 1 .3 2.2 1.5 3.4.7.7 1.2 1.5 1.4 2.5"/>',
  target: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1.4"/>',
  zap: '<path d="M13 2.5 4.5 13.5H11l-1 8 8.5-11H12Z"/>',
  layers: '<path d="m12 3 8.5 4.5L12 12 3.5 7.5 12 3Z"/><path d="m3.5 12 8.5 4.5 8.5-4.5"/><path d="m3.5 16.5 8.5 4.5 8.5-4.5"/>',
  cube: '<path d="M20.5 8a2 2 0 0 0-1-1.7l-7-4a2 2 0 0 0-2 0l-7 4A2 2 0 0 0 3.5 8v8a2 2 0 0 0 1 1.7l7 4a2 2 0 0 0 2 0l7-4a2 2 0 0 0 1-1.7Z"/><path d="m3.8 7 8.2 4.7L20.2 7"/><path d="M12 21.5v-9.8"/>',
  tag: '<path d="M12 2.5H3.5a1 1 0 0 0-1 1V12l8.8 8.8a1.4 1.4 0 0 0 2 0l7.5-7.5a1.4 1.4 0 0 0 0-2Z"/><circle cx="7" cy="7" r="1.2"/>',
  calendar: '<rect x="3.5" y="4.5" width="17" height="17" rx="2.5"/><path d="M16 2.5V6M8 2.5V6M3.5 10h17"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5.3l3.6 2.1"/>',
  history: '<path d="M3.5 12a8.5 8.5 0 1 0 2.9-6.4L3.5 8"/><path d="M3.5 3.5v5h5"/><path d="M12 7.5v5l3.2 2"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4.5 21c0-3.9 3.4-6 7.5-6s7.5 2.1 7.5 6"/>',
  logout: '<path d="M9.5 21H6a2.5 2.5 0 0 1-2.5-2.5v-13A2.5 2.5 0 0 1 6 3h3.5"/><path d="m16 16.5 4.5-4.5L16 7.5"/><path d="M20.5 12h-11"/>',
  lock: '<rect x="4" y="10.5" width="16" height="11" rx="2.5"/><path d="M7.8 10.5V7.2a4.2 4.2 0 0 1 8.4 0v3.3"/>',
  eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z"/><circle cx="12" cy="12" r="3"/>',
  'eye-off': '<path d="M10.7 6.1A8.9 8.9 0 0 1 12 6c6 0 9.5 6 9.5 6a17 17 0 0 1-2.4 3.2"/><path d="M6.4 7.9A16.6 16.6 0 0 0 2.5 12S6 18 12 18a9 9 0 0 0 3.4-.6"/><path d="M9.9 9.9a3 3 0 0 0 4.2 4.2"/><path d="m3 3 18 18"/>',
  info: '<circle cx="12" cy="12" r="9"/><path d="M12 16.5v-5M12 8h.01"/>',
  'alert-circle': '<circle cx="12" cy="12" r="9"/><path d="M12 7.5v5.5M12 16.5h.01"/>',
  'alert-triangle': '<path d="m21.2 18-8-14a1.9 1.9 0 0 0-3.3 0l-8 14A1.9 1.9 0 0 0 4.4 21h15.2a1.9 1.9 0 0 0 1.6-3Z"/><path d="M12 9.5v4M12 17h.01"/>',
  'check-circle': '<circle cx="12" cy="12" r="9"/><path d="m8.5 12.2 2.4 2.4 4.6-5"/>',
  'x-circle': '<circle cx="12" cy="12" r="9"/><path d="m15 9-6 6M9 9l6 6"/>',
  'help-circle': '<circle cx="12" cy="12" r="9"/><path d="M9.3 9.2a2.8 2.8 0 0 1 5.4.9c0 1.9-2.7 2.4-2.7 2.4v1"/><path d="M12 17h.01"/>',
  'file-code': '<path d="M14.5 2.5H7A2 2 0 0 0 5 4.5v15a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7Z"/><path d="M14 2.5V7h5"/><path d="m10.5 12.5-2 2 2 2M14 12.5l2 2-2 2"/>',
  code: '<path d="m16 18 5.5-6L16 6M8 6 2.5 12 8 18"/>',
  message: '<path d="M20.5 11.6a8.2 8.2 0 0 1-8.5 8.4 8.6 8.6 0 0 1-3.8-.9L3.5 20.5l1-4.6a8.5 8.5 0 1 1 16-4.3Z"/>',
  'message-plus': '<path d="M20.5 11.6a8.2 8.2 0 0 1-8.5 8.4 8.6 8.6 0 0 1-3.8-.9L3.5 20.5l1-4.6a8.5 8.5 0 1 1 16-4.3Z"/><path d="M12 8.5v5M9.5 11h5"/>',
  compass: '<circle cx="12" cy="12" r="9"/><path d="m15.8 8.2-2 5.6-5.6 2 2-5.6Z"/>',
  columns: '<rect x="3.5" y="4" width="17" height="16" rx="2.5"/><path d="M12 4v16"/>',
  table: '<rect x="3" y="4" width="18" height="16" rx="2.5"/><path d="M3 10h18M9 4v16"/>',
  'back-top': '<path d="M12 20V6"/><path d="m6 12 6-6 6 6"/><path d="M5 3h14"/>',
  'back-bottom': '<path d="M12 4v14"/><path d="m6 12 6 6 6-6"/><path d="M5 21h14"/>',
  loader: '<path d="M12 3v3.5"/><path d="M12 17.5V21"/><path d="M5.4 5.4l2.5 2.5"/><path d="M16.1 16.1l2.5 2.5"/><path d="M3 12h3.5"/><path d="M17.5 12H21"/><path d="M5.4 18.6l2.5-2.5"/><path d="M16.1 7.9l2.5-2.5"/>',
  mic: '<rect x="9" y="2.5" width="6" height="11" rx="3"/><path d="M5.5 11.5a6.5 6.5 0 0 0 13 0"/><path d="M12 18v3.5M8.5 21.5h7"/>',
  broom: '<path d="M14 3.5 20.5 10"/><path d="m9 8.5 6.5 6.5-4 4a4 4 0 0 1-5.6 0l-1-1a4 4 0 0 1 0-5.6Z"/><path d="M4 20.5 2.5 22"/>',
  share: '<circle cx="18" cy="5.5" r="2.5"/><circle cx="6" cy="12" r="2.5"/><circle cx="18" cy="18.5" r="2.5"/><path d="m8.2 10.8 7.6-4M8.2 13.2l7.6 4"/>',
  network: '<rect x="15.5" y="15.5" width="6" height="6" rx="1.6"/><rect x="2.5" y="15.5" width="6" height="6" rx="1.6"/><rect x="9" y="2.5" width="6" height="6" rx="1.6"/><path d="M5.5 15.5v-2.8a1 1 0 0 1 1-1h11a1 1 0 0 1 1 1v2.8"/><path d="M12 11.7V8.5"/>',
  'shield-check': '<path d="M12 2.5 4.5 5.5v6c0 4.6 3.1 8.5 7.5 10 4.4-1.5 7.5-5.4 7.5-10v-6Z"/><path d="m9 12 2.2 2.2L15.5 10"/>',
  gauge: '<path d="M12 14l3.5-3.5"/><path d="M3.6 18.5a10 10 0 1 1 16.8 0"/><circle cx="12" cy="14" r="1.4"/>',
  hash: '<path d="M4.5 9h15M4.5 15h15M10 3.5 8.5 20.5M15.5 3.5 14 20.5"/>',
  key: '<circle cx="7.5" cy="15.5" r="4.5"/><path d="m20.5 2.5-9.8 9.8"/><path d="m15.5 7.5 3 3 3-3-3-3"/>',
};

export default {
  name: 'AppIcon',
  props: {
    name: { type: String, required: true },
    size: { type: [Number, String], default: 18 },
    strokeWidth: { type: [Number, String], default: 1.8 },
    spin: { type: Boolean, default: false },
    pulse: { type: Boolean, default: false },
  },
  computed: {
    px() {
      return typeof this.size === 'number' ? this.size : parseFloat(this.size) || 18;
    },
    paths() {
      return ICONS[this.name] || ICONS['help-circle'];
    },
  },
};
</script>

<style scoped lang="scss">
.app-icon {
  display: inline-block;
  flex-shrink: 0;
  vertical-align: middle;

  &.is-spin {
    animation: fxSpin 1.1s linear infinite;
  }
  &.is-breath {
    animation: fxBreath 1.9s var(--ease-inout) infinite;
  }
}
</style>
