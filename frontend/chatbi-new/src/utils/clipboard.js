
/**
 * 复制文本到剪贴板（兼容所有浏览器）
 * @param {string} text - 要复制的文本
 * @returns {Promise<void>}
 */
export function copyToClipboard(text) {
  return new Promise((resolve, reject) => {
    // 1. 优先使用 Clipboard API
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text)
        .then(resolve)
        .catch(err => {
          // 如果 Clipboard API 失败（如权限问题），降级到 execCommand
          fallbackCopy(text, resolve, reject);
        });
    } else {
      // 2. 不支持 Clipboard API，直接使用 execCommand
      fallbackCopy(text, resolve, reject);
    }
  });
}

/**
 * 降级方案：使用 document.execCommand('copy')
 */
function fallbackCopy(text, resolve, reject) {
  // 创建一个临时的 textarea 元素
  const textarea = document.createElement('textarea');
  textarea.value = text;
  // 移出视口并隐藏，防止影响页面布局
  textarea.style.position = 'fixed';
  textarea.style.left = '-9999px';
  textarea.style.top = '-9999px';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  
  // 选中文本
  textarea.select();
  try {
    const successful = document.execCommand('copy');
    if (successful) {
      resolve();
    } else {
      reject(new Error('execCommand copy failed'));
    }
  } catch (err) {
    reject(err);
  } finally {
    document.body.removeChild(textarea);
  }
}

