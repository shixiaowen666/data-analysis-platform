import MarkdownIt from 'markdown-it';
import throttle from 'lodash/throttle';

// 1. 创建 markdown-it 实例（单例模式，避免重复初始化）
const md = new MarkdownIt({
  html: true,
  linkify: true,
  // ... 其他配置
});
// 2. 创建节流渲染函数
export const renderMarkdown = throttle((markdownText) => {
  return md.render(markdownText);
}, 80); // 80ms 的间隔在视觉上流畅且能减轻CPU负担[reference:1]

//export const renderMarkdown = (markdownText) => md.render(markdownText);



export function parseThinkBlocks(text) {
  if (!text || !String(text).trim()) return [];
  const raw = String(text);

  // 按 ### 标题切段
  let sections = raw.split(/(?=^###\s+)/m).filter((s) => s.trim());
  if (!sections.length) sections = raw.split(/\n\s*\n/).filter((s) => s.trim());

  let no = 0;
  return sections.map((sec, i) => {
    const isStep = /^###\s+/.test(sec.trim());
    if (isStep) no += 1;
    // ### 降级为 h3（markdown-it 默认已是 h3）
    const html = renderMarkdown(sec);
    return {
      id: `blk-${i}`,
      type: isStep ? 'step' : 'plain',
      no: isStep ? no : 0,
      html,
    };
  });
}