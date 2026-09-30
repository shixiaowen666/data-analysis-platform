<template>
  <div :class="['left-menu', { 'is-collapse': isCollapse }]">
    <div class="left-menu__content">
      <el-menu
        default-active="1-1"
        active-text-color="#2b5cff"
        :collapse="isCollapse"
        :default-openeds="['1', '2', '3', '4','5']"
        :collapse-transition="false"
      >
        <el-submenu index="1">
          <template slot="title">
            <DataAccess class="left-menu__icon" />
            <span>数据接入</span>
          </template>
          <el-menu-item-group>
            <el-menu-item index="1-1" @click="handleClick(RouterEnum.DATASOURCEMANAGER)">数据源管理</el-menu-item>
          </el-menu-item-group>
        </el-submenu>
        <el-submenu index="2">
          <template slot="title">
            <ModelAndMapping class="left-menu__icon" />
            <span>模型与映射</span>
          </template>
          <el-menu-item-group>
            <el-menu-item index="2-1" @click="handleClick(RouterEnum.DATAMODELMANAGER)">数据模型管理</el-menu-item>
            <el-menu-item index="2-2" @click="handleClick(RouterEnum.FIELDMAPPINGMANAGER)">数据表管理</el-menu-item>
          </el-menu-item-group>
        </el-submenu>
        <el-submenu index="3">
          <template slot="title">
            <MetricsAndDimensions class="left-menu__icon" />
            <span>指标与维度</span>
          </template>
          <el-menu-item-group>
            <el-menu-item index="3-1" @click="handleClick(RouterEnum.METRICDATAMANAGER)">指标管理</el-menu-item>
            <el-menu-item index="3-2" @click="handleClick(RouterEnum.DIMENSIONMANAGER)">维度管理</el-menu-item>
          </el-menu-item-group>
        </el-submenu>
        <el-submenu index="4">
          <template slot="title">
            <ConsumptionLayer class="left-menu__icon" />
            <span>业务层</span>
          </template>
          <el-menu-item-group>
            <el-menu-item index="4-1" @click="handleClick(RouterEnum.PORTFOLIOMANAGER)">业务表</el-menu-item>
            <el-menu-item index="4-2" @click="handleClick(RouterEnum.METRICDATAPREVIEW)">指标数据预览</el-menu-item>
            <!--<el-menu-item index="4-3" @click="handleClick(RouterEnum.NEWBI)">智能问数</el-menu-item>-->
          </el-menu-item-group>
        </el-submenu>

        <el-submenu index="5">
          <template slot="title">
            <SystemConfig class="left-menu__icon" />
            <span>系统配置</span>
          </template>
          <el-menu-item-group>
            <el-menu-item index="5-1" v-if="showPormptPage" @click="handleClick(RouterEnum.PROMPTMANAGER)">提示词管理</el-menu-item>
            <el-menu-item index="5-2" @click="handleClick(RouterEnum.AILOGMANAGER)">日志管理</el-menu-item>
            <el-menu-item index="5-3" @click="handleClick(RouterEnum.TASKCONFIG)">定时任务</el-menu-item>
          </el-menu-item-group>
        </el-submenu>
      </el-menu>
    </div>

    <div class="left-menu__footer">
      <el-button
        size="small"
        :icon="!isCollapse ? 'el-icon-s-fold' : 'el-icon-s-unfold'"
        @click="toggleCollapse"
      />
    </div>
  </div>
</template>

<script>
import {RouterEnum} from '@/utils/common.js';
import DataAccess from '@/components/svgs/DataAccess.vue';
import ModelAndMapping from '@/components/svgs/ModelAndMapping.vue';
import MetricsAndDimensions from '@/components/svgs/MetricsAndDimensions.vue';
import ConsumptionLayer from '@/components/svgs/ConsumptionLayer.vue';
import SystemConfig from '@/components/svgs/SystemConfig.vue';

import { isSuperAdmin } from '@/utils/auth'
export default {
  name: 'LeftMenu',
  components: {
    DataAccess,
    ModelAndMapping,
    MetricsAndDimensions,
    ConsumptionLayer,
    SystemConfig
  },
  data() {
    return {
      RouterEnum,
      isCollapse: false,
      showPormptPage: false,
    };
  },
  mounted() {
    this.showPormptPage = isSuperAdmin()
  },

  methods: {
    handleClick(val) {
      this.$emit('openlink', val);
    },
    toggleCollapse() {
      this.isCollapse = !this.isCollapse;
      this.$emit('collapse-change', this.isCollapse);
    },
  },
};
</script>

<style scoped lang="scss">
@use '@/styles/layout.scss' as *;

.left-menu {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  width: $layout-sidebar-width;
  height: 100%;
  background: linear-gradient(180deg, #fdfeff 0%, #f8fafd 100%);
  border-right: 1px solid rgba(43, 92, 255, 0.07);
  transition: width 0.2s;

  &.is-collapse {
    width: $layout-sidebar-collapse-width;
  }

  .left-menu__content {
    flex: 1;
    min-height: 0;
    overflow-x: hidden;
    overflow-y: auto;
    padding: 10px 10px 0;
  }

  .left-menu__footer {
    flex-shrink: 0;
    display: flex;
    justify-content: flex-end;
    padding: 10px 12px;
    border-top: 1px solid rgba(43, 92, 255, 0.06);

    .el-button {
      font-size: 15px;
      color: #66758a;
      border-color: rgba(43, 92, 255, 0.14);
      border-radius: 9px;

      &:hover {
        color: var(--brand, #2b5cff);
      }
    }
  }

  ::v-deep .el-menu {
    border-right: none;
    background: transparent;
  }

  ::v-deep .el-menu-item-group__title {
    padding: 0;
  }

  /* 分组标题:更醒目的层级感 */
  ::v-deep .el-submenu__title {
    height: 42px;
    line-height: 42px;
    padding-left: 12px !important;
    margin: 2px 0;
    border-radius: 10px;
    font-size: 13px;
    font-weight: 600;
    color: #3c465e;
    letter-spacing: 0.3px;
    transition: background 0.18s ease, color 0.18s ease;

    &:hover {
      background: rgba(43, 92, 255, 0.05);
      color: var(--brand, #2b5cff);
    }

    .el-submenu__icon-arrow {
      font-size: 11px;
      color: #9aa7bd;
      right: 12px;
    }
  }

  /* 子项:胶囊态 + 左侧指示条 */
  ::v-deep .el-menu-item {
    position: relative;
    height: 38px;
    line-height: 38px;
    min-width: 0;
    margin: 2px 0 2px 8px;
    padding-left: 32px !important;
    border-radius: 9px;
    font-size: 13px;
    color: #5a6880;
    transition: background 0.18s ease, color 0.18s ease;

    &:hover {
      background: rgba(43, 92, 255, 0.06);
      color: var(--brand, #2b5cff);
    }

    &.is-active {
      background: linear-gradient(
        100deg,
        rgba(59, 130, 246, 0.1),
        rgba(43, 92, 255, 0.07)
      );
      color: var(--brand, #2b5cff);
      font-weight: 600;

      &::before {
        content: "";
        position: absolute;
        left: 14px;
        top: 50%;
        transform: translateY(-50%);
        width: 3px;
        height: 14px;
        border-radius: 3px;
        background: linear-gradient(180deg, #3b82f6, #2b5cff);
      }
    }
  }

  .left-menu__icon {
    display: inline-block;
    width: 17px;
    height: 17px;
    margin-right: 8px;
    vertical-align: middle;
    color: #8a97ad;
    transition: color 0.18s ease;

    svg {
      width: 100%;
      height: 100%;
      display: block;
    }

    path {
      fill: currentColor;
    }
  }

  ::v-deep .el-submenu.is-active > .el-submenu__title .left-menu__icon,
  ::v-deep .el-submenu__title:hover .left-menu__icon {
    color: var(--brand, #2b5cff);
  }
}
</style>
