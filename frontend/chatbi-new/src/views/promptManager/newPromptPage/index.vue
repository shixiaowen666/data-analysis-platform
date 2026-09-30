<template>
  <div style="height: 683px; min-width: 600px">
    <div style="display: flex; margin-top: 10px" class="one-bgdiv">
      <div style="width: 70px">版本号<span style="color: red">*</span></div>

      <div style="width: 80%; display: flex; align-items: center; gap: 10px">
        <el-input
          v-model="prompt.version"
          placeholder="如:v1.1.0"
          :disabled="promptItem != null"
          @input="formatVersion"
          style="width: 60%"
        ></el-input>
        <span
          v-if="!isValid && prompt.version"
          style="color: red; width: 150px"
        >
          请使用 v1.1.1 格式
        </span>

        <span v-if="isExist && prompt.version" style="color: red; width: 150px">
          版本号已存在
        </span>
      </div>
    </div>

    <div style="display: flex" class="one-bgdiv">
      <div style="width: 70px">描述<span style="color: red">*</span></div>

      <div style="flex: 1">
        <el-input
          v-model="prompt.description"
          placeholder="如:修改了XXX"
        ></el-input>
      </div>
    </div>
    <div class="one-bgdiv" style="height: 560px">
      <Text-Editor
        :fileContent.sync="prompt.content"
        :editHeight="470"
        :readOnly="false"
      />
    </div>

    <div class="horizontal-line"></div>

    <div style="display: flex;">
      <div class="one-bgdiv" style="display: flex; justify-content: flex-end">
        <el-button @click="close()">取消</el-button>
        <el-button type="primary" @click="newPrompt()">
          <div v-if="promptItem == null">创建</div>
          <div v-else>更新</div>
        </el-button>
      </div>
    </div>
  </div>
</template>

<script>
import TextEditor from "@/components/TextEditor";
import toast from "@/utils/toast";

import {
  newPromptAPI,
  editPromptAPI,
  getPromptDetailAPI,
  existsPromptVersionAPI,
} from "@/api/promptManager/promptAPI.js";

export default {
  name: "newPromptPage",
  components: {
    TextEditor,
  },

  props: ["promptItem", "groupID"], //传递过来

  data() {
    return {
      prompt: {
        id: "",
        version: "",
        description: "",
        content: "",
        group_id: "",
      },
      isValid: true,
      isExist: false,
    };
  },
  mounted() {
    this.getPromptDetail();
  },

  methods: {
    formatVersion() {
      this.isValid = true;
      this.isExist = false;
      // 自动添加 v 前缀，限制输入
      let value = this.prompt.version.replace(/[^v0-9.]/g, "");
      // 确保 v 在开头
      if (value && !value.startsWith("v")) {
        value = "v" + value.replace(/^v+/, "");
      }
      this.prompt.version = value;
      // 验证格式
      this.isValid = /^v\d+\.\d+\.\d+$/.test(this.prompt.version);

      let params = { group_id: this.groupID, version: this.prompt.version };
      existsPromptVersionAPI(params)
        .then((response) => {
          if (response.code == 200) {
            if (response.data.exists) {
              this.isExist = true;
            } else {
              this.isExist = false;
            }
          }
        })
        .catch(() => {})
        .finally(() => {});
    },

    //关闭窗口
    close() {
      this.$emit("close");
    },

    detectError() {
      if (!this.prompt.version?.toString().trim()) {
        toast.error("请输入版本号");
        return false;
      }

      if (!this.prompt.description?.toString().trim()) {
        toast.error("请输入描述");
        return false;
      }

      if (!this.prompt.content || !this.htmlToText(this.prompt.content)) {
        toast.error("请输入内容");
        return false;
      }
      return true;
    },

    htmlToText(html, options = {}) {
      const {
        preserveNewlines = true,
        removeEmptyLines = true,
        bulletSymbol = "• ",
      } = options;

      if (!html) return "";

      // 移除 script 和 style
      let text = html
        .replace(/<style[^>]*>[\s\S]*?<\/style>/gi, "")
        .replace(/<script[^>]*>[\s\S]*?<\/script>/gi, "");

      // 块级标签转换行
      text = text.replace(/<br\s*\/?>/gi, "\n");
      text = text.replace(/<\/p>/gi, "\n");
      text = text.replace(/<\/div>/gi, "\n");
      text = text.replace(/<\/h[1-6]>/gi, "\n");
      text = text.replace(/<\/li>/gi, "\n");
      text = text.replace(/<li[^>]*>/gi, bulletSymbol);
      text = text.replace(/<tr[^>]*>/gi, "");
      text = text.replace(/<\/tr>/gi, "\n");
      text = text.replace(/<td[^>]*>/gi, "");
      text = text.replace(/<\/td>/gi, "\t");

      // 移除所有剩余标签
      text = text.replace(/<[^>]+>/g, "");

      // 处理 HTML 实体
      const entities = {
        "&nbsp;": " ",
        "&lt;": "<",
        "&gt;": ">",
        "&amp;": "&",
        "&quot;": '"',
        "&#39;": "'",
        "&copy;": "©",
        "&reg;": "®",
        "&trade;": "™",
        "&hellip;": "…",
        "&mdash;": "—",
        "&ldquo;": '"',
        "&rdquo;": '"',
        "&lsquo;": "'",
        "&rsquo;": "'",
      };
      Object.keys(entities).forEach((key) => {
        text = text.replace(new RegExp(key, "g"), entities[key]);
      });

      // 清理空白
      if (removeEmptyLines) {
        text = text.replace(/\n\s*\n/g, "\n");
      }

      // 按行处理，去除行首行尾空格
      text = text
        .split("\n")
        .map((line) => line.trim())
        .filter((line) => line || !removeEmptyLines)
        .join("\n");

      return text.trim();
    },

    textToHtml(text) {
      // 如果内容包含换行，转换为 HTML
      if (text.includes("\n")) {
        return text
          .replace(/&/g, "&amp;")
          .replace(/</g, "&lt;")
          .replace(/>/g, "&gt;")
          .replace(/\n/g, "<br>");
      }

      return `<p>${text}</p>`;
    },

    getPromptDetail() {
      if (this.promptItem != null) {
        let params = {
          group_id: this.groupID,
          version: this.promptItem.version,
        };
        getPromptDetailAPI(params)
          .then((response) => {
            if (response.code == 200) {
              this.prompt = response.data;
              this.prompt.id = this.promptItem.id;
              this.prompt.content = this.textToHtml(this.prompt.content);
            } else {
              toast.error(response.message);
            }
          })
          .catch(() => {})
          .finally(() => {});
      }
    },

    newPrompt() {
      if (!this.isValid) return;
      if (this.isExist) return;

      if (!this.detectError()) return;

      let tempPrompt = JSON.parse(JSON.stringify(this.prompt));
      tempPrompt.content = this.htmlToText(tempPrompt.content);
      tempPrompt.group_id = this.groupID;

      editPromptAPI(tempPrompt)
        .then((response) => {
          if (response.code == 200) {
            if (!tempPrompt.id) {
              toast.success("添加成功," + " 版本号:" + response.data.version);
            } else {
              toast.success("修改成功," + " 版本号:" + response.data.version);
            }

            this.$emit("sure");
          } else {
            toast.error(response.message);
          }
        })
        .catch(() => {})
        .finally(() => {
        });
    },
  },
};
</script>
