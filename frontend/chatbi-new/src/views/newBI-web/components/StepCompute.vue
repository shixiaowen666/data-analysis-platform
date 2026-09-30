<template>
  <data-viewer
    :records="step.data.records"
    :columns="step.data.columns"
    :page="step.data.page"
    :page-size="step.data.pageSize"
    :total="step.data.total"
    :total-page="step.data.totalPage"
    value-by="key"
    default-view="table"
    @prev="$emit('page', step.data.page - 1)"
    @next="$emit('page', step.data.page + 1)"
  />
</template>

<script>
import DataViewer from '@/components/web/DataViewer.vue';
export default {
  name: 'StepCompute',
  components: { DataViewer },
  props: { step: { type: Object, required: true } },
};
</script>
