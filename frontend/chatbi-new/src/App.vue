<template>
  <div id="app">
    <router-view />
  </div>
</template>

<script>
export default {
  name: 'App',
}
</script>

<style lang="scss">
*,
*::before,
*::after {
  box-sizing: border-box;
}

html,
body {
  margin: 0;
  padding: 0;
  height: 100%;
  overflow: hidden;
}

/*$min-width-mobile: 320px;
$min-width-tablet: 768px;
$min-width-desktop: 1024px;*/

#app {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Hiragino Sans GB',
    'Microsoft YaHei', 'Helvetica Neue', Helvetica, Arial, sans-serif;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
  height: 100%;
  color: #1d2129;
  text-align: left;


  /*min-width: $min-width-mobile; // 默认移动端

  // 如果是 iPad 或桌面，再放宽限制
  @media (min-width: 768px) {
    min-width: $min-width-tablet;
  }
  @media (min-width: 1024px) {
    min-width: $min-width-desktop;
  }*/
}
</style>
