<template>
  <el-table-column
    v-bind="$attrs"
    :prop="prop"
    :label="label"
    :width="width"
    :min-width="minWidth"
  >
    <template slot-scope="scope">
      <template v-if="useTooltip">
        <truncate-tip
          :text="getDisplayText(scope)"
          :isMultiLine="isMultiLine"
        />
      </template>
      <span v-else class="cell-text">
        <slot :row="scope.row" :column="scope.column" :index="scope.$index">
          {{ getDisplayText(scope) }}
        </slot>
      </span>
    </template>
  </el-table-column>
</template>

<script>
import TruncateTip from "@/components/TruncateTip";

export default {
  name: 'ElTableColumnWithTooltip',
  inheritAttrs: false,
  components: { TruncateTip },
  props: {
    prop: { type: String, default: '' },
    label: { type: String, default: '' },
    width: { type: String, default: '' },
    minWidth: { type: String, default: '' },
    useTooltip: { type: Boolean, default: true },
    formatter: { type: Function, default: null },
    isMultiLine: {type: Boolean, default: false},
  },
  methods: {
    getDisplayText(scope) {
      if (this.formatter) {
        return this.formatter(scope.row, scope.column, scope.row[this.prop], scope.$index)
      }
      const val = scope.row[this.prop]
      if (Array.isArray(val) && val.length > 0 && val[0] && val[0].name) {
        return val.map(item => item.name).join(', ')
      }
      if (Array.isArray(val) && val.length == 0) {
        return ''
      }
      return val
    }
  }
}
</script>