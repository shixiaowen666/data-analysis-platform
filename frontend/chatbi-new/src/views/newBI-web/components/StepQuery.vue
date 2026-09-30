<template>
  <div class="squery">
    <!-- 查询条件（折叠摘要 + 展开编辑，SQL 入口已收纳在展开面板内） -->
    <query-toolbar
      :step="step"
      v-on="$listeners"
      @toggle-toolbar="(v) => $emit('toggle-toolbar', v)"
    />

    <!-- 错误 -->
    <state-block
      v-if="step.error"
      type="error"
      :title="step.error"
      desc="可调整查询条件后重试"
      action-text="重新查询"
      @action="$emit('apply')"
    />

    <!-- 数据 -->
    <div v-else class="squery__data">
      <data-viewer
        :records="step.data.records"
        :columns="step.data.columns"
        :page="step.data.page"
        :page-size="step.data.pageSize"
        :total="step.data.total"
        :total-page="step.data.totalPage"
        :loading="step.loadingData"
        :hit-keys="step.highlightKeys"
        value-by="key"
        @prev="$emit('page', step.data.page - 1)"
        @next="$emit('page', step.data.page + 1)"
      />
    </div>
  </div>
</template>

<script>
import DataViewer from '@/components/web/DataViewer.vue';
import StateBlock from '@/components/web/StateBlock.vue';
import QueryToolbar from './QueryToolbar.vue';

export default {
  name: 'StepQuery',
  components: { DataViewer, StateBlock, QueryToolbar },
  props: {
    step: { type: Object, required: true },
  },
};
</script>

<style scoped lang="scss">
.squery {
  &__data {
    margin-top: 12px;
    padding-top: 12px;
    border-top: 1px dashed var(--bd-light);
  }
}
</style>
