const { defineConfig } = require('@vue/cli-service')
const path = require('path')

module.exports = defineConfig({
  transpileDependencies: true,

  // 输出目录
  outputDir: 'dist',

  // 开发服务器
  devServer: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:5001',
        changeOrigin: true
      }
    }
  },

  // webpack 配置
  configureWebpack: {
    resolve: {
      alias: {
        '@': path.resolve(__dirname, 'src')
      }
    },
    // 性能提示：chunk 超过 500KB 时警告
    performance: {
      maxAssetSize: 500 * 1024,       // 500KB
      maxEntrypointSize: 500 * 1024,  // 500KB
      hints: 'warning'
    },
    optimization: {
      splitChunks: {
        chunks: 'all',
        maxSize: 500 * 1024,  // 单个 chunk 最大 500KB
        cacheGroups: {
          // Vue 核心库单独打包
          vue: {
            test: /[\\/]node_modules[\\/](vue|vue-router|pinia)[\\/]/,
            name: 'vendor-vue',
            priority: 20
          },
          // icon-park 图标库单独打包（体积大）
          icons: {
            test: /[\\/]node_modules[\\/]@icon-park[\\/]/,
            name: 'vendor-icons',
            priority: 15
          },
          // chart.js 单独打包
          chart: {
            test: /[\\/]node_modules[\\/]chart\.js[\\/]/,
            name: 'vendor-chart',
            priority: 15
          },
          // 其他第三方库
          vendors: {
            test: /[\\/]node_modules[\\/]/,
            name: 'vendor-misc',
            priority: 10
          }
        }
      }
    }
  },

  // 生产环境不生成 source map
  productionSourceMap: false
})
