const {defineConfig} = require('@vue/cli-service');
module.exports = defineConfig({
  transpileDependencies: true,
  lintOnSave: false,

  devServer: {
    port: 8000,
    open: true,
    hot: true,
    proxy: {
      // detail: https://cli.vuejs.org/config/#devserver-proxy
      '/': {
        target: `http://47.111.6.75:8091/`,
        //target: `http://39.106.192.185:8089/`,
        ws: false,
        changeOrigin: true,
      },
      '/api': {
        target: `ws://47.111.6.75:8091/api`,
        //target: `ws://39.106.192.185:8089/api`,
        ws: true,
        changeOrigin: true,
        pathRewrite: {
          ['^/api']: ''
        },
      },

      //},
    },
    //disableHostCheck: true
  },
});