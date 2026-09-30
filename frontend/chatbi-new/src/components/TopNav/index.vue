<template>
  <div class="top-nav">
    <div class="top-nav__brand">
      <img class="top-nav__logo" src="@/assets/logo.png" alt="logo" />
      <span class="top-nav__title">数据分析平台</span>
    </div>


    <div class="top-nav__actions">
      <div class="top-nav__switch">
        <button
          type="button"
          class="top-nav__switch-item"
          :class="{ 'is-active': activeSide === 1 }"
          @click="showAgent"
        >
          <base-icon name="message-circle" :size="14" :stroke-width="2" />
          <span>应用端</span>
        </button>
        <button
          v-if="showManagePage"
          type="button"
          class="top-nav__switch-item"
          :class="{ 'is-active': activeSide === 0 }"
          @click="showManager"
        >
          <base-icon name="settings" :size="14" :stroke-width="2" />
          <span>管理端</span>
        </button>
      </div>

      <el-dropdown trigger="click" @command="handleCommand">
        <div class="top-nav__user">
          <span class="top-nav__avatar">{{ avatarText }}</span>
          <span class="top-nav__username">{{ displayName }}</span>
          <base-icon name="chevron-down" :size="13" class="top-nav__arrow" />
        </div>
        <el-dropdown-menu slot="dropdown">
          <el-dropdown-item command="showVersion">查看版本</el-dropdown-item>
          <el-dropdown-item command="logout">退出登录</el-dropdown-item>
        </el-dropdown-menu>
      </el-dropdown>
    </div>
  </div>
</template>

<script>
import { getUserInfo, clearAuth, isAdmin } from '@/utils/auth'
import {szLoginLink} from "@/utils/common.js"

export default {
  data() {
    return {
      userInfo: getUserInfo() || {},
       activeSide: 1,
       showManagePage:false,
    }
  },
  computed: {
    displayName() {     
      return this.userInfo.name || this.userInfo.username || '用户'
    },

    avatarText() {
      const name = this.displayName.trim()
      if (!name) {
        return '?'
      }
      return name.charAt(0).toUpperCase()
    },
  },

  /*watch: {
    // 监听路由对象的变化
    $route: {
      handler(to, from) {
        // 当路由变化时重新加载数据
        this.userInfo = getUserInfo() || {}
      },
      immediate: true // 组件创建时立即执行一次
    }
  },*/

  mounted() {
    this.userInfo = getUserInfo() || {}
    this.showManagePage = isAdmin()
  },

  methods: {
    showAgent(){
      this.activeSide = 1;
      this.$emit('openPage', 1);
      
    },

    showManager(){
      this.activeSide = 0;
      this.$emit('openPage', 0);
    },

    showVersion(){
      this.$confirm("版本号:" + process.env.VUE_APP_VERSION, "提示", {
        confirmButtonText: "确定",
        type: "info",
        showCancelButton: false,
      })
        .then(() => {
        })
        .catch(() => {});
    },

    handleCommand(command) {
      //console.log(command)
      if (command === 'logout') {
        this.logout()
      } else if (command === 'showVersion') {
        this.showVersion()
      }
    },
    logout() {
      clearAuth()
      //this.$router.push({ path: '/login' }).catch(() => {})
      if(szLoginLink) window.location.href = szLoginLink;
      else this.$router.push({ path: '/login' }).catch(() => {})
    },

  },
}
</script>

<style scoped lang="scss">
@use '@/styles/layout.scss' as *;

.top-nav {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: $layout-header-height;
  padding: 0 20px;
  background: #fff;

  .top-nav__brand {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .top-nav__logo {
    width: 36px;
    height: 24px;
  }

  .top-nav__title {
    font-size: 18px;
    font-weight: 600;
    color: #1d2129;
    white-space: nowrap;
  }

  .top-nav__actions {
    display: flex;
    align-items: center;
    gap: 20px;
  }

  .top-nav__switch {
    display: flex;
    align-items: center;
    gap: 4px;
    padding: 3px;
    background: #f1f4f9;
    border-radius: 999px;
  }

  .top-nav__switch-item {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 30px;
    padding: 0 16px;
    border: none;
    border-radius: 999px;
    background: transparent;
    font-size: 13px;
    font-weight: 500;
    color: #64748b;
    cursor: pointer;
    transition: all 0.2s ease;
    outline: none;

    .base-icon {
      opacity: 0.75;
    }

    &:hover {
      color: var(--brand, #2b5cff);
    }

    &.is-active {
      background: #fff;
      color: var(--brand, #2b5cff);
      font-weight: 600;
      box-shadow: 0 1px 4px rgba(30, 41, 59, 0.12);

      .base-icon {
        opacity: 1;
      }
    }
  }

  .top-nav__user {
    display: flex;
    align-items: center;
    gap: 8px;
    cursor: pointer;
    outline: none;

    &:hover {
      .top-nav__username {
        color: #0073ff;
      }
    }
  }

  .top-nav__avatar {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 26px;
    height: 26px;
    border-radius: 50%;
    background: var(--accent-gradient, linear-gradient(135deg, #3b82f6, #06b6d4));
    color: #fff;
    font-size: 12px;
    font-weight: 600;
    flex-shrink: 0;
  }

  .top-nav__username {
    font-size: 14px;
    color: #1d2129;
    max-width: 120px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    transition: color 0.2s;
  }

  .top-nav__arrow {
    font-size: 12px;
    color: #86909c;
  }
}
</style>
