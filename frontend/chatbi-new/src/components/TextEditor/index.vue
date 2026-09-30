<template>
  <div style="width:100%;">
    <input
      type="file"
      ref="fileInput"
      accept=".txt,.doc,.docx"
      @change="handleFileSelect"
      style="display: none"
    />
    
    <div style="display: flex; align-items: center; gap: 10px;">
      <span style="width:60px;flex-shrink: 0;">内容<span style="color: red">*</span></span>
      <el-button type="primary" @click="$refs.fileInput.click()" v-if="!readOnly" style="flex-shrink: 0;">
        <i class="el-icon-upload2"></i> 导入文件
      </el-button>
      
      <div class="file-info" style="flex: 1; min-width: 0;display: flex;gap:10px;" v-if="isExport">

        <truncate-tip flex-shrink: 1; style="max-width: 70%;color: #409EFF;background-color: #ecf5ff;
    border-color: #d9ecff;border-radius: 999px;
    font-weight: 500;
    border: none;padding: 0 10px;
    line-height: 30px;"
                      :text="fileName"
                    />

   <truncate-tip flex-shrink: 0; style="color: rgb(27 27 28);;background-color: #e9e9eb;
    border-color: #e9e9eb;border-radius: 999px;
    font-weight: 500;
    border: none;padding: 0 10px;
    line-height: 30px;"
                      :text="`大小:${fileSize}`"
                    />
     <truncate-tip flex-shrink: 0; style="color: #67c23a;background-color: #f0f9eb;
    border-color: #e1f3d8; border-radius: 999px;
    font-weight: 500;
    border: none;padding: 0 10px;
    line-height: 30px;"
                      :text="`字数:${wordCount}`"
                    />
      </div>
    </div>
    
    <div class="content-box" style="margin-top:5px;">
      <!-- 使用 wangeditor -->
      <WangEditor
        ref="editor"
        v-model="content"
        :readOnly="readOnly"
        :height="editHeight"
        :showToolbar="!readOnly"
        @change="onContentChange"
      />
    </div>
  </div>
</template>

<script>
import mammoth from 'mammoth';
import WangEditor from '@/components/wangEditor'; // 上面的组件
import TruncateTip from "@/components/TruncateTip";
import toast from '@/utils/toast';

export default {
  name: "TextEditor",
  components: {
    WangEditor,
    TruncateTip
  },
  props: {
    fileContent: { type: String, default: '' },
    readOnly: { type: Boolean, default: false },
    editHeight: { type: Number, default: 400 }
  },
  data() {
    return {
      content: this.fileContent,
      fileName: '',
      fileSize: '',
      wordCount: 0,
      isExport: false
    };
  },
  watch: {
    fileContent: {
      handler(val) {
        // val 是字符串，直接使用
        this.content = val
      },
      immediate: true
    }  
  },
  methods: {
    onContentChange(html) {
      this.$emit('update:fileContent', html);
      this.wordCount = this.getWordCount();
    },
    
    getWordCount() {
      if (this.$refs.editor) {
        return this.$refs.editor.getWordCount();
      }
      return 0;
    },
    
    async handleFileSelect(event) {
      const file = event.target.files[0];
      if (!file) return;
      
      this.fileName = file.name;
      this.fileSize = this.formatFileSize(file.size);
      
      try {
        const content = await this.parseFile(file);
        this.content = content;
        this.wordCount = this.countWords(content);
        toast.success('读取成功')
      } catch (error) {
        toast.error('读取失败: ' + error.message)
      }
      
      event.target.value = '';
    },
    
    parseFile(file) {
      return new Promise((resolve, reject) => {
        const fileName = file.name.toLowerCase();
        
        if (fileName.endsWith('.docx')) {
          const reader = new FileReader();
          reader.onload = async (e) => {
            try {
              const result = await mammoth.convertToHtml({ 
                arrayBuffer: e.target.result 
              });
              this.isExport = true;
              resolve(result.value);
            } catch (error) {
              reject(error);
            }
          };
          reader.onerror = reject;
          reader.readAsArrayBuffer(file);
        } else if (fileName.endsWith('.doc')) {
          const reader = new FileReader();
          reader.onload = (e) => {
            const text = e.target.result;
            const cleanText = text.replace(/[^\x20-\x7E\u4e00-\u9fa5\n\r\t]/g, '');
            if (cleanText.trim()) {
              this.isExport = true;
              resolve(`<pre>${cleanText}</pre>`);
            } else {
              reject(new Error('请将 .doc 文件另存为 .docx 或 .txt 格式'));
            }
          };
          reader.onerror = reject;
          reader.readAsText(file, 'UTF-8');
        } else if (fileName.endsWith('.txt')) {
          const reader = new FileReader();
          reader.onload = (e) => {
            const text = e.target.result;
            this.isExport = true;
            resolve(`<pre>${text}</pre>`);
          };
          reader.onerror = reject;
          reader.readAsText(file, 'UTF-8');
        } else {
          reject(new Error('不支持的文件格式，请上传 .txt .doc .docx'));
        }
      });
    },
    
    countWords(text) {
      const plainText = text.replace(/<[^>]*>/g, '').replace(/\s/g, '');
      return plainText.length;
    },
    
    formatFileSize(bytes) {
      if (bytes === 0) return '0 B';
      const k = 1024;
      const sizes = ['B', 'KB', 'MB', 'GB'];
      const i = Math.floor(Math.log(bytes) / Math.log(k));
      return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
    }
  }
};
</script>