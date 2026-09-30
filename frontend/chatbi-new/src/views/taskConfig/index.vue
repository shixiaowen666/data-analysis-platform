<template>
  <div style="display: flex; width: 100%; height: 100%" class="my-custom-style">
    <div class="allbg">
      <div class="tablebg">

    <div style="display: flex; margin-top: 10px" class="one-bgdiv">
        <div class="vertical-line" style="    height: 24px;
    width: 6px;"></div>
      <div style="    font-weight: 800;
    margin-left: 10px;
    font-size: 16px;">定时任务配置</div>
    </div>


    <div style="display: flex; flex-direction: column; margin-top: 10px" class="one-bgdiv">
      <div>任务开关</div>
    </div>

    <div style="display: flex; flex-direction: column; margin-top: 10px" class="one-bgdiv">
        <el-switch
            v-model="enabled"
            :active-text="enabledText"
            @change="enabledChange"
            >
        </el-switch>
    </div>

    <div style="display: flex; flex-direction: column; margin-top: 10px" class="one-bgdiv">
      <div>Cron表达式</div>
    </div>

    <div style="display: flex; flex-direction: column; margin-top: 10px; width: 50%;" class="one-bgdiv">
        <el-input
          v-model="cron"
          placeholder="例如:002 * * ?"
          @input="handleInput"
        ></el-input>
     </div>


    <div style="display: flex; margin-top: 10px" class="one-bgdiv">
      <div style="width: 70px;display: flex;gap: 10px;">

        <el-button @click="setCron(1)">每分钟</el-button>
        <el-button @click="setCron(2)">每小时</el-button>
        <el-button @click="setCron(3)">每日凌晨2点</el-button>
        <el-button @click="setCron(4)">每周一凌晨2点</el-button>
      </div>

    </div>



    <div style="display: flex; margin-top: 10px;    padding-bottom: 20px;">
      <div class="one-bgdiv" style="display: flex;">
        <el-button @click="handerValidateCron()">校验表达式</el-button>
        <el-button :disabled="!validate" type="primary" @click="saveDimTaskConfig()">
          保存配置
        </el-button>
      </div>
    </div>
      </div>
      </div>
  </div>
</template>

<script>
import { validateCron } from '@/utils/cron-validator'
import toast from "@/utils/toast";

import {
  getDimTaskConfigAPI,
  saveDimTaskConfigAPI,
} from "@/api/dimensionManager/dimensionAPI.js";

export default {
  name: "taskConfig",
  components: {

  },

  props: [], //传递过来

  data() {
    return {
        enabled:false,
        enabledText:'已关闭',
        cron: '',

        validate:false,
    };
  },
  mounted() {
    this.getDimTaskConfig()


  },

  methods: {
    enabledChange(val){
        if(val){
             this.enabledText='已开启'
        }
        else{
             this.enabledText='已关闭'
        }

    },

    getDimTaskConfig() {

        getDimTaskConfigAPI()
          .then((response) => {
            if (response.code == 200) {
                this.enabled = response.data.enabled
                this.cron = response.data.cron
                
                if(this.cron) this.validate = true
            } else {
              toast.error(response.message);
            }
          })
          .catch(() => {})
          .finally(() => {});
    },

    setCron(type){
        this.validate = true

        if(type == 1) this.cron = '0 * * * * ?'
        if(type == 2) this.cron = '0 0 * * * ?'
        if(type == 3) this.cron = '0 0 2 * * ?'
        if(type == 4) this.cron = '0 0 2 ? * MON'
    },


    saveDimTaskConfig() {
        let data= {enabled: this.enabled,cron:this.cron}

      saveDimTaskConfigAPI(data)
        .then((response) => {
          if (response.code == 200) {
            toast.success('保存成功');
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
        });
    },

    handleInput(){
         this.validate = false
    },

    handerValidateCron(){
        const res = validateCron(this.cron)
            if (res.valid) {
                this.validate = true
                toast.success('校验通过')

            } else {
                toast.error(res.message)
            }
    },
  },
};
</script>
