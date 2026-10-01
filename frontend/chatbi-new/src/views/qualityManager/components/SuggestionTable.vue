<template>
  <div>
    <el-table :data="value" size="small" border>
      <el-table-column width="50" align="center">
        <template slot-scope="{ row }"><el-checkbox v-model="row.accepted" :disabled="readonly" /></template>
      </el-table-column>
      <el-table-column label="#" prop="seq" width="40" />
      <el-table-column label="规则" prop="ruleCode" width="60">
        <template slot-scope="{ row }"><span class="qm-mono">{{ row.ruleCode || (row.source === 'MANUAL' ? '手动' : '') }}</span></template>
      </el-table-column>
      <el-table-column label="资产" width="130">
        <template slot-scope="{ row }">
          <el-select v-if="!readonly && row.source === 'MANUAL'" v-model="row.assetType" size="mini" class="st-asset-sel" @change="onAssetChange(row)">
            <el-option v-for="(label, code) in ASSET_TYPE" :key="code" :label="label" :value="code" />
          </el-select>
          <span v-else>{{ ASSET_TYPE[row.assetType] || row.assetType }}</span>
        </template>
      </el-table-column>
      <el-table-column label="目标" min-width="140" show-overflow-tooltip>
        <template slot-scope="{ row }">
          <el-input v-if="!readonly && row.source === 'MANUAL' && !(row.assetType || '').startsWith('PROMPT')" v-model="row.targetLabel" size="mini" placeholder="表名 / 指标 / 维度 code" @input="row.targetKey = row.targetLabel" />
          <span v-else>{{ row.targetLabel }}</span>
        </template>
      </el-table-column>
      <el-table-column label="变更（前 → 后）" min-width="260">
        <template slot-scope="{ row }">
          <template v-if="row.assetType && row.assetType.startsWith('PROMPT')">
            <span class="qm-mono">{{ row.beforeValue || '当前' }}</span> →
            <el-tag v-if="row.afterValue" size="mini" type="success">草稿 {{ row.afterValue }}</el-tag>
            <el-button v-else type="text" size="mini" @click="$emit('edit-prompt', row)">打开提示词编辑器</el-button>
          </template>
          <template v-else-if="editing === row">
            <el-input type="textarea" autosize v-model="row.afterValue" size="mini" @blur="editing = null" />
          </template>
          <template v-else>
            <span class="diff-del qm-mono" v-if="row.beforeValue">{{ short(row.beforeValue) }}</span>
            <span class="diff-add qm-mono" @dblclick="!readonly && (editing = row)">{{ short(row.afterValue) }}</span>
            <el-button v-if="!readonly" type="text" size="mini" icon="el-icon-edit" @click="editing = row" />
          </template>
        </template>
      </el-table-column>
      <el-table-column label="理由" prop="reason" min-width="160" show-overflow-tooltip />
      <el-table-column label="置信" width="70" align="center">
        <template slot-scope="{ row }"><el-tag size="mini" :type="{ HIGH: 'success', MEDIUM: 'warning', LOW: 'info' }[row.confidence]">{{ { HIGH: '高', MEDIUM: '中', LOW: '低' }[row.confidence] }}</el-tag></template>
      </el-table-column>
      <el-table-column v-if="showApply" label="状态" width="90" align="center">
        <template slot-scope="{ row }"><el-tag size="mini" :type="{ PUBLISHED: 'success', FAILED: 'danger', ROLLED_BACK: 'info', APPLIED: '' }[row.applyStatus] || 'info'">{{ row.applyStatus }}</el-tag><div v-if="row.applyError" class="qm-muted" style="font-size: 11px">{{ row.applyError }}</div></template>
      </el-table-column>
      <el-table-column v-if="!readonly" width="50">
        <template slot-scope="{ $index }"><el-button type="text" icon="el-icon-delete" @click="value.splice($index, 1)" /></template>
      </el-table-column>
    </el-table>
    <div v-if="!readonly" style="margin-top: 8px">
      <el-button size="mini" icon="el-icon-plus" @click="addManual">追加手动变更</el-button>
    </div>
  </div>
</template>

<script>
import { ASSET_TYPE } from '@/api/qualityManager/qualityAPI'
export default {
  name: 'SuggestionTable',
  props: { value: { type: Array, default: () => [] }, readonly: Boolean, showApply: Boolean },
  data() { return { ASSET_TYPE, editing: null } },
  methods: {
    short(s) { s = s == null ? '' : String(s); return s.length > 80 ? s.slice(0, 80) + '…' : s },
    addManual() {
      this.value.push({ seq: this.value.length + 1, source: 'MANUAL', confidence: 'HIGH', accepted: true, assetType: 'TABLE_DESC', targetKey: '', targetLabel: '', field: '', beforeValue: '', afterValue: '', reason: '管理员追加' })
      this.editing = this.value[this.value.length - 1]
    },
    onAssetChange(row) {
      if ((row.assetType || '').startsWith('PROMPT')) {
        // 提示词变更：目标即提示词分组，内容通过编辑器产出草稿版本
        row.targetKey = row.assetType === 'PROMPT_SUMMARY' ? 'prompt-summary' : 'prompt-main'
        row.targetLabel = row.targetKey
        row.field = 'version'
        row.afterValue = ''
        if (this.editing === row) this.editing = null
      } else if (row.field === 'version') {
        row.field = ''; row.targetKey = ''; row.targetLabel = ''
      }
    },
  },
}
</script>
