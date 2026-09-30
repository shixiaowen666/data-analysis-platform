<template>
  <div class="newbi-frame">
    <div class="newbi-frame__inner">
      <div class="newbi-sidebar">
        <div class="newbi-sidebar__section newbi-sidebar__section--agents">
          <aiBody-Page ref="aiBodyRef" @changeAgent="changeAgent" />
        </div>
        <div class="newbi-sidebar__divider"></div>
        <div class="newbi-sidebar__section newbi-sidebar__section--history">
          <ai-HistoryPage ref="aiHistoryRef" @changeHistory="changeHistory" @newAiBody="newAiBody"/>
        </div>
      </div>

      <div class="newbi-main">
        <query-Page ref="childRef" @agentBusy="agentBusy" />
      </div>
    </div>
  </div>
</template>

<script>
import queryPage from "@/views/newBI/queryPage";
import aiHistoryPage from "@/views/newBI/aiHistoryPage";
import aiBodyPage from "@/views/newBI/aiBodyPage";
import toast from '@/utils/toast';

export default {
  name: "newBI",
  components: {
    queryPage,
    aiHistoryPage,
    aiBodyPage,
  },
  data() {
    return {
      selectedAgent: null,
    };
  },

  mounted() {},

  methods: {
    agentBusy(isDisabled){
      this.$refs.aiBodyRef.disableAgent(isDisabled);
      this.$refs.aiHistoryRef.disableHistory(isDisabled);
    },

    newAiBody(){
      this.changeAgent(this.selectedAgent);
    },

    changeHistory(history) {
      let agent = this.$refs.aiBodyRef.getAgent(history.aiBodyCode);

      if (agent != null) {
        //拿到历史,设置给queryPage
        this.$refs.childRef.setHistory(agent, history);
      } else {
        toast.error("该智能体已被删除，历史记录不能使用")
      }
    },

    changeAgent(agent) {
      if (agent == null) {
        toast.error("该智能体不存在")
        return;
      }
      this.selectedAgent = agent
      this.$refs.childRef.setSelectedAgent(agent);
      this.$refs.aiHistoryRef.getChatHistoryList(agent.code,true);
    },
  },
};
</script>

<style scoped lang="scss">
.newbi-frame {
  display: flex;
  width: 100%;
  height: 100%;
  overflow: hidden;
  border-top: 1px solid var(--border-light, #e5eaf1);
  /* 年轻明亮的浅色科技感背景 */
  background:
    radial-gradient(ellipse at 12% 20%, rgba(59, 130, 246, 0.07) 0%, transparent 42%),
    radial-gradient(ellipse at 88% 78%, rgba(6, 182, 212, 0.08) 0%, transparent 45%),
    radial-gradient(ellipse at 55% 0%, rgba(139, 92, 246, 0.05) 0%, transparent 40%),
    linear-gradient(180deg, #fbfcff 0%, #f4f7fd 100%);
}

.newbi-frame__inner {
  display: flex;
  width: 100%;
  height: 100%;
  min-width: 0;
}

.newbi-sidebar {
  width: clamp(220px, 16vw, 280px);
  flex-shrink: 0;
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: rgba(255, 255, 255, 0.66);
  backdrop-filter: blur(10px);
  border-right: 1px solid var(--border-light, #e5eaf1);
}

.newbi-sidebar__section {
  min-height: 0;
  padding: 14px 12px 0px;
  display: flex;
  flex-direction: column;

  &--agents {
    flex: 0 1 46%;
  }

  &--history {
    flex: 1 1 54%;
  }
}

.newbi-sidebar__divider {
  flex-shrink: 0;
  height: 1px;
  margin: 0 12px;
  background: linear-gradient(
    90deg,
    transparent,
    var(--border-light, #e5eaf1) 18%,
    var(--border-light, #e5eaf1) 82%,
    transparent
  );
}

.newbi-main {
  flex: 1;
  min-width: 0;
  height: 100%;
}
</style>
