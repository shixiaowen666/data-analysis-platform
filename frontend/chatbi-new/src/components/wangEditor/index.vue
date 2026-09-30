<template>
  <div ref="editorContainer" class="editor-wrapper"></div>
</template>


<script>
import E from 'wangeditor';

export default {
  name: 'WangEditor',
  props: {
    value: { type: String, default: '' },
    readOnly: { type: Boolean, default: false },
    height: { type: Number, default: 400 },
    showToolbar: { type: Boolean, default: true },
    placeholder: { type: String, default: '请输入内容...' }
  },
  data() {
    return {
      editor: null,
      isReady: false
    };
  },
  mounted() {
    this.$nextTick(() => {
      this.initEditor();
    });
  },
  beforeDestroy() {
    if (this.editor) {
      try {
        this.editor.destroy();
      } catch (error) {
        console.warn('编辑器销毁失败:', error);
      }
      this.editor = null;
      this.isReady = false;
    }
  },
  methods: {
    initEditor() {
      if (!this.$refs.editorContainer) {
        console.warn('编辑器容器不存在');
        return;
      }

      try {
        this.editor = new E(this.$refs.editorContainer);
        
        // ===== 基本配置 =====
        this.editor.config.height = this.height;
        this.editor.config.placeholder = this.placeholder;
        
        // ✅ 方法1：配置中设置（但可能不生效）
        this.editor.config.readOnly = this.readOnly;
        
        const defaultContent = this.value || '<p><br></p>';
        
        // ===== 自定义工具栏 =====
        if (this.showToolbar) {
          this.editor.config.menus = [
            'head',
            'bold',
            'foreColor',
            'backColor',
            'justify',
            'table',
            'code',
            'undo',
            'redo'
          ];
        } else {
          this.editor.config.menus = [];
        }
        
        // ===== 图片上传配置 =====
        this.editor.config.uploadImgServer = '/api/upload';
        this.editor.config.uploadImgHeaders = {
          'Authorization': 'Bearer ' + localStorage.getItem('token')
        };
        this.editor.config.uploadImgMaxSize = 2 * 1024 * 1024;
        this.editor.config.uploadImgMaxLength = 5;
        this.editor.config.uploadImgAccept = ['jpg', 'jpeg', 'png', 'gif'];
        
        this.editor.config.uploadImgHooks = {
          success: (xhr, editor, result) => {
            console.log('图片上传成功:', result);
          },
          fail: (xhr, editor, result) => {
            console.error('图片上传失败:', result);
          },
          error: (xhr, editor) => {
            console.error('图片上传出错:', xhr);
          }
        };
        
        // ===== 内容变化监听 =====
        this.editor.config.onchange = (html) => {
          if (!html || html.trim() === '' || html === '<p></p>') {
            html = '<p><br></p>';
            if (this.editor && this.isReady) {
              this.editor.txt.html(html);
            }
          }
          this.$emit('input', html);
          this.$emit('change', html);
        };
        
        this.editor.config.onblur = (html) => {
          this.$emit('blur', html);
        };
        
        this.editor.txt.html(defaultContent);
        
        // ===== 创建编辑器 =====
        this.editor.create();
        this.isReady = true;
        
        // ✅ 关键：创建完成后立即应用只读状态
        this.$nextTick(() => {
          this.applyReadOnly();
          this.ensureContent();
        });
        
      } catch (error) {
        console.error('编辑器初始化失败:', error);
        this.$nextTick(() => {
          this.initEditor();
        });
      }
    },
    
    // ✅ 强制应用只读状态
    applyReadOnly() {
      if (!this.editor || !this.isReady) return;
      
      try {
        if (this.readOnly) {
          // 禁用编辑器
          this.editor.disable();
          // ✅ 额外处理：隐藏工具栏（如果有）
          if (!this.showToolbar) {
            const toolbar = this.$refs.editorContainer?.querySelector('.w-e-toolbar');
            if (toolbar) {
              toolbar.style.display = 'none';
            }
          }
        } else {
          this.editor.enable();
          // 显示工具栏
          if (!this.showToolbar) {
            const toolbar = this.$refs.editorContainer?.querySelector('.w-e-toolbar');
            if (toolbar) {
              toolbar.style.display = '';
            }
          }
        }
        // 同步更新 config
        this.editor.config.readOnly = this.readOnly;
      } catch (error) {
        console.warn('应用只读模式失败:', error);
      }
    },
    
    // ✅ 切换只读模式
    setReadOnly(readOnly) {
      if (!this.editor || !this.isReady) return;
      
      try {
        if (readOnly) {
          this.editor.disable();
        } else {
          this.editor.enable();
        }
        this.editor.config.readOnly = readOnly;
        
        // 触发事件让父组件知道状态变化
        //this.$emit('readonly-change', readOnly);
      } catch (error) {
        console.warn('切换只读模式失败:', error);
      }
    },
    
    ensureContent() {
      if (!this.editor || !this.isReady) return;
      
      try {
        let content = this.editor.txt.html();
        if (!content || content.trim() === '' || 
            content === '<p></p>' || content === '<p><br></p>') {
          this.editor.txt.html('<p><br></p>');
        }
      } catch (error) {
        console.warn('确保内容失败:', error);
      }
    },
    
    getContent() {
      if (!this.editor || !this.isReady) return '';
      try {
        return this.editor.txt.html();
      } catch (error) {
        return '';
      }
    },
    
    getText() {
      if (!this.editor || !this.isReady) return '';
      try {
        return this.editor.txt.text();
      } catch (error) {
        return '';
      }
    },
    
    setContent(html) {
      if (!this.editor || !this.isReady) return;
      
      try {
        if (!html || html.trim() === '') {
          html = '<p><br></p>';
        }
        this.editor.txt.html(html);
      } catch (error) {
        console.warn('设置内容失败:', error);
      }
    },
    
    clearContent() {
      if (this.editor && this.isReady) {
        try {
          this.editor.txt.html('<p><br></p>');
        } catch (error) {
          console.warn('清空内容失败:', error);
        }
      }
    },
    
    getWordCount() {
      if (!this.editor || !this.isReady) return 0;
      try {
        return this.editor.txt.text().length;
      } catch (error) {
        return 0;
      }
    },
    
    destroyEditor() {
      if (this.editor) {
        try {
          this.editor.destroy();
        } catch (error) {
          console.warn('销毁编辑器失败:', error);
        }
        this.editor = null;
        this.isReady = false;
      }
    }
  },
  watch: {
    value(newVal) {
      if (this.editor && this.isReady) {
        try {
          const currentContent = this.editor.txt.html();
          if (newVal !== currentContent) {
            const content = newVal || '<p><br></p>';
            this.editor.txt.html(content);
          }
        } catch (error) {
          console.warn('更新内容失败:', error);
        }
      }
    },
    readOnly(newVal) {
      // ✅ 响应式切换
      this.setReadOnly(newVal);
    }
  }
};
</script>

<style scoped lang="scss">
.editor-wrapper {
  border-radius: 4px;
  overflow: hidden;
  border: 1px solid #dcdfe6;

}

::v-deep {

  .w-e-toolbar {
    border-bottom: 1px solid #dcdfe6;
    flex-wrap: wrap;
    background: #fafafa;
  }

  .w-e-text-container {
    min-height: 400px;
    position: relative;
    cursor: pointer !important;

    .w-e-text {
      padding: 10px 15px !important;
      min-height: 380px;
      font-size: 16px !important;
      line-height: 1.6 !important;
      outline: none !important;
      cursor: text !important;
    }

    .w-e-text-placeholder {
      position: absolute !important;
      left: 15px !important;
      top: 10px !important;
      color: #b3b3b3 !important;
      font-size: 16px !important;
      pointer-events: none !important;
      z-index: 1 !important;
      line-height: 1.6 !important;
      user-select: none !important;
      font-weight: normal !important;
    }
  }
    .w-e-text::-webkit-scrollbar-thumb:hover {
      cursor: pointer !important;
    }


    .w-e-text::-webkit-scrollbar-thumb {
      background: #c1c1c1;
      border-radius: 3px;
       cursor: pointer !important;
    }
  .w-e-text::-webkit-scrollbar {
      width: 8px;
      height: 6px;
    }
.w-e-text::-webkit-scrollbar-track {
      background: transparent;
    }

  //.w-e-scroll {
  //  max-height: 400px;
  //  overflow-y: auto;
  //}

  
}
</style>

