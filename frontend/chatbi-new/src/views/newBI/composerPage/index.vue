<template>
  <div
    class="my-custom-style"
  >

  <div
      style="
        background: transparent;
        display: flex;
        width: 100%;
        justify-content: center;
        margin-bottom: 15px;
      "
    >
      <div
        :style="
          newPage
            ? `background: transparent; height: 100%;display: flex;
    justify-content: center;flex-direction: column;width: 80%;min-height: 122px;max-height:122px;`
            : `background: transparent; height: 100%;display: flex;
    justify-content: center;flex-direction: column;width: 95%;margin-bottom: 15px;min-height: 122px;max-height:122px;`
        "
        class="aiInput"
      >
        <div style="width: 100%">
          <el-input
            placeholder="请输入您的问题,如:资产质量情况怎么样?"
            v-model="text"
            type="textarea"
            resize="none"
            class="aiInput-textarea"
            :disabled="busy"
            @keyup.enter.native="onAction"
          >
          </el-input>
        </div>
        <div
          class="aiInput-bottom"
          style="display: flex; justify-content: space-between"
        >
          <div class="history-loading">
            <div
              v-loading="loadingHistory"
              :element-loading-delay="500"
              element-loading-text="加载中..."
              style="margin-top: 15px; width: 100px; height: 30px"
            ></div>
          </div>

          <div style="margin-bottom: 5px" >
            
            <el-button
            class="question-btn"
            @click.stop="showHelpPage()"
            
          > 
            <base-icon name="info" :size="13" :stroke-width="2" />
            问数范围
          </el-button>


            <el-button
              class="question-btn"
               @click.stop="$emit('showQuestion')"
              v-if="!newPage && showQuestionButton"
              :disabled="busy"
              style="margin-left: 10px;"
            >
              <base-icon name="cube" :size="16" :stroke-width="2" />
              推荐问
            </el-button>

            <el-button class="send-btn" type="primary" @click.stop="onAction" :disabled="loadingHistory">
              <base-icon
                v-if="!canStop"
                name="send"
                :size="16"
                :stroke-width="2"
              />
              <base-icon
                v-if="canStop"
                name="stop"
                :size="16"
                :stroke-width="2"
              />
            </el-button>
          </div>
        </div>
      </div>
    </div>




      <el-dialog
      width="70%"
      top="8vh"
      title="问数范围"
      :visible.sync="isShowHelpPage"
      :show-close="false"
      :close-on-click-modal="false"
      append-to-body
      class="my-custom-style"
   
    >
    <div    style="height:70vh">
    <iframe src='/static/help.html' width="100%" height="100%" style="  border: none;
  overflow: hidden;
  display: block;" 
    frameborder="0"></iframe>
    </div>

    <div class="horizontal-line"></div>
    <div style="display: flex;">
      <div class="one-bgdiv" style="display: flex; justify-content: flex-end">
        <el-button @click="isShowHelpPage=false">关闭</el-button>
      </div>
    </div>

    </el-dialog>


  </div>
</template>


<script>
import TruncateTip from "@/components/TruncateTip";
import toast from '@/utils/toast';

export default {
  name: "welcomePage",
  components: { TruncateTip },
  props: {
    newPage: { type: Boolean, default: false },
    busy: { type: Boolean, default: false },
    canStop: { type: Boolean, default: false },
    loadingHistory: { type: Boolean, default: false },
    showQuestionButton: { type: Boolean, default: true }
  },
  data() {
    return {
      text: "",
      isShowHelpPage: false,
    };
  },

  methods: {

      showHelpPage(){
      this.isShowHelpPage=true
    },

      reset() {
    this.text = "";
  },

    onAction() {
        //console.log(this.newPage)

      if (this.busy) {
        this.$emit("stop");
        return;
      }
      const t = this.text.trim();
      if (!t) return;
      if (t.length > 300) {
        toast.warn("问题最多 300 个字");
        return;
      }
      this.$emit("send", t);
      this.reset();
    },
  },
};
</script>


<style scoped lang="scss">
.aiInput {
  border-radius: 16px;
  transition: box-shadow 0.2s cubic-bezier(0.645, 0.045, 0.355, 1),
    border-color 0.2s;
  background: rgba(255, 255, 255, 0.94);
  border: 1px solid var(--border-light, #e5eaf1);
  box-shadow: 0 4px 20px rgba(30, 41, 59, 0.07);
  backdrop-filter: blur(8px);

  outline: none;
  resize: none;
  font-family: -apple-system, Segoe UI, Roboto, sans-serif;
  font-size: 15px;

  &:focus-within {
    border-color: rgba(43, 92, 255, 0.4);
    box-shadow: 0 4px 20px rgba(43, 92, 255, 0.12),
      0 0 0 3px rgba(43, 92, 255, 0.08);
  }
}

.aiInput-bottom {
  padding: 12px 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: nowrap;
  overflow: hidden;
}

.aiInput-textarea ::v-deep .el-textarea__inner,
.aiInput-textarea.is-disabled ::v-deep .el-textarea__inner,
.aiInput-textarea ::v-deep .el-textarea__inner:disabled {
  border: none !important;
  background: transparent !important;
  box-shadow: none !important;
  color: var(--text-primary, #1e293b);
  -webkit-text-fill-color: var(--text-primary, #1e293b);
  cursor: not-allowed;
}

.aiInput-textarea ::v-deep .el-textarea__inner {
  //border: 1px solid #DCDFE6;
  border-radius: 14px;
  width: 100%;
  flex: 1;
  outline: none;
  resize: none;
  font-family: -apple-system, Segoe UI, Roboto, sans-serif;
  padding: 20px 20px 8px;
  font-size: 16px;
  cursor: text;
}

.history-loading {
  ::v-deep .el-loading-spinner .circular {
    height: 24px;
    width: 24px;
  }
  ::v-deep .el-loading-spinner {
    display: flex;
    gap: 5px;
  }
  ::v-deep .el-loading-spinner .el-loading-text {
    font-size: 10px;
  }
  ::v-deep .el-loading-mask {
    background: transparent;
  }
}

.question-btn {
  //width: 40px;
  height: 36px !important;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 10px !important;
  border-radius: 12px;

  border: none;
  box-shadow: 0 4px 12px rgba(43, 92, 255, 0.3);
  transition: all 0.15s;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: 0 6px 16px rgba(43, 92, 255, 0.4);
  }

  &.is-disabled,
  &.is-disabled:hover {
    color: #c0c4cc;
    transform: none;
    box-shadow: none;

    background: #fff;
  }

  &:hover {
    background: linear-gradient(135deg, #4f8dfa 0%, #3b6cff 55%, #6156ee 100%);
    background-position: 100% 50%;
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(43, 92, 255, 0.38);
    border: none;
    color: white;
  }
}

.send-btn {
  width: 40px;
  height: 36px !important;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 !important;
  border-radius: 12px;
  background: var(--accent-gradient, linear-gradient(135deg, #3b82f6, #06b6d4));
  border: none;
  box-shadow: 0 4px 12px rgba(43, 92, 255, 0.3);
  transition: all 0.15s;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: 0 6px 16px rgba(43, 92, 255, 0.4);
  }
}
</style>