<template>
  <div
    class="my-custom-style"
  >

    <div
      :style="`position: absolute;width:80%;bottom: 177px;left:${left}px;z-index:2;`"
      id="recommendPanel"
    >
      <div class="recommend-panel is-show">
        <div>
          <div
            style="
              display: flex;
              justify-content: space-between;
              height: 40px;
              margin: 6px;
            "
          >
            <div style="display: flex">
              <div
                class="badge"
              >
                ✦
              </div>

              <div style="padding: 0 10px">
                <div style="height: 20px; font-size: 16px; font-weight: bold">
                  智能推荐问
                </div>
                <div style="height: 15px; font-size: 12px; color: #929090">
                  智能推荐问
                </div>
              </div>
            </div>

            <div>
              <el-button
                type="text"
                style="
                  width: 30px;
                  height: 30px;
                  border-radius: 10px;
                  border: 1px solid rgba(91, 149, 226, 0.16);
                  background: rgba(255, 255, 255, 0.72);
                  color: #7c8ca4;
                  padding: 0px;
                  cursor: pointer;
                "
                @click="hideQuestionCommand"
              >
                ×
              </el-button>
            </div>
          </div>
        </div>
        
          <div style="margin-bottom: 10px">
            <ScrollNavigator>
              <div style="display: flex; gap: 8px; padding: 4px 4px">
                <div>
                  <el-button
                    class="question-show-btn"
                    @click="getQuestionList(null, true)"
                    :class="{
                      'is-active': currentQuestionTag == null,
                    }"
                  >
                    <span style="font-size: 13px"> 全部推荐 </span>
                  </el-button>
                </div>

                <!-- 这里放置任意内容，可以是一组标签、卡片等 -->
                <div v-for="tag in questionTagList" :key="tag.id">
                  <span
                    style="
                      white-space: nowrap;
                      overflow: hidden;
                      text-overflow: ellipsis;
                    "
                    @click="getQuestionList(tag, true)"
                  >
                    <truncate-tip
                      style="font-size: 13px"
                      class="question-show-btn"
                      :text="tag.name"
                      :class="{
                        'is-active':
                          currentQuestionTag &&
                          currentQuestionTag.id === tag.id,
                      }"
                    />
                  </span>
                </div>
              </div>
            </ScrollNavigator>
          </div>

          <div
          class="question-loading"
          style="
            width: 100%;
            display: flex;
            max-height: 310px;
            min-height: 160px;
            justify-content: center;
          "
          v-if="loadingQuestion"
        >
          <div
            v-loading="loadingQuestion"
            :element-loading-delay="500"
            element-loading-text="加载中..."
            style="width: 20%; height: 160px"
          ></div>
        </div>

        <div v-show="!loadingQuestion" style="              max-height: 310px;
              min-height: 160px;">
          <div
            style="
              display: grid;
              grid-template-columns: repeat(2, minmax(0, 1fr));
              gap: 10px;
              position: relative;
              z-index: 1;

              overflow: hidden auto;
            "
            class="no-scrollbar"
          >
            <div
              class="rec-item"
              v-for="q in questionList"
              :key="q.id"
              @click.stop="sendQuestion(q)"
            >
              <div
                style="
                  margin-bottom: 6px;
                  color: #1f77d8;
                  font-size: 12px;
                  font-weight: 700;
                  padding-right: 8px;
                "
              >
                <truncate-tip
                  :text="q.description"
                  style="
                    overflow: hidden;
                    -webkit-line-clamp: 2;
                    text-overflow: ellipsis;
                    display: -webkit-box;
                    -webkit-box-orient: vertical;
                    white-space: normal;
                  "
                />
              </div>

              <div
                style="
                  font-size: 13px;
                  color: #35465f;
                  line-height: 1.55;
                  padding-right: 8px;
                  max-height: 80px;
                "
              >
                <truncate-tip
                  :text="q.question"
                  style="
                    overflow: hidden;
                    -webkit-line-clamp: 3;
                    text-overflow: ellipsis;
                    display: -webkit-box;
                    -webkit-box-orient: vertical;
                    white-space: normal;
                  "
                />
              </div>
            </div>
          </div>

        </div>
        
          <div
            style="margin-top: 10px; display: flex; justify-content: flex-end"
            v-if="showQuestionNext"
          >
            <el-button type="text" @click="getNextQuestionList()"
              ><base-icon name="refresh" :size="14" /> 换一批</el-button
            >
          </div>

      </div>
    </div>


  </div>
</template>


<script>
import ScrollNavigator from "@/components/ScrollNavigator";
import TruncateTip from "@/components/TruncateTip";
import {
  getQuestionAPI,
  getTagListAPI,
} from "@/api/newBI/newBIAPI.js";
import toast from '@/utils/toast';

export default {
  name: "welcomePage",
  components: { TruncateTip, ScrollNavigator },
  props: {
    //showQuestion: { type: Boolean, default: false },
    aiBodyCode: { type: String, default: '' },
    //openPanel: {type: Object, default: null },
    left: { type: Number, default: 0 },
  },
  data() {
    return {
        //left: 0,
      loadingQuestion:false,
      currentQuestionTag: null,
      queryQuestionParams: {
        aiBodyCode: "",
        keyword: "",
        status: 1,
        tagId: '',
        page: 1,
        pageSize: 8,
        total: 0,
      },
      questionList: [],
      questionTagList:[],
      showQuestionNext: false,
    };
  },
  mounted() {
    window.removeEventListener("click", this.quesiongPanelclick);
    //this.compute();
    this.getTagList()
    this.getQuestionList(null,true)
    window.addEventListener("click", this.quesiongPanelclick);
  },

  beforeDestroy() {
    window.removeEventListener("click", this.quesiongPanelclick);
  },

  methods: {
    hideQuestionCommand() {
      window.removeEventListener("click", this.quesiongPanelclick);
      this.$emit('update:showQuestion', false);
      this.questionTagList = [];
      this.questionList = [];
    },

    getQuestionList(tag, newTag = true) {
        this.queryQuestionParams.aiBodyCode = this.aiBodyCode
        if(newTag){
            this.currentQuestionTag = tag;
            if (tag) {
            this.queryQuestionParams.tagId = tag.id;
            } else {
            this.queryQuestionParams.tagId = "";
            }
            this.queryQuestionParams.page = 1;
            this.queryQuestionParams.pageSize = 8;
            this.queryQuestionParams.total = 0;
        }

      this.loadingQuestion = true;
      getQuestionAPI(this.queryQuestionParams)
        .then((response) => {
          if (response.code == 200) {
            this.questionList = [];
            this.questionList = response.data.list;

            this.queryQuestionParams.page = response.data.page;
            this.queryQuestionParams.pageSize = response.data.pageSize;
            this.queryQuestionParams.total = response.data.total;

            const pageCount = Math.ceil(
              this.queryQuestionParams.total / this.queryQuestionParams.pageSize
            );
            if (pageCount > 1) {
              this.showQuestionNext = true;
            } else {
              this.showQuestionNext = false;
            }
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          //loading.close();
          this.loadingQuestion = false;
        });
    },

    getTagList() {
      getTagListAPI()
        .then((response) => {
          if (response.code == 200) {
            this.questionTagList = [];
            this.questionTagList = response.data;
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
          //loading.close();
        });
    },

    getNextQuestionList() {
      const pageCount = Math.ceil(
        this.queryQuestionParams.total / this.queryQuestionParams.pageSize
      );
      if (this.queryQuestionParams.page < pageCount) {
        this.queryQuestionParams.page++;
      } else {
        this.queryQuestionParams.page = 1;
      }
      this.getQuestionList(null, false);
    },

    sendQuestion(q) {
      this.$emit('update:showQuestion', false);
      this.$emit('send', q.question)
    },

    quesiongPanelclick(e) {
      const panel = document.getElementById("recommendPanel");
      if (panel) {
        const isPanel = panel.contains(e.target);

        if (!isPanel) {
          this.hideQuestionCommand();
        }
      }
    },

  },
};
</script>


<style scoped lang="scss">
.recommend-panel {
  //position: absolute;
  //left: 0;
  //right: 0;
  //bottom: 104px;
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.95),
    rgba(240, 248, 255, 0.92)
  );
  border: 1px solid rgba(65, 150, 255, 0.24);
  border-radius: 22px;
  box-shadow: 0 24px 70px rgba(31, 100, 210, 0.18),
    inset 0 0 0 1px rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(22px);
  padding: 18px 18px 8px 18px;
  display: none;
  transform: translateY(12px) scale(0.98);
  opacity: 0;
  transition: 0.22s ease;
  overflow: hidden;

  &.is-show {
    display: block;
    opacity: 1;
    transform: translateY(0) scale(1);
    animation: panelIn 0.24s ease;
  }
}

@keyframes panelIn {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.recommend-panel::before {
  content: "";
  position: absolute;
  width: 260px;
  height: 260px;
  border-radius: 999px;
  right: -80px;
  top: -120px;
  background: radial-gradient(
    circle,
    rgba(50, 171, 255, 0.22),
    transparent 68%
  );
  pointer-events: none;
}

.recommend-panel::after {
  content: "";
  position: absolute;
  width: 160px;
  height: 160px;
  border-radius: 999px;
  left: -50px;
  bottom: -70px;
  background: radial-gradient(circle, rgba(87, 92, 255, 0.12), transparent 70%);
  pointer-events: none;
}

.question-loading {
  ::v-deep .el-loading-spinner .circular {
    height: 48px;
    width: 48px;
  }

  ::v-deep .el-loading-spinner .el-loading-text {
    font-size: 14px;
  }
  ::v-deep .el-loading-mask {
    background: transparent;
  }
}

.question-show-btn {
  //width: 40px;
  cursor: pointer;
  height: 36px !important;
  line-height: 36px;
  max-width: 100px;
  //display: inline-flex;
  //align-items: center;
  padding: 0 10px !important;
  border-radius: 12px;

  border: none;
  box-shadow: 0 4px 12px rgba(43, 92, 255, 0.3);
  transition: all 0.15s;

  &:hover:not(:disabled) {
    transform: translateY(-1px);
    box-shadow: 0 6px 16px rgba(43, 92, 255, 0.4);
  }

  &:hover {
    background: linear-gradient(135deg, #4f8dfa 0%, #3b6cff 55%, #6156ee 100%);
    background-position: 100% 50%;
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(43, 92, 255, 0.38);
    border: none;
    color: white !important;
  }

  &.is-active {
    background: linear-gradient(135deg, #4f8dfa 0%, #3b6cff 55%, #6156ee 100%);
    background-position: 100% 50%;
    transform: translateY(-2px);
    box-shadow: 0 6px 16px rgba(43, 92, 255, 0.38);
    border: none;
    color: white !important;
  }

  &.is-disabled,
  &.is-disabled:hover {
    color: #c0c4cc;
    transform: none;
    box-shadow: none;
  }
}

.rec-item {
  min-height: 64px;
  max-height: 122px;
  border-radius: 15px;
  border: 1px solid rgba(82, 154, 255, 0.16);
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.86),
    rgba(244, 249, 255, 0.74)
  );
  padding: 12px 13px;
  cursor: pointer;
  transition: 0.22s;
  position: relative;
  overflow: hidden;
}

.rec-item::after {
  content: "";
  position: absolute;
  right: -22px;
  top: -22px;
  width: 54px;
  height: 54px;
  border-radius: 999px;
  background: rgba(49, 157, 255, 0.12);
}

.rec-item:hover {
  transform: translateY(-2px);
  border-color: rgba(32, 136, 255, 0.35);
  box-shadow: 0 14px 28px rgba(36, 120, 220, 0.14);
  background: linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.96),
    rgba(232, 246, 255, 0.88)
  );
}

.badge{
                    width: 34px;
                  height: 34px;
                  border-radius: 13px;
                  background: linear-gradient(135deg, #37c7ff, #336dff);
                  color: #fff;
                  display: grid;
                  place-items: center;
                  box-shadow: var(--glow);
                  font-size: 17px;
}
</style>