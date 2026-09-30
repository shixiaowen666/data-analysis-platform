<template>
  <div class="think-container">
    <div
      v-for="block in visibleBlocks"
      :key="block.id"
      class="block-wrapper"
      v-html="block.html"
    ></div>
    <div ref="sentinel" class="sentinel"></div>
  </div>
</template>

<script>
import MarkdownIt from 'markdown-it';
import { v4 as uuidv4 } from 'uuid';

const md = new MarkdownIt({
  html: false,
  linkify: false,
  typographer: false,
  breaks: true,
  highlight: (str) => `<pre><code>${str}</code></pre>`
});

const defaultHeadingOpen = md.renderer.rules.heading_open || function(tokens, idx, options, env, self) {
  return self.renderToken(tokens, idx, options);
};

md.renderer.rules.heading_open = function(tokens, idx, options, env, self) {
  const token = tokens[idx];
  const tag = token.tag;
  if (tag === 'h3' && env && typeof env.stepCounter === 'number') {
    env.stepCounter += 1;
    token.attrSet('data-step', env.stepCounter);
  }
  return defaultHeadingOpen.call(this, tokens, idx, options, env, self);
};

export default {
  name: 'ThinkRenderer',
  props: {
    content: { type: String, required: true }
  },
  data() {
    return {
      allBlocks: [],
      visibleBlocks: [],
      observer: null,
      initialLoadCount: 10,
      batchSize: 10,
      stepCounter: 0
    };
  },
  watch: {
    content: {
      immediate: true,
      handler(val) {
        this.stepCounter = 0;
        // 先尝试按 h3 分割
        let sections = val.split(/(?=^###\s+)/m).filter(s => s.trim() !== '');
        // 如果没有 h3，改为按空行分割
        if (sections.length === 0) {
          sections = val.split(/\n\s*\n/).filter(s => s.trim() !== '');
        }
        if (sections.length === 0) sections = [' '];

        this.allBlocks = sections.map(section => {
          const env = { stepCounter: this.stepCounter };
          const html = md.render(section, env);
          this.stepCounter = env.stepCounter;
          const hasH3 = /<h3/i.test(html);
        
        const hasOnlyH3 = /^\s*<h3[^>]*>.*?<\/h3>\s*$/i.test(html.trim());

        // 只有包含 H3 且不止 H3 才显示竖线
        const showVerticalLine = hasH3 && !hasOnlyH3;
        const wrapperClass = `markdown-body${showVerticalLine ? ' has-h3' : ''}`;

        //const wrapperClass = `markdown-body${hasH3 ? ' has-h3' : ''}`;
          
          return {
            id: uuidv4(),
            // 直接渲染 markdown-body，竖线加在 .block-wrapper 上
            //html: `<div class="markdown-body has-h3">${html}</div>`
            html: `<div class="${wrapperClass}">${html}</div>`
          };
        });
        this.visibleBlocks = this.allBlocks.slice(0, this.initialLoadCount);
        this.setupObserver();
      }
    }
  },
  methods: {
    setupObserver() {}
  },
  beforeDestroy() { }
};
</script>

<style scoped>
.think-container { word-wrap: break-word; }
.block-wrapper {
  margin-bottom: 0.5em;
  position: relative;        /* 竖线定位基准 */
  padding-left: 15px;        /* 为竖线和圆圈留空间 */
  padding-right: 15px;
}
.sentinel { height: 1px; visibility: hidden; }
</style>