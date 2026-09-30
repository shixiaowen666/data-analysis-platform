<template>
  <div class="history-sidebar my-custom-style">
    <div class="history-sidebar__header">
      <span class="history-sidebar__header-badge">
        <base-icon name="history" :size="14" :stroke-width="2" />
      </span>
      <span>历史记录</span>
    </div>
    <div style="padding: 0px 2px 10px">
      <el-input
        placeholder="搜索历史对话..."
        v-model="keyword"
        @keyup.enter.native="handleEnter"
        suffix-icon="el-icon-search"
        style="font-size: 12px;"
      >
      </el-input>
    </div>

    <div class="history-sidebar__list no-scrollbar">
      <template v-for="item in chatHistoryListItem">

        <div
  v-if="item.children && item.children.length > 0"
  :key="item.id"
  class="history-sidebar__group"
>
          <div
            class="history-sidebar__group-label"
            :class="{ 'is-expanded': isExpanded(item.id) }"
            @click="toggleGroup(item.id)"
          >
            <base-icon
              name="chevron-right"
              :size="12"
              :stroke-width="2"
              class="history-sidebar__group-arrow"
            />
            <span>{{ item.text }}</span>
            <span class="history-sidebar__group-count">{{
              item.children.length
            }}</span>
          </div>

          <div
            v-show="isExpanded(item.id)"
            class="history-sidebar__group-body"
            :style="isDisabled?'cursor: not-allowed;':''"
          >
            <div
              v-for="record in item.children"
              :key="record.id"
              class="history-sidebar__item"
              :class="{
                'is-active': currentHistory && currentHistory.id === record.id,
              }"

              :style="isDisabled?'pointer-events: none;opacity: 0.5;':''"
            >
              <base-icon
                name="message-plus"
                :size="13"
                :stroke-width="1.8"
                class="history-sidebar__item-icon"
              />
              <span class="history-sidebar__item-name" @click="getChatHistoryListDetail(record)">
                <truncate-tip :text="record.text" :otherText="record.desc"/>
              </span>
              
              <span class="agent-sidebar__actions">
                 <el-tooltip content="删除" :placement="$toolTipPlacement" :effect="$toolTipEffect" :open-delay="$toolTipOpenDelay">
                <button
                  class="agent-sidebar__action-btn agent-sidebar__action-btn--danger"
                  title="删除"
                  @click.stop="deleteHistory(record)"
                >
                  <base-icon name="trash" :size="13" :stroke-width="2" />
                </button>
                 </el-tooltip>
              </span>

            </div>
          </div>
        </div>
      </template>

      <div v-if="isAllEmpty" class="history-sidebar__empty">
        <base-icon name="clock" :size="22" :stroke-width="1.6" />
        <span>暂无历史对话</span>
      </div>
    </div>
  </div>
</template>

<script>
import {
  getChatHistoryListAPI,
  getChatHistoryListDetailAPI,
  deleteHistoryAPI,
} from "@/api/newBI/newBIAPI";

import {
  groupHistory,
} from '@/utils/parseChat';

import TruncateTip from "@/components/TruncateTip";
import toast from '@/utils/toast';

export default {
  name: "aiHistoryPage",
  components: {TruncateTip},
  data() {
    return {
      chatHistoryListItem: [],
      //allChatHistoryListItem: [],
      expandedGroups: {}, // 默认全部折叠
      keyword: "",
      currentHistory: null, //当前选择得

      aiBodyCode: '',
      isDisabled: false,
    };
  },

  computed: {
    isAllEmpty() {
      return (
        this.chatHistoryListItem.length === 0 ||
        this.chatHistoryListItem.every((item) => item.children.length === 0)
      );
    },
  },

  mounted() {
    //this.getChatHistoryList();
  },

  methods: {
    /*loadingScreen() {
      const loading = this.$loading({
        lock: true,
        text: "Loading",
        spinner: "el-icon-loading",
        background: "rgba(0, 0, 0, 0.7)",
      });

      return loading;
    },*/

    disableHistory(isDisabled){
      this.isDisabled = isDisabled
    },

    handleEnter() {
      this.getChatHistoryList()

      /*if (this.keyword == "") {
        this.chatHistoryListItem = [...this.allChatHistoryListItem];
      } else {
        this.chatHistoryListItem = this.allChatHistoryListItem
          .map((dept) => ({
            ...dept,
            records: dept.records.filter((record) =>
              record.chatName.includes(this.keyword)
            ),
          }))
          .filter((dept) => dept.records.length > 0);
      }*/
    },

    isExpanded(key) {
      return !!this.expandedGroups[key];
    },

    toggleGroup(key) {
      this.$set(this.expandedGroups, key, !this.expandedGroups[key]);
    },

    getChatHistoryList(aiBodyCode,newAIBody) {
      if(newAIBody){ 
        this.aiBodyCode = aiBodyCode
        this.keyword = ''
        this.currentHistory = null
      }

      let params = {keyword: this.keyword}
      //const loading = this.loadingScreen();
      getChatHistoryListAPI(params)
        .then((response) => {
          if (response.code == 200) {
            this.chatHistoryListItem = [];

            this.chatHistoryListItem = groupHistory(response.data || {},  this.aiBodyCode);

            //console.log(this.chatHistoryListItem)
            
            /*let today = {
              key: "today",
              name: "今天",
              records: response.data.today.filter(
                (item) => item.aiBodyCode == this.aiBodyCode
              ),
            };
            this.chatHistoryListItem.push(today);

            let yesterday = {
              key: "yesterday",
              name: "昨天",
              records: response.data.yesterday.filter(
                (item) => item.aiBodyCode == this.aiBodyCode
              ),
            };
            this.chatHistoryListItem.push(yesterday);
            let lastWeek = {
              key: "lastWeek",
              name: "上周",
              records: response.data.lastWeek.filter(
                (item) => item.aiBodyCode == this.aiBodyCode
              ),
            };
            this.chatHistoryListItem.push(lastWeek);
            let lastMonth = {
              key: "lastMonth",
              name: "上个月",
              records: response.data.lastMonth.filter(
                (item) => item.aiBodyCode == this.aiBodyCode
              ),
            };
            this.chatHistoryListItem.push(lastMonth);
            let lastSixMonth = {
              key: "lastSixMonth",
              name: "六个月",
              records: response.data.lastSixMonth.filter(
                (item) => item.aiBodyCode == this.aiBodyCode
              ),
            };
            this.chatHistoryListItem.push(lastSixMonth);
            let moreThanSixMonth = {
              key: "moreThanSixMonth",
              name: "六个月前",
              records: response.data.moreThanSixMonth.filter(
                (item) => item.aiBodyCode == this.aiBodyCode
              ),
            };
            this.chatHistoryListItem.push(moreThanSixMonth);*/

            //this.allChatHistoryListItem = [...this.chatHistoryListItem];
          } else {
            toast.error(response.message)
          }
        })
        .catch(() => {})
        .finally(() => {
          //loading.close();
        });
    },

    getChatHistoryListDetail(row) {
      this.currentHistory = row;
      let query = { chatSessionId: row.id };
      //const loading = this.loadingScreen();
      getChatHistoryListDetailAPI(query)
        .then((response) => {
          if (response.code == 200) {
            let chatHistoryDetail = response.data;
            this.$emit("changeHistory", chatHistoryDetail);
          } else {
            toast.error(response.message)
          }
        })
        .catch(() => {})
        .finally(() => {
          //loading.close();
        });
    },

    deleteHistory(row){
      this.$confirm("确定要删除 " + row.text + " ?", "提示", {
        confirmButtonText: "确定",
        cancelButtonText: "取消",
        type: "warning",
      })
        .then(() => {
        deleteHistoryAPI(row.id)
        .then((response) => {
          if (response.code == 200) {
            toast.success('删除成功')
            if(this.currentHistory?.id == row.id){
               //this.currentHistory = null
               this.$emit("newAiBody");
            }
            else{
              this.getChatHistoryList(this.aiBodyCode, false)
            }
          }
          else{
            toast.error(response.message)
          }
        })
                .catch(() => {})
        .finally(() => {
          //loading.close();
        });

        })
        .catch(() => {});
    }
  },
};
</script>

<style scoped lang="scss">
.agent-sidebar__actions {
  display: flex;
  align-items: center;
  gap: 2px;
  opacity: 0;
  transition: opacity 0.15s;
}

.history-sidebar__item:hover .agent-sidebar__actions,
.history-sidebar__item.is-active .agent-sidebar__actions {
  opacity: 1;
}

.agent-sidebar__action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  padding: 0;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--text-muted, #94a3b8);
  cursor: pointer;
  transition: all 0.15s;

  &:hover {
    background: rgba(43, 92, 255, 0.12);
    color: var(--brand, #2b5cff);
  }

  &--danger:hover {
    background: rgba(239, 68, 68, 0.1);
    color: #ef4444;
  }
}

.history-sidebar {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.history-sidebar__header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 2px 10px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
  flex-shrink: 0;
}

.history-sidebar__header-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border-radius: 7px;
  color: #fff;
  background: linear-gradient(135deg, #8b5cf6, #6366f1);
  box-shadow: 0 2px 6px rgba(99, 102, 241, 0.25);
}

.history-sidebar__list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overflow-x: hidden;
}

.history-sidebar__group {
  margin-bottom: 10px;
}

.history-sidebar__group-label {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 5px 8px;
  border-radius: 7px;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.04em;
  color: var(--text-muted, #94a3b8);
  text-align: left;
  cursor: pointer;
  user-select: none;
  transition: background 0.15s, color 0.15s;

  &:hover {
    background: rgba(43, 92, 255, 0.05);
    color: var(--brand, #2b5cff);
  }

  &.is-expanded {
    color: var(--text-secondary, #475569);

    .history-sidebar__group-arrow {
      transform: rotate(90deg);
    }
  }
}

.history-sidebar__group-arrow {
  flex-shrink: 0;
  transition: transform 0.2s ease;
}

.history-sidebar__group-count {
  margin-left: auto;
  min-width: 18px;
  height: 16px;
  line-height: 16px;
  padding: 0 5px;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 600;
  text-align: center;
  color: #7286a3;
  background: rgba(43, 92, 255, 0.07);
}

.history-sidebar__group-body {
  padding-left: 4px;
}

.history-sidebar__item {
  display: flex;
  align-items: center;
  gap: 7px;
  height: 32px;
  padding: 0 8px;
  border-radius: 8px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;

  &:hover {
    background: rgba(43, 92, 255, 0.06);

    .history-sidebar__item-name {
      color: var(--brand, #2b5cff);
    }

    .history-sidebar__item-icon {
      color: var(--brand, #2b5cff);
    }
  }

  &.is-active {
    background: rgba(43, 92, 255, 0.06);

    .history-sidebar__item-name {
      color: var(--brand, #2b5cff);
      font-weight: 600;
    }

    .history-sidebar__item-icon {
      color: var(--brand, #2b5cff);
    }
  }
}

.history-sidebar__item-icon {
  flex-shrink: 0;
  color: var(--text-muted, #94a3b8);
  transition: color 0.15s;
}

.history-sidebar__item-name {
  flex: 1;
  min-width: 0;
  font-size: 13px;
  color: var(--text-secondary, #475569);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: left;
  transition: color 0.15s;
}

.history-sidebar__empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 24px 8px;
  font-size: 12px;
  color: var(--text-muted, #94a3b8);
}
</style>
